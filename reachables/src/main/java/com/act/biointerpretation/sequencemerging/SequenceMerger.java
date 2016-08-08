package com.act.biointerpretation.sequencemerging;

import act.installer.GenbankInstaller;
import act.installer.UniprotInstaller;
import act.server.NoSQLAPI;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.BiointerpretationProcessor;
import com.act.biointerpretation.Utils.OrgMinimalPrefixGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
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
  private static final String SYNONYMS = "synonyms";
  private static final String SOURCE_SEQUENCE_IDS = "source_sequence_ids";
  private static final String PROTEIN_EXISTENCE = "proteinExistence";
  private static final String COMMENT = "comment";
  private static final String TEXT = "text";
  private static final String TYPE = "type";
  private static final String BRENDA_ID = "brenda_id";
  private static final String XREF = "xref";
  private static final String NAME = "name";
  private static final String PRODUCT_NAMES = "product_names";
  private static final String ACCESSION = "accession";
  private static final String SRC = "src";
  private static final String PATENT = "Patent";
  private static final String VAL = "val";
  private static final String PATENT_YEAR = "patent_year";
  private static final String COUNTRY_CODE = "country_code";
  private static final String PATENT_NUMBER = "patent_number";
  private static final String PMID = "PMID";
  private static final String SEQUENCES = "sequences";

  // currently never queried, only used for storage that could be useful for later purposes
  private Map<Long, Long> sequenceMigrationMap = new HashMap<>();
  private Map<Long, Long> organismMigrationMap = new HashMap<>();
  private Map<Long, Long> reactionMigrationMap = new HashMap<>();

  private Map<String, String> minimalPrefixMapping;

  public SequenceMerger(NoSQLAPI noSQLAPI) {
    super(noSQLAPI);
  }

  @Override
  public String getName() {
    return PROCESSOR_NAME;
  }

  @Override
  public void init() {
    Map<String, Long> orgMap = new HashMap<>();

    Iterator<Organism> orgIterator = getNoSQLAPI().readOrgsFromInKnowledgeGraph();

    while (orgIterator.hasNext()) {
      Organism org = orgIterator.next();
      orgMap.put(org.getName(), 1L);
    }

    OrgMinimalPrefixGenerator prefixGenerator = new OrgMinimalPrefixGenerator(orgMap);
    minimalPrefixMapping = prefixGenerator.getMinimalPrefixMapping();

    markInitialized();
  }

  @Override
  public void run() throws IOException, ReactionException {
    LOGGER.info("copying all chemicals");
    super.processChemicals();

    /* reactions should be written before the sequence merging so that the sequence references in the
    reactions collection can be updated  */
    LOGGER.info("copying all reactions");
    processReactions();
    LOGGER.info("processing sequences for deduplication");
    processSequences();
  }

  /**
   * Copies all reactions over to the WriteDB and stores the mapping from old ID to new ID in reactionMigrationMap
   */
  @Override
  public void processReactions() {
    Iterator<Reaction> reactionIterator = getNoSQLAPI().readRxnsFromInKnowledgeGraph();

    while (reactionIterator.hasNext()) {
      Reaction oldRxn = reactionIterator.next();
      Long oldId = (long) oldRxn.getUUID();
      Long newId = (long) getNoSQLAPI().writeToOutKnowlegeGraph(oldRxn);
      reactionMigrationMap.put(oldId, newId);
    }
  }

  @Override
  public void processSequences() {
    Iterator<Seq> sequences = getNoSQLAPI().readSeqsFromInKnowledgeGraph();
    Map<UniqueSeq, List<Seq>> sequenceGroups = new HashMap<>();

    // stores all sequences with the same ecnum, organism (accounts for prefix), and protein sequence in the same list
    while (sequences.hasNext()) {
      Seq sequence = sequences.next();

      if (sequence.get_org_name() == null ||
          sequence.get_sequence() == null ||
          sequence.get_ec() == null) {
        // copy sequence directly, no merging will be possible
        writeSequence(sequence);
      }

      /* changes the organism name to its minimal prefix; must occur before stored in the sequenceGroup map so that
      all seq entries with the same minimal prefix org name, ecnum, & protein sequence are merged */
      migrateOrganism(sequence);

      UniqueSeq uniqueSeq = new UniqueSeq(sequence);
      List<Seq> matchingSeqs = sequenceGroups.get(uniqueSeq);

      if (matchingSeqs != null) {
        // add UniqueSeq object to already existent list that shares the same ecnum, organism & protein sequence
        matchingSeqs.add(sequence);
        sequenceGroups.put(uniqueSeq, matchingSeqs);
      } else {
        // create a new modifiable list for the UniqueSeq object and add a new mapping
        List<Seq> seqs = new ArrayList<>();
        seqs.add(sequence);
        sequenceGroups.put(uniqueSeq, seqs);
      }
    }

    for (Map.Entry<UniqueSeq, List<Seq>> sequenceGroup : sequenceGroups.entrySet()) {
      List<Seq> allMatchedSeqs = sequenceGroup.getValue();

      // stores the IDs of all sequences that are about to be merged
      Set<Long> matchedSeqsIDs = new HashSet<>();
      // stores the IDs of all reactions that referenced merged sequences and should now refer to the merged Sequence ID
      Set<Long> reactionRefs = new HashSet<>();

      for (Seq sequence : allMatchedSeqs) {
        matchedSeqsIDs.add((long) sequence.getUUID());
        reactionRefs.addAll(sequence.getReactionsCatalyzed());
      }

      // merges all sequences that share the same ecnum, organism and protein sequence
      Seq mergedSequence = mergeSequences(allMatchedSeqs);

      // for reference, adds all the seq IDs that were merged
      mergedSequence.get_metadata().put(SOURCE_SEQUENCE_IDS, matchedSeqsIDs);

      Long mergedSeqId = writeSequence(mergedSequence);

      // maps the old duplicate sequences to the new merged sequence entry
      for (Long matchedSeqId : matchedSeqsIDs) {
        sequenceMigrationMap.put(matchedSeqId, mergedSeqId);
      }

      // update reactions that were referencing the merged sequences so that they new refer to the new merged sequence
      updateReactionsReferencingDuplicatedSeqs(matchedSeqsIDs, reactionRefs, mergedSeqId);

    }

    // TODO: need to handle organism prefixes in the Reactions collection; Mark says to not worry about this just yet

  }

  private Long writeSequence(Seq sequence) {
    return (long) getNoSQLAPI().getWriteDB().submitToActSeqDB(
        sequence.get_srcdb(),
        sequence.get_ec(),
        sequence.get_org_name(),
        sequence.getOrgId(),
        sequence.get_sequence(),
        sequence.get_references(),
        sequence.getReactionsCatalyzed(),
        MongoDBToJSON.conv(sequence.get_metadata())
    );
  }

  /**
   * Changes organism name to its minimal prefix and updates the organism ID appropriately
   * @param sequence the Seq entry we are updating
   */
  private void migrateOrganism(Seq sequence) {
    String organismName = checkForOrgPrefix(sequence.get_org_name());
    sequence.set_organism_name(organismName);

    Long newOrgId = getNoSQLAPI().getWriteDB().getOrganismId(organismName);

    if (newOrgId == -1) {
      newOrgId = getNoSQLAPI().getWriteDB().submitToActOrganismNameDB(organismName);
    }

    organismMigrationMap.put(sequence.getOrgId(), newOrgId);
    sequence.setOrgId(newOrgId);
  }

  /**
   * Checks if there is an existing organism prefix in the prefix tree;
   * @param orgName the organism name you are checking for a valid prefix
   * @return a valid prefix
   */
  private String checkForOrgPrefix(String orgName) {
    return minimalPrefixMapping.get(orgName);
  }

  /**
   * This class is used to group sequences that share the same ecnum, organism and protein sequence
   */
  private static class UniqueSeq {
    String ecnum;
    String organism;
    String protSeq;

    private UniqueSeq (Seq sequence) {
      this.ecnum = sequence.get_ec();
      this.organism = sequence.get_org_name();
      this.protSeq = sequence.get_sequence();
    }

    @Override
    public int hashCode() {
      return ecnum.hashCode() ^ organism.hashCode() ^ protSeq.hashCode();
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

      return (this.ecnum.equals(other.ecnum) && this.organism.equals(other.organism) &&
          this.protSeq.equals(other.protSeq));
    }
  }

  private Seq mergeSequences(List<Seq> sequences) {
    if (sequences.size() < 1) {
      throw new RuntimeException("0 matched sequences in this sequence group");
    } else if (sequences.size() == 1) {
      return sequences.get(0);
    }

    Seq firstSequence = sequences.get(0);
    JSONObject firstSeqMetadata = firstSequence.get_metadata();

    // this field is empty for every Seq entry, so we're removing it
    firstSeqMetadata.remove(PROTEIN_EXISTENCE);

    /* we want to convert the brenda_ids from being stored in a comment JSONArray to being stored in
    an xref map (JSONObject) */
    JSONArray comment = firstSeqMetadata.getJSONArray(COMMENT);

    Set<Long> brendaIds = new HashSet<>();

    for (int i = 0; i < comment.length(); i++) {
      JSONObject commentObject = comment.getJSONObject(i);

      if (commentObject.has(TEXT) && commentObject.has(TYPE) &&
          commentObject.getString(TYPE).equals(BRENDA_ID)) {
        brendaIds.add(commentObject.getLong(TEXT));
      }
    }

    firstSeqMetadata.remove(COMMENT);

    JSONObject xrefObject = new JSONObject();
    xrefObject.put(BRENDA_ID, brendaIds);

    firstSeqMetadata.put(XREF, xrefObject);

    // initialized mergedSequence with firstSequence
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

    // merge the rest of the matched sequences
    for (Seq sequence : sequences) {
      if (mergedSequence.get_ec() != sequence.get_ec() ||
          mergedSequence.get_sequence() != sequence.get_sequence() ||
          mergedSequence.get_org_name() != sequence.get_org_name()) {

        String msg = "matching sequence map constructed improperly; at least one of ec #, protein sequence, & " +
            "organism don't match";

        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }

      mergeReferences(mergedSequence.get_references(), sequence.get_references());

      mergeMetadata(mergedSequence.get_metadata(), sequence.get_metadata());

      mergeReactionRefs(mergedSequence.getReactionsCatalyzed(), sequence.getReactionsCatalyzed());
    }

    return mergedSequence;
  }

  private void mergeReactionRefs(Set<Long> mergedReactionRefs, Set<Long> newReactionRefs) {
    if (mergedReactionRefs == null || mergedReactionRefs.size() == 0) {
      mergedReactionRefs = newReactionRefs;
    }

    for (Long newReactionRef : newReactionRefs) {
      // Set operations automatically handle the case that the newReactionRef already exists in the mergedReactionRefs
      mergedReactionRefs.add(newReactionRef);
    }
  }

  private void mergeMetadata(JSONObject mergedMetadata, JSONObject newMetadata) {
    if (mergedMetadata == null || mergedMetadata == new JSONObject()) {
      mergedMetadata = newMetadata;
    }

    // ensures that the new gene name is added to the synonyms list in the case that it doesn't match the old gene name
    boolean geneNameMatches = true;

    if (newMetadata.has(NAME)) {
      String newName = newMetadata.getString(NAME);

      if (mergedMetadata.has(NAME)) {
        String oldName = mergedMetadata.getString(NAME);

        if (!oldName.equals(newName)) {
          geneNameMatches = false;
        }
      } else {
        mergedMetadata.put(NAME, newName);
      }
    }

    if (newMetadata.has(SYNONYMS)) {
      if (!geneNameMatches) {
        newMetadata.append(SYNONYMS, newMetadata.getString(NAME));
      }

      JSONArray newSynonyms = newMetadata.getJSONArray(SYNONYMS);

      if (mergedMetadata.has(SYNONYMS)) {
        for (int i = 0; i < newSynonyms.length(); i++) {
          mergedMetadata = GenbankInstaller.updateArrayField(SYNONYMS, newSynonyms.getString(i), mergedMetadata);
        }
      } else {
        mergedMetadata.put(SYNONYMS, newSynonyms);
      }
    }

    if (newMetadata.has(PRODUCT_NAMES)) {
      JSONArray newProductNames = newMetadata.getJSONArray(PRODUCT_NAMES);

      if (mergedMetadata.has(PRODUCT_NAMES)) {
        for (int i = 0; i < newProductNames.length(); i++) {
          mergedMetadata = GenbankInstaller.updateArrayField(PRODUCT_NAMES, newProductNames.getString(i),
              mergedMetadata);
        }
      } else {
        mergedMetadata.put(PRODUCT_NAMES, newProductNames);
      }

    }

    if (newMetadata.has(ACCESSION)) {
      JSONObject newAccession = newMetadata.getJSONObject(ACCESSION);

      if (mergedMetadata.has(ACCESSION)) {
        mergedMetadata = GenbankInstaller.updateAccessions(newAccession, mergedMetadata, Seq.AccType.genbank_nucleotide,
            GenbankInstaller.NUCLEOTIDE_ACCESSION_PATTERN);
        mergedMetadata = GenbankInstaller.updateAccessions(newAccession, mergedMetadata, Seq.AccType.genbank_protein,
            GenbankInstaller.PROTEIN_ACCESSION_PATTERN);
        mergedMetadata = GenbankInstaller.updateAccessions(newAccession, mergedMetadata, Seq.AccType.uniprot,
            UniprotInstaller.UNIPROT_ACCESSION_PATTERN);
      } else {
        mergedMetadata.put(ACCESSION, newAccession);
      }
    }

    // converts old comment JSONArrays to fit the new xref JSONObject model
    if (newMetadata.has(COMMENT)) {
      JSONArray comment = newMetadata.getJSONArray(COMMENT);

      Set<Long> newBrendaIds = new HashSet<>();
      for (int i = 0; i < comment.length(); i++) {
        JSONObject commentObject = comment.getJSONObject(i);

        if (commentObject.has(TEXT) && commentObject.has(TYPE) &&
            commentObject.getString(TYPE).equals(BRENDA_ID)) {
          newBrendaIds.add(commentObject.getLong(TEXT));
        }
      }

      if (mergedMetadata.has(XREF) && mergedMetadata.getJSONObject(XREF).has(BRENDA_ID)) {
        JSONArray brendaIds = mergedMetadata.getJSONObject(XREF).getJSONArray(BRENDA_ID);

        Set<Long> oldBrendaIds = new HashSet<>();

        for (int i = 0; i < brendaIds.length(); i++) {
          oldBrendaIds.add((Long) brendaIds.get(i));
        }

        for (Long brendaId : newBrendaIds) {
          // set operations handle duplicate case
          oldBrendaIds.add(brendaId);
        }

        mergedMetadata.getJSONObject(XREF).put(BRENDA_ID, oldBrendaIds);
      } else {
        JSONObject xrefObject = new JSONObject();
        xrefObject.put(BRENDA_ID, newBrendaIds);
        mergedMetadata.put(XREF, xrefObject);
      }

    }

  }

  private void mergeReferences(List<JSONObject> mergedRefs, List<JSONObject> newRefs) {
    if (mergedRefs == null || mergedRefs.size() == 0) {
      mergedRefs = newRefs;
    }

    for (JSONObject newRef : newRefs) {
      if (newRef.getString(SRC).equals(PMID)) {
        String newPmid = newRef.getString(VAL);

        ListIterator<JSONObject> mergedRefsIterator = mergedRefs.listIterator();

        Set<String> oldPmids = new HashSet<>();

        while (mergedRefsIterator.hasNext()) {
          JSONObject mergedRef = mergedRefsIterator.next();

          if (mergedRef.getString(SRC).equals(PMID)) {
            oldPmids.add(mergedRef.getString(VAL));
          }
        }

        if (!oldPmids.contains(newPmid)) {
          mergedRefsIterator.add(newRef);
        }
      } else if (newRef.getString(SRC).equals(PATENT)) {
        boolean patentExists = false;
        String newCountryCode = newRef.getString(COUNTRY_CODE);
        String newPatentNumber = newRef.getString(PATENT_NUMBER);
        String newPatentYear = newRef.getString(PATENT_YEAR);

        ListIterator<JSONObject> mergedRefsIterator = mergedRefs.listIterator();

        while (mergedRefsIterator.hasNext()) {
          JSONObject mergedRef = mergedRefsIterator.next();

          if (mergedRef.getString(SRC).equals(PATENT) &&
              mergedRef.getString(COUNTRY_CODE).equals(newCountryCode) &&
              mergedRef.getString(PATENT_NUMBER).equals(newPatentNumber) &&
              mergedRef.getString(PATENT_YEAR).equals(newPatentYear)) {
            patentExists = true;
            break;
          }
        }

        if (!patentExists) {
          mergedRefsIterator.add(newRef);
        }
      }
    }
  }

  /**
   * Update reactions that were referencing the merged sequences so that they new refer to the new merged sequence
   * @param matchedSeqsIDs the IDs of the sequences that were merged
   * @param reactionRefs the IDs of the reactions that referenced those merged sequences
   * @param newSeqID the ID of the merged sequence that should replace the IDs of the sequences that were merged
   */
  private void updateReactionsReferencingDuplicatedSeqs(Set<Long> matchedSeqsIDs, Set<Long> reactionRefs,
                                                        Long newSeqID) {
    for (Long reactionRef : reactionRefs) {
      Reaction reaction = getNoSQLAPI().readReactionFromInKnowledgeGraph(reactionRef);
      Set<JSONObject> proteins = reaction.getProteinData();

      for (JSONObject protein : proteins) {
        JSONArray sequenceIDs = protein.getJSONArray(SEQUENCES);
        Set<Long> newSequenceIDs = new HashSet<>();

        for (int i = 0; i < sequenceIDs.length(); i++) {
          if (matchedSeqsIDs.contains(sequenceIDs.getLong(i))) {
            newSequenceIDs.add(newSeqID);
          } else {
            newSequenceIDs.add(sequenceIDs.getLong(i));
          }
        }

        protein.put(SEQUENCES, new JSONArray(newSequenceIDs));
      }

      reaction.setProteinData(proteins);

      // since reactions are already copied over to the write db while maintaining source ID, we update those reactions
      getNoSQLAPI().getWriteDB().updateActReaction(reaction, reactionRef.intValue());

    }
  }

}
