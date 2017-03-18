/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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
  public static final List<Seq> SEQUENCES = assembleSequences();


  private static List<Seq> assembleSequences() {
    List<Seq> sequences = new ArrayList<Seq>() {{
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

    for (Seq sequence : sequences) {
      int id = sequence.getUUID();
      int rxnID = id/10;
      sequence.addReactionsCatalyzed((long) rxnID);
    }

    return sequences;
  }

  // need to add seq Id/10 as rxn ref id for all of these.

  public static final Map<Long, Seq> SEQ_MAP = new HashMap<Long, Seq>() {{
    for (Seq seq : SEQUENCES) {
      put(Long.valueOf(seq.getUUID()), seq);
    }
  }};

  public Reaction makeTestReaction(Long[] substrates, Long[] products, Integer[] substrateCoefficients,
                                   Integer[] productCoefficients, boolean useMetacycStyleOrganisms, String ecnum,
                                   String reactionDescription) {
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
        new Long[]{}, new Long[]{}, new Long[]{}, ecnum, ConversionDirectionType.LEFT_TO_RIGHT,
        StepDirection.LEFT_TO_RIGHT, reactionDescription, Reaction.RxnDetailType.CONCRETE);
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

  public Reaction makeTestReaction(Long[] substrates, Long[] products, Integer[] substrateCoefficients,
                                   Integer[] productCoefficients, boolean useMetacycStyleOrganisms) {
    return makeTestReaction(substrates, products, substrateCoefficients, productCoefficients, useMetacycStyleOrganisms,
        "1.1.1.1",  String.format("test reaction %d", nextTestReactionId));
  }

  public Reaction makeTestReaction(Long[] substrates, Long[] products, boolean useMetacycStyleOrganisms) {
    return makeTestReaction(substrates, products, null, null, useMetacycStyleOrganisms);
  }

  public Reaction makeTestReaction(Long[] substrates, Long[] products) {
    return makeTestReaction(substrates, products, false);
  }
}
