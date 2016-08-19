package com.act.biointerpretation.sequencemerging;

import act.installer.GenbankInstaller;
import act.installer.UniprotInstaller;
import act.server.NoSQLAPI;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.BiointerpretationProcessor;
import com.act.biointerpretation.Utils.OrgMinimalPrefixGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

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
  private static final String SOURCE_REACTION_ID = "source_reaction_id";
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
  private static final String ORGANISM = "organism";

  private Map<Long, Long> sequenceMigrationMap = new HashMap<>();
  private Map<Long, Long> organismMigrationMap = new HashMap<>();

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
    Iterator<Organism> orgIterator = getNoSQLAPI().readOrgsFromInKnowledgeGraph();

    OrgMinimalPrefixGenerator prefixGenerator = new OrgMinimalPrefixGenerator(orgIterator);
    minimalPrefixMapping = prefixGenerator.getMinimalPrefixMapping();

    markInitialized();
  }

  /**
   * Copies all reactions over to the WriteDB and stores the mapping from old ID to new ID in reactionMigrationMap
   *
   * For each Reaction:
   * 1) Updates the IDs of the merged sequences to the new merged Sequence ID
   * 2) Since organism names were mapped to their minimal prefix, updates the IDs of the organisms to the
   * ID of the minimal prefix
   * 3) Updates the source_reaction_id
   */
  @Override
  public void processReactions() {
    Iterator<Reaction> reactionIterator = getNoSQLAPI().readRxnsFromInKnowledgeGraph();

    while (reactionIterator.hasNext()) {
      Reaction oldRxn = reactionIterator.next();
      Set<JSONObject> proteins = oldRxn.getProteinData();

      for (JSONObject protein : proteins) {
        JSONArray sequenceIDs = protein.getJSONArray(SEQUENCES);
        Set<Long> newSequenceIDs = new HashSet<>();

        for (int i = 0; i < sequenceIDs.length(); i++) {
          newSequenceIDs.add(sequenceMigrationMap.get(sequenceIDs.getLong(i)));
        }

        protein.put(SEQUENCES, new JSONArray(newSequenceIDs));

        protein.put(ORGANISM, organismMigrationMap.get(protein.getLong(ORGANISM)));

        protein.put(SOURCE_REACTION_ID, (long) oldRxn.getUUID());
      }

      oldRxn.setProteinData(proteins);

      Long newId = (long) getNoSQLAPI().writeToOutKnowlegeGraph(oldRxn);

      super.reactionMigrationMap.put((long) oldRxn.getUUID(), newId);
    }
  }


  @Override
  public void processSequences() {
    Iterator<Seq> sequences = getNoSQLAPI().readSeqsFromInKnowledgeGraph();
    Map<UniqueSeq, List<Seq>> sequenceGroups = new HashMap<>();

    int numberOfSequencesMerged = 0;

    // # of sequences that aren't merged due to lack of Seq entry matches
    int numberOfSequencesUnmerged = 0;

    // # of sequences that aren't merged due to lack of information
    int numberOfSequencesUnmergedInfo = 0;

    // stores all sequences with the same ecnum, organism (accounts for prefix), and protein sequence in the same list
    while (sequences.hasNext()) {
      Seq sequence = sequences.next();

      /* changes the organism name to its minimal prefix; must occur before stored in the sequenceGroup map so that
      all seq entries with the same minimal prefix org name, ecnum, & protein sequence are merged */
      migrateOrganism(sequence);

      if (sequence.getOrgName() == null || sequence.getOrgName().isEmpty() ||
          sequence.getSequence() == null || sequence.getSequence().isEmpty() ||
          sequence.getEc() == null || sequence.getEc().isEmpty()) {
        // copy sequence directly, no merging will be possible
        writeSequence(sequence);
        numberOfSequencesUnmergedInfo++;
      }

      UniqueSeq uniqueSeq = new UniqueSeq(sequence);

      if (sequenceGroups.containsKey(uniqueSeq)) {
        // add UniqueSeq object to already existent list that shares the same ecnum, organism & protein sequence
        sequenceGroups.get(uniqueSeq).add(sequence);
      } else {
        // create a new modifiable list for the UniqueSeq object and add a new mapping
        List<Seq> seqs = new ArrayList<>();
        seqs.add(sequence);
        sequenceGroups.put(uniqueSeq, seqs);
      }
    }

    for (Map.Entry<UniqueSeq, List<Seq>> sequenceGroup : sequenceGroups.entrySet()) {
      List<Seq> allMatchedSeqs = sequenceGroup.getValue();

      if (allMatchedSeqs.size() == 1) {
        numberOfSequencesUnmerged++;
      } else {
        numberOfSequencesMerged += allMatchedSeqs.size();
      }

      // stores the IDs of all sequences that are about to be merged
      Set<Long> matchedSeqsIDs = new HashSet<>();

      for (Seq sequence : allMatchedSeqs) {
        matchedSeqsIDs.add((long) sequence.getUUID());
      }

      // merges all sequences that share the same ecnum, organism and protein sequence
      Seq mergedSequence = mergeSequences(allMatchedSeqs);

      // for reference, adds all the seq IDs that were merged
      mergedSequence.getMetadata().put(SOURCE_SEQUENCE_IDS, matchedSeqsIDs);

      Long mergedSeqId = writeSequence(mergedSequence);

      // maps the old duplicate sequences to the new merged sequence entry
      for (Long matchedSeqId : matchedSeqsIDs) {
        sequenceMigrationMap.put(matchedSeqId, mergedSeqId);
      }
    }

    LOGGER.info("%d number of sequences merged", numberOfSequencesMerged);
    LOGGER.info("%d number of sequences unmerged due to lack of information", numberOfSequencesUnmergedInfo);
    LOGGER.info("%d number of sequences unmerged due to lack of Seq entry matches", numberOfSequencesUnmerged);
  }

  private Long writeSequence(Seq sequence) {
    return (long) getNoSQLAPI().getWriteDB().submitToActSeqDB(
        sequence.getSrcdb(),
        sequence.getEc(),
        sequence.getOrgName(),
        organismMigrationMap.get(sequence.getOrgId()),
        sequence.getSequence(),
        sequence.getReferences(),
        sequence.getReactionsCatalyzed(),
        MongoDBToJSON.conv(sequence.getMetadata())
    );
  }

  /**
   * Changes organism name to its minimal prefix and updates the organism ID appropriately
   * @param sequence the Seq entry we are updating
   */
  private void migrateOrganism(Seq sequence) {
    if (sequence.getOrgName() == null || sequence.getOrgName().isEmpty()) {
      return;
    }

    String organismName = checkForOrgPrefix(sequence.getOrgName());
    sequence.setOrgName(organismName);

    Long newOrgId = getNoSQLAPI().getWriteDB().getOrganismId(organismName);

    if (newOrgId == -1) {
      newOrgId = getNoSQLAPI().getWriteDB().submitToActOrganismNameDB(organismName);
    }

    organismMigrationMap.put(sequence.getOrgId(), newOrgId);
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
      this.ecnum = sequence.getEc();
      this.organism = sequence.getOrgName();
      this.protSeq = sequence.getSequence();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      UniqueSeq uniqueSeq = (UniqueSeq) o;

      if (ecnum != null ? !ecnum.equals(uniqueSeq.ecnum) : uniqueSeq.ecnum != null) return false;
      if (organism != null ? !organism.equals(uniqueSeq.organism) : uniqueSeq.organism != null) return false;
      return protSeq != null ? protSeq.equals(uniqueSeq.protSeq) : uniqueSeq.protSeq == null;

    }

    @Override
    public int hashCode() {
      int result = ecnum != null ? ecnum.hashCode() : 0;
      result = 31 * result + (organism != null ? organism.hashCode() : 0);
      result = 31 * result + (protSeq != null ? protSeq.hashCode() : 0);
      return result;
    }
  }

  private Seq mergeSequences(List<Seq> sequences) {
    if (sequences.size() < 1) {
      throw new RuntimeException("0 matched sequences in this sequence group");
    } else if (sequences.size() == 1) {
      return sequences.get(0);
    }

    Seq firstSequence = sequences.get(0);
    JSONObject firstSeqMetadata = firstSequence.getMetadata();

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
        firstSequence.getEc(),
        firstSequence.getOrgId(),
        firstSequence.getOrgName(),
        firstSequence.getSequence(),
        firstSequence.getReferences(),
        MongoDBToJSON.conv(firstSequence.getMetadata()),
        firstSequence.getSrcdb()
    );

    mergedSequence.setReactionsCatalyzed(firstSequence.getReactionsCatalyzed());

    // merge the rest of the matched sequences
    for (Seq sequence : sequences) {
      if (!mergedSequence.getEc().equals(sequence.getEc()) ||
          !mergedSequence.getSequence().equals(sequence.getSequence()) ||
          !mergedSequence.getOrgName().equals(sequence.getOrgName())) {

        String msg = "matching sequence map constructed improperly; at least one of ec #, protein sequence, & " +
            "organism don't match";

        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }

      mergeReferences(mergedSequence.getReferences(), sequence.getReferences());

      mergeMetadata(mergedSequence.getMetadata(), sequence.getMetadata());

      mergeReactionRefs(mergedSequence.getReactionsCatalyzed(), sequence.getReactionsCatalyzed());
    }

    return mergedSequence;
  }

  private void mergeReactionRefs(Set<Long> mergedReactionRefs, Set<Long> newReactionRefs) {
    if (mergedReactionRefs == null || mergedReactionRefs.size() == 0) {
      mergedReactionRefs = newReactionRefs;
      return;
    }

    for (Long newReactionRef : newReactionRefs) {
      // Set operations automatically handle the case that the newReactionRef already exists in the mergedReactionRefs
      mergedReactionRefs.add(newReactionRef);
    }
  }

  private void mergeMetadata(JSONObject mergedMetadata, JSONObject newMetadata) {
    if (mergedMetadata == null || mergedMetadata == new JSONObject()) {
      mergedMetadata = newMetadata;
      return;
    }

    // ensures that the new gene name is added to the synonyms list in the case that it doesn't match the old gene name
    boolean geneNameMatches = true;

    if (newMetadata.has(NAME) && mergedMetadata.has(NAME)) {
      if (!newMetadata.getString(NAME).equals(mergedMetadata.getString(NAME))) {
        geneNameMatches = false;
      }
    }

    if (!mergedMetadata.has(NAME) && newMetadata.has(NAME)) {
      mergedMetadata.put(NAME, newMetadata.getString(NAME));
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
      return;
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
          mergedRefs.add(newRef);
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
          mergedRefs.add(newRef);
        }
      }
    }
  }

}
