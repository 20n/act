package com.act.reachables;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Chemical.REFS;
import act.shared.Reaction;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.jena.system.JenaSystem.forEach;

public class LoadAct extends SteppedTask {
  // Constants
  private static String _fileloc = "com.act.reachables.LoadAct";

  private static final boolean SET_METADATA_ON_NW_NODES = false;

  private static final String DEFAULT_DB_HOST = "localhost";
  private static final int DEFAULT_PORT = 27017;
  private static final String DEFAULT_DATABASE = "validator_profiling_2";

  // Fields
  private MongoDB db;
  private Set<String> optional_universal_inchis;
  private Set<String> optional_cofactor_inchis;
  private int loaded, total;
  private List<String> fieldSetForChemicals;

  private LoadAct(Set<String> optional_universal_inchis, Set<String> optional_cofactor_inchis) {
    this.optional_universal_inchis = optional_universal_inchis;
    this.optional_cofactor_inchis = optional_cofactor_inchis;
    this.fieldSetForChemicals = new ArrayList<>();
    this.db = new MongoDB(DEFAULT_DB_HOST, DEFAULT_PORT, DEFAULT_DATABASE);

    ActData.instance().Act = new Network("Act");
    ActData.instance().ActTree = new Network("Act Tree");

    // the following take time, so will be done in init();
    ActData.instance().allrxnids = null;
    ActData.instance().cofactors = null;
    ActData.instance().natives = null;
    ActData.instance().chemInchis = null;
    ActData.instance().chemId2Inchis = null;
    ActData.instance().chemId2ReadableName = null;
    ActData.instance().chemToxicity = null;
    ActData.instance().chemsReferencedInRxns = null;

    GlobalParams._hostOrganismIDs = new Long[GlobalParams._hostOrganisms.length];
    for (int i = 0; i<GlobalParams._hostOrganisms.length; i++) {
      GlobalParams._hostOrganismIDs[i] = this.db.getOrganismId(GlobalParams._hostOrganisms[i]);
    }

    total = -1;
    loaded = -1;
  }

  public static Network getReachablesTree(Set<String> natives, Set<String> cofactors, boolean restrictToSeq, String[] extra_chem_fields) {
    GlobalParams._actTreeOnlyIncludeRxnsWithSequences = restrictToSeq;

    // init loader
    LoadAct act = new LoadAct(natives, cofactors);

    // set fields to include in the tree even if they are not reachables
    if (extra_chem_fields != null)
      for (String f : extra_chem_fields)
        act.setFieldForExtraChemicals(f);

    // load data from db; and compute reachables tree
    act.run();

    return ActData.instance().ActTree;
  }

  public static Network getReachablesTree(Set<String> natives, Set<String> cofactors, boolean restrictToSeq) {
    return getReachablesTree(natives, cofactors, restrictToSeq, null);
  }

  public static Network getReachablesTree(Set<String> natives, Set<String> cofactors) {
    return getReachablesTree(natives, cofactors, true);
  }

  public static String toInChI(Long id) {
    return ActData.instance().chemId2Inchis.get(id);
  }

  private static void addToNw(Reaction rxn) {
    /**
     * This class takes in a reaction and adds it to ActData.
     *
     * Special handling is afforded to abstract reactions and reactions w/ only cofactors as substrates.
     */

    /* --------------- Setup data on this particular reaction ------------------ */

    long rxnid = rxn.getUUID();
    Set<Long> substrates = new HashSet<>(Arrays.asList(rxn.getSubstrates()));
    Set<Long> substrateCofactors = new HashSet<>(Arrays.asList(rxn.getSubstrateCofactors()));
    Set<Long> products = new HashSet<>(Arrays.asList(rxn.getProducts()));
    Set<Long> productCofactors = new HashSet<>(Arrays.asList(rxn.getProductCofactors()));


    /* --------------- Setup data structures to be used in ActData update ------------------ */

    HashSet<Long> incoming = new HashSet<>();
    HashSet<Long> outgoing = new HashSet<>();
    HashSet<Long> incomingCofactors = new HashSet<>();
    HashSet<Long> outgoingCofactors = new HashSet<>();


    /* --------------- Add to respective sets ------------------ */

    // Normal substrates
    substrates.
            stream().
            filter(s -> !ActData.instance().metaCycBigMolsOrRgrp.contains(s)).
            forEach(incoming::add);
    products.
            stream().
            filter(s -> !ActData.instance().metaCycBigMolsOrRgrp.contains(s)).
            forEach(outgoing::add);

    // Cofactors
    // Can't just use the cofactors in the DB as some of the cofactors in the DB are junk (One is just a null id).
    substrateCofactors.
            stream().
            filter(LoadAct::isCofactor).
            forEach(incomingCofactors::add);

    productCofactors.
            stream().
            filter(LoadAct::isCofactor).
            forEach(outgoingCofactors::add);


    /* --------------- Update ActData ------------------ */
    // TODO Have some way of bundling all this ActData stuff up into more transparent state
    // while still retaining fast debugging cycles later in cascades due to the serialization.

    if (incoming.isEmpty() && substrates.isEmpty()) {
      /*
        If it has no substrates, but only cofactors, we effectively make the
        cofactors the substrates as a way to resolve the 'blacklist' used in WavefrontExpansion correctly
        We need the substrates to be empty otherwise we may end up adding fake reactions.

        Import Note: This causes cofactors to become kinda cofactors, as we don't include them as cofactors,
        but as the primary reactants here.  Therefore, one cannot assume that
        cofactors are ONLY in the cofactors group.
       */
      ActData.instance().rxnSubstrates.put(rxnid, incomingCofactors);
      ActData.instance().rxnSubstratesCofactors.put(rxnid, new HashSet<>());
    } else if (incoming.isEmpty() && !substrates.isEmpty()) {
      /*
        This means we have fake reactions, which currently only activate another reaction if we
        the fake substrate is somehow activated.  We could add special handling in cascades,
        but probably better to deal with it here.
       */
      ActData.instance().rxnSubstrates.put(rxnid, substrates);
      ActData.instance().rxnSubstratesCofactors.put(rxnid, incomingCofactors);
    } else {
      // Normal case, reaction has substrates and cofactors
      ActData.instance().rxnSubstrates.put(rxnid, incoming);
      ActData.instance().rxnSubstratesCofactors.put(rxnid, incomingCofactors);
    }

    ActData.instance().rxnProducts.put(rxnid, outgoing);
    ActData.instance().rxnProductsCofactors.put(rxnid, outgoingCofactors);

    incoming.stream().forEach(s -> {
      if (!ActData.instance().rxnsThatConsumeChem.containsKey(s)) {
        ActData.instance().rxnsThatConsumeChem.put(s, new HashSet<>());
      }
      ActData.instance().rxnsThatConsumeChem.get(s).add(rxnid);
    });

    outgoing.stream().forEach(p -> {
      if (!ActData.instance().rxnsThatProduceChem.containsKey(p))
        ActData.instance().rxnsThatProduceChem.put(p, new HashSet<>());
      ActData.instance().rxnsThatProduceChem.get(p).add(rxnid);
    });

    // add to internal copy of network
    ActData.instance().rxnHasSeq.put(rxnid, rxn.hasProteinSeq());

    // now see if this is a new "class" of rxn,
    // we only use classes to expand reactions
    Pair<Set<Long>, Set<Long>> rxnClass = Pair.of(incoming, outgoing);
    if (!ActData.instance().rxnClasses.contains(rxnClass)) {
      // the first reaction that shows up in this class, get to
      // represent the entire class. So we install it in the
      // datasets mirroring the non-class structures...

      ActData.instance().rxnClassesSubstrates.put(rxnid, incoming);
      ActData.instance().rxnClassesProducts.put(rxnid, outgoing);
      ActData.instance().rxnClasses.add(rxnClass);

      for (Long s : incoming) {
        if (!ActData.instance().rxnClassesThatConsumeChem.containsKey(s))
          ActData.instance().rxnClassesThatConsumeChem.put(s, new HashSet<>());
        ActData.instance().rxnClassesThatConsumeChem.get(s).add(rxnid);
      }
      for (Long p : outgoing) {
        if (!ActData.instance().rxnClassesThatProduceChem.containsKey(p))
          ActData.instance().rxnClassesThatProduceChem.put(p, new HashSet<>());
        ActData.instance().rxnClassesThatProduceChem.get(p).add(rxnid);
      }
    }

    /* --------------- Add anything that doesn't exist to the ActData ------------------ */

    substrates.stream()
            .filter(p -> !ActData.instance().metaCycBigMolsOrRgrp.contains(p))
            .forEach(ActData.instance().chemsReferencedInRxns::add);

    products.stream()
            .filter(p -> !ActData.instance().metaCycBigMolsOrRgrp.contains(p))
            .forEach(ActData.instance().chemsReferencedInRxns::add);



    /* --------------- Setup data structures to be used in ActData update ------------------ */

    // If a reaction has no substrates but does include substrate cofactors, include it.
    if (substrates.isEmpty() && !substrateCofactors.isEmpty() && !products.isEmpty()) {
      List<Long> filteredProducts = products.stream()
              .filter(p -> !isCofactor(p) && !ActData.instance().metaCycBigMolsOrRgrp.contains(p))
              .collect(Collectors.toList());

      if (!filteredProducts.isEmpty()) {
        substrateCofactors.stream()
                .filter(LoadAct::isCofactor)
                .forEach(s -> {

                  // Cofactor is now the substrate
                  Node sub = Node.get(s, true);

                  filteredProducts.
                          stream().
                          forEach(p -> {
                            Node prd = Node.get(p, true);
                            ActData.instance().Act.addNode(prd, p);
                            ActData.instance().chemsInAct.put(p, prd);

                            Edge r = Edge.get(sub, prd, true);
                            ActData.instance().Act.addEdge(r);
                          });
                });
        // This is only done if there are TRULY no substrates, not if we filter them out
        logProgress("Installing reaction with zero substrates in separate ActData collection: %d\n", rxn.getUUID());
        ActData.instance().noSubstrateRxnsToProducts.put(rxnid, new ArrayList<>(filteredProducts));
      }
    } else {
      substrates.
              stream().
              filter(s -> isCofactor(s) || ActData.instance().metaCycBigMolsOrRgrp.contains(s)).
              forEach(s -> {
                Node sub = Node.get(s, true);
                ActData.instance().chemsInAct.put(s, sub);
                ActData.instance().Act.addNode(sub, s);
                products.
                        stream().
                        filter(p -> isCofactor(p) || ActData.instance().metaCycBigMolsOrRgrp.contains(p)).
                        forEach(p -> {
                          // TODO: rxnECNumber rxnEasyDesc and rxnDataSource are only used
                          // for information in cascades output. Do not load them during
                          // reachables computation....
                          // TODO: There is no reason to load all 5M of them either. cascades
                          // only needs the ones that are referenced in the reachables computation
                          // anySmallMoleculeEdges = true;

                          Node prd = Node.get(p, true);
                          ActData.instance().Act.addNode(prd, p);
                          ActData.instance().chemsInAct.put(p, prd);

                          Edge r = Edge.get(sub, prd, true);
                          ActData.instance().Act.addEdge(r);
                        });
              });
    }

  }

  public static void annotateRxnEdges(Reaction rxn, HashSet<Edge> rxn_edges) {
    for (Edge e : rxn_edges) {
      Edge.setAttribute(e, "isRxn", true);
      Edge.setAttribute(e, "datasource", rxn.getDataSource());
      Edge.setAttribute(e, "srcRxnID", rxn.getUUID());
      Edge.setAttribute(e, "srcRxn", rxn.getReactionName());
      if (rxn.getECNum() != null)
        Edge.setAttribute(e, "srcRxnEC", rxn.getECNum());
    }
  }

  public static boolean isCofactor(long m) {
    return ActData.instance().cofactors.contains(m);
  }

  private static void logProgress(String format, Object... args) {
    if (!GlobalParams.LOG_PROGRESS)
      return;

    System.err.format(_fileloc + ": " + format, args);
  }

  private static void logProgress(String msg) {
    if (!GlobalParams.LOG_PROGRESS)
      return;

    System.err.println(_fileloc + ": " + msg);
  }

  private List<Long> getAllIDsSorted() {
    List<Long> allids = this.db.getAllReactionUUIDs();
    Collections.sort(allids);
    return allids;
  }

  private Set<Long> getNatives() {
    Set<Long> natives_ids = new HashSet<>();

    if (this.optional_universal_inchis == null) {

      // pull whatever is in the DB
      List<Chemical> cs = this.db.getNativeMetaboliteChems();
      for (Chemical c : cs)
        natives_ids.add(c.getUuid());

    } else {

      // use the inchis provided to the constructor
      for (String inchi : this.optional_universal_inchis) {
        Chemical c = this.db.getChemicalFromInChI(inchi);

        if (c == null) {
          logProgress("LoadAct: WARNING: Starting native not in db.");
          logProgress("LoadAct:        : InChI = " + inchi);
          continue;
        }

        natives_ids.add(c.getUuid());
      }

    }

    return natives_ids;
  }

  private Set<Long> getCofactors() {
    Set<Long> cofactor_ids = new HashSet<>();

    if (this.optional_cofactor_inchis == null) {

      // pull whatever is in the DB
      List<Chemical> cs = this.db.getCofactorChemicals();
      for (Chemical c : cs)
        cofactor_ids.add(c.getUuid());

    } else {

      // use the inchis provided to the constructor
      for (String inchi : this.optional_cofactor_inchis) {
        Chemical c = this.db.getChemicalFromInChI(inchi);

        if (c == null) {
          logProgress("LoadAct: SEVERE WARNING: Starting cofactor not in db.");
          logProgress("LoadAct:               : InChI = " + inchi);
          continue;
        }

        cofactor_ids.add(c.getUuid());
      }
    }

    return cofactor_ids;
  }

  public void setFieldForExtraChemicals(String f) {
    this.fieldSetForChemicals.add(f);
  }

  private HashMap<String, List<Long>> getChemicalWithUserSpecFields() {
    HashMap<String, List<Long>> specials = new HashMap<>();

    for (String f : this.fieldSetForChemicals) {
      List<Chemical> cs = this.db.getChemicalsThatHaveField(f);
      specials.put(f, extractChemicalIDs(cs));
    }
    return specials;
  }

  private Set<Long> getMetaCycBigMolsOrRgrp() {
    HashSet<Long> ids = new HashSet<>();
    DBIterator chemCursor = this.db.getIdCursorForFakeChemicals();
    while (chemCursor.hasNext()) {
      ids.add((Long) chemCursor.next().get("_id"));
    }
    chemCursor.close();
    return ids;
  }

  private List<Long> extractChemicalIDs(List<Chemical> cs) {
    List<Long> cids = new ArrayList<Long>();
    for (Chemical c : cs)
      cids.add(c.getUuid());
    return cids;
  }

  private void addReactionsToNetwork() {
    DBIterator iterator = this.db.getIteratorOverReactions(true);
    Reaction r;
    Map<Reaction.RxnDataSource, Integer> counts = new HashMap<>();
    for (Reaction.RxnDataSource src : Reaction.RxnDataSource.values())
      counts.put(src, 0);
    // since we are iterating until the end,
    // the getNextReaction call will close the DB cursor...

    while ((r = this.db.getNextReaction(iterator)) != null) {
      // this rxn comes from a datasource, METACYC, BRENDA or KEGG.
      // ensure the configuration tells us to include this datasource...
      Reaction.RxnDataSource src = r.getDataSource();
      Set<Reaction> reactionsWithAccurateDirections = r.correctForReactionDirection();
      counts.put(src, counts.get(src) + reactionsWithAccurateDirections.size());
      logProgress("Pulled: %s\r", counts.toString());

      // Correct for right-to-left and reversible actions, adding all appropriate directions to the graph.
      for (Reaction directedRxn : reactionsWithAccurateDirections) {
        addToNw(directedRxn);
      }

    }
    logProgress("");

    logProgress("Rxn aggregate into %d classes.\n", ActData.instance().rxnClasses.size());
  }

  @Override
  public double percentDone() {
    return 100.0 * ((double) this.loaded / this.total);
  }

  @Override
  public void doMoreWork() {
    logProgress("Pulling %d reactions from MongoDB:\n", this.total);
    addReactionsToNetwork();
    this.loaded = this.total;
  }

  @Override
  public void init() {
    ActData.instance().allrxnids = getAllIDsSorted();
    total = ActData.instance().allrxnids.size();

    loaded = 0;
    ActData.instance().cofactors = getCofactors();
    ActData.instance().natives = getNatives();
    ActData.instance().chemicalsWithUserField = getChemicalWithUserSpecFields();
    ActData.instance().chemicalsWithUserField_treeOrganic = new HashSet<>();
    ActData.instance().chemicalsWithUserField_treeArtificial = new HashSet<>();
    ActData.instance().metaCycBigMolsOrRgrp = getMetaCycBigMolsOrRgrp();
    ActData.instance().chemsReferencedInRxns = new HashSet<>();
    ActData.instance().chemsInAct = new HashMap<>();
    ActData.instance().rxnsInAct = new HashMap<>();
    ActData.instance().rxnSubstrates = new HashMap<>();
    ActData.instance().rxnsThatConsumeChem = new HashMap<>();
    ActData.instance().rxnsThatProduceChem = new HashMap<>();
    ActData.instance().rxnProducts = new HashMap<>();
    ActData.instance().rxnOrganisms = new HashMap<>();
    ActData.instance().rxnSubstratesCofactors = new HashMap<>();
    ActData.instance().rxnProductsCofactors = new HashMap<>();
    ActData.instance().rxnHasSeq = new HashMap<>();

    ActData.instance().rxnClassesSubstrates = new HashMap<>();
    ActData.instance().rxnClassesProducts = new HashMap<>();
    ActData.instance().rxnClasses = new HashSet<>();
    ActData.instance().rxnClassesThatConsumeChem = new HashMap<>();
    ActData.instance().rxnClassesThatProduceChem = new HashMap<>();

    ActData.instance().noSubstrateRxnsToProducts = new HashMap<>();
    logProgress("Initialization complete.");
  }

  @Override
  public void finalize(TaskMonitor tm) {
    logProgress("ComputeReachablesTree.. starting");
    ActData.instance().chemToxicity = new HashMap<Long, Set<Integer>>();
    ActData.instance().chemInchis = new HashMap<String, Long>();
    ActData.instance().chemId2Inchis = new HashMap<Long, String>();
    ActData.instance().chemId2ReadableName = new HashMap<Long, String>();
    ActData.instance().chemIdIsAbstraction = new HashMap<Long, Boolean>();

    pullChemicalsReferencedInRxns();

    ActData.instance().chemsReferencedInRxns.addAll(ActData.instance().cofactors);
    ActData.instance().chemsReferencedInRxns.addAll(ActData.instance().natives);

    for (String f : ActData.instance().chemicalsWithUserField.keySet())
      ActData.instance().chemsReferencedInRxns.addAll(ActData.instance().chemicalsWithUserField.get(f));

    // computes reachables tree and writes it into ActData.instance().ActTree
    new ComputeReachablesTree(this.db);
  }

  private void pullChemicalsReferencedInRxns() {
    int N = ActData.instance().chemsReferencedInRxns.size();
    int count = 0;
    logProgress("Extracting metadata from chemicals.");
    for (Long id : ActData.instance().chemsReferencedInRxns) {
      logProgress("\t pullChemicalsReferencedInRxns: %d\r", count++);
      Chemical c = this.db.getChemicalFromChemicalUUID(id);
      ActData.instance().chemInchis.put(c.getInChI(), id);
      ActData.instance().chemId2Inchis.put(id, c.getInChI());
      ActData.instance().chemIdIsAbstraction.put(id, isAbstractInChI(c.getInChI()));
      String name = c.getShortestBRENDAName();
      if (name == null) {
        // see if there is a metacyc name:
        Object meta = c.getRef(Chemical.REFS.METACYC, new String[] { "meta" });
        if (meta != null) {
          // if we are here, the entry was referenced in metacyc, so must
          // have some name association there. see if we can pull that out.

          // the xref.METACYC.meta field should *always* be a JSONArray
          // (even if its an array with a single JSONObject within it)
          // sanity check that, and abort if the type does not match
          if (!(meta instanceof JSONArray)) {
            throw new RuntimeException("Expect only Arrays in db.chemicals.{xref.METACYC.meta}, but found: " + meta.getClass());
          }

          name = ((JSONObject) ((JSONArray)meta).get(0) ).getString("sname");

          // if failed to pull out a name from metacyc, report it
          if (name == null)
            System.out.println("ERROR: Looks like a metacyc entry chemical, but no metacyc name: " + id);
        }
      }
      if (name == null) {
        // stuff the inchi into the name
        name = c.getInChI();
      }
      ActData.instance().chemId2ReadableName.put(id, name);

      if (SET_METADATA_ON_NW_NODES) {
        String[] xpath = { "metadata", "toxicity" };
        Object o = c.getRef(REFS.DRUGBANK, xpath);
        if (o == null || !(o instanceof String))
          continue;
        Set<Integer> ld50s = extractLD50vals((String)o);
        ActData.instance().chemToxicity.put(id, ld50s);

        // set chemical attributes
        String txt = null; // D MongoDB.chemicalAsString(c, id);
        Set<Integer> tox = ActData.instance().chemToxicity.get(id);
        Long n1 = ActData.instance().chemsInAct.get(id).getIdentifier();
        int fanout = ActData.instance().rxnsThatConsumeChem.containsKey(id) ? ActData.instance().rxnsThatConsumeChem.get(id).size() : -1;
        int fanin = ActData.instance().rxnsThatProduceChem.containsKey(id) ? ActData.instance().rxnsThatProduceChem.get(id).size() : -1;

        setMetadata(n1, tox, c, txt, fanout, fanin);

      }
    }
    logProgress("");
  }

  private boolean isAbstractInChI(String inchi) {
    // Create a Pattern object
    Pattern r = Pattern.compile("^InChI=1S\\/[A-Z0-9]*R");
    return r.matcher(inchi).find();
  }

  private Set<Integer> extractLD50vals(String annotation) {
    // an example of what we want to process is: "Oral, mouse: LD50 = 338 mg/kg; Oral, rat: LD50 = 1944 mg/kg"
    // a second example of what to process is:   "Acute oral toxicity (LD<sub>50</sub>) in rats is 264 mg/kg."
    // a third example of what to process is :   "Oral mouse and rat LD<sub>50</sub> are 338 mg/kg and 425 mg/kg respectively"
    // a fourth example of what to process is:   "oral LD<sub>50</sub>s were 1,100-1,550 mg/kg; 1,450 mg/kg; and 1,490 mg/kg; respectively"

    // most of time it is specified as mg/kg but rarely we also have g/kg: "Oral LD50 in rat: >5 g/kg"
    Set<Integer> ld50s = new HashSet<Integer>();
    int idx = 0;
    int len = annotation.length();
    String[] locator = { "LD50", "LD<sub>50</sub>" };
    int[] locator_len = { locator[0].length(), locator[1].length() };
    int delta = 60; // 60 chars +- the locator
    for (int l = 0; l<locator.length; l++) {
      while ((idx = annotation.indexOf(locator[l], idx)) != -1) {
        // look around a few tokens to find numbers that we can use...
        int low = idx - delta < 0 ? 0 : idx - delta;
        int high = idx + delta > len ? len : idx + delta;
        String sub = annotation.substring(low, idx) + annotation.substring(idx + locator_len[l], high);
        Scanner scan = new Scanner(sub).useDelimiter("[^0-9,]+");
        while (scan.hasNext()) {
          String scanned = scan.next().replaceAll(",", "");
          try { ld50s.add(Integer.parseInt(scanned)); }
          catch (NumberFormatException e) { }
        }

        idx++; // so that we skip the occurrence that we just added
      }
    }
    return ld50s;
  }

  private void setMetadata(Long n, Set<Integer> tox, Chemical c, String fulltxt, int fanout, int fanin) {
    Node.setAttribute(n, "isNative", c.isNative());
    Node.setAttribute(n, "fanout", fanout);
    Node.setAttribute(n, "fanin", fanin);
    if (c.getCanon() != null) Node.setAttribute(n, "canonical", c.getCanon());
    if (c.getInChI() != null) Node.setAttribute(n, "InChI", c.getInChI());
    if (c.getSmiles() != null) Node.setAttribute(n, "SMILES", c.getSmiles());
    if (c.getShortestName() != null) Node.setAttribute(n, "Name", c.getShortestName());
    if (c.getBrendaNames() != null && c.getSynonyms() != null) Node.setAttribute(n, "Synonyms", c.getBrendaNames().toString() + c.getSynonyms().toString());
    setXrefs(n, c);
    if (fulltxt != null) Node.setAttribute(n, "fulltxt", fulltxt);
    if (tox != null && tox.size() > 0) {
      Node.setAttribute(n, "toxicity_all", tox.toString());
      int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
      for (int i : tox) {
        max = max < i ? i : max;
        min = min > i ? i : min;
      }
      Node.setAttribute(n, "toxicity_min", min);
      Node.setAttribute(n, "toxicity_max", max);
    }
  }

  private void setXrefs(Long node, Chemical c) {
    for (REFS typ : Chemical.REFS.values()) {
      if (c.getRef(typ) != null) {
        Double valuation = c.getRefMetric(typ);
        Node.setAttribute(node, typ.name(), c.getRef(typ).toString());
        Node.setAttribute(node, "metric" + typ.name(), valuation == null ? -999999999.0 : valuation);
        Node.setAttribute(node, "log10metric" + typ.name(), valuation == null ? -99.0 : Math.log10(valuation));
        Node.setAttribute(node, "has" + typ.name(), true);
      } else {
        Node.setAttribute(node, "has" + typ.name(), false);
      }
    }
  }

}
