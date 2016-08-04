package com.act.biointerpretation.sequencemerging;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.act.biointerpretation.test.util.TestUtils;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SequenceMergerTest {

  private MockedNoSQLAPI mockAPI;
  TestUtils utilsObject;

  @Before
  public void setUp() throws IOException, ReactionException {

    // will need duplicate sequences that have proteinexistence, comment

    // will need maybe 2 or 3 reactions that reference these sequences

    // sequences should be merged into one, proteinexistence should be gone, comment should be xref, rest of data should be merged

    // reaction sequence references should be combined into the new proper ID

    // once organism name prefix matching is fixed, test that as well

    // ==========================================
    // assembling reaction
    // ==========================================
    List<Reaction> testReactions = new ArrayList<>();
    utilsObject = new TestUtils();

    Reaction reaction = utilsObject.makeTestReaction(new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L}, false);

    Set<JSONObject> proteinData = new HashSet<>();
    JSONObject proteinDataObj = new JSONObject();

    Set<Long> sequenceSet = new HashSet<>(Arrays.asList(1L, 2L, 3L));
    proteinDataObj.put("sequences", sequenceSet);

    proteinData.add(proteinDataObj);
    reaction.setProteinData(proteinData);
    testReactions.add(reaction);

    // ========================================
    // assembling sequences
    // ========================================
    List<Seq> testSequences = new ArrayList<>();

    JSONObject metadata = new JSONObject();
    metadata.put("proteinExistence", new JSONObject());

    JSONObject commentObject = new JSONObject();
    commentObject.put("text", 128930);
    commentObject.put("type", "brenda_id");

    metadata.put("comment", new JSONArray(Collections.singletonList(commentObject)));

    List<JSONObject> references = new ArrayList<>();

    JSONObject pmid = new JSONObject();
    pmid.put("src", "PMID");
    pmid.put("val", "2423423");
    references.add(pmid);

    JSONObject patent = new JSONObject();
    patent.put("src", "Patent");
    patent.put("country_code", "JP");
    patent.put("patent_number", "2008518610");
    patent.put("patent_year", "2008");
    references.add(patent);

    Seq sequence1 = new Seq(1L, "1.1.1.1", 4000003474L, "Mus musculus", "AJKFLGKJDFS", references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);
    sequence1.addReactionsCatalyzed(1L);

    testSequences.add(sequence1);

    commentObject.put("text", 128931);
    metadata.put("comment", new JSONArray(Collections.singletonList(commentObject)));

    references = new ArrayList<>();

    references.add(pmid);

    patent = new JSONObject();
    patent.put("src", "Patent");
    patent.put("country_code", "EP");
    patent.put("patent_number", "2904117");
    patent.put("patent_year", "2015");
    references.add(patent);

    Seq sequence2 = new Seq(2L, "1.1.1.1", 4000003474L, "Mus musculus", "AJKFLGKJDFS", references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);
    sequence2.addReactionsCatalyzed(1L);

    testSequences.add(sequence2);

    commentObject.put("text", 128932);
    metadata.put("comment", new JSONArray(Collections.singletonList(commentObject)));

    references = new ArrayList<>();

    pmid = new JSONObject();
    pmid.put("src", "PMID");
    pmid.put("val", "218394");
    references.add(pmid);

    patent = new JSONObject();
    patent.put("src", "Patent");
    patent.put("country_code", "JP");
    patent.put("patent_number", "2008518610");
    patent.put("patent_year", "2008");
    references.add(patent);

    Seq sequence3 = new Seq(3L, "1.1.1.1", 4000003474L, "Mus musculus", "AJKFLGKJDFS", references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);
    sequence3.addReactionsCatalyzed(1L);

    testSequences.add(sequence3);

    Seq sequence4 = new Seq(4L, "1.1.1.2", 4000003474L, "Mus musculus", "AJKFLGKJDFS", references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);
    sequence4.addReactionsCatalyzed(1L);

    testSequences.add(sequence4);

    Map<Long, String> testOrgNames = new HashMap<>();
    testOrgNames.put(4000003474L, "Mus musculus");

    mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, testSequences, testOrgNames, new HashMap<>());

    NoSQLAPI noSQLAPI = mockAPI.getMockNoSQLAPI();
    SequenceMerger seqMerger = new SequenceMerger(noSQLAPI);
    seqMerger.init();
    seqMerger.run();

  }

  @Test
  public void testMergeEndToEnd() {
    List<JSONObject> references = new ArrayList<>();

    JSONObject pmid = new JSONObject();
    pmid.put("src", "PMID");
    pmid.put("val", "2423423");
    references.add(pmid);

    JSONObject patent = new JSONObject();
    patent.put("src", "Patent");
    patent.put("country_code", "JP");
    patent.put("patent_number", "2008518610");
    patent.put("patent_year", "2008");
    references.add(patent);

    patent = new JSONObject();
    patent.put("src", "Patent");
    patent.put("country_code", "EP");
    patent.put("patent_number", "2904117");
    patent.put("patent_year", "2015");
    references.add(patent);

    pmid = new JSONObject();
    pmid.put("src", "PMID");
    pmid.put("val", "218394");
    references.add(pmid);

    JSONObject metadata = new JSONObject();

    JSONObject xrefObject = new JSONObject();
    xrefObject.put("brenda_id", new JSONArray(Arrays.asList(128931, 128930, 128932)));
    metadata.put("xref", xrefObject);

    metadata.put("source_sequence_ids", Arrays.asList(1,2,3));

    Seq mergedSeq = new Seq(1L, "1.1.1.1", 0L, "Mus musculus", "AJKFLGKJDFS", references,
        MongoDBToJSON.conv(metadata), Seq.AccDB.genbank);

    Reaction reaction = new Reaction(1L,
        new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L},
        new Long[]{}, new Long[]{}, new Long[]{}, "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT,
        StepDirection.LEFT_TO_RIGHT, "test reaction", Reaction.RxnDetailType.CONCRETE);

    JSONObject proteinData = new JSONObject();

    Set<Long> sequenceSet = new HashSet<>(Collections.singletonList(1L));
    proteinData.put("sequences", sequenceSet);

    reaction.addProteinData(proteinData);

    Seq testSeq = mockAPI.getMockWriteMongoDB().getSeqFromID(1L);
    Reaction testReaction = mockAPI.getMockWriteMongoDB().getReactionFromUUID(1L);

    if (testSeq != null) {
      compareSeqs("for testMergeEndToEnd", mergedSeq, testSeq);
    }

    if (testReaction != null) {
      compareReactions("for testMergeEndToEnd", reaction, mockAPI.getMockWriteMongoDB().getReactionFromUUID(1L));
    }

    // TODO: need to add test for organisms

  }

  private void compareSeqs(String message, Seq expectedSeq, Seq testSeq) {
    assertEquals("comparing ec " + message, expectedSeq.get_ec(), testSeq.get_ec());
    assertEquals("comparing org_id " + message, expectedSeq.getOrgId(), testSeq.getOrgId());
    assertEquals("comparing organism " + message, expectedSeq.get_org_name(), testSeq.get_org_name());
    assertEquals("comparing sequence " + message, expectedSeq.get_sequence(), testSeq.get_sequence());
    assertEquals("comparing references " + message, expectedSeq.get_references().toString(),
        testSeq.get_references().toString());
    assertEquals("comparing metadata " + message, expectedSeq.get_metadata().toString(),
        testSeq.get_metadata().toString());
    assertEquals("comapring src db " + message, expectedSeq.get_srcdb(), testSeq.get_srcdb());
  }

  private void compareReactions(String message, Reaction expectedReaction, Reaction testReaction) {
    assertEquals("comparing ec " + message, expectedReaction.getECNum(), testReaction.getECNum());
    assertEquals("comparing protein data " + message, expectedReaction.getProteinData().toString(),
        testReaction.getProteinData().toString());
  }

}
