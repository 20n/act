package com.act.biointerpretation.test.util;

import act.server.MongoDB;
import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
import com.mongodb.DBObject;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class MockedNoSQLAPI {
  public static final Answer CRASH_BY_DEFAULT = new Answer() {
    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
      throw new RuntimeException(String.format("Unexpected mock method called: %s", invocation.getMethod().getName()));
    }
  };

  NoSQLAPI mockNoSQLAPI = null;
  MongoDB mockReadMongoDB = null;
  MongoDB mockWriteMongoDB = null;

  Map<Long, Reaction> idToReactionMap = new HashMap<>();
  Map<Long, Chemical> idToChemicalMap = new HashMap<>();
  List<Chemical> chemicals = new ArrayList<>();
  List<Organism> orgs = new ArrayList<>();

  final List<Reaction> writtenReactions = new ArrayList<>();
  final Map<Long, Chemical> writtenChemicals = new HashMap<>();
  final Map<Long, String> writtenOrganismNames = new HashMap<>();
  final Map<Long, Seq> writtenSequences = new HashMap<>();

  final Map<Long, Seq> seqMap = new HashMap<>();
  final Map<Long, String> readOrganismNames = new HashMap<>();

  public static Reaction copyReaction(Reaction r, Long newId) {
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
      newR.setMechanisticValidatorResult(r.getMechanisticValidatorResult());
    }

    return newR;
  }

  public MockedNoSQLAPI() { }

  public void installMocks(List<Reaction> testReactions, List<Seq> sequences, Map<Long, String> orgNames,
                           Map<Long, String> chemIdToInchi) {
    installMocks(testReactions, Collections.EMPTY_LIST, sequences, orgNames, chemIdToInchi);
  }

  public void installMocks(List<Reaction> testReactions, List<Long> testChemIds,
                           List<Seq> sequences, Map<Long, String> orgNames, Map<Long, String> chemIdToInchi) {
    this.readOrganismNames.putAll(orgNames);

    for (Map.Entry<Long, String> orgName : readOrganismNames.entrySet()) {
      orgs.add(new Organism(orgName.getKey(), -1, orgName.getValue()));
    }

    for (Seq seq : sequences) {
      seqMap.put(Long.valueOf(seq.getUUID()), seq);
    }

    // Mock the NoSQL API and its DB connections, throwing an exception if an unexpected method gets called.
    this.mockNoSQLAPI = mock(NoSQLAPI.class, CRASH_BY_DEFAULT);
    this.mockReadMongoDB = mock(MongoDB.class, CRASH_BY_DEFAULT);
    this.mockWriteMongoDB = mock(MongoDB.class, CRASH_BY_DEFAULT);

    /* Note: the Mockito .when(<method call>) API doesn't seem to work on the NoSQLAPI and MongoDB mocks instantiated
     * above.  Specifying a mocked answer like:
     *   Mockito.when(mockNoSQLAPI.getReadDB()).thenReturn(mockReadMongoDB);
     * invokes mockNoSQLAPI.getReadDB() (which is not unreasonable given the method call definition, which looks like
     * invocation) before its mocked behavior is defined.
     *
     * See https://groups.google.com/forum/#!topic/mockito/CqlI4EAvTwA for a thread on this issue.
     *
     * It's possible that specifying `CRASH_BY_DEFAULT` as the default action is interfering with Mockito's mechanism
     * for intercepting and overriding method calls.  However, having the test crash when methods whose behavior
     * hasn't been explicitly re-defined or allowed to propagate to the normal method seems like an important safety
     * check.  As such, we can work around the issue by using the `do*` form of mocking, where the stubbing API allows
     * us to specify the method whose behavior to intercept separately from its invocation.  These calls look like:
     *   Mockito.doAnswer(new Answer() { ... do some work ... }).when(mockObject).methodName(argMatchers)
     * which are a bit backwards but actually work in practice.
     *
     * Note also that we could potentially use spys instead of defining explicit mock behavior via Answers and
     * capturing arguments.  That said, the Answer API is pretty straightforward to use and gives us a great deal of
     * flexibility when defining mock behavior.  And since it works for now, we'll keep it until somebody writes
     * something better!
     */
    doReturn(this.mockReadMongoDB).when(this.mockNoSQLAPI).getReadDB();
    doReturn(this.mockWriteMongoDB).when(this.mockNoSQLAPI).getWriteDB();

    for (Reaction r : testReactions) {
      this.idToReactionMap.put(Long.valueOf(r.getUUID()), r);

      Long[] substrates = r.getSubstrates();
      Long[] products = r.getProducts();
      List<Long> allSubstratesProducts = new ArrayList<>(substrates.length + products.length);
      allSubstratesProducts.addAll(Arrays.asList(substrates));
      allSubstratesProducts.addAll(Arrays.asList(products));
      for (Long id : allSubstratesProducts) {
        if (!this.idToChemicalMap.containsKey(id)) {
          Chemical c = new Chemical(id);
          if (chemIdToInchi.containsKey(id)) {
            c.setInchi(chemIdToInchi.get(id));
          } else {
            // Use /FAKE/BRENDA prefix to avoid computing InChI keys.
            c.setInchi(String.format("InChI=/FAKE/BRENDA/TEST/%d", id));
          }
          this.idToChemicalMap.put(id, c);
        }
      }
    }

    for (Long id : testChemIds) {
      if (!this.idToChemicalMap.containsKey(id)) {
        Chemical c = new Chemical(id);
        if (chemIdToInchi.containsKey(id)) {
          c.setInchi(chemIdToInchi.get(id));
        } else {
          // Use /FAKE/BRENDA prefix to avoid computing InChI keys.
          c.setInchi(String.format("InChI=/FAKE/BRENDA/TEST/%d", id));
        }
        this.idToChemicalMap.put(id, c);
      }
    }


    List<Long> chemicalIds = new ArrayList<>(this.idToChemicalMap.keySet());
    Collections.sort(chemicalIds);

    /* ****************************************
     * Read DB and NoSQLAPI read method mocking */

    // Return the set of artificial reactions we created when the caller asks for an iterator over the read DB.
    doReturn(testReactions.iterator()).when(mockNoSQLAPI).readRxnsFromInKnowledgeGraph();

    doAnswer(new Answer<Iterator<Chemical>>() {
      @Override
      public Iterator<Chemical> answer(InvocationOnMock invocation) throws Throwable {
        return new Iterator<Chemical>() {
          Iterator<Long> idIterator = chemicalIds.iterator();
          @Override
          public boolean hasNext() {
            return idIterator.hasNext();
          }

          @Override
          public Chemical next() {
            return idToChemicalMap.get(idIterator.next());
          }
        };
      }
    }).when(mockNoSQLAPI).readChemsFromInKnowledgeGraph();

    doReturn(sequences.iterator()).when(mockNoSQLAPI).readSeqsFromInKnowledgeGraph();

    doReturn(orgs.iterator()).when(mockNoSQLAPI).readOrgsFromInKnowledgeGraph();

    // Look up reactions/chems by id in the maps we just created.
    doAnswer(new Answer<Reaction>() {
      @Override
      public Reaction answer(InvocationOnMock invocation) throws Throwable {
        return idToReactionMap.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockNoSQLAPI).readReactionFromInKnowledgeGraph(any(Long.class));
    doAnswer(new Answer<Chemical>() {
      @Override
      public Chemical answer(InvocationOnMock invocation) throws Throwable {
        return idToChemicalMap.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockNoSQLAPI).readChemicalFromInKnowledgeGraph(any(Long.class));

    doAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        return readOrganismNames.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockReadMongoDB).getOrganismNameFromId(any(Long.class));

    doAnswer(new Answer<Seq>() {
      @Override
      public Seq answer(InvocationOnMock invocation) throws Throwable {
        Long id = invocation.getArgumentAt(0, Long.class);
        return seqMap.get(id);
      }
    }).when(mockReadMongoDB).getSeqFromID(any(Long.class));

    doAnswer(new Answer<Long>() {
      @Override
      public Long answer(InvocationOnMock invocation) throws Throwable {
        String targetOrganism = invocation.getArgumentAt(0, String.class);
        for (Map.Entry<Long, String> entry : readOrganismNames.entrySet()) {
          if (entry.getValue().equals(targetOrganism)) {
            return entry.getKey();
          }
        }
        return null;
      }
    }).when(mockReadMongoDB).getOrganismId(any(String.class));

    /* ****************************************
     * Write DB and NoSQLAPI write method mocking */

    // Capture written reactions, making a copy with a fresh ID for later verification.
    doAnswer(new Answer<Integer>() {
      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        Reaction r = invocation.getArgumentAt(0, Reaction.class);
        Long id = writtenReactions.size() + 1L;
        Reaction newR = copyReaction(r, id);
        writtenReactions.add(newR);
        return id.intValue();
      }
    }).when(mockNoSQLAPI).writeToOutKnowlegeGraph(any(Reaction.class));

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
    }).when(mockWriteMongoDB).updateActReaction(any(Reaction.class), anyInt());

    doAnswer(new Answer<Long>() {
      @Override
      public Long answer(InvocationOnMock invocation) throws Throwable {
        Chemical chem = invocation.getArgumentAt(0, Chemical.class);
        Long id = writtenChemicals.size() + 1L;
        Chemical newChem = new Chemical(id);
        newChem.setInchi(chem.getInChI());
        writtenChemicals.put(id, newChem);
        return id;
      }
    }).when(mockNoSQLAPI).writeToOutKnowlegeGraph(any(Chemical.class));

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Long id = writtenOrganismNames.size() + 1L;
        writtenOrganismNames.put(id, invocation.getArgumentAt(0, Organism.class).getName());
        return null;
      }
    }).when(mockWriteMongoDB).submitToActOrganismNameDB(any(Organism.class));

    doAnswer(new Answer() {
      @Override
      public Long answer(InvocationOnMock invocation) throws Throwable {
        Long id = writtenOrganismNames.size() + 1L;
        writtenOrganismNames.put(id, invocation.getArgumentAt(0, String.class));
        return id;
      }
    }).when(mockWriteMongoDB).submitToActOrganismNameDB(any(String.class));

    doAnswer(new Answer<Long>() {
      @Override
      public Long answer(InvocationOnMock invocation) throws Throwable {
        String targetOrganism = invocation.getArgumentAt(0, String.class);
        for (Map.Entry<Long, String> entry : writtenOrganismNames.entrySet()) {
          if (entry.getValue().equals(targetOrganism)) {
            return entry.getKey();
          }
        }
        return -1L;
      }
    }).when(mockWriteMongoDB).getOrganismId(any(String.class));

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
        List<JSONObject> pmids = invocation.getArgumentAt(5, List.class);
        Set<Long> rxns = invocation.getArgumentAt(6, Set.class);
        DBObject meta = invocation.getArgumentAt(7, DBObject.class);

        writtenSequences.put(id, Seq.rawInit(id, ec, org_id, org, seq, pmids, meta, src, rxns));

        return id.intValue();
      }
    }).when(mockWriteMongoDB).submitToActSeqDB(
        any(Seq.AccDB.class),
        any(String.class),
        any(String.class),
        any(Long.class),
        any(String.class),
        any(List.class),
        any(Set.class),
        any(DBObject.class)
    );

    doAnswer(new Answer<Seq>() {
      @Override
      public Seq answer(InvocationOnMock invocation) throws Throwable {
        return writtenSequences.get(invocation.getArgumentAt(0, Long.class));
      }
    }).when(mockWriteMongoDB).getSeqFromID(any(Long.class));

    doAnswer(new Answer<Reaction>() {
      @Override
      public Reaction answer(InvocationOnMock invocation) throws Throwable {

        Long id = invocation.getArgumentAt(0, Long.class);

        for (int i = 0; i < writtenReactions.size(); i++) {
          if (writtenReactions.get(i).getUUID() == id) {
            return writtenReactions.get(i);
          }
        }

        return null;
      }
    }).when(mockWriteMongoDB).getReactionFromUUID(any(Long.class));

  }

  public NoSQLAPI getMockNoSQLAPI() {
    return mockNoSQLAPI;
  }

  public MongoDB getMockReadMongoDB() {
    return mockReadMongoDB;
  }

  public MongoDB getMockWriteMongoDB() {
    return mockWriteMongoDB;
  }

  public Map<Long, Reaction> getIdToReactionMap() {
    return idToReactionMap;
  }

  public Map<Long, Chemical> getIdToChemicalMap() {
    return idToChemicalMap;
  }

  public List<Reaction> getWrittenReactions() {
    return writtenReactions;
  }

  public Map<Long, Chemical> getWrittenChemicals() {
    return writtenChemicals;
  }

  public Map<Long, String> getWrittenOrganismNames() {
    return writtenOrganismNames;
  }

  public Map<Long, Seq> getWrittenSequences() {
    return writtenSequences;
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
    return this.chemMapToInchiSet(ids, this.idToChemicalMap);
  }

  public Set<String> writeDBChemicalIdsToInchis(Long[] ids) {
    return this.chemMapToInchiSet(ids, this.writtenChemicals);
  }
}
