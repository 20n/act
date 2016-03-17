package com.act.biointerpretation.reactionmerging;

import act.api.NoSQLAPI;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.sar.SAR;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;


// TODO: consider using https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo instead of manual mocking?
public class ReactionMergerTest {

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(ReactionMergerTest.class);
  }

  @After
  public void tearDown() throws Exception {

  }

  public static class ReactionHashingTestCase {
    String description;
    Integer expectedHashBucketCount;
    List<Reaction> reactions = new ArrayList<>();
    public ReactionHashingTestCase(String description, Integer expectedHashBucketCount) {
      this.description = description;
      this.expectedHashBucketCount = expectedHashBucketCount;
    }

    public ReactionHashingTestCase addRxn(Reaction rxn) {
      this.reactions.add(rxn);
      return this;
    }

    public Iterator<Reaction> rxnIterator() {
      return this.reactions.iterator();
    }

    public String getDescription() {
      return this.description;
    }

    public Integer getExpectedHashBucketCount() {
      return this.expectedHashBucketCount;
    }
  }

  @Test
  public void testHashing() throws Exception {
/*    ReactionHashingTestCase testCases = new ReactionHashingTestCase[] {{
        new ReactionHashingTestCase()
    }};*/
    List<Reaction> testReactions = new ArrayList<>();
    Map<ReactionMerger.SubstratesProducts, PriorityQueue<Long>> results;
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc and no cofactors/enzymes should be hashed to one bucket",
        1, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L, 5L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with subset substreates should be hashed to two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{5L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with different substrates and no cofactors/enzymes should be hashed as two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.2", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc but different EC numbers should be hashed to two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[]{1L}, new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc and different sub. cofactors should be hashed to two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[]{1L}, new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc and different prod. cofactors should be hashed to two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[]{1L},
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[]{2L},
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc and different coenzymes should be hashed to two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.get(0).setSubstrateCoefficient(1L, 1);
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.get(1).setSubstrateCoefficient(1L, 2);
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc and different coefficients should be hashed to two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.RIGHT_TO_LEFT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc and different conv. directions should be hashed to two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.RIGHT_TO_LEFT, "Reaction 2",
        Reaction.RxnDetailType.CONCRETE
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc and different step directions should be hashed to two buckets",
        2, results.size());

    testReactions.clear();
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE
    ));
    testReactions.add(new Reaction(
        1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
        Reaction.RxnDetailType.ABSTRACT
    ));
    results = ReactionMerger.hashReactions(testReactions.iterator());
    assertEquals("Reactions with identical sub/prodc and different rxn detail types should be hashed to one bucket",
        1, results.size());
  }
  

  private static final Long DEFAULT_ORGANISM_ID = 1L;
  private static final Map<Long, String> ORGANISM_NAMES = new HashMap<Long, String>() {{
    put(DEFAULT_ORGANISM_ID, "Homo sapiens");
  }};

  // Use distinct id spaces for input proteins to ensure ids are re-mapped during the merging/writing.
  private static final List<Seq> SEQUENCES = new ArrayList<Seq>() {{
    add(new Seq(10L, "1.2.3.4", DEFAULT_ORGANISM_ID, "Homo sapiens", "SEQA",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(20L, "1.2.3.4", DEFAULT_ORGANISM_ID, "Homo sapiens", "SEQB",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(30L, "1.2.3.4", DEFAULT_ORGANISM_ID, "Homo sapiens", "SEQC",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(40L, "1.2.3.4", DEFAULT_ORGANISM_ID, "Homo sapiens", "SEQD",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(50L, "1.2.3.4", DEFAULT_ORGANISM_ID, "Homo sapiens", "SEQE",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(60L, "1.2.3.4", DEFAULT_ORGANISM_ID, "Homo sapiens", "SEQF",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
  }};
  private static final Map<Long, Seq> SEQ_MAP = new HashMap<Long, Seq>() {{
    for (Seq seq : SEQUENCES) {
      put(Long.valueOf(seq.getUUID()), seq);
    }
  }};

  private long nextTestReactionId = 0;
  private Reaction makeTestReaction(Long[] substrates, Long[] products) {
    nextTestReactionId++;

    JSONObject protein = new JSONObject().
        put("id", nextTestReactionId).
        put("sequences", new JSONArray()).
        put("organism", DEFAULT_ORGANISM_ID);

    // Add a sequence reference if we
    Long sequenceId = nextTestReactionId * 10;
    if (SEQ_MAP.containsKey(sequenceId)) {
      protein.put("sequences", protein.getJSONArray("sequences").put(sequenceId));
    }

    Reaction r = new Reaction(nextTestReactionId,
        substrates, products,
        new Long[]{}, new Long[]{}, new Long[]{}, "1.1.1.1",
        ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT,
        String.format("test reaction %d", nextTestReactionId), Reaction.RxnDetailType.CONCRETE);
    r.addProteinData(protein);
    return r;
  }

  /**
   * This large and sprawling test verifies that reactions are merged as expected based on their substrates and
   * products.  It's volume is primarily due to the complicated context the ReactionMerger requires, both in terms
   * of the data it consumes (reactions, chemicals, sequences) and the fact that it relies on the NoSQLAPI class to
   * iterate over reactions and store the merged results.
   * @throws Exception
   */
  @Test
  public void testMergingEndToEnd() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();
    // Group 1
    testReactions.add(makeTestReaction(new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L}));
    testReactions.add(makeTestReaction(new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L}));
    testReactions.add(makeTestReaction(new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L}));
    // Group 2
    testReactions.add(makeTestReaction(new Long[]{7L, 2L, 3L}, new Long[]{8L, 5L, 6L}));
    testReactions.add(makeTestReaction(new Long[]{7L, 2L, 3L}, new Long[]{8L, 5L, 6L}));
    // Group 3
    testReactions.add(makeTestReaction(new Long[]{9L, 2L, 3L}, new Long[]{8L, 5L, 6L}));

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, SEQUENCES, ORGANISM_NAMES);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    /* ****************************************
     * Create a reaction merger and run it on the mocked objects. */

    ReactionMerger merger = new ReactionMerger(mockNoSQLAPI);
    merger.run();

    // Test the results of the merge.
    assertEquals("Input reactions should be merged into three output reactions",
        3, mockAPI.getWrittenReactions().size());

    // Check merged structure of first three reactions.
    // TODO: we might be able to do this faster by creating an expected reaction and doing a deep comparison.

    /* Beware: sloppy, repetative test code.  I'll let this sort of thing slide in tests that aren't likely to be
     * reused elsewhere, but it's definitely not pretty. */
    Reaction r1 = mockAPI.getWrittenReactions().get(0);
    assertNotNull("Merged reaction 1 should not be null", r1);

    assertEquals("Merged reaction 1 has expected substrates",
        mockAPI.readDBChemicalIdsToInchis(new Long[] {1L, 2L, 3L}),
        mockAPI.writeDBChemicalIdsToInchis(r1.getSubstrates())
    );
    assertEquals("Merged reaction 1 has expected products",
        mockAPI.readDBChemicalIdsToInchis(new Long[] {4L, 5L, 6L}),
        mockAPI.writeDBChemicalIdsToInchis(r1.getProducts())
    );

    assertEquals("Merged reaction 1 should have 3 protein objects", 3, r1.getProteinData().size());
    Set<String> r1Sequences = new HashSet<>(3);
    for (JSONObject o : r1.getProteinData()) {
      Long id = o.getLong("id");
      JSONArray sequences = o.getJSONArray("sequences");
      assertNotNull("Sequences for protein %d should not be null", id);
      assertEquals(String.format("Protein %d should have one sequence", id),
          1, sequences.length());
      Seq seq = mockAPI.getWrittenSequences().get(sequences.getLong(0));
      assertNotNull("Referenced seq object should not be null", seq);
      assertEquals("New sequence object's sequence string should match original",
          SEQ_MAP.get(id * 10L).get_sequence(), seq.get_sequence());
      r1Sequences.add(seq.get_sequence());
      assertEquals("New Seq object should reference the migrated reaction by id",
          Long.valueOf(r1.getUUID()), seq.getReactionsCatalyzed().iterator().next());
    }
    assertEquals("All expected sequences are accounted for",
        new HashSet<>(Arrays.asList("SEQA", "SEQB", "SEQC")),
        r1Sequences
    );


    Reaction r2 = mockAPI.getWrittenReactions().get(1);
    assertNotNull("Merged reaction 2 should not be null", r2);
    assertEquals("Merged reaction 2 has expected substrates",
        mockAPI.readDBChemicalIdsToInchis(new Long[] {7L, 2L, 3L}),
        mockAPI.writeDBChemicalIdsToInchis(r2.getSubstrates())
    );
    assertEquals("Merged reaction 2 has expected products",
        mockAPI.readDBChemicalIdsToInchis(new Long[] {8L, 5L, 6L}),
        mockAPI.writeDBChemicalIdsToInchis(r2.getProducts())
    );

    assertEquals("Merged reaction 2 should have 2 protein objects", 2, r2.getProteinData().size());
    Set<String> r2Sequences = new HashSet<>(2);
    for (JSONObject o : r2.getProteinData()) {
      Long id = o.getLong("id");
      JSONArray sequences = o.getJSONArray("sequences");
      assertNotNull("Sequences for protein %d should not be null", id);
      assertEquals(String.format("Protein %d should have one sequence", id),
          1, sequences.length());
      Seq seq = mockAPI.getWrittenSequences().get(sequences.getLong(0));
      assertNotNull("Referenced seq object should not be null", seq);
      assertEquals("New sequence object's sequence string should match original",
          SEQ_MAP.get(id * 10L).get_sequence(), seq.get_sequence());
      r2Sequences.add(seq.get_sequence());
      assertEquals("New Seq object should reference the migrated reaction by id",
          Long.valueOf(r2.getUUID()), seq.getReactionsCatalyzed().iterator().next());
    }
    assertEquals("All expected sequences are accounted for",
        new HashSet<>(Arrays.asList("SEQD", "SEQE")),
        r2Sequences
    );


    Reaction r3 = mockAPI.getWrittenReactions().get(2);
    assertNotNull("Merged reaction 3 should not be null", r3);
    assertEquals("Merged reaction 3 has expected substrates",
        mockAPI.readDBChemicalIdsToInchis(new Long[] {9L, 2L, 3L}),
        mockAPI.writeDBChemicalIdsToInchis(r3.getSubstrates())
    );
    assertEquals("Merged reaction 3 has expected products",
        mockAPI.readDBChemicalIdsToInchis(new Long[] {8L, 5L, 6L}),
        mockAPI.writeDBChemicalIdsToInchis(r3.getProducts())
    );

    assertEquals("Merged reaction 3 should have 1 protein objects", 1, r3.getProteinData().size());
    Set<String> r3Sequences = new HashSet<>(1);
    for (JSONObject o : r3.getProteinData()) {
      Long id = o.getLong("id");
      JSONArray sequences = o.getJSONArray("sequences");
      assertNotNull("Sequences for protein %d should not be null", id);
      assertEquals(String.format("Protein %d should have one sequence", id),
          1, sequences.length());
      Seq seq = mockAPI.getWrittenSequences().get(sequences.getLong(0));
      assertNotNull("Referenced seq object should not be null", seq);
      assertEquals("New sequence object's sequence string should match original",
          SEQ_MAP.get(id * 10L).get_sequence(), seq.get_sequence());
      r3Sequences.add(seq.get_sequence());
      assertEquals("New Seq object should reference the migrated reaction by id",
          Long.valueOf(r3.getUUID()), seq.getReactionsCatalyzed().iterator().next());
    }
    assertEquals("All expected sequences are accounted for",
        new HashSet<>(Arrays.asList("SEQF")),
        r3Sequences
    );
  }
}
