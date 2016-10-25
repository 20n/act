package com.act.biointerpretation.test.util;

import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.mongodb.DBObject;
import org.json.JSONArray;
import org.json.JSONObject;
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
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class MockedMongoDB {
  public static final Answer CRASH_BY_DEFAULT = new Answer() {
    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
      throw new RuntimeException(String.format("Unexpected mock method called: %s", invocation.getMethod().getName()));
    }
  };

  MongoDB mockMongoDB = null;

  Map<Long, Reaction> reactionMap = new HashMap<>();
  Map<Long, Chemical> chemicalMap = new HashMap<>();
  Map<Long, Seq> seqMap = new HashMap<>();
  Map<Long, String> organismMap = new HashMap<>();

  /* WARNING: floating point serializing/deserialization to JSON is not always exact; this function could cause
  issues for JSONObjects containing floating point numbers */
  private static JSONObject deepCopy(JSONObject obj) {
    String objSerialized = obj.toString();
    return new JSONObject(objSerialized);
  }

  private static Reaction copyReaction(Reaction r, Long newId) {
    Reaction newR = new Reaction(newId, r.getSubstrates(), r.getProducts(),
        r.getSubstrateCofactors(), r.getProductCofactors(), r.getCoenzymes(),
        r.getECNum(), r.getConversionDirection(),
        r.getPathwayStepDirection(), r.getReactionName(), r.getRxnDetailType());
    for (JSONObject protein : r.getProteinData()) {
      JSONObject newProtein = new JSONObject(protein, JSONObject.getNames(protein));
      newR.addProteinData(newProtein);
    }

    Long[] substrates = r.getSubstrates();
    for (int i = 0; i < substrates.length; i++) {
      newR.setSubstrateCoefficient(substrates[i], r.getSubstrateCoefficient(substrates[i]));
    }

    Long[] products = r.getProducts();
    for (int i = 0; i < products.length; i++) {
      newR.setProductCoefficient(products[i], r.getProductCoefficient(products[i]));
    }

    if (r.getMechanisticValidatorResult() != null) {
      newR.setMechanisticValidatorResult(deepCopy(r.getMechanisticValidatorResult()));
    }


    return newR;
  }

  private static Seq copySeq(Seq seq) {
    JSONObject oldMetadata = seq.getMetadata();
    JSONObject metadata = deepCopy(oldMetadata);

    List<JSONObject> oldRefs = seq.getReferences();

    List<JSONObject> references = new ArrayList<>();

    for (JSONObject oldRef : oldRefs) {
      JSONObject copy = new JSONObject(oldRef, JSONObject.getNames(oldRef));
      references.add(copy);
    }

    return new Seq(seq.getUUID(), seq.getEc(), seq.getOrgId(), seq.getOrgName(), seq.getSequence(), references,
        MongoDBToJSON.conv(metadata), seq.getSrcdb());
  }

  public MockedMongoDB() { }

  public void installMocks(List<Reaction> testReactions, List<Seq> sequences, Map<Long, String> orgNames,
                           Map<Long, String> chemIdToInchi) {
    installMocks(testReactions, Collections.EMPTY_LIST, sequences, orgNames, chemIdToInchi);
  }

  public void installMocks(List<Reaction> testReactions, List<Long> testChemIds,
                           List<Seq> sequences, Map<Long, String> orgNames, Map<Long, String> chemIdToInchi) {
    if (orgNames != null) {
      this.organismMap.putAll(orgNames);
    }

    if (sequences != null) {
      for (Seq seq : sequences) {
        seqMap.put(Long.valueOf(seq.getUUID()), seq);
      }
    }

    // Mock the NoSQL API and its DB connections, throwing an exception if an unexpected method gets called.
    this.mockMongoDB = mock(MongoDB.class, CRASH_BY_DEFAULT);

    if (testReactions != null) {
      for (Reaction r : testReactions) {
        this.reactionMap.put(Long.valueOf(r.getUUID()), r);

        Long[] substrates = r.getSubstrates();
        Long[] products = r.getProducts();
        List<Long> allSubstratesProducts = new ArrayList<>(substrates.length + products.length);
        allSubstratesProducts.addAll(Arrays.asList(substrates));
        allSubstratesProducts.addAll(Arrays.asList(products));
        for (Long id : allSubstratesProducts) {
          if (!this.chemicalMap.containsKey(id)) {
            Chemical c = new Chemical(id);
            if (chemIdToInchi.containsKey(id)) {
              c.setInchi(chemIdToInchi.get(id));
            } else {
              // Use /FAKE/BRENDA prefix to avoid computing InChI keys.
              c.setInchi(String.format("InChI=/FAKE/BRENDA/TEST/%d", id));
            }
            this.chemicalMap.put(id, c);
          }
        }
      }
    }

    if (testChemIds != null) {
      for (Long id : testChemIds) {
        if (!this.chemicalMap.containsKey(id)) {
          Chemical c = new Chemical(id);
          if (chemIdToInchi.containsKey(id)) {
            c.setInchi(chemIdToInchi.get(id));
          } else {
            // Use /FAKE/BRENDA prefix to avoid computing InChI keys.
            c.setInchi(String.format("InChI=/FAKE/BRENDA/TEST/%d", id));
          }
          this.chemicalMap.put(id, c);
        }
      }
    }


    /* ****************************************
     * Method mocking */

    doAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        return organismMap.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockMongoDB).getOrganismNameFromId(any(Long.class));

    doAnswer(new Answer<Seq>() {
      @Override
      public Seq answer(InvocationOnMock invocation) throws Throwable {
        return seqMap.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockMongoDB).getSeqFromID(any(Long.class));

    doAnswer(new Answer<Iterator<Seq>>() {
      @Override
      public Iterator<Seq> answer(InvocationOnMock invocation) throws Throwable {
        return seqMap.values().iterator();
      }
    }).when(mockMongoDB).getSeqIterator();

    doAnswer(new Answer<Iterator<Chemical>>() {
      @Override
      public Iterator<Chemical> answer(InvocationOnMock invocation) throws Throwable {
        List<Chemical> chemicals = new ArrayList<>();
        for (Long id: (List<Long>) invocation.getArgumentAt(0, List.class)){
          if (chemicalMap.get(id) == null) {
            return null;
          }
          chemicals.add(chemicalMap.get(id));
        }

        return chemicals.iterator();
      }
    }).when(mockMongoDB).getChemicalsbyIds(any(List.class), any(boolean.class));

    doAnswer(new Answer<Iterator<Reaction>>() {
      @Override
      public Iterator<Reaction> answer(InvocationOnMock invocation) throws Throwable {
        List<Reaction> reactions = new ArrayList<>();
        for (Long id: (List<Long>) invocation.getArgumentAt(0, List.class)){
          if (reactionMap.get(id) == null) {
            return null;
          }
          reactions.add(reactionMap.get(id));
        }

        return reactions.iterator();
      }
    }).when(mockMongoDB).getReactionsIteratorById(any(List.class), any(boolean.class));

    doAnswer(new Answer<Long>() {
      @Override
      public Long answer(InvocationOnMock invocation) throws Throwable {
        String targetOrganism = invocation.getArgumentAt(0, String.class);
        for (Map.Entry<Long, String> entry : organismMap.entrySet()) {
          if (entry.getValue().equals(targetOrganism)) {
            return entry.getKey();
          }
        }
        return -1L;
      }
    }).when(mockMongoDB).getOrganismId(any(String.class));

    doAnswer(new Answer<Long> () {
      @Override
      public Long answer(InvocationOnMock invocation) throws Throwable {
        Long id = (long) organismMap.size();
        String name = invocation.getArgumentAt(0, String.class);
        organismMap.put(id, name);
        return id;
      }
    }).when(mockMongoDB).submitToActOrganismNameDB(any(String.class));

    doAnswer(new Answer<List<Seq>> () {
      @Override
      public List<Seq> answer(InvocationOnMock invocation) throws Throwable {
        String seq = invocation.getArgumentAt(0, String.class);
        String ec = invocation.getArgumentAt(1, String.class);
        String organism = invocation.getArgumentAt(2, String.class);

        List<Seq> matchedSeqs = new ArrayList<>();

        for (Map.Entry<Long, Seq> entry : seqMap.entrySet()) {
          Seq sequence = entry.getValue();

          if (sequence.getEc() != null && sequence.getEc().equals(ec)
              && sequence.getSequence().equals(seq)
              && sequence.getOrgName().equals(organism)) {
            matchedSeqs.add(copySeq(sequence));
          }
        }

        return matchedSeqs;
      }
    }).when(mockMongoDB).getSeqFromSeqEcOrg(any(String.class), any(String.class), any(String.class));

    doAnswer(new Answer<List<Seq>> () {
      @Override
      public List<Seq> answer(InvocationOnMock invocation) throws Throwable {
        String accession = invocation.getArgumentAt(0, String.class);

        List<Seq> matchedSeqs = new ArrayList<>();

        for (Map.Entry<Long, Seq> entry : seqMap.entrySet()) {
          Seq sequence = entry.getValue();
          JSONObject metadata = sequence.getMetadata();

          if (!metadata.has("accession") ||
              !metadata.getJSONObject("accession").has(Seq.AccType.genbank_protein.toString())) {
            continue;
          }

          JSONArray accessionArray =
              metadata.getJSONObject("accession").getJSONArray(Seq.AccType.genbank_protein.toString());

          for (int i = 0; i < accessionArray.length(); i++) {
            if (accessionArray.getString(i).equals(accession)) {
              matchedSeqs.add(copySeq(sequence));
              break;
            }
          }

        }

        return matchedSeqs;
      }
    }).when(mockMongoDB).getSeqFromGenbankProtAccession(any(String.class));

    doAnswer(new Answer<List<Seq>> () {
      @Override
      public List<Seq> answer(InvocationOnMock invocation) throws Throwable {
        String accession = invocation.getArgumentAt(0, String.class);
        String seq = invocation.getArgumentAt(1, String.class);

        List<Seq> matchedSeqs = new ArrayList<>();

        for (Map.Entry<Long, Seq> entry : seqMap.entrySet()) {
          Seq sequence = entry.getValue();
          JSONObject metadata = sequence.getMetadata();
          String databaseSequence = sequence.getSequence();

          if (!seq.equals(databaseSequence)) {
            continue;
          }

          if (!metadata.has("accession") ||
              !metadata.getJSONObject("accession").has(Seq.AccType.genbank_nucleotide.toString())) {
            continue;
          }

          JSONArray accessionArray =
              metadata.getJSONObject("accession").getJSONArray(Seq.AccType.genbank_nucleotide.toString());

          for (int i = 0; i < accessionArray.length(); i++) {
            if (accessionArray.getString(i).equals(accession)) {
              matchedSeqs.add(copySeq(sequence));
              break;
            }
          }
        }

        return matchedSeqs;
      }
    }).when(mockMongoDB).getSeqFromGenbankNucAccessionSeq(any(String.class), any(String.class));

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Seq seq = invocation.getArgumentAt(0, Seq.class);

        if (seqMap.containsKey((long) seq.getUUID())) {
          seqMap.get((long) seq.getUUID()).setMetadata(seq.getMetadata());
        }

        return null;
      }
    }).when(mockMongoDB).updateMetadata(any(Seq.class));

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Seq seq = invocation.getArgumentAt(0, Seq.class);

        if (seqMap.containsKey((long) seq.getUUID())) {
          seqMap.get((long) seq.getUUID()).setReferences(seq.getReferences());
        }

        return null;
      }
    }).when(mockMongoDB).updateReferences(any(Seq.class));


    // See http://site.mockito.org/mockito/docs/current/org/mockito/Mockito.html#do_family_methods_stubs
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Reaction toBeUpdated = invocation.getArgumentAt(0, Reaction.class);
        int id = invocation.getArgumentAt(1, Integer.class);

        Reaction newR = copyReaction(toBeUpdated, Long.valueOf(id));

        reactionMap.remove(Long.valueOf(id));
        reactionMap.put(Long.valueOf(id), newR);

        return null;
      }
    }).when(mockMongoDB).updateActReaction(any(Reaction.class), anyInt());

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Long id = organismMap.size() + 1L;
        organismMap.put(id, invocation.getArgumentAt(0, Organism.class).getName());
        return null;
      }
    }).when(mockMongoDB).submitToActOrganismNameDB(any(Organism.class));


    doAnswer(new Answer<Integer>() {
      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        Long id = seqMap.size() + 1L;
        Seq.AccDB src = invocation.getArgumentAt(0, Seq.AccDB.class);
        String ec = invocation.getArgumentAt(1, String.class);
        String org = invocation.getArgumentAt(2, String.class);
        Long org_id = invocation.getArgumentAt(3, Long.class);
        String seq = invocation.getArgumentAt(4, String.class);
        List<JSONObject> pmids = invocation.getArgumentAt(5, List.class);
        Set<Long> rxns = invocation.getArgumentAt(6, Set.class);
        DBObject meta = invocation.getArgumentAt(7, DBObject.class);

        seqMap.put(id, Seq.rawInit(id, ec, org_id, org, seq, pmids, meta, src, rxns));

        return id.intValue();
      }
    }).when(mockMongoDB).submitToActSeqDB(
        any(Seq.AccDB.class),
        any(String.class),
        any(String.class),
        any(Long.class),
        any(String.class),
        any(List.class),
        any(Set.class),
        any(DBObject.class)
    );
  }


  public MongoDB getMockMongoDB() {
    return mockMongoDB;
  }

  public Map<Long, Reaction> getReactionMap() {
    return reactionMap;
  }

  public Map<Long, Chemical> getChemicalMap() {
    return chemicalMap;
  }

  public Map<Long, String> getOrganismMap() {
    return organismMap;
  }

  public Map<Long, Seq> getSeqMap() {
    return seqMap;
  }

  private Set<String> chemMapToInchiSet(Long[] ids, Map<Long, Chemical> chemMap) {
    Set<String> inchis = new HashSet<>();
    for (Long id : ids) {
      Chemical c = chemMap.get(id);
      // Let NPEs happen here if bad ids are passed.
      inchis.add(c.getInChI());
    }
    return inchis;
  }

  public Set<String> readDBChemicalIdsToInchis(Long[] ids) {
    return this.chemMapToInchiSet(ids, this.chemicalMap);
  }

}
