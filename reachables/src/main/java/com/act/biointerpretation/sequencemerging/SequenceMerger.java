package com.act.biointerpretation.sequencemerging;

import act.installer.GenbankInstaller;
import act.installer.UniprotInstaller;
import act.server.NoSQLAPI;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.act.biointerpretation.BiointerpretationProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;

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
  public void run() {
    LOGGER.info("processing sequences");
    processSequences();
  }

  @Override
  public void processSequences() {
    Iterator<Seq> sequences = getNoSQLAPI().readSeqsFromInKnowledgeGraph();
    Map<UniqueSeq, List<Seq>> sequenceGroups = new HashMap<>();

    // stores all sequences with the same ecnum, organism, and protein sequence in the same list
    // TODO: organism prefix matching
    while (sequences.hasNext()) {
      Seq sequence = sequences.next();
      UniqueSeq uniqueSeq = new UniqueSeq(sequence);
      List<Seq> matchingSeqs = sequenceGroups.get(uniqueSeq);
      matchingSeqs.add(sequence);
    }

    Iterator sequenceGroupIterator = sequenceGroups.entrySet().iterator();

    while (sequenceGroupIterator.hasNext()) {
      Map.Entry pair = (Map.Entry) sequenceGroupIterator.next();
      List<Seq> allMatchedSeqs = (List<Seq>) pair.getValue();
      Seq mergedSequence = mergeSequences(allMatchedSeqs);
      // TODO: write mergedSequence to db
    }

  }

  private static class UniqueSeq {
    String ecnum;
    String organism;
    String protSeq;

    private UniqueSeq (Seq sequence) {
      ecnum = sequence.get_ec();
      organism = sequence.get_org_name();
      protSeq = sequence.get_sequence();
    }
  }

  private Seq mergeSequences(List<Seq> sequences) {
    if (sequences.size() < 1) {
      return null;
    } else if (sequences.size() == 1) {
      return sequences.get(0);
    }

    Seq firstSequence = sequences.get(0);
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

      // TODO: have to merge reaction and substrate data; are we still keeping this data?

    }

    return mergedSequence;
  }

  private JSONObject mergeMetadata(JSONObject mergedMetadata, JSONObject newMetadata) {

    // we don't bother merging proteinExistence because there is no current Seq entry for which 'metadata.proteinExistence' != new JSONObject

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

    // TODO: merge comments; are these necessary?

    JSONArray oldComment = mergedMetadata.getJSONArray("comment");
    JSONArray newComment = newMetadata.getJSONArray("comment");


    return mergedMetadata;
  }

  // can use set operations in order to have less logic
  // may not need to return anything at all since you're changing the referenced mergedRefs
  private List<JSONObject> mergeReferences(List<JSONObject> mergedRefs, List<JSONObject> newRefs) {

    for (JSONObject newRef : newRefs) {

      if (newRef.getString("src").equals("PMID")) {

        boolean pmidExists = false;
        String newPmid = newRef.getString("val");

        for (JSONObject mergedRef : mergedRefs) {

          if (mergedRef.getString("src").equals("PMID") &&
              mergedRef.getString("val").equals(newPmid)) {
            pmidExists = true;
          }

          if (!pmidExists) {
            mergedRefs.add(newRef);
          }
        }
      } else if (newRef.getString("src").equals("Patent")) {

        boolean patentExists = false;
        String newCountryCode = newRef.getString("country_code");
        String newPatentNumber = newRef.getString("patent_number");
        String newPatentYear = newRef.getString("patent_year");

        for (JSONObject mergedRef : mergedRefs) {

          if (mergedRef.getString("src").equals("Patent") &&
              mergedRef.getString("country_code").equals(newCountryCode) &&
              mergedRef.getString("patent_number").equals(newPatentNumber) &&
              mergedRef.getString("patent_year").equals(newPatentYear)) {
            patentExists = true;
          }

          if (!patentExists) {
            mergedRefs.add(newRef);
          }
        }
      }
    }

    return mergedRefs;
  }

//  There are multiple Seq entries that are identical in everything but ID (e.g. 93775, 93774)
//  There are multiple Seq entries that are identical in everything but ID and 'metadata.comment.text', a seemingly irrelevant field that shouldn't warrant different Seq entries (e.g. 51784, 51785)
//  There are multiple Seq entries that are identical in everything but ID and the reactions they reference (e.g. 6924, 6937)
//  There are multiple Seq entries that are identical in everything but organism post-fixes (different strains)
//  We believe each Seq entry that shares the same ecnum, org and seq should collapse into one entry.

}
