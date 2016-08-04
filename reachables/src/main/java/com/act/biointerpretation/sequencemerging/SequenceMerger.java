package com.act.biointerpretation.sequencemerging;

import act.installer.GenbankInstaller;
import act.installer.UniprotInstaller;
import act.server.NoSQLAPI;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.BiointerpretationProcessor;
import com.mongodb.util.JSON;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class SequenceMerger extends BiointerpretationProcessor {
  private static final Logger LOGGER = LogManager.getFormatterLogger(SequenceMerger.class);
  private static final String PROCESSOR_NAME = "Sequence Merger";

  public SequenceMerger(NoSQLAPI noSQLAPI) {
    super(noSQLAPI);
  }

  @Override
  public String getName() {
    return PROCESSOR_NAME;
  }

  @Override
  public void init() {
    // Do nothing for this class, as there's no initialization necessary.
    markInitialized();
  }

  @Override
  public void run() throws IOException, ReactionException{
    LOGGER.info("copying all chemicals");
    super.processChemicals();
    LOGGER.info("copying all reactions");
    processReactions();
    LOGGER.info("processing sequences for deduplication");
    processSequences();
  }

  /**
   * Copies all reactions over to the WriteDB
   */
  @Override
  public void processReactions() {
    Iterator<Reaction> iterator = getNoSQLAPI().readRxnsFromInKnowledgeGraph();

    while (iterator.hasNext()) {

      Reaction oldRxn = iterator.next();
      Long oldId = (long) oldRxn.getUUID();
      int newId = getNoSQLAPI().writeToOutKnowlegeGraph(oldRxn);

      // TODO: Store the oldId somewhere; you can maybe cache it in some HashMap of oldId to newId

    }
  }

  @Override
  public void processSequences() {
    Iterator<Seq> sequences = getNoSQLAPI().readSeqsFromInKnowledgeGraph();
    Map<UniqueSeq, List<Seq>> sequenceGroups = new HashMap<>();

    // stores all sequences with the same ecnum, organism, and protein sequence in the same list
    while (sequences.hasNext()) {
      Seq sequence = sequences.next();
      UniqueSeq uniqueSeq = new UniqueSeq(sequence);
      List<Seq> matchingSeqs = sequenceGroups.get(uniqueSeq);
      if (matchingSeqs != null) {
        matchingSeqs.add(sequence);
        sequenceGroups.put(uniqueSeq, matchingSeqs);
      } else {
        List<Seq> seqs = new ArrayList<>();
        seqs.add(sequence);
        sequenceGroups.put(uniqueSeq, seqs);
      }
    }

    Iterator sequenceGroupIterator = sequenceGroups.entrySet().iterator();

    while (sequenceGroupIterator.hasNext()) {
      Map.Entry pair = (Map.Entry) sequenceGroupIterator.next();
      List<Seq> allMatchedSeqs = (List<Seq>) pair.getValue();

      Set<Long> matchedSeqsIDs = new HashSet<>();
      Set<Long> reactionRefs = new HashSet<>();
      for (Seq sequence : allMatchedSeqs) {
        matchedSeqsIDs.add((long) sequence.getUUID());
        reactionRefs.addAll(sequence.getReactionsCatalyzed());
      }

      Seq mergedSequence = mergeSequences(allMatchedSeqs);

      Long id = (long) getNoSQLAPI().getWriteDB().submitToActSeqDB(
          mergedSequence.get_srcdb(),
          mergedSequence.get_ec(),
          mergedSequence.get_org_name(),
          mergedSequence.getOrgId(),
          mergedSequence.get_sequence(),
          mergedSequence.get_references(),
          mergedSequence.getReactionsCatalyzed(),
          MongoDBToJSON.conv(mergedSequence.get_metadata())
      );

      // TODO: copy organisms; do we need to copy cofactors?

      updateReactionsReferencingDuplicatedSeqs(matchedSeqsIDs, reactionRefs, id);

    }

  }

  // TODO: this class should handle organism prefix matching; will have to adjust orgId as well
  private static class UniqueSeq {
    String ecnum;
    String organism;
    String protSeq;

    private UniqueSeq (Seq sequence) {
      ecnum = sequence.get_ec();
      organism = sequence.get_org_name();
      protSeq = sequence.get_sequence();
    }

    @Override
    public int hashCode() {
      return ecnum.hashCode() + organism.hashCode() + protSeq.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }

      if (getClass() != obj.getClass()) {
        return false;
      }

      final UniqueSeq other = (UniqueSeq) obj;

      if (this.ecnum.equals(other.ecnum) &&
          this.organism.equals(other.organism) &&
          this.protSeq.equals(other.protSeq)) {
        return true;
      }

      return false;
    }
  }

  private Seq mergeSequences(List<Seq> sequences) {
    if (sequences.size() < 1) {
      return null;
    } else if (sequences.size() == 1) {
      return sequences.get(0);
    }

    Seq firstSequence = sequences.get(0);
    JSONObject firstSeqMetadata = firstSequence.get_metadata();

    firstSeqMetadata.remove("proteinExistence");

    JSONArray comment = firstSeqMetadata.getJSONArray("comment");

    Set<Long> brendaIds = new HashSet<>();
    for (int i = 0; i < comment.length(); i++) {
      JSONObject commentObject = comment.getJSONObject(i);
      if (commentObject.has("text") && commentObject.has("type") &&
          commentObject.getString("type").equals("brenda_id")) {

        brendaIds.add(commentObject.getLong("text"));

      }
    }

    // converting comment JSONArray to xref JSONObject
    firstSeqMetadata.remove("comment");

    JSONObject xrefObject = new JSONObject();
    xrefObject.put("brenda_id", brendaIds);

    firstSeqMetadata.put("xref", xrefObject);
    firstSequence.set_metadata(firstSeqMetadata);

    Seq mergedSequence = new Seq(
        -1, // assume ID will be set when the sequence is written to the DB
        firstSequence.get_ec(),
        firstSequence.getOrgId(),
        firstSequence.get_org_name(),
        firstSequence.get_sequence(),
        firstSequence.get_references(),
        MongoDBToJSON.conv(firstSequence.get_metadata()),
        firstSequence.get_srcdb()
    );

    for (Seq sequence : sequences) {
      if (mergedSequence.get_ec() != sequence.get_ec() ||
          mergedSequence.get_sequence() != sequence.get_sequence() ||
          mergedSequence.get_org_name() != sequence.get_org_name()) {
        LOGGER.error("matching sequence map constructed improperly; at least one of ec #, protein sequence, & " +
            "organism don't match");
        continue;
      }

      List<JSONObject> mergedRefs = mergeReferences(mergedSequence.get_references(), sequence.get_references());
      mergedSequence.set_references(mergedRefs);

      JSONObject mergedMetadata = mergeMetadata(mergedSequence.get_metadata(), sequence.get_metadata());
      mergedSequence.set_metadata(mergedMetadata);

      Set<Long> reactionRefs = mergeReactionRefs(mergedSequence.getReactionsCatalyzed(), sequence.getReactionsCatalyzed());
      mergedSequence.setReactionsCatalyzed(reactionRefs);

    }

    return mergedSequence;
  }

  private Set<Long> mergeReactionRefs(Set<Long> mergedReactionRefs, Set<Long> newReactionRefs) {

    if (mergedReactionRefs == null || mergedReactionRefs.size() == 0) {

      if (newReactionRefs == null || newReactionRefs.size() == 0) {

        return null;

      }

      return newReactionRefs;

    }

    if (newReactionRefs != null && newReactionRefs.size() != 0) {

      for (Long newReactionRef : newReactionRefs) {

        // Set operations automatically handle the case that the newReactionRef already exists in the mergedReactionRefs
        mergedReactionRefs.add(newReactionRef);

      }

    }

    return mergedReactionRefs;

  }

  private JSONObject mergeMetadata(JSONObject mergedMetadata, JSONObject newMetadata) {

    // used to ensure that the new gene name is added to the synonyms list in the case that it doesn't match the old gene name
    boolean geneNameMatches = true;

    if (newMetadata.has("name")) {

      String newName = newMetadata.getString("name");

      if (mergedMetadata.has("name")) {

        String oldName = mergedMetadata.getString("name");

        if (!oldName.equals(newName)) {
          geneNameMatches = false;
        }

      } else {

        mergedMetadata.put("name", newName);

      }
    }

    if (newMetadata.has("synonyms")) {

      if (!geneNameMatches) {
        newMetadata.append("synonyms", newMetadata.getString("name"));
      }

      JSONArray newSynonyms = newMetadata.getJSONArray("synonyms");

      if (mergedMetadata.has("synonyms")) {

        for (int i = 0; i < newSynonyms.length(); i++) {
          mergedMetadata = GenbankInstaller.updateArrayField("synonyms", newSynonyms.getString(i), mergedMetadata);
        }

      } else {

        mergedMetadata.put("synonyms", newSynonyms);

      }
    }

    if (newMetadata.has("product_names")) {

      JSONArray newProductNames = newMetadata.getJSONArray("product_names");

      if (mergedMetadata.has("product_names")) {

        for (int i = 0; i < newProductNames.length(); i++) {
          mergedMetadata = GenbankInstaller.updateArrayField("product_names", newProductNames.getString(i), mergedMetadata);
        }

      } else {

        mergedMetadata.put("product_names", newProductNames);

      }

    }

    if (newMetadata.has("accession")) {

      JSONObject newAccession = newMetadata.getJSONObject("accession");

      if (mergedMetadata.has("accession")) {

        mergedMetadata = GenbankInstaller.updateAccessions(newAccession, mergedMetadata, Seq.AccType.genbank_nucleotide,
            GenbankInstaller.NUCLEOTIDE_ACCESSION_PATTERN);
        mergedMetadata = GenbankInstaller.updateAccessions(newAccession, mergedMetadata, Seq.AccType.genbank_protein,
            GenbankInstaller.PROTEIN_ACCESSION_PATTERN);
        mergedMetadata = GenbankInstaller.updateAccessions(newAccession, mergedMetadata, Seq.AccType.uniprot,
            UniprotInstaller.UNIPROT_ACCESSION_PATTERN);

      } else {

        mergedMetadata.put("accession", newAccession);

      }

    }

    // converts old comment JSONArrays to fit the new xref JSONObject model
    if (newMetadata.has("comment")) {

      JSONArray comment = newMetadata.getJSONArray("comment");

      Set<Long> newBrendaIds = new HashSet<>();
      for (int i = 0; i < comment.length(); i++) {
        JSONObject commentObject = comment.getJSONObject(i);
        if (commentObject.has("text") && commentObject.has("type") &&
            commentObject.getString("type").equals("brenda_id")) {

          newBrendaIds.add(commentObject.getLong("text"));

        }
      }

      if (mergedMetadata.has("xref") && mergedMetadata.getJSONObject("xref").has("brenda_id")) {

        JSONArray brendaIds = mergedMetadata.getJSONObject("xref").getJSONArray("brenda_id");
        Set<Long> oldBrendaIds = new HashSet<>();
        for (int i = 0; i < brendaIds.length(); i++) {
          oldBrendaIds.add((Long) brendaIds.get(i));
        }

        for (Long brendaId : newBrendaIds) {

          // set operations handle duplicate case
          oldBrendaIds.add(brendaId);
        }

        mergedMetadata.getJSONObject("xref").put("brenda_id", oldBrendaIds);

      } else {

        JSONObject xrefObject = new JSONObject();
        xrefObject.put("brenda_id", newBrendaIds);
        mergedMetadata.put("xref", xrefObject);

      }

    }


    return mergedMetadata;
  }

  // can use set operations in order to have less logic
  // may not need to return anything at all since you're changing the referenced mergedRefs
  private List<JSONObject> mergeReferences(List<JSONObject> mergedRefs, List<JSONObject> newRefs) {

    for (JSONObject newRef : newRefs) {

      if (newRef.getString("src").equals("PMID")) {

        boolean pmidExists = false;
        String newPmid = newRef.getString("val");

        ListIterator<JSONObject> mergedRefsIterator = mergedRefs.listIterator();

        while (mergedRefsIterator.hasNext()) {
          JSONObject mergedRef = mergedRefsIterator.next();

          if (mergedRef.getString("src").equals("PMID") &&
              mergedRef.getString("val").equals(newPmid)) {

            pmidExists = true;
            break;

          }

        }

        if (!pmidExists) {

          mergedRefsIterator.add(newRef);

        }
      } else if (newRef.getString("src").equals("Patent")) {

        boolean patentExists = false;
        String newCountryCode = newRef.getString("country_code");
        String newPatentNumber = newRef.getString("patent_number");
        String newPatentYear = newRef.getString("patent_year");

        ListIterator<JSONObject> mergedRefsIterator = mergedRefs.listIterator();

        while (mergedRefsIterator.hasNext()) {
          JSONObject mergedRef = mergedRefsIterator.next();

          if (mergedRef.getString("src").equals("Patent") &&
              mergedRef.getString("country_code").equals(newCountryCode) &&
              mergedRef.getString("patent_number").equals(newPatentNumber) &&
              mergedRef.getString("patent_year").equals(newPatentYear)) {

            patentExists = true;
            break;

          }


        }

        if (!patentExists) {

          mergedRefsIterator.add(newRef);

        }
      }
    }

    return mergedRefs;
  }

  private void updateReactionsReferencingDuplicatedSeqs(Set<Long> matchedSeqsIDs, Set<Long> reactionRefs, Long newSeqID) {
    for (Long reactionRef : reactionRefs) {
      Reaction reaction = getNoSQLAPI().readReactionFromInKnowledgeGraph(reactionRef);
      Set<JSONObject> proteins = reaction.getProteinData();

      for (JSONObject protein : proteins) {
        JSONArray sequenceIDs = protein.getJSONArray("sequences");
        Set<Long> newSequenceIDs = new HashSet<>();

        for (int i = 0; i < sequenceIDs.length(); i++) {
          if (matchedSeqsIDs.contains(sequenceIDs.getLong(i))) {
            newSequenceIDs.add(newSeqID);
          } else {
            newSequenceIDs.add(sequenceIDs.getLong(i));
          }
        }

        protein.put("sequences", new JSONArray(newSequenceIDs));
      }

      reaction.setProteinData(proteins);

      // since reactions are already copied over to the write db while maintaining source ID, we update those reactions
      getNoSQLAPI().getWriteDB().updateActReaction(reaction, reactionRef.intValue());

    }
  }

}
