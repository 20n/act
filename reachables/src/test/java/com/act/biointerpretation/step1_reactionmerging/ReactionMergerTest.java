package com.act.biointerpretation.step1_reactionmerging;

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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
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

// TODO: consider using https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo instead of manual mocking?
public class ReactionMergerTest {

  @Before
  public void setUp() throws Exception {

  }

  @After
  public void tearDown() throws Exception {

  }

  private static final Long DEFAULT_ORGANISM_ID = 1L;
  private static final Map<Long, String> ORGANISM_NAMES = new HashMap<Long, String>() {{
    put(DEFAULT_ORGANISM_ID, "Homo sapiens");
  }};

  private static final List<Seq> SEQUENCES = new ArrayList<Seq>() {{
    add(new Seq(10L, "SEQA", DEFAULT_ORGANISM_ID, "", "",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(20L, "SEQB", DEFAULT_ORGANISM_ID, "", "",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(30L, "SEQC", DEFAULT_ORGANISM_ID, "", "",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(40L, "SEQD", DEFAULT_ORGANISM_ID, "", "",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(50L, "SEQE", DEFAULT_ORGANISM_ID, "", "",
        Collections.emptyList(), new BasicDBObject(), Seq.AccDB.brenda));
    add(new Seq(60L, "SEQF", DEFAULT_ORGANISM_ID, "", "",
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

  @Test
  public void testNoSequenceMerging() throws Exception {
    Assert.assertTrue("foo", true);

    // Mock the NoSQL API and its DB connections, throwing an exception if an unexpected method gets called.
    NoSQLAPI mockNoSQLAPI = Mockito.mock(NoSQLAPI.class, crashByDefault);
    MongoDB mockReadMongoDB = Mockito.mock(MongoDB.class, crashByDefault);
    MongoDB mockWriteMongoDB = Mockito.mock(MongoDB.class, crashByDefault);

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

    Mockito.doReturn(mockReadMongoDB).when(mockNoSQLAPI).getReadDB();
    Mockito.doReturn(mockWriteMongoDB).when(mockNoSQLAPI).getWriteDB();

    //Mockito.when(mockReadMongoDB.toString()).thenReturn("Foo!");
    Mockito.doReturn("Foo!").when(mockReadMongoDB).toString();

    System.out.format("Calling getReadDB: %s\n", mockNoSQLAPI.getReadDB());

    List<Reaction> testReactions = new ArrayList<>();
    testReactions.add(makeTestReaction(new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L}));
    testReactions.add(makeTestReaction(new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L}));
    testReactions.add(makeTestReaction(new Long[]{1L, 2L, 3L}, new Long[]{4L, 5L, 6L}));

    testReactions.add(makeTestReaction(new Long[]{7L, 2L, 3L}, new Long[]{8L, 5L, 6L}));
    testReactions.add(makeTestReaction(new Long[]{7L, 2L, 3L}, new Long[]{8L, 5L, 6L}));

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
    Mockito.doReturn(testReactions.iterator()).when(mockNoSQLAPI).readRxnsFromInKnowledgeGraph();
    // Look up reactions/chems by id in the maps we just created.
    Mockito.doAnswer(new Answer<Reaction>() {
      @Override
      public Reaction answer(InvocationOnMock invocation) throws Throwable {
        return idToReactionMap.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockNoSQLAPI).readReactionFromInKnowledgeGraph(Mockito.any(Long.class));
    Mockito.doAnswer(new Answer<Chemical>() {
      @Override
      public Chemical answer(InvocationOnMock invocation) throws Throwable {
        return idToChemicalMap.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockNoSQLAPI).readChemicalFromInKnowledgeGraph(Mockito.any(Long.class));

    Mockito.doAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        return ORGANISM_NAMES.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockReadMongoDB).getOrganismNameFromId(Mockito.any(Long.class));

    Mockito.doAnswer(new Answer<Seq>() {
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
    Mockito.doAnswer(new Answer<Integer>() {
      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        Reaction r = invocation.getArgumentAt(0, Reaction.class);
        Long id = writtenReactions.size() + 1L;

        System.out.format("Writing new reaction with id %d\n", id);

        Reaction newR = copyReaction(r, id);
        writtenReactions.add(newR);
        return id.intValue();
      }
    }).when(mockNoSQLAPI).writeToOutKnowlegeGraph(Mockito.any(Reaction.class));

    // See http://site.mockito.org/mockito/docs/current/org/mockito/Mockito.html#do_family_methods_stubs
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Reaction toBeUpdate = invocation.getArgumentAt(0, Reaction.class);
        int id = invocation.getArgumentAt(1, Integer.class);
        int matchingIndex = -1;

        System.out.format("updatingnew reaction with id %d\n", id);

        for (int i = 0; i < writtenReactions.size(); i++) {
          if (writtenReactions.get(i).getUUID() == id) {
            matchingIndex = i;
            break;
          }
        }

        if (matchingIndex == -1) {
          return null;
        }

        Reaction newR = copyReaction(writtenReactions.get(matchingIndex), Long.valueOf(id));
        writtenReactions.set(matchingIndex, newR);

        return null;
      }
    }).when(mockWriteMongoDB).updateActReaction(Mockito.any(Reaction.class), Mockito.anyInt());

    final Map<Long, String> submittedOrganismNames = new HashMap<>();
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Long id = submittedOrganismNames.size() + 1L;
        submittedOrganismNames.put(id, invocation.getArgumentAt(0, Organism.class).getName());
        return null;
      }
    }).when(mockWriteMongoDB).submitToActOrganismNameDB(Mockito.any(Organism.class));

    Mockito.doAnswer(new Answer<Long>() {
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
    Mockito.doAnswer(new Answer<Integer>() {
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

    System.out.format("Done running reaction merger.\n");

    for (Reaction r : writtenReactions) {
      System.out.format("Written reaction: %s\n", r);
    }

  }
}
