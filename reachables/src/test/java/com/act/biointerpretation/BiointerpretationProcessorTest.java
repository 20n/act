package com.act.biointerpretation;

import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.Seq;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import com.mongodb.BasicDBObject;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BiointerpretationProcessorTest {
  private static final String POTATO = "Solanum tuberosum";
  private static final String[] INCHIS = new String[] {
      "InChI=1S/12CN.2Fe.2H/c12*1-2;;;;/q12*-1;+2;+3;;",
      "InChI=1S/CH5O4P/c1-5-6(2,3)4/h1H3,(H2,2,3,4)",
      "InChI=1S/C7H5Cl3/c8-4-5-2-1-3-6(9)7(5)10/h1-3H,4H2",
      "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)",
  };

  private MockedNoSQLAPI noSQLAPI;

  @Before
  public void setup() throws Exception {
    Map<Long, String> inchiMap = new HashMap<>();
    for (int i = 0; i < INCHIS.length; i++) {
      inchiMap.put(i + 1L, INCHIS[i]);
    }

    Reaction testReaction = new Reaction(
        100L, new Long[]{1L, 2L}, new Long[]{3L, 4L}, new Long[0], new Long[0], new Long[0],
        "1.1.1.1", ConversionDirectionType.LEFT_TO_RIGHT, StepDirection.LEFT_TO_RIGHT, "Reaction 1",
        Reaction.RxnDetailType.CONCRETE);
    Seq testSeq = new Seq(1000L, "1.1.1.1", 10L,
        POTATO,
        "TATERISAWARE", // This is a valid amino acid sequence.
        new ArrayList<>(),
        new BasicDBObject(),
        Seq.AccDB.trembl  // Tremble before the mighty POTATO!
    );
    testSeq.setReactionsCatalyzed(Collections.singleton(100L));
    Map<Long, String> organismMap = new HashMap<Long, String>() {{
      put(10L, POTATO);
    }};

    testReaction.addProteinData(new JSONObject().
        put("datasource", "FAKE!").
        put("organisms", new JSONArray(Collections.singletonList(10L))).
        put("sequences", new JSONArray(Collections.singletonList(Long.valueOf(testSeq.getUUID()))))
    );

    noSQLAPI = new MockedNoSQLAPI();
    noSQLAPI.installMocks(
        Collections.singletonList(testReaction),
        Collections.singletonList(testSeq),
        organismMap,
        inchiMap
    );
  }

  @Test
  public void testBiointerpretationProcessor() throws Exception {
    BiointerpretationProcessor processor = new BiointerpretationProcessor(noSQLAPI.getMockNoSQLAPI()) {
      @Override
      public String getName() {
        return "testProcessor";
      }

      @Override
      public void init() throws Exception {
        this.initCalled = true;
      }
    };
    // Must call init or run() will throw an exception.
    processor.init();
    // Do the thing!
    processor.run();

    // Check that we didn't break the data.
    List<Reaction> reactions = noSQLAPI.getWrittenReactions();
    Map<Long, Chemical> chemicals = noSQLAPI.getWrittenChemicals();
    Map<Long, Seq> seqs = noSQLAPI.getWrittenSequences();
    Map<Long, String> orgNames = noSQLAPI.getWrittenOrganismNames();

    assertEquals("One reaction written to DB", 1, reactions.size());
    Reaction r = reactions.get(0);
    assertTrue("Reaction is of type reaction", r instanceof Reaction);
    assertEquals("EC num matches expected", "1.1.1.1", r.getECNum());

    Set<String> substrates = new HashSet<String>() {{
        add("InChI=1S/12CN.2Fe.2H/c12*1-2;;;;/q12*-1;+2;+3;;");
        add("InChI=1S/CH5O4P/c1-5-6(2,3)4/h1H3,(H2,2,3,4)");
    }};
    Set<String> products = new HashSet<String>() {{
      add("InChI=1S/C7H5Cl3/c8-4-5-2-1-3-6(9)7(5)10/h1-3H,4H2");
      add("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)");
    }};

    for (Long id : r.getSubstrates()) {
      assertTrue("Substrate appears in expected set", substrates.contains(chemicals.get(id).getInChI()));
    }

    for (Long id : r.getProductCofactors()) {
      assertTrue("Product appears in expected set", products.contains(chemicals.get(id).getInChI()));
    }

    assertEquals("Reaction has one protein", 1, r.getProteinData().size());
    JSONObject protein = r.getProteinData().iterator().next();
    assertEquals("Protein organism maps to expected name",
        POTATO, orgNames.get(protein.getJSONArray("organisms").getLong(0)));

    assertEquals("One seq written to DB", 1, seqs.size());
    Seq seq = seqs.values().iterator().next();
    assertEquals("Protein links to single seq in DB", seq, seqs.get(protein.getJSONArray("sequences").getLong(0)));
    assertEquals("Sequence EC number is expected", "1.1.1.1", seq.getEc());
    assertEquals("Sequence organisms is expected", POTATO, seq.getOrgName());
    assertEquals("Sequence refers to one reaction", 1, seq.getReactionsCatalyzed().size());
    assertEquals("Sequence refers to reaction correctly",
        Long.valueOf(r.getUUID()), seq.getReactionsCatalyzed().iterator().next());
  }
}
