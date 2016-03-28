package com.act.biointerpretation.reactionmerging;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import act.shared.Seq;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.mongodb.BasicDBObject;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


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

    public ReactionHashingTestCase setSubstrateCoefficient(int reactionIdx, long chemId, int count) {
      this.reactions.get(reactionIdx).setSubstrateCoefficient(chemId, count);
      return this;
    }

    public ReactionHashingTestCase setProductCoefficient(int reactionIdx, long chemId, int count) {
      this.reactions.get(reactionIdx).setProductCoefficient(chemId, count);
      return this;
    }
  }

  @Test
  public void testHashing() throws Exception {
    ReactionHashingTestCase[] testCases = new ReactionHashingTestCase[] {
        new ReactionHashingTestCase("Rxns with same sub/prod and no cofactors/enzymes should be hashed to one bucket", 1).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Reactions with subset substrates should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L, 5L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Reactions with subset substreates should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L, 5L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Rxns with different subs. and no cofactors/enzymes should be hashed as two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{5L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Rxn with same sub/prod but different EC numbers should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.2", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Rxns with same sub/prod and different sub. cofactors should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[]{1L}, new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Rxns with same sub/prod and different prod. cofactors should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[]{1L}, new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Rxns with same sub/prod and different coenzymes should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[]{1L},
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[]{2L},
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Rxns with same sub/prod and different coefficients should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).setSubstrateCoefficient(0, 1, 1).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)).setSubstrateCoefficient(1, 1, 2),

        new ReactionHashingTestCase("Rxns with same sub/prod and different conv. directions should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.RIGHT_TO_LEFT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Rxns with same sub/prod and different step directions should be hashed to two buckets", 2).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.RIGHT_TO_LEFT, "Reaction 2",
                Reaction.RxnDetailType.CONCRETE)),

        new ReactionHashingTestCase("Rxns with same sub/prod and different rxn detail types should be hashed to one bucket", 1).
            addRxn(new Reaction(
                1L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
                Reaction.RxnDetailType.CONCRETE)).
            addRxn(new Reaction(
                2L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
                "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 2",
                Reaction.RxnDetailType.ABSTRACT))
    };

    for (ReactionHashingTestCase testCase : testCases) {
      Map<ReactionMerger.SubstratesProducts, PriorityQueue<Long>> results =
          ReactionMerger.hashReactions(testCase.rxnIterator());
      assertEquals(testCase.getDescription(), testCase.getExpectedHashBucketCount(), Integer.valueOf(results.size()));
    }
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
  private Reaction makeTestReaction(Long[] substrates, Long[] products,
                                    Integer[] substrateCoefficients, Integer[] productCoefficients,
                                    boolean useMetacycStyleOrganisms) {
    nextTestReactionId++;

    JSONObject protein = new JSONObject().
        put("id", nextTestReactionId).
        put("sequences", new JSONArray());

    if (useMetacycStyleOrganisms) {
      protein = protein.put("organisms", new JSONArray(Arrays.asList(DEFAULT_ORGANISM_ID)));
    } else {
      protein = protein.put("organism", DEFAULT_ORGANISM_ID);
    }

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

    if (substrateCoefficients != null) {
      for (int i = 0; i < substrateCoefficients.length; i++) {
        r.setSubstrateCoefficient(substrates[i], substrateCoefficients[i]);
      }
    }

    if (productCoefficients != null) {
      for (int i = 0; i < productCoefficients.length; i++) {
        r.setProductCoefficient(products[i], productCoefficients[i]);
      }
    }

    return r;
  }

  private Reaction makeTestReaction(Long[] substrates, Long[] products, boolean useMetacycStyleOrganisms) {
    return makeTestReaction(substrates, products, null, null, useMetacycStyleOrganisms);
  }

  private Reaction makeTestReaction(Long[] substrates, Long[] products) {
    return makeTestReaction(substrates, products, false);
  }

  /**
   * BRENDA and Metacyc structure their protein objects differently.  BRENDA specifies an "organism" field with a
   * single ID value, while Metacyc specifies a list of "organisms" ids.  Handling these incorrectly can cause
   * exceptions to be thrown when running the reaction merger.
   * @throws Exception
   */
  @Test
  public void testOrganismMigrationForDifferentProteinTypes() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();
    // BRENDA style
    testReactions.add(makeTestReaction(new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L}, false));
    // Metacyc style
    testReactions.add(makeTestReaction(new Long[]{4L, 5L, 6L}, new Long[]{7L, 8L, 9L}, true));

    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, SEQUENCES, ORGANISM_NAMES);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    ReactionMerger merger = new ReactionMerger(mockNoSQLAPI);
    merger.run();

    assertEquals("Reactions should not have been merged", 2, mockAPI.getWrittenReactions().size());

    Reaction writtenReaction1 = mockAPI.getWrittenReactions().get(0);
    assertEquals("Reaction 1 has one protein", 1, writtenReaction1.getProteinData().size());
    JSONObject reaction1Protein = writtenReaction1.getProteinData().iterator().next();
    assertTrue("Reaction 1's protein has an organism field", reaction1Protein.has("organism"));
    assertFalse("Reaction 1's protein does not have an organisms field", reaction1Protein.has("organisms"));
    assertTrue("Reaction 2's protein's organisms is a Long", reaction1Protein.get("organism") instanceof Long);
    assertEquals("Reaction 1's protein has the correct single organism id", 0L, reaction1Protein.getLong("organism"));

    Reaction writtenReaction2 = mockAPI.getWrittenReactions().get(1);
    assertEquals("Reaction 2 has one protein", 1, writtenReaction2.getProteinData().size());
    JSONObject reaction2Protein = writtenReaction2.getProteinData().iterator().next();
    assertFalse("Reaction 2's protein does not have an organism field", reaction2Protein.has("organism"));
    assertTrue("Reaction 2's protein has an organisms field", reaction2Protein.has("organisms"));
    assertTrue("Reaction 2's protein's organisms is a JSONArray",
        reaction2Protein.get("organisms") instanceof JSONArray);
    JSONArray reaction2ProteinOrganisms = reaction2Protein.getJSONArray("organisms");
    assertEquals("Reaction 2's protein's organisms has one entry", 1L, reaction2ProteinOrganisms.length());
    assertTrue("Reaction 2's protein's organisms' first entry is a long",
        reaction2ProteinOrganisms.get(0) instanceof Long);
    assertEquals("Reaction 2's protein's organisms' first entry is unchanged from the original",
        1L, reaction2ProteinOrganisms.getLong(0));
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

  @Test
  public void testCoeffieicntsAreCorrectlyTransferred() throws Exception {
    List<Reaction> testReactions = new ArrayList<>();

    Long[] substrates = {1L, 2L, 3L};
    Long[] products = {4L, 5L, 6L};
    Integer[] substrateCoefficients = {1, 2, 3};
    Integer[] productCoefficients = {2, 3, 1};

    // Group 1
    testReactions.add(makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, false));
    testReactions.add(makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, false));

    for (int i = 0; i < substrates.length; i++) {
      assertEquals(String.format("Input reaction substrate %d has correct coefficient set", substrates[i]),
          substrateCoefficients[i], testReactions.get(0).getSubstrateCoefficient(substrates[i]));
    }
    for (int i = 0; i < products.length; i++) {
      assertEquals(String.format("Input reaction product %d has correct coefficient set", products[i]),
          productCoefficients[i], testReactions.get(0).getProductCoefficient(products[i]));
    }


    MockedNoSQLAPI mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(testReactions, SEQUENCES, ORGANISM_NAMES);

    NoSQLAPI mockNoSQLAPI = mockAPI.getMockNoSQLAPI();

    /* **************************************** */
    ReactionMerger merger = new ReactionMerger(mockNoSQLAPI);
    merger.run();

    assertEquals("Input reactions should be merged into one output reactions",
        1, mockAPI.getWrittenReactions().size());
    Reaction rxn = mockAPI.getWrittenReactions().get(0);

    /* We don't necessarily know what ids the products/substrates will get (we can guess, but it's just a guess), but we
     * do know for certain that they'll be inserted in the same order they appear in the original reaction.  By sorting
     * the substrate/product ids, we can iterate over them in the same order they would have appeared originally, which
     * allows us to compare each new reaction's coefficient against the similarly positioned coefficient from the
     * old reaction. */

    // Copy the substrates/products into new arrays before we modify them.
    List<Long> writtenSubstrates = new ArrayList<>(Arrays.asList(rxn.getSubstrates()));
    List<Long> writtenProducts = new ArrayList<>(Arrays.asList(rxn.getProducts()));

    Collections.sort(writtenSubstrates);
    Collections.sort(writtenProducts);

    for (int i = 0; i < writtenSubstrates.size(); i++) {
      Integer newCoefficient = rxn.getSubstrateCoefficient(writtenSubstrates.get(i));
      assertNotNull(String.format("Output reaction substrate coefficient for chemical %d is not null", i),
          newCoefficient);
      assertEquals(String.format("Coefficient for output chemical %d matches original", i),
          substrateCoefficients[i], newCoefficient);
    }

    for (int i = 0; i < writtenProducts.size(); i++) {
      Integer newCoefficient = rxn.getProductCoefficient(writtenProducts.get(i));
      assertNotNull(String.format("Output reaction product coefficient for chemical %d is not null", i),
          newCoefficient);
      assertEquals(String.format("Coefficient for output chemical %d matches original", i),
          productCoefficients[i], newCoefficient);
    }
  }
}
