package com.act.biointerpretation.reactionmerging;

import act.api.NoSQLAPI;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.sar.SAR;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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

  private Answer crashByDefault = new Answer() {
    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
      throw new RuntimeException(String.format("Unexpected mock method called: %s", invocation.getMethod().getName()));
    }
  };

  private Reaction copyReaction(Reaction r, Long newId) {
    Reaction newR = new Reaction(newId, r.getSubstrates(), r.getProducts(),
        r.getSubstrateCofactors(), r.getProductCofactors(), r.getCoenzymes(),
        r.getECNum(), r.getConversionDirection(),
        r.getPathwayStepDirection(), r.getReactionName(), r.getRxnDetailType());
    for (JSONObject protein : r.getProteinData()) {
      JSONObject newProtein = new JSONObject(protein, JSONObject.getNames(protein));
      newR.addProteinData(newProtein);
    }

    return newR;
  }

  /**
   * This large and sprawling test verifies that reactions are merged as expected based on their substrates and
   * products.  It's volume is primarily due to the complicated context the ReactionMerger requires, both in terms
   * of the data it consumes (reactions, chemicals, sequences) and the fact that it relies on the NoSQLAPI class to
   * iterate over reactions and store the merged results.
   * @throws Exception
   */
  @Test
  public void testMerging() throws Exception {
    assertTrue("foo", true);

    // Mock the NoSQL API and its DB connections, throwing an exception if an unexpected method gets called.
    NoSQLAPI mockNoSQLAPI = mock(NoSQLAPI.class, crashByDefault);
    MongoDB mockReadMongoDB = mock(MongoDB.class, crashByDefault);
    MongoDB mockWriteMongoDB = mock(MongoDB.class, crashByDefault);

    /* Note: the Mockito .when(<method call>) API doesn't seem to work on the NoSQLAPI and MongoDB mocks instantiated
     * above.  Specifying a mocked answer like:
     *   Mockito.when(mockNoSQLAPI.getReadDB()).thenReturn(mockReadMongoDB);
     * invokes mockNoSQLAPI.getReadDB() (which is not unreasonable given the method call definition, which looks like
     * invocation) before its mocked behavior is defined.
     *
     * See https://groups.google.com/forum/#!topic/mockito/CqlI4EAvTwA for a thread on this issue.
     *
     * It's possible that specifying `crashByDefault` as the default action is interfering with Mockito's mechanism for
     * intercepting and overriding method calls.  However, having the test crash when methods whose behavior hasn't been
     * explicitly re-defined or allowed to propagate to the normal method seems like an important safety check.  As
     * such, we can work around the issue by using the `do*` form of mocking, where the stubbing API allows us to
     * specify the method whose behavior to intercept separately from its invocation.  These calls look like:
     *   Mockito.doAnswer(new Answer() { ... do some work ... }).when(mockObject).methodName(argMatchers)
     * which are a bit backwards but actually work in practice.
     *
     * Note also that we could potentially use spys instead of defining explicit mock behavior via Answers and capturing
     * arguments.  That said, the Answer API is pretty straightforward to use and gives us a great deal of flexibility
     * when defining mock behavior.  And since it works for now, we'll keep it until somebody writes something better!
     */

    doReturn(mockReadMongoDB).when(mockNoSQLAPI).getReadDB();
    doReturn(mockWriteMongoDB).when(mockNoSQLAPI).getWriteDB();


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

    Map<Long, Reaction> idToReactionMap = new HashMap<>();
    Map<Long, Chemical> idToChemicalMap = new HashMap<>();
    for (Reaction r : testReactions) {
      idToReactionMap.put(Long.valueOf(r.getUUID()), r);

      Long[] substrates = r.getSubstrates();
      Long[] products = r.getProducts();
      List<Long> allSubstratesProducts = new ArrayList<>(substrates.length + products.length);
      allSubstratesProducts.addAll(Arrays.asList(substrates));
      allSubstratesProducts.addAll(Arrays.asList(products));
      for (Long id : allSubstratesProducts) {
        if(!idToChemicalMap.containsKey(id)) {
          idToChemicalMap.put(id, new Chemical(id));
        }
      }
    }

    /* ****************************************
     * Read DB and NoSQLAPI read method mocking */

    // Return the set of artificial reactions we created when the caller asks for an iterator over the read DB.
    doReturn(testReactions.iterator()).when(mockNoSQLAPI).readRxnsFromInKnowledgeGraph();
    // Look up reactions/chems by id in the maps we just created.
    doAnswer(new Answer<Reaction>() {
      @Override
      public Reaction answer(InvocationOnMock invocation) throws Throwable {
        return idToReactionMap.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockNoSQLAPI).readReactionFromInKnowledgeGraph(Mockito.any(Long.class));
    doAnswer(new Answer<Chemical>() {
      @Override
      public Chemical answer(InvocationOnMock invocation) throws Throwable {
        return idToChemicalMap.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockNoSQLAPI).readChemicalFromInKnowledgeGraph(Mockito.any(Long.class));

    doAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        return ORGANISM_NAMES.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockReadMongoDB).getOrganismNameFromId(Mockito.any(Long.class));

    doAnswer(new Answer<Seq>() {
      @Override
      public Seq answer(InvocationOnMock invocation) throws Throwable {
        Long id = invocation.getArgumentAt(0, Long.class);
        return SEQ_MAP.get(id);
      }
    }).when(mockReadMongoDB).getSeqFromID(Mockito.any(Long.class));

    /* ****************************************
     * Write DB and NoSQLAPI write method mocking */

    // Capture written reactions, making a copy with a fresh ID for later verification.
    final List<Reaction> writtenReactions = new ArrayList<>();
    doAnswer(new Answer<Integer>() {
      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        Reaction r = invocation.getArgumentAt(0, Reaction.class);
        Long id = writtenReactions.size() + 1L;

        Reaction newR = copyReaction(r, id);
        writtenReactions.add(newR);
        return id.intValue();
      }
    }).when(mockNoSQLAPI).writeToOutKnowlegeGraph(Mockito.any(Reaction.class));

    // See http://site.mockito.org/mockito/docs/current/org/mockito/Mockito.html#do_family_methods_stubs
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Reaction toBeUpdated = invocation.getArgumentAt(0, Reaction.class);
        int id = invocation.getArgumentAt(1, Integer.class);
        int matchingIndex = -1;

        for (int i = 0; i < writtenReactions.size(); i++) {
          if (writtenReactions.get(i).getUUID() == id) {
            matchingIndex = i;
            break;
          }
        }

        if (matchingIndex == -1) {
          return null;
        }

        Reaction newR = copyReaction(toBeUpdated, Long.valueOf(id));
        writtenReactions.set(matchingIndex, newR);

        return null;
      }
    }).when(mockWriteMongoDB).updateActReaction(Mockito.any(Reaction.class), Mockito.anyInt());

    final Map<Long, String> submittedOrganismNames = new HashMap<>();
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Long id = submittedOrganismNames.size() + 1L;
        submittedOrganismNames.put(id, invocation.getArgumentAt(0, Organism.class).getName());
        return null;
      }
    }).when(mockWriteMongoDB).submitToActOrganismNameDB(Mockito.any(Organism.class));

    doAnswer(new Answer<Long>() {
      @Override
      public Long answer(InvocationOnMock invocation) throws Throwable {
        String targetOrganism = invocation.getArgumentAt(0, String.class);
        for (Map.Entry<Long, String> entry : submittedOrganismNames.entrySet()) {
          if (entry.getValue().equals(targetOrganism)) {
            return entry.getKey();
          }
        }
        return null;
      }
    }).when(mockWriteMongoDB).getOrganismId(Mockito.any(String.class));

    // Store the sequences into a map for later verification.
    Map<Long, Seq> writtenSequences = new HashMap<>();
    // TODO: there must be a better way than this, right?
    doAnswer(new Answer<Integer>() {
      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        Long id = writtenSequences.size() + 1L;
        Seq.AccDB src = invocation.getArgumentAt(0, Seq.AccDB.class);
        String ec = invocation.getArgumentAt(1, String.class);
        String org = invocation.getArgumentAt(2, String.class);
        Long org_id = invocation.getArgumentAt(3, Long.class);
        String seq = invocation.getArgumentAt(4, String.class);
        List<String> pmids = invocation.getArgumentAt(5, List.class);
        Set<Long> rxns = invocation.getArgumentAt(6, Set.class);
        HashMap<Long, Set<Long>> rxn2substrates = invocation.getArgumentAt(7, HashMap.class);
        HashMap<Long, Set<Long>> rxn2products = invocation.getArgumentAt(8, HashMap.class);
        Set<Long> substrates_uniform = invocation.getArgumentAt(9, Set.class);
        Set<Long> substrates_diverse = invocation.getArgumentAt(10, Set.class);
        Set<Long> products_uniform = invocation.getArgumentAt(11, Set.class);
        Set<Long> products_diverse = invocation.getArgumentAt(12, Set.class);
        SAR sar = invocation.getArgumentAt(13, SAR.class);
        DBObject meta = invocation.getArgumentAt(14, DBObject.class);

        writtenSequences.put(id, Seq.rawInit(id, ec, org_id, org, seq, pmids, meta, src,
            new HashSet<String>(), new HashSet<String>(), rxns, substrates_uniform, substrates_diverse,
            products_uniform, products_diverse, rxn2substrates, rxn2products, sar));

        return id.intValue();
      }
    }).when(mockWriteMongoDB).submitToActSeqDB(
        Mockito.any(Seq.AccDB.class),
        Mockito.any(String.class),
        Mockito.any(String.class),
        Mockito.any(Long.class),
        Mockito.any(String.class),
        Mockito.any(List.class),
        Mockito.any(Set.class),
        Mockito.any(HashMap.class),
        Mockito.any(HashMap.class),
        Mockito.any(Set.class),
        Mockito.any(Set.class),
        Mockito.any(Set.class),
        Mockito.any(Set.class),
        Mockito.any(SAR.class),
        Mockito.any(DBObject.class)
    );

    /* ****************************************
     * Create a reaction merger and run it on the mocked objects. */

    ReactionMerger merger = new ReactionMerger(mockNoSQLAPI);
    merger.run();

    // Test the results of the merge.
    assertEquals("Input reactions should be merged into three output reactions", 3, writtenReactions.size());

    // Check merged structure of first three reactions.
    // TODO: we might be able to do this faster by creating an expected reaction and doing a deep comparison.

    /* Beware: sloppy, repetative test code.  I'll let this sort of thing slide in tests that aren't likely to be
     * reused elsewhere, but it's definitely not pretty. */
    Reaction r1 = writtenReactions.get(0);
    assertNotNull("Merged reaction 1 should not be null", r1);
    assertEquals("Merged reaction 1 has expected substrates",
        new HashSet<>(Arrays.asList(1L, 2L, 3L)),
        new HashSet<>(Arrays.asList(r1.getSubstrates()))
    );
    assertEquals("Merged reaction 1 has expected products",
        new HashSet<>(Arrays.asList(4L, 5L, 6L)),
        new HashSet<>(Arrays.asList(r1.getProducts()))
    );

    assertEquals("Merged reaction 1 should have 3 protein objects", 3, r1.getProteinData().size());
    Set<String> r1Sequences = new HashSet<>(3);
    for (JSONObject o : r1.getProteinData()) {
      Long id = o.getLong("id");
      JSONArray sequences = o.getJSONArray("sequences");
      assertNotNull("Sequences for protein %d should not be null", id);
      assertEquals(String.format("Protein %d should have one sequence", id),
          1, sequences.length());
      Seq seq = writtenSequences.get(sequences.getLong(0));
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


    Reaction r2 = writtenReactions.get(1);
    assertNotNull("Merged reaction 2 should not be null", r2);
    assertEquals("Merged reaction 2 has expected substrates",
        new HashSet<>(Arrays.asList(7L, 2L, 3L)),
        new HashSet<>(Arrays.asList(r2.getSubstrates()))
    );
    assertEquals("Merged reaction 2 has expected products",
        new HashSet<>(Arrays.asList(8L, 5L, 6L)),
        new HashSet<>(Arrays.asList(r2.getProducts()))
    );

    assertEquals("Merged reaction 2 should have 2 protein objects", 2, r2.getProteinData().size());
    Set<String> r2Sequences = new HashSet<>(2);
    for (JSONObject o : r2.getProteinData()) {
      Long id = o.getLong("id");
      JSONArray sequences = o.getJSONArray("sequences");
      assertNotNull("Sequences for protein %d should not be null", id);
      assertEquals(String.format("Protein %d should have one sequence", id),
          1, sequences.length());
      Seq seq = writtenSequences.get(sequences.getLong(0));
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


    Reaction r3 = writtenReactions.get(2);
    assertNotNull("Merged reaction 3 should not be null", r3);
    assertEquals("Merged reaction 3 has expected substrates",
        new HashSet<>(Arrays.asList(9L, 2L, 3L)),
        new HashSet<>(Arrays.asList(r3.getSubstrates()))
    );
    assertEquals("Merged reaction 3 has expected products",
        new HashSet<>(Arrays.asList(8L, 5L, 6L)),
        new HashSet<>(Arrays.asList(r3.getProducts()))
    );

    assertEquals("Merged reaction 3 should have 1 protein objects", 1, r3.getProteinData().size());
    Set<String> r3Sequences = new HashSet<>(1);
    for (JSONObject o : r3.getProteinData()) {
      Long id = o.getLong("id");
      JSONArray sequences = o.getJSONArray("sequences");
      assertNotNull("Sequences for protein %d should not be null", id);
      assertEquals(String.format("Protein %d should have one sequence", id),
          1, sequences.length());
      Seq seq = writtenSequences.get(sequences.getLong(0));
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
