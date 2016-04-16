package com.act.biointerpretation.test.util;

import act.shared.Reaction;
import act.shared.Seq;
import com.mongodb.BasicDBObject;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestUtils {

  private long nextTestReactionId = 0;
  public static final Long DEFAULT_ORGANISM_ID = 1L;
  public static final Map<Long, String> ORGANISM_NAMES = new HashMap<Long, String>() {{
    put(DEFAULT_ORGANISM_ID, "Homo sapiens");
  }};

  // Use distinct id spaces for input proteins to ensure ids are re-mapped during the merging/writing.
  public static final List<Seq> SEQUENCES = new ArrayList<Seq>() {{
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

  public static final Map<Long, Seq> SEQ_MAP = new HashMap<Long, Seq>() {{
    for (Seq seq : SEQUENCES) {
      put(Long.valueOf(seq.getUUID()), seq);
    }
  }};

  public Reaction makeTestReaction(Long[] substrates, Long[] products, Integer[] substrateCoefficients,
                                   Integer[] productCoefficients, boolean useMetacycStyleOrganisms) {
    nextTestReactionId++;

    JSONObject protein = new JSONObject().put("id", nextTestReactionId).put("sequences", new JSONArray());

    if (useMetacycStyleOrganisms) {
      protein = protein.put("organisms", new JSONArray(Arrays.asList(DEFAULT_ORGANISM_ID)));
    } else {
      protein = protein.put("organism", DEFAULT_ORGANISM_ID);
    }

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

  public Reaction makeTestReaction(Long[] substrates, Long[] products, boolean useMetacycStyleOrganisms) {
    return makeTestReaction(substrates, products, null, null, useMetacycStyleOrganisms);
  }

  public Reaction makeTestReaction(Long[] substrates, Long[] products) {
    return makeTestReaction(substrates, products, false);
  }


}
