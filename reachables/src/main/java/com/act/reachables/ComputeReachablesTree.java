package com.act.reachables;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.json.JSONArray;

import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Chemical.REFS;
import act.shared.helpers.MongoDBToJSON;
import act.shared.ConsistentInChI;

public class ComputeReachablesTree {

  HashMap<Long, Long> importantAncestor;
  HashMap<Long, String> importantClades;
  HashMap<Long, String> functionalCategory;
  HashMap<Long, Double> subtreeVal;
  HashMap<Long, Double> subtreeSz;
  HashMap<Long, Double> subtreeVendorsSz;
  Tree<Long> tree;
  WavefrontExpansion wavefrontExpansion;

  private static final TargetSelectionSubstructs SUBSTRUCTURES = new TargetSelectionSubstructs();
  MongoDB db;

  ComputeReachablesTree(MongoDB db) {
    this.db = db;

    this.importantAncestor = new HashMap<Long, Long>();
    this.functionalCategory = new HashMap<Long, String>();
    this.subtreeVal = new HashMap<Long, Double>();
    this.subtreeSz = new HashMap<Long, Double>();
    this.subtreeVendorsSz = new HashMap<Long, Double>();

    logProgress("Initiating WavefrontExpansion.expandAndPickParents");

    this.wavefrontExpansion = new WavefrontExpansion();
    this.tree = wavefrontExpansion.expandAndPickParents();
    this.tree.ensureForest();

    // Paba
    findChemicalAndAllItsDescendants("InChI=1S/C7H7NO2/c8-6-3-1-5(2-4-6)7(9)10/h1-4H,8H2,(H,9,10)");

    logProgress("Initiating initImportantClades");
    initImportantClades();

    logProgress("Initiating computeImportantAncestors");
    // each node TO closest ancestor that has > _significantFanout
    computeImportantAncestors();

    logProgress("Initiating computeSubtreeValues Sigma prices");
    // each node TO sum of the values of its children + its own value
    computeSubtreeValues();

    logProgress("Initiating computeSubtreeSizes");
    // each node TO the size of the subtree rooted under it
    computeSubtreeSizes();

    logProgress("Initiating computeSubtreeVendorSizes");
    // each node TO the size of the subtree, i.e.,
    // total # of unique (vendor, subtree chemical) pairs in the subtree
    computeSubtreeVendorSizes();

    boolean singleTree = false;
    if (singleTree) {
      logProgress("Initiating addTreeSingleRoot");
      // creates a single tree rooted at a node
      // that represents the natives
      addTreeSingleRoot();
    } else {
      logProgress("Initiating addTreeNativeRoots");
      // creates a forest, many trees whose roots
      // are one step from the natives
      addTreeNativeRoots();
    }
  }

  public void findChemicalAndAllItsDescendants(String inchi) {
    HashMap<Long, Set<Long>> substrates_dataset = GlobalParams.USE_RXN_CLASSES ? ActData.instance().rxnClassesSubstrates : ActData.instance().rxnSubstrates;
    HashMap<Long, Set<Long>> products_dataset = GlobalParams.USE_RXN_CLASSES ? ActData.instance().rxnClassesProducts : ActData.instance().rxnProducts;
    Set<Long> products_made = new HashSet<>();
    Map<Long, List<Long>> productToSubstrateMapping = new HashMap<>();
    Long targetId = 0L;

    for (Map.Entry<Long, Set<Long>> entry : substrates_dataset.entrySet()) {
      for (Long subId : entry.getValue()) {
        if (ActData.instance().chemId2Inchis.get(subId).equals(inchi)) {
          targetId = subId;
          if (products_dataset.get(entry.getKey()) != null) {
            products_made.addAll(WavefrontExpansion.productsThatAreNotAbstract(products_dataset.get(entry.getKey())));
          }
        }
      }
    }

    try {
      PrintWriter writer = new PrintWriter("pabaclade.txt", "UTF-8");
      // Implement simple BFS
      Set<Long> idsSeenBefore = new HashSet<>();
      LinkedList<Long> queue = new LinkedList<>();
      queue.addAll(products_made);

      while (queue.size() != 0) {
        Long candidateId = queue.pop();
        if (idsSeenBefore.contains(candidateId)) {
          continue;
        }

        // find all reactions where the candidate id is the substrate
        for (Map.Entry<Long, Set<Long>> entry : substrates_dataset.entrySet()) {
          for (Long subId : entry.getValue()) {
            if (subId.equals(candidateId)) {
              if (products_dataset.get(entry.getKey()) != null) {
                // create the parent-child relationship
                for (Long id : WavefrontExpansion.productsThatAreNotAbstract(products_dataset.get(entry.getKey()))) {
                  List<Long> substartes = productToSubstrateMapping.get(candidateId);
                  if (substartes == null) {
                    substartes = new ArrayList<>();
                    productToSubstrateMapping.put(candidateId, substartes);
                  }
                  substartes.add(id);

                  // Only pick reactants that are similar to PABA and are reachable
                  if ((deltaBetweenChemical(id, targetId) >= 0) && tree.allNodes().contains(id)) {
                    queue.add(id);
                  }
                }
                //queue.addAll(WavefrontExpansion.productsThatAreNotAbstract(products_dataset.get(entry.getKey())));
              }
            }
          }
        }

        // print the tree
        idsSeenBefore.add(candidateId);
//        List<String> res = printTree(candidateId, targetId, productToSubstrateMapping);
//        for (String result : res) {
//          writer.println(String.format("%s", result));
//        }
        writer.println(ActData.instance().chemId2Inchis.get(candidateId));
        //writer.println("\n");
        writer.flush();
      }

      writer.flush();
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println(e.getMessage());
    }
  }

  private Integer deltaBetweenChemical(Long p, Long template) {
    String prod = ActData.instance().chemId2Inchis.get(p);
    String templateString = ActData.instance().chemId2Inchis.get(template);
    return countCarbons(prod) - countCarbons(templateString);
  }

  private Integer countCarbons(String inchi) {
    String[] spl = inchi.split("/");
    if (spl.length <= 2)
      return null;

    String formula = spl[1];
    Pattern regex = Pattern.compile("C([0-9]+)");
    Matcher m = regex.matcher(formula);
    if (m.matches()) {
      return Integer.parseInt(m.group(1));
    } else {
      return formula.contains("C") ? 1 : 0;
    }
  }

  private List<String> printTree(Long productId, Long targetId, Map<Long, List<Long>> productToSubstrates) {
    if (productId.equals(targetId)) {
      List<String> single = new ArrayList<>();
      single.add(ActData.instance().chemId2Inchis.get(targetId));
      return single;
    }

    List<String> compiledList = new ArrayList<>();

    if (productToSubstrates.get(productId) != null) {
      for (Long id : productToSubstrates.get(productId)) {
        List<String> res = printTree(id, targetId, productToSubstrates);
        List<String> manipulatedArray = new ArrayList<>();
        for (String i : res) {
          manipulatedArray.add(i + " --> " + ActData.instance().chemId2Inchis.get(productId));
        }
        compiledList.addAll(manipulatedArray);
      }
    }

    return compiledList;
  }

  
  private static String _fileloc = "com.act.reachables.ComputeReachablesTree";
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

  private void initImportantClades() {
    this.importantClades = new HashMap<Long, String>();
    for (String[] clade : Categories.InChI2CategoryName) {
      // since the consistent inchi installed depends on a flag
      // in the installer code, make sure that we use the same defn.
      String inchi = ConsistentInChI.consistentInChI(clade[0], "Important Clades");
      String cladeName = clade[1];
      Long id = ActData.instance().chemInchis.get(inchi);
      this.importantClades.put(id, cladeName);
    }
  }

  private void computeImportantAncestors() {
    // populate this.importantAncestor
    // for each node the closest ancestor that has > _significantFanout

    // for logging, we keep the ancestor -> subtree set
    HashMap<Long, Set<Long>> ancestory = new HashMap<Long, Set<Long>>();
    // init worklist with the roots of the tree
    List<Long> worklist = new ArrayList<Long>();
    worklist.addAll(this.tree.roots());
    // process the worklist
    while (worklist.size() > 0) {
      Long elem = worklist.remove(0);
      if (this.tree.getChildren(elem) != null)
        for (Long child : this.tree.getChildren(elem))
          worklist.add(child);

      // now process this elem
      if (this.tree.getChildren(elem) != null && this.tree.getChildren(elem).size() > GlobalParams._actTreeSignificantFanout) {
        this.importantAncestor.put(elem, elem); // this node itself is significant so it overrides anything above
        ancestory.put(elem, new HashSet<Long>());
        ancestory.get(elem).add(elem);
      } else {
        // this elem itself is not significant, so it can possibly inherit from parents.
        if (this.importantAncestor.containsKey(this.tree.getParent(elem))) {
          Long impAnc = this.importantAncestor.get(this.tree.getParent(elem));
          this.importantAncestor.put(elem, impAnc);
          ancestory.get(impAnc).add(elem);
        }
      }
    }
    // done

    // now assign to each node a functional category Fcat:
    // 1. find for each important ancestor its Fcat:
    //     Look it up in importantClades
    //    (either the ancestor directly will have an entry OR need to follow its parents until we find one that has an entry)
    // 2. Assign all the children (gotten from the ancestory map) Fcat

    for (Long ancestor : ancestory.keySet()) {
      // 1. find for each important ancestor its Fcat
      String Fcat = this.importantClades.get(ancestor);
      if (Fcat == null) {
        // ancestor is not directly tagged an important functional category "head",
        // therefore, we need to go up to its parents until we find one.
        Long hasFcat = ancestor;
        while (hasFcat != null && (Fcat = this.importantClades.get(hasFcat)) == null) {
          hasFcat = this.tree.getParent(hasFcat);
        }
        // at the end of this either a) we reached the root, and hasFcat got to null,
        // or b) Fcat != null and so we have an assignment
      }

      // 2. Assign all the children (gotten from the ancestory map) Fcat
      for (Long n : ancestory.get(ancestor)) {
        this.functionalCategory.put(n, Fcat == null ? "unknown" : Fcat);
      }
    }

    if (GlobalParams._actTreeDumpClades) {
      // diagnostic dump:
      logProgress("-------------------------------------------------------------");
      logProgress("Main branchoff point\tID\tNames");
      logProgress("-------------------------------------------------------------");
      for (Long ancestor : ancestory.keySet()) {
        if (this.tree.roots().contains(ancestor))
          continue;
        logProgress("\n\n");
        for (Long e : ancestory.get(ancestor)) {
          logProgress("%d\t%d\t%s\n", ancestor, e, getNames(e));
        }
      }
      logProgress("-------------------------------------------------------------");
    }
  }

  class InorderTraverseCountSubtreeSz extends InorderTraverse<Long> {
    InorderTraverseCountSubtreeSz(Tree<Long> t) { super(t); }

    @Override
    Double nodeValue(Double initVal, List<Double> childrenVals) {
      Double sum = initVal;
      for (Double s : childrenVals) sum += s;
      return sum;
    }
  }

  private void computeSubtreeVendorSizes() {
    HashMap<Long, Double> vendors_val = new HashMap<Long, Double>();
    for (Long n : this.tree.allNodes()) {
      Chemical c = this.db.getChemicalFromChemicalUUID(n);
      if (c == null)
        vendors_val.put(n, 0.0);
      else
        vendors_val.put(n, new Double(c.getChemSpiderNumUniqueVendors()));
    }
    InorderTraverseCountSubtreeSz traversal = new InorderTraverseCountSubtreeSz(this.tree);
    for (Long root : this.tree.roots()) {
      traversal.exec(root,
          vendors_val /* input values: node->#uniq_vendors */,
          this.subtreeVendorsSz/* output values: node->subtree_value */);
    }
  }

  private void computeSubtreeSizes() {
    HashMap<Long, Double> ident = new HashMap<Long, Double>();
    for (Long n : this.tree.allNodes()) ident.put(n, 1.0);
    InorderTraverseCountSubtreeSz traversal = new InorderTraverseCountSubtreeSz(this.tree);
    for (Long root : this.tree.roots()) {
      traversal.exec(root,
          ident /* input values: node->1 */,
          this.subtreeSz/* output values: node->subtree_value */);
    }
  }

  class InorderTraverseWithSubtreeSum extends InorderTraverse<Long> {
    InorderTraverseWithSubtreeSum(Tree<Long> t) { super(t); }

    @Override
    Double nodeValue(Double initVal, List<Double> childrenVals) {
      Double sum = 0.0;
      for (Double s : childrenVals)
        sum += s;
      return initVal + sum;
    }
  }

  private void computeSubtreeValues() {
    HashMap<Long, Double> nodeValues = getIndividualNodePrices();
    InorderTraverseWithSubtreeSum traversal = new InorderTraverseWithSubtreeSum(this.tree);
    for (Long root : this.tree.roots()) {
      traversal.exec(root,
          nodeValues /* input values: node->node_value */,
          this.subtreeVal/* output values: node->subtree_value */);
    }
  }

  private HashMap<Long, Double> getIndividualNodePrices() {
    HashMap<Long, Double> nodePrices = new HashMap<Long, Double>();
    REFS which = GlobalParams.pullPricesFrom() == GlobalParams._PricesFrom[0] ? REFS.SIGMA : REFS.DRUGBANK;
    for (Long n : this.tree.allNodes()) {
      Double price = 0.0;
      Chemical c = this.db.getChemicalFromChemicalUUID(n);
      if (c != null) {
        if (c.getRef(which) != null) { // else price stays 0.0
          price = c.getRefMetric(which);
          if (price == null)
            price = 0.0;
        }
      }
      nodePrices.put(n, price);
    }
    return nodePrices;
  }

  private String getNames(Long n) {
    Chemical c = this.db.getChemicalFromChemicalUUID(n);
    return c.getBrendaNames().toString() + ";" + c.getSynonyms().toString();
  }

  private void addTreeSingleRoot() {
    HashMap<Long, Node> nodes = new HashMap<Long, Node>();

    for (Long root : this.tree.roots()) {
      Node tree_root = Node.get(root, true);
      nodes.put(root, tree_root);
      ActData.instance().ActTree.addNodeTreeSpecific(tree_root, root, 0, null /* root of single tree */);
      setRootAttributes(tree_root, -1);

      addTreeUnder(null, root, 0, nodes, root);
    }
    logProgress("\nDone addTreeSingleRoot");
  }

  private void addTreeNativeRoots() {
    HashMap<Long, Node> nodes = new HashMap<Long, Node>();

    for (Long root : this.tree.roots()) {
      // instead of adding the root as the central hub, we add a separate tree for each
      // native/cofactor (which should all be the children of the root)
      for (Long nativ : this.tree.getChildren(root)) {
        Node native_center = Node.get(nativ, true);
        nodes.put(nativ, native_center);
        ActData.instance().ActTree.addNodeTreeSpecific(native_center, nativ, 0, null /* root of disjoint tree */);
        // setRootAttributes(tree_root, -1);
        addTreeUnder(null, nativ, 0, nodes, nativ);
      }
    }
    logProgress("\nDone addTreeNativeRoots");
  }

  int addTreeUnderCallCount = 0;

  private void addTreeUnder(Long parentid, Long n, Integer atlayer, HashMap<Long, Node> nodes, Long root) {
    addTreeUnderCallCount++;
    logProgress("com.act.reachables.ComputeReachablesTree: Num nodes added to tree (TODO: speedup): %d\r", addTreeUnderCallCount);

    // more than one child, it makes sense to add this node as a branch off point.
    Node node = Node.get(n, true);
    ActData.instance().ActTree.addNodeTreeSpecific(node, n, atlayer, parentid);
    nodes.put(n, node);
    @SuppressWarnings("unchecked")
    HashMap<String, Integer> attr = (HashMap<String, Integer>)this.tree.nodeAttributes.get(n);
    // need to add if layer == 1 then globalLayer->1, hostLayer=getHostLayerOf(n) else globalLayer->layer, hostLayer=-1
    setNodeAttributes(node, n, attr, root);

    // add edge to parent
    if (parentid != null) {
      Node parentnode = Node.get(parentid, false);
      Edge to_parent_edge = Edge.get(node, parentnode, true);
      ActData.instance().ActTree.addEdgeTreeSpecific(to_parent_edge, node.id);
      Integer globalLayer = attr.get("globalLayer");
      double globalLayerPositive = Integer.valueOf(2 + (globalLayer != null ? globalLayer : 0)).doubleValue(); // make sure it is a positive number.
      Edge.setAttribute(to_parent_edge, "globalLayerPositive", globalLayerPositive);
      Edge.setAttribute(to_parent_edge, "globalLayerPositive_inv", 1.0/globalLayerPositive);
      Edge.setAttribute(to_parent_edge, "functionalCategory", this.functionalCategory.get(n) != null ? this.functionalCategory.get(n) : "");
      Edge.setAttribute(to_parent_edge, "importantAncestor", this.importantAncestor.get(n) != null ? "" + this.importantAncestor.get(n): "");
      Edge.setAttribute(to_parent_edge, "under_root", root);
    }

    Set<Long> children = this.tree.getChildren(n);

    int num_children_added = 0;
    if (children != null && children.size() <= GlobalParams._actTreeCompressNodesWithChildrenLessThan) {
      // only one child, so this node is just a connector node,
      // skip it and connect child directly to parent
      for (Long ch : children)
        addTreeUnder(parentid, ch, atlayer + 1, nodes, root); // notice that we leave the parent as "parent" and not "n"

      // IMP: num_children_added to this node remains 0
    } else {
      // recurse to all children
      if (children != null)
        for (Long ch : children) {
          addTreeUnder(node.id, ch, atlayer + 1, nodes, root);
          num_children_added++;
        }
    }

    // if (num_children_added == 0 && (parentid != null && parentid == -1))
    //   Node.setAttribute(node, "centralAndWithNoChild", true);
  }

  private void setRootAttributes(Node n, int layer) {

    Node.setAttribute(n.getIdentifier(), "canonical", "natives/cofactors");
    Node.setAttribute(n.getIdentifier(), "Name", "natives/cofactors");
    Node.setAttribute(n.getIdentifier(), "Synonyms", "natives/cofactors");
    Node.setAttribute(n.getIdentifier(), "fulltxt", "natives/cofactors");
    Node.setAttribute(n.getIdentifier(), "InChI", "");
    Node.setAttribute(n.getIdentifier(), "SMILES", "");

    Node.setAttribute(n.getIdentifier(), "globalLayer", layer);
  }

  private void setNodeAttributes(Node n, Long nid, HashMap<String, Integer> attributes, Long root) {
    Chemical c = this.db.getChemicalFromChemicalUUID(nid);
    String txt = null;

    for (String key : attributes.keySet())
      Node.setAttribute(n.getIdentifier(), key, attributes.get(key));
    Node.setAttribute(n.getIdentifier(), "subtreeSz", this.subtreeSz.get(nid) != null ? this.subtreeSz.get(nid) : -1);
    Node.setAttribute(n.getIdentifier(), "subtreeVendorsSz", this.subtreeVendorsSz.get(nid) != null ? this.subtreeVendorsSz.get(nid) : -1);
    Node.setAttribute(n.getIdentifier(), "subtreeValue", this.subtreeVal.get(nid) != null ? this.subtreeVal.get(nid) : -1);
    Double subtreeValueIncrement = subtreeValueIncrement(nid);
    if (subtreeValueIncrement != null)
      Node.setAttribute(n.getIdentifier(), "subtreeValueIncrement", subtreeValueIncrement);
    if (subtreeValueIncrement != null && subtreeValueIncrement > 0.0)
      Node.setAttribute(n.getIdentifier(), "subtreeValueIncrementLog", Math.log(subtreeValueIncrement));
    Node.setAttribute(n.getIdentifier(), "functionalCategory", this.functionalCategory.get(nid) != null ? this.functionalCategory.get(nid) : "");
    Node.setAttribute(n.getIdentifier(), "importantAncestor", this.importantAncestor.get(nid) != null ? "" + this.importantAncestor.get(nid) : "");
    Node.setAttribute(n.getIdentifier(), "num_children", this.tree.getChildren(nid) != null ? this.tree.getChildren(nid).size() : 0);
    Node.setAttribute(n.getIdentifier(), "parent", this.tree.getParent(nid) != null ? this.tree.getParent(nid) : -1);
    Node.setAttribute(n.getIdentifier(), "under_root", root);
    if (this.importantAncestor.get(nid) != null && this.importantAncestor.get(nid) == nid)
      Node.setAttribute(n.getIdentifier(), "owns_clade", true);
    if (txt != null) Node.setAttribute(n.getIdentifier(), "fulltxt", txt);
    if (c != null) {
      if (c.getInChI() != null) Node.setAttribute(n.getIdentifier(), "InChI", c.getInChI());
    }
  }

  /**
   * Returns extended attributes for a particular chemical as JSON for use in serialization of reachables trees.
   *
   * Note that Node is not used in this function.  This is to eliminate the possibility of this additional (and
   * potentially very large) structure from being added to the tree structure.  Instead, this should be generated on
   * demand, written, and discarded immediately to reduce memory overhead.
   * @param c A chemical whose attributes should be looked up.
   * @return A JSONObject containing extended attributes for the specified chemical.  Serialize this with the
   * reachables tree.
   */
  public static Map<String, Object> getExtendedChemicalInformationJSON(Chemical c) {
    HashMap<String, Object> result = new HashMap<>();

    String[] names = getReadableName(c.getInChI(), c.getBrendaNames(), c.getSynonyms());
    result.put("ReadableName", names[0]);
    result.put("NameOfLen" + GlobalParams._actTreePickNameOfLengthAbout, names[1]);
    // InChI is already stored as part of the network for debugging purposes.
    if (c.getSmiles() != null) {
      result.put("SMILES", c.getSmiles());
    }
    if (c.getCanon() != null) {
      result.put("canonical", c.getCanon());
    }
    if (c.getShortestName() != null) {
      result.put("Name", c.getShortestName());
    }
    if (c.getBrendaNames() != null && c.getSynonyms() != null) {
      result.put("Synonyms", c.getBrendaNames().toString() + c.getSynonyms().toString());
    }

    JSONObject has = new JSONObject();
    for (REFS db : REFS.values()) {
      JSONObject dbhas = c.getRef(db);
      if (dbhas != null) {
        String url;
        switch (db) {
          case WIKIPEDIA:
            // dbid, e.g., = "http://en.wikipedia.org/wiki/Arsenous acid"
            url = (String) dbhas.get("dbid");
            has.put("wikipedia", url);
            addToURLs(url, has);
            break;

          case DRUGBANK:
            // dbid, e.g = DB04456
            // contains druginteractions patents etc.
            url = "http://www.drugbank.ca/drugs/" + dbhas.get("dbid");
            has.put("drugbank", url);
            addToURLs(url, has);
            break;

          case KEGG_DRUG:
            // dbid, e.g. = D04018
            url = "http://www.kegg.jp/entry/" + dbhas.get("dbid");
            has.put("kegg_drug", url);
            break;

          case SIGMA:
            // dbid, e.g. = FLUKA_54789 ALDRICH_420085 SIGMA_C7495
            // metadata.sigma = FLUKA or ALDRICH SIGMA, id = 54789 420085 or C7495
            // url = http://www.sigmaaldrich.com/catalog/product/sigma/C7495
            // url = http://www.sigmaaldrich.com/catalog/product/fluka/54789
            // url = http://www.sigmaaldrich.com/catalog/product/aldrich/420085
            JSONObject meta;
            String subdb = (String) (meta = (JSONObject) dbhas.get("metadata")).get("sigma");
            url = "";
            if (subdb.equals("SIGMA"))
              url = "http://www.sigmaaldrich.com/catalog/product/sigma/" + meta.get("id");
            else if (subdb.equals("ALDRICH"))
              url = "http://www.sigmaaldrich.com/catalog/product/aldrich/" + meta.get("id");
            else if (subdb.equals("FLUKA"))
              url = "http://www.sigmaaldrich.com/catalog/product/fluka/" + meta.get("id");
            has.put("sigma", url);
            addToURLs(url, has);
            break;

          case HSDB:
            // dbid, e.g. = CAS_102-54-5
            has.put("hsdb", dbhas.get("dbid"));
            break;

          case WHO:
            // dbid, e.g. = corresponding drugbank id
            url = "http://www.drugbank.ca/drugs/" + dbhas.get("dbid");
            has.put("who", url);
            addToURLs(url, has);
            break;

          case SIGMA_POLYMER:
            // dbid, e.g. = CAS_123322-60-1
            has.put("sigma_polymer", dbhas.get("dbid"));
            break;

          case ALT_PUBCHEM:
            // contains alternative pubchem names and structures, not relevant, ignore
            break;

          case KEGG:
            // id, is a list of kegg ids e.g., [ "C10394" ]
            // but url is a single url to chemical
            url = (String) dbhas.get("url");
            has.put("kegg", url);
            addToURLs(url, has);
            break;

          case METACYC:
            // metacyc is slightly complex because each entry might have multiple url refs into metacyc db
            // so we need to pull out the xref.METACYC.meta which gives a list of objects
            // each of these objects has a url field that we can establish into the output
            JSONArray metacyc_meta = (JSONArray) dbhas.get("meta");
            Set<String> uniqurls = new HashSet<String>();
            for (int i = 0; i < metacyc_meta.length(); i++) {
              Object o = metacyc_meta.get(i);
              JSONObject jo = (JSONObject)o;
              if (jo.has("url")) {
                uniqurls.add((String) jo.get("url"));
              }
            }
            JSONArray urls = new JSONArray();
            for (String u : uniqurls) {
              urls.put(u);
              addToURLs(u, has);
            }
            has.put("metacyc", urls);
            break;

          case DEA: // very little data, dump the entire object to the output (contains, id, common names etc.)
            has.put("dea", MongoDBToJSON.conv(dbhas));
            break;

          case CHEBI:
            // CHEBI.metadata.{name, id} is a good jsonfield to output
            url = "http://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:" + dbhas.get("dbid");
            has.put("chebi", url);
            addToURLs(url, has);
            has.put("chebi_name", dbhas.get("dbid"));
            break;

          case PUBCHEM_TOX: // no data
          case TOXLINE: // no data
          case pubmed: // no data
          case genbank: // no data
          case CURATED: // fallthrough to default includeall
          default:
            // by default put the entire DBObject (converted to JSONObject) in the node,
            // this could be really large; but for the really large ones (drugbank etc, we only xref)
            has.put(db.name(), MongoDBToJSON.conv(dbhas));
        }
      }
    }
    result.put("has", has);

    return result;
  }

  private static void addToURLs(String url, JSONObject container) {
    if (!container.has("urls"))
      container.put("urls", new JSONArray());
    JSONArray urlArr = (JSONArray) container.get("urls");
    urlArr.put(url);
  }

  private Double subtreeValueIncrement(Long nid) {
    if (this.subtreeVal.get(nid) == null)
      return null;
    Double nodeVal = this.subtreeVal.get(nid);
    if (this.tree.getChildren(nid) != null) {
      // if there are some children then nodeVal := its value - max (children's val)
      Double max = 0.0;
      for (Long ch : this.tree.getChildren(nid)) {
        Double chVal = this.subtreeVal.get(ch);
        if (chVal != null)
          max = Math.max(max, chVal);
      }
      nodeVal -= max;
      // by defn, since the subtreeVal are cummulative sums, subtracting the
      // max child will not result in a negative val, so logs can be safely taken
    }

    return nodeVal;
  }

  private static String[] getReadableName(String inchi, List<String> brendaNames, List<String> synonyms) {
    if (brendaNames == null && synonyms == null)
      if (inchi == null) {
        return new String[] { "[no name]", "no name" };
      } else {
        String truncatedName = inchi.substring(0, GlobalParams._actTreePickNameOfLengthAbout) + "...";
        return new String[] { truncatedName, truncatedName };
      }
    int lenAway = Integer.MAX_VALUE;
    String closestLenName = "no name";
    List<String> goodNames = new ArrayList<String>();
    for (String b : brendaNames)
      if (goodNameCharacteristics(b)) {
        if (goodNames.size() < 3) {
          goodNames.add(b);
          int delta = Math.abs(b.length() - GlobalParams._actTreePickNameOfLengthAbout);
          if (lenAway > delta) { lenAway = delta; closestLenName = b; }
        }
      }
    for (String s : synonyms)
      if (goodNameCharacteristics(s)) {
        if (goodNames.size() < 3) {
          goodNames.add(s);
          int delta = Math.abs(s.length() - GlobalParams._actTreePickNameOfLengthAbout);
          if (lenAway > delta) { lenAway = delta; closestLenName = s; }
        }
      }
    return new String[] { goodNames.toString(), closestLenName };
  }

  private static final Pattern ALPHABETIC = Pattern.compile("[a-zA-Z]");
  private static boolean goodNameCharacteristics(String name) {
    return name.length() > 4 && ALPHABETIC.matcher(name).find(); // it is >4 characters and contains alphabetic characters
  }

}

// This is from categories.xlsx that Chris tagged, and then we pulled out the category names
class Categories {
  public static String[][] InChI2CategoryName = {
  {
    "InChI=1S/C30H50O/c1-24(2)14-11-17-27(5)20-12-18-25(3)15-9-10-16-26(4)19-13-21-28(6)22-23-29-30(7,8)31-29/h14-16,20-21,29H,9-13,17-19,22-23H2,1-8H3/b25-15+,26-16+,27-20+,28-21+/t29-/m0/s1",
    "Steroids",
//    [(3S)-2,3-oxidosqualene, (3S)-squalene-2,3-epoxide, (S)-2,3-oxidosqualene, (3R)-Squalene epoxide, 2,3-Epoxysqualene, (S)-2,3-Epoxysqualene, 2,3-oxidosqualene, (S)-squalene-2,3-epoxide, (S)-2,3-epoxysqualene];[2,3-Oxidosqualene, Squalene 2,3-oxide, Squalene 2,3-epoxide, 2,3-Edsq, (S)-Squalene-2,3-epoxide, 2,3-epoxisqualene, AC1NQWZH, (3S)-2,2-dimethyl-3-[(7E)-3,7,12,16,20-pentamethylhenicosa-3,7,11,15,19-pentaenyl]oxirane]
  },


  {
    "InChI=1S/C15H28O7P2/c1-13(2)7-5-8-14(3)9-6-10-15(4)11-12-21-24(19,20)22-23(16,17)18/h7,9,11H,5-6,8,10,12H2,1-4H3,(H,19,20)(H2,16,17,18)",
    "Sesuiterpenes",
//    [trans-farnesyl diphosphate, a poly-cis-polyprenyl diphosphate longer by one C5 unit, 2-trans,6-trans-farnesyl diphosphate, trans,trans-farnesyl diphosphate];[2-cis,6-trans-farnesyl diphosphate, CHEBI:19515, (2Z,6E)-3,7,11-trimethyldodeca-2,6,10-trien-1-yl trihydrogen diphosphate, 1fpp, AC1L9IDX, CHEMBL1160060, LMPR0103010010, C16826, phosphono [(2Z,6E)-3,7,11-trimethyldodeca-2,6,10-trienyl] hydrogen phosphate, 3,7,11-trimethyldodeca-2,6,10-trien-1-yl trihydrogen diphosphate, phosphono 3,7,11-trimethyldodeca-2,6,10-trienyl hydrogen phosphate, AC1L19U8, FPP003, all-trans Farnesyl pyrophosphate, CHEBI:50277, 13058-04-3]
  },


  {
    "InChI=1S/C10H20O7P2/c1-9(2)5-4-6-10(3)7-8-16-19(14,15)17-18(11,12)13/h5,7H,4,6,8H2,1-3H3,(H,14,15)(H2,11,12,13)/b10-7+",
    "Monoterpenes",
//    [];[Polyprenyl diphosphate, trans-Polyisopentenyldiphosphate, trans-Geranyl pyrophosphate, geranyl-PP, GPP, Gpp]
  },


  {
    "InChI=1S/C15H12O5/c16-9-3-1-8(2-4-9)13-7-12(19)15-11(18)5-10(17)6-14(15)20-13/h1-6,13,16-18H,7H2/t13-/m0/s1",
    "Flavanoids",
    // "names":{"synonyms":["naringenin","Salipurol","naringetol","salipurpol","pelargidanon","Asahina","Naringenine","(S)-Naringenin","YSO1","(-)-(2S)-Naringenin","Naringenin","4',5,7-Trihydroxyflavanone","(2S)-Naringenin"]}
  },
  {
    "InChI=1S/C15H12O5/c16-9-3-1-8(2-4-9)13-7-12(19)15-11(18)5-10(17)6-14(15)20-13/h1-6,13,16-18H,7H2/t13-/m1/s1",
    "Flavanoids",
    // "names":{"synonyms":["(2R)-5,7-dihydroxy-2-(4-hydroxyphenyl)-2,3-dihydro-4H-chromen-4-one","AC1LDI7C","CHEBI:50201","MolPort-002-507-277","BB_NC-1001","480-41-1","BBL010488","STK801623","ZINC00001785","AKOS004119880"]}
  },
  {
    "InChI=1S/C15H12O5/c16-9-3-1-8(2-4-9)13-7-12(19)15-11(18)5-10(17)6-14(15)20-13/h1-6,13,16-18H,7H2",
    "Flavanoids",
    // "names":{"synonyms":["naringenin","(-)-Naringenin","Naringenine","NARIGENIN","4',5,7-Trihydroxyflavanone","MLS000738094","480-41-1","NSC11855","NSC34875","4',7-Trihydroxyflavanone"]}
  },
  {
    "InChI=1S/C15H10O7/c16-7-4-10(19)12-11(5-7)22-15(14(21)13(12)20)6-1-2-8(17)9(18)3-6/h1-5,16-19,21H",
    "Flavanoids",
    // "names":{"synonyms":["quercetin","Meletin","Sophoretin","Quercetine","Quercetol","Xanthaurine","Quercitin","Quertine","Flavin meletin","Quercetin","3,3',4,5,7-Pentahydroxyflavone","3,5,7,3',4'-Pentahydroxyflavone"]}}
  },
  {
    "InChI=1S/C15H10O6/c16-8-3-1-7(2-4-8)15-14(20)13(19)12-10(18)5-9(17)6-11(12)21-15/h1-6,16-18,20H",
    "Flavanoids",
    // "names":{"synonyms":["kaempferol","Rhamnolutein","Kempferol","Populnetin","Robigenin","Trifolitin","Pelargidenolon","Kaempherol","Rhamnolutin","Swartziol","3,5,7-triOH-Flavone","Kaempferol","3,4',5,7-Tetrahydroxyflavone","3,5,7-Trihydroxy-2-(4-hydroxyphenyl)-4H-1-benzopyran-4-one","5,7,4'-Trihydroxyflavonol","C.I. 75640","Indigo yellow","Nimbecetin"]}
  },


  {
    "InChI=1S/C30H42N7O18P3S/c1-30(2,25(42)28(43)33-10-9-20(39)32-11-12-59-21(40)8-5-17-3-6-18(38)7-4-17)14-52-58(49,50)55-57(47,48)51-13-19-24(54-56(44,45)46)23(41)29(53-19)37-16-36-22-26(31)34-15-35-27(22)37/h3-8,15-16,19,23-25,29,38,41-42H,9-14H2,1-2H3,(H,32,39)(H,33,43)(H,47,48)(H,49,50)(H2,31,34,35)(H2,44,45,46)/b8-5+/t19-,23-,24-,25+,29-/m1/s1",
    "Coumarines and isoflavonoids",
    // "names":{"synonyms":["AC1L18ZJ","S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] 3-(4-hydroxyphenyl)prop-2-enethioate","p-Coumaroyl-CoA","4-Coumaroyl-CoA","4-Hydroxycinnamoyl-CoA"]}
  },


  {
    "InChI=1S/C16H25N5O15P2/c1-4-7(22)9(24)11(26)15(33-4)35-38(30,31)36-37(28,29)32-2-5-8(23)10(25)14(34-5)21-3-18-6-12(21)19-16(17)20-13(6)27/h3-5,7-11,14-15,22-26H,2H2,1H3,(H,28,29)(H,30,31)(H3,17,19,20,27)/t4-,5+,7+,8+,9+,10+,11-,14+,15+/m0/s1",
    "Oligosaccharides/carbohydrates/fucrose-fanout",
    // "names":{"synonyms":["Gdp fucose","Guanosine diphosphofucose","GDP-L-fucose","GDP-beta-L-fucose","guanosine diphosphate fucose","AC1L96W5","CHEBI:17009","HMDB01095","(6-deoxy-beta-l-galactopyranosyl) ester","guanosine 5'-[3-(6-deoxy-L-galactopyranosyl) dihydrogen diphosphate]"]}
  },


  {
    "InChI=1S/C18H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h6-7,9-10H,2-5,8,11-17H2,1H3,(H,19,20)/b7-6+,10-9+",
    "Lipids",
    // "names":{"synonyms":["linoleic acid","Linolic acid","Linoleate","cis,cis-Linoleic acid","Telfairic acid","cis-9,cis-12-Octadecadienoic acid","Emersol 315","Grape seed oil","Unifac 6550","cis,cis-9,12-Octadecadienoic acid","Linoleic acid","(9Z,12Z)-Octadecadienoic acid","9-cis,12-cis-Octadecadienoate","9-cis,12-cis-Octadecadienoic acid"]}
  },
  {
    "InChI=1S/C18H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h6-7,9-10H,2-5,8,11-17H2,1H3,(H,19,20)/p-1/b7-6-,10-9-",
    "Lipids",
    // "names":{"synonyms":["Linoleic acid","ion(1-)","cis,cis-9,12-octadecadienoate","(9Z,12Z)-octadeca-9,12-dienoate","cis,cis-linoleate","60-33-3","omega-6 fatty acid","LINOLEIC_ACID","AC1NUT3J","CHEBI:30245","cis-Delta(9,12)-octadecadienoate"]}
  },
  {
    "InChI=1S/C18H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h6-7,9-10H,2-5,8,11-17H2,1H3,(H,19,20)",
    "Lipids",
    // "names":{"synonyms":["9,12-Octadecadienoic acid","cis-Linoleic acid","Linoleic acid","9,12-Octadecadienoic acid (Z,Z)-","60-33-3","10-trans,12-cis-linoleic acid"]}
  },
  {
    "InChI=1S/C18H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h6-7,9-10H,2-5,8,11-17H2,1H3,(H,19,20)/p-1",
    "Lipids",
    // "names":{"synonyms":["linoleoate","9,12-octadecadienoate","Linoleate","linoleate","9-cis,12-cis-Octadecadienoate"]
  },
  {
    "InChI=1S/C33H58N7O17P3S/c1-4-5-6-7-8-9-10-11-12-13-24(42)61-17-16-35-23(41)14-15-36-31(45)28(44)33(2,3)19-54-60(51,52)57-59(49,50)53-18-22-27(56-58(46,47)48)26(43)32(55-22)40-21-39-25-29(34)37-20-38-30(25)40/h20-22,26-28,32,43-44H,4-19H2,1-3H3,(H,35,41)(H,36,45)(H,49,50)(H,51,52)(H2,34,37,38)(H2,46,47,48)/t22-,26-,27-,28+,32-/m1/s1",
    "Lipids",
    // "names":{"synonyms":["Dodecanoyl-coa","Lauroyl-coa","Lauroyl-coenzyme A","Dodecanoyl-coenzyme A","DODECYL-COA","Lauroyl coenzyme A","Coenzyme A","S-dodecanoate","6244-92-4","Coenzyme A","lauroyl-","Lauryl-CoA","Lauroyl-CoA","Dodecanoyl-CoA"]}
  },
  {
    "InChI=1S/C33H58N7O17P3S/c1-4-5-6-7-8-9-10-11-12-13-24(42)61-17-16-35-23(41)14-15-36-31(45)28(44)33(2,3)19-54-60(51,52)57-59(49,50)53-18-22-27(56-58(46,47)48)26(43)32(55-22)40-21-39-25-29(34)37-20-38-30(25)40/h20-22,26-28,32,43-44H,4-19H2,1-3H3,(H,35,41)(H,36,45)(H,49,50)(H,51,52)(H2,34,37,38)(H2,46,47,48)/t22-,26+,27-,28+,32+/m0/s1",
    "Lipids",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C33H58N7O17P3S/c1-4-5-6-7-8-9-10-11-12-13-24(42)61-17-16-35-23(41)14-15-36-31(45)28(44)33(2,3)19-54-60(51,52)57-59(49,50)53-18-22-27(56-58(46,47)48)26(43)32(55-22)40-21-39-25-29(34)37-20-38-30(25)40/h20-22,26-28,32,43-44H,4-19H2,1-3H3,(H,35,41)(H,36,45)(H,49,50)(H,51,52)(H2,34,37,38)(H2,46,47,48)",
    "Lipids",
    // "names":{"synonyms":["lauryl-CoA"]}
  },
  {
    "InChI=1S/C37H66N7O17P3S/c1-4-5-6-7-8-9-10-11-12-13-14-15-16-17-28(46)65-21-20-39-27(45)18-19-40-35(49)32(48)37(2,3)23-58-64(55,56)61-63(53,54)57-22-26-31(60-62(50,51)52)30(47)36(59-26)44-25-43-29-33(38)41-24-42-34(29)44/h24-26,30-32,36,47-48H,4-23H2,1-3H3,(H,39,45)(H,40,49)(H,53,54)(H,55,56)(H2,38,41,42)(H2,50,51,52)/t26-,30-,31-,32+,36-/m1/s1",
    "Lipids",
    // "names":{"synonyms":["palmitoyl-CoA","Hexadecanoyl-CoA","s-{1-[5-(6-amino-9h-purin-9-yl)-4-hydroxy-3-(phosphonooxy)tetrahydrofuran-2-yl]-3,5,9-trihydroxy-8,8-dimethyl-3,5-dioxido-10,14-dioxo-2,4,6-trioxa-11,15-diaza-3lambda~5~,5lambda~5~-diphosphaheptadecan-17-yl} hexadecanethioate(non-preferred name)","AC1L1AH5","AC1Q68ZG","P9804_SIGMA","AR-1L3794","Palmitoyl-(carbonyl-14C)-coenzyme A","[(2R,3S,4R,5R)-5-(6-aminopurin-9-yl)-2-[[[[(3R)-3-[2-(2-hexadecanoylsulfanylethylcarbamoyl)ethylcarbamoyl]-3-hydroxy-2,2-dimethyl-propoxy]-hydroxy-phosphoryl]oxy-hydroxy-phosphoryl]oxymethyl]-4-hydroxy-oxolan-3-yl]oxyphosphonic acid","S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] hexadecanethioate","Palmitoyl-CoA"]}
  },


  {
    "InChI=1S/C12H22R2NO8P/c1-15(2,3)6-7-21-24(18,19)22-9-10(23-12(14)17)8-20-11(16)4-5-13/h10H,4-9H2,1-3H3/p+1/t10-/m0/s1",
    "Glycolipids (a subclade of lipids)",
    // "names":{"synonyms":[]}
  },


  {
    "InChI=1S/C18H37NO2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-18(21)17(19)16-20/h14-15,17-18,20-21H,2-13,16,19H2,1H3/b15-14+/t17-,18+/m0/s1",
    "Sphingolipids (a subclass of lipids)",
    // "names":{"synonyms":["sphingosine","4-Sphingenine","D-erythro-Sphingosine","D-Sphingosine","Sphing-4-enine","Sphingenine","Sphingoid","cerebroside","Erythrosphingosine","(4E)-Sphingenine","Sphingosine"]}
  },
  {
    "InChI=1S/C18H37NO2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-18(21)17(19)16-20/h14-15,17-18,20-21H,2-13,16,19H2,1H3/b15-14+/t17-,18+/m1/s1",
    "Sphingolipids (a subclass of lipids)",
    // "names":{"synonyms":["SPH","AC1L9L4S","(E,2R,3R)-2-aminooctadec-4-ene-1,3-diol"]}
  },
  {
    "InChI=1S/C18H37NO2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-18(21)17(19)16-20/h14-15,17-18,20-21H,2-13,16,19H2,1H3",
    "Sphingolipids (a subclass of lipids)",
    // "names":{"synonyms":["Sphingosine"]}
  },
  {
    "InChI=1S/C18H37NO2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-18(21)17(19)16-20/h14-15,17-18,20-21H,2-13,16,19H2,1H3/b15-14-/t17-,18+/m1/s1",
    "Sphingolipids (a subclass of lipids)",
    // "names":{"synonyms":[]}
  },


  {
    "InChI=1S/C20H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20(21)22/h6-7,9-10,12-13,15-16H,2-5,8,11,14,17-19H2,1H3,(H,21,22)",
    "Signalling molecules (a subclass of lipids)",
    // "names":{"synonyms":["5,8,11,14-Eicosatetraenoic acid","AG-F-70356","AG-H-11197","Spectrum_000091","SpecPlus_000727","Spectrum4_000905","AC1L18RV","CBiol_001948","KBioGR_000259","KBioGR_001370","5,8,11,14-Eicosatetraenoicacid","Arachidonic acid","506-32-1","ARACHIDONIC_ACID"]}
  },
  {
    "InChI=1S/C20H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20(21)22/h6-7,9-10,12-13,15-16H,2-5,8,11,14,17-19H2,1H3,(H,21,22)/b7-6+,10-9+,13-12+,16-15+",
    "Signalling molecules (a subclass of lipids)",
    // "names":{"synonyms":["arachidonic acid","arachidonate","506-32-1","5,8,11,14-Eicosatetraenoic acid","(all-Z)-","(5Z,8Z,11Z,14Z)-icosa-5,8,11,14-tetraenoic acid","all-cis-5,8,11,14-eicosatetraenoic acid","CHEBI:15843","5Z,8Z,11Z,14Z-eicosatetraenoic acid","cis-5,8,11,14-Eicosatetraenoic acid","ST069383","Arachidonate","Arachidonic acid","(5Z,8Z,11Z,14Z)-Icosatetraenoic acid"]}
  },
  {
    "InChI=1S/C20H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20(21)22/h6-7,9-10,12-13,15-16H,2-5,8,11,14,17-19H2,1H3,(H,21,22)/p-1/b7-6-,10-9-,13-12-,16-15-",
    "Signalling molecules (a subclass of lipids)",
    // "names":{"synonyms":["arachidonate","(5Z,8Z,11Z,14Z)-icosa-5,8,11,14-tetraenoate","506-32-1","CHEBI:32395","ARACHIDONIC_ACID","AC1NUT0I","5,8,11,14-icosatetraenoate","ZINC04474696","(all-Z)-icosa-5,8,11,14-tetraenoate","A828211"]}
  },
  {
    "InChI=1S/C20H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20(21)22/h6-7,9-10,12-13,15-16H,2-5,8,11,14,17-19H2,1H3,(H,21,22)/p-1",
    "Signalling molecules (a subclass of lipids)",
    // "names":{"synonyms":["arachidonate","arachidonoate"]}
  },


  {
    "InChI=1S/C20H32O5/c1-2-3-6-9-15(21)12-13-17-16(18-14-19(17)25-24-18)10-7-4-5-8-11-20(22)23/h4,7,12-13,15-19,21H,2-3,5-6,8-11,14H2,1H3,(H,22,23)/p-1/b7-4-,13-12+/t15-,16+,17+,18-,19+/m0/s1",
    "Prostaglandins (a subclass of lipids)",
    // "names":{"synonyms":["prostaglandin h2","(5Z,13E,15S)-9alpha,11alpha-epidioxy-15-hydroxyprosta-5,13-dienoate","prostaglandin H2 anion","(5Z)-7-{(1R,4S,5R,6R)-6-[(1E,3S)-3-hydroxyoct-1-en-1-yl]-2,3-dioxabicyclo[2.2.1]hept-5-yl}hept-5-enoate","CHEBI:57405"]}
  },
  {
    "InChI=1S/C20H32O5/c1-2-3-6-9-15(21)12-13-17-16(18-14-19(17)25-24-18)10-7-4-5-8-11-20(22)23/h4,7,12-13,15-19,21H,2-3,5-6,8-11,14H2,1H3,(H,22,23)/b7-4-,13-12+/t15-,16+,17+,18-,19+/m0/s1",
    "Prostaglandins (a subclass of lipids)",
    // "names":{"synonyms":["prostaglandin h2","PGH2","9,11-Epoxymethano-pgh2","Endoperoxide H2","Prostaglandin R2","prostaglandin-H2","9S,11R-epidioxy-15S-hydroxy-5Z,13E-prostadienoic acid","15-Hydroxy-9alpha,11alpha-peroxidoprosta-5,13-dienoic acid","(15S)Hydroxy-9alpha,11alpha-(epoxymethano)prosta-5,13-dienoic acid","(5Z,13E)-(15S)-9alpha,11alpha-Epidioxy-15-hydroxyprosta-5,13-dienoate","Prostaglandin H2","(5Z,13E,15S)-9alpha,11alpha-Epidioxy-15-hydroxyprosta-5,13-dienoate","(5Z,9alpha,11alpha,13E,15S)-9,11-Epidioxy-15-hydroxy-prosta-5,13-dienoate"]}
  },
  {
    "InChI=1S/C20H32O5/c1-2-3-6-9-15(21)12-13-17-16(18-14-19(17)25-24-18)10-7-4-5-8-11-20(22)23/h4,7,12-13,15-19,21H,2-3,5-6,8-11,14H2,1H3,(H,22,23)/b7-4+,13-12+",
    "Prostaglandins (a subclass of lipids)",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C20H32O5/c1-2-3-6-9-15(21)12-13-17-16(18-14-19(17)25-24-18)10-7-4-5-8-11-20(22)23/h4,7,12-13,15-19,21H,2-3,5-6,8-11,14H2,1H3,(H,22,23)",
    "Prostaglandins (a subclass of lipids)",
    // "names":{"synonyms":["prostaglandin h2"]}
  },


  {
    "InChI=1S/C3H9O6P/c4-1-3(5)2-9-10(6,7)8/h3-5H,1-2H2,(H2,6,7,8)",
    "Glycerophospholipids (a subclade of phospholipids and more generally lipids)",
    // "names":{"synonyms":["3-Glycerophosphate","1-Glycerophosphate","1-Glycerophosphoric acid","alpha-glycerophosphoric acid","alpha-Phosphoglycerol","Glycerophosphoric acid","Glycerol alpha-phosphate","Glycerol 1-phosphate","2,3-dihydroxypropyl dihydrogen phosphate","DL-Glycerol 1-phosphate","Glycerol-3-phosphate","rac-Glycerol 1-phosphate","DL-Glyceryl 1-phosphate","DL-Glycerol 3-phosphate"]}
  },
  {
    "InChI=1S/C3H9O6P/c4-1-3(5)2-9-10(6,7)8/h3-5H,1-2H2,(H2,6,7,8)/p-2",
    "Glycerophospholipids (a subclade of phospholipids and more generally lipids)",
    // "names":{"synonyms":["2,3-dihydroxypropyl phosphate","AC1MVHNU","L-alpha-glycerophosphate","glycerol 1-phosphate dianion","CHEBI:231935","CPD0-1186","AKOS005208731","LS-5012","InChI=1/C3H9O6P/c4-1-3(5)2-9-10(6,7)8/h3-5H,1-2H2,(H2,6,7,8)/p-","glyceryl phosphate","mono(2,3-dihydroxypropyl hydrogen phosphate)"]}
  },
  {
    "InChI=1S/C3H9O6P/c4-1-3(5)2-9-10(6,7)8/h3-5H,1-2H2,(H2,6,7,8)/t3-/m0/s1",
    "Glycerophospholipids (a subclade of phospholipids and more generally lipids)",
    // "names":{"synonyms":["sn-Glycerol 1-phosphate","sn-Gro-1-P","1GP","3-phosphoglycerol","6tim","L-Glycerol 1-phosphate","D-(glycerol 3-phosphate)","L-(glycerol 1-phosphate)","CHEBI:16221","AC1L9712"]}
  },
  {
    "InChI=1S/C3H9O6P/c4-1-3(5)2-9-10(6,7)8/h3-5H,1-2H2,(H2,6,7,8)/t3-/m1/s1",
    "Glycerophospholipids (a subclade of phospholipids and more generally lipids)",
    // "names":{"synonyms":["sn-Glycerol 3-phosphate","alpha-glycerophosphate","glycerol-3-phosphate","Glycerol 3-phosphate","GLYCEROPHOSPHATE","sn-Gro-1-P","Glycerophosphoric acid I","DL-Glycerol 1-phosphate","DL-Glycerol 3-phosphate","a-Phosphoglycerol","Glycerophosphoric acid","D-Glycerol 1-phosphate"]}
  },


  {
    "InChI=1S/C39H72O5/c1-3-5-7-9-11-13-15-17-19-21-23-25-27-29-31-33-38(41)43-36-37(35-40)44-39(42)34-32-30-28-26-24-22-20-18-16-14-12-10-8-6-4-2/h17-20,37,40H,3-16,21-36H2,1-2H3/b19-17+,20-18+",
    "Phosphoglycolipids",
    // "names":{"synonyms":["AC1NSUWJ","[3-hydroxy-2-[(E)-octadec-9-enoyl]oxypropyl] (E)-octadec-9-enoate"]}
  },
  {
    "InChI=1S/C39H72O5/c1-3-5-7-9-11-13-15-17-19-21-23-25-27-29-31-33-38(41)43-36-37(35-40)44-39(42)34-32-30-28-26-24-22-20-18-16-14-12-10-8-6-4-2/h17-20,37,40H,3-16,21-36H2,1-2H3/b19-17+,20-18+/t37-/m0/s1",
    "Phosphoglycolipids",
    // "names":{"synonyms":["Sn-1,2-Diolein","sn-1,2-Dioleoylglycerol","BRN 1730457","Olein","1,2-di-","(S)-(-)-","AC1O5SPD","LS-98318","4-02-00-01662 (Beilstein Handbook Reference)","[(2S)-3-hydroxy-2-[(Z)-octadec-9-enoyl]oxypropyl] (E)-octadec-9-enoate","9-Octadecenoic acid (Z)-","1-(hydroxymethyl)-1,2-ethanediyl ester","(S)-","9-Octadecenoic acid (Z)-","1-(hydroxymethyl)-1,2-ethanediyl ester","(S)- (9CI)"]}
  },
  {
    "InChI=1S/C39H72O5/c1-3-5-7-9-11-13-15-17-19-21-23-25-27-29-31-33-38(41)43-36-37(35-40)44-39(42)34-32-30-28-26-24-22-20-18-16-14-12-10-8-6-4-2/h17-20,37,40H,3-16,21-36H2,1-2H3",
    "Phosphoglycolipids",
    // "names":{"synonyms":["(Z)-octadec-9-enoic acid [3-hydroxy-2-[(E)-octadec-9-enoyl]oxy-propyl] ester","(Z)-9-octadecenoic acid [3-hydroxy-2-[(E)-1-oxooctadec-9-enoxy]propyl] ester","[2-[(E)-octadec-9-enoyl]oxy-3-oxidanyl-propyl] (Z)-octadec-9-enoate","[3-hydroxy-2-[(E)-octadec-9-enoyl]oxypropyl] (Z)-octadec-9-enoate","[3-hydroxy-2-[(E)-octadec-9-enoyl]oxy-propyl] (Z)-octadec-9-enoate","1,2-diolein"]}
  },


  {
    "InChI=1S/C9H11NO4/c10-6(9(13)14)3-5-1-2-7(11)8(12)4-5/h1-2,4,6,11-12H,3,10H2,(H,13,14)/t6-/m0/s1",
    "Catechols",
    // "names":{"synonyms":["levodopa","L-dopa","Dopar","Bendopa","Larodopa","Levopa","3-Hydroxy-L-tyrosine","Cidandopa","Dopaidan","Dopalina","3,4-Dihydroxy-L-phenylalanine","L-Dopa","L-beta-(3,4-Dihydroxyphenyl)alanine","Levodopa","Dihydroxy-L-phenylalanine"]}
  },
  {
    "InChI=1S/C9H11NO4/c10-6(9(13)14)3-5-1-2-7(11)8(12)4-5/h1-2,4,6,11-12H,3,10H2,(H,13,14)/t6-/m1/s1",
    "Catechols",
    // "names":{"synonyms":["D-Dopa","Dopa D-form","3,4-Dihydroxy-D-phenylalanine","3-Hydroxy-D-tyrosine","D-3,4-Dihydroxyphenylalanine","CHEBI:49169","(+)-3,4-Dihydroxyphenylalanine","D-3-(3,4-Dihydroxyphenyl)alanine","(2R)-2-amino-3-(3,4-dihydroxyphenyl)propanoic acid","(+)-3-(3,4-Dihydroxyphenyl)alanine"]}
  },
  {
    "InChI=1S/C9H11NO4/c10-6(9(13)14)3-5-1-2-7(11)8(12)4-5/h1-2,4,6,11-12H,3,10H2,(H,13,14)",
    "Catechols",
    // "names":{"synonyms":["DL-DOPA","DL-Dioxyphenylalanine","3-Hydroxytyrosine","3-Hydroxy-DL-tyrosine","DL-Dihydroxyphenylalanine","DL-3,4-Dopa","3-hydroxy-","(R,S)-Dopa","3,4-Dihydroxy-DL-phenylalanine"]}
  },


  {
    "InChI=1S/C5H12O7P2/c1-5(2)3-4-11-14(9,10)12-13(6,7)8/h3H,4H2,1-2H3,(H,9,10)(H2,6,7,8)",
    "Prenylated products (downstream includes all terpenes)",
    // "names":{"synonyms":["DMAPP","dimethylallyl-PPi","2-Isopentenyl diphosphate","dimethylallyl-PP","Dimethylallyl diphosphate","delta2-Isopentenyl diphosphate","Dimethylallyl pyrophosphate","Monoprenyl diphosphate","Prenyl diphosphate","Prenol pyrophosphate"]}
  },
  {
    "InChI=1S/C5H12O7P2/c1-5(2)3-4-11-14(9,10)12-13(6,7)8/h3H,4H2,1-2H3,(H,9,10)(H2,6,7,8)/p-3",
    "Prenylated products (downstream includes all terpenes)",
    // "names":{"synonyms":["Prenyl diphosphate","3,3-dimethylallyl pyrophosphate","dimethylallyl-diphosphate","dimethylallylpyrophosphate","dimethylallyl diphosphate","3,3-dimethylallyl diphosphate","Dimethylallyl diphosphate","Dimethylallyl pyrophosphate","prenyl diphosphate"]}
  },

  {
    "InChI=1S/C20H36O7P2/c1-15(11-14-26-29(24,25)27-28(21,22)23)7-9-17-16(2)8-10-18-19(3,4)12-6-13-20(17,18)5/h11,17-18H,2,6-10,12-14H2,1,3-5H3,(H,24,25)(H2,21,22,23)/b15-11+/t17-,18-,20+/m1/s1",
    "Diterpenes subclass",
    // "names":{"synonyms":["ent-Copalyl diphosphate","(-)-Copalyl diphosphate","Copalyl pyrophosphate","AC1NQXR1","9,10-Syn-cPP","9,10-syn-Copalyl diphosphate","CHEBI:28151","LMPR0104030001","C06089","9betaH-Labda-9(17),13-dien-15-ol diphosphate ester"]}
  },
  {
    "InChI=1S/C20H36O7P2/c1-15(11-14-26-29(24,25)27-28(21,22)23)7-9-17-16(2)8-10-18-19(3,4)12-6-13-20(17,18)5/h11,17-18H,2,6-10,12-14H2,1,3-5H3,(H,24,25)(H2,21,22,23)/b15-11+/t17-,18+,20-/m1/s1",
    "Diterpenes subclass",
    // "names":{"synonyms":["9alpha-Copalyl diphosphate","syn-Copalyl diphosphate"]}
  },
  {
    "InChI=1S/C20H36O7P2/c1-15(11-14-26-29(24,25)27-28(21,22)23)7-9-17-16(2)8-10-18-19(3,4)12-6-13-20(17,18)5/h11,17-18H,2,6-10,12-14H2,1,3-5H3,(H,24,25)(H2,21,22,23)/b15-11+/t17-,18-,20+/m0/s1",
    "Diterpenes subclass",
    // "names":{"synonyms":["Copalyl diphosphate","Labdadienyl diphosphate","AC1NQZD5","CHEBI:30939","LMPR0104030002","C11901","[(E)-5-[(1S,4aS,8aS)-5,5,8a-trimethyl-2-methylidene-3,4,4a,6,7,8-hexahydro-1H-naphthalen-1-yl]-3-methylpent-2-enyl] phosphono hydrogen phosphate","(+)-Copalyl diphosphate"]}
  },


  {
    "InChI=1S/C21H30O2/c1-13(22)17-6-7-18-16-5-4-14-12-15(23)8-10-20(14,2)19(16)9-11-21(17,18)3/h12,16-19H,4-11H2,1-3H3",
    "Steroids (and a specific subclass therein of androgen steroids)",
    // "names":{"synonyms":["(4-14c)pregn-4-ene-3,20-dione","STK374710","Delta4 -Pregnen-3,20-dione","Decolorizing Carbon","17-Isoprogesterone","17-acetyl-10,13-dimethyl-1,2,6,7,8,9,11,12,14,15,16,17-dodecahydrocyclopenta[a]phenanthren-3-one","NORIT A(R)","AC1L1J8O","NCIOpen2_008018","AC1Q1K19","Pregn-4-ene-3,20-dione","Progesterone"]}
  },
  {
    "InChI=1S/C21H30O2/c1-13(22)17-6-7-18-16-5-4-14-12-15(23)8-10-20(14,2)19(16)9-11-21(17,18)3/h12,16-19H,4-11H2,1-3H3/t16-,17+,18-,19-,20-,21+/m0/s1",
    "Steroids (and a specific subclass therein of androgen steroids)",
    // "names":{"synonyms":["progesterone","Luteohormone","Agolutin","Crinone","Pregnenedione","Syngesterone","Cyclogest","Utrogestan","Luteol","Corlutina","Progesterone","4-Pregnene-3,20-dione"]}
  },


  {
    "InChI=1S/C20H36O7P2/c1-17(2)9-6-10-18(3)11-7-12-19(4)13-8-14-20(5)15-16-26-29(24,25)27-28(21,22)23/h9,11,13,15H,6-8,10,12,14,16H2,1-5H3,(H,24,25)(H2,21,22,23)/b18-11+,19-13+,20-15+",
    "Diterpenes",
    // "names":{"synonyms":["Geranylgeranyl diphosphate","Geranylgeranyl pyrophosphate","geranylgeranyl-PP","GGDP","GGPP","Geranylgeraniol pyrophosphate","trans-Geranylgeranyl pyrophosphate","(E,E,E)-Geranylgeranyl diphosphate","all-trans-Geranylgeranyl diphosphate","all-trans-Geranylgeranyl pyrophosphate"]}
  },
  {
    "InChI=1S/C20H36O7P2/c1-17(2)9-6-10-18(3)11-7-12-19(4)13-8-14-20(5)15-16-26-29(24,25)27-28(21,22)23/h9,11,13,15H,6-8,10,12,14,16H2,1-5H3,(H,24,25)(H2,21,22,23)/b18-11-,19-13+,20-15+",
    "Diterpenes",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C20H36O7P2/c1-17(2)9-6-10-18(3)11-7-12-19(4)13-8-14-20(5)15-16-26-29(24,25)27-28(21,22)23/h9,11,13,15H,6-8,10,12,14,16H2,1-5H3,(H,24,25)(H2,21,22,23)/p-3",
    "Diterpenes",
    // "names":{"synonyms":["Geranylgeranyl diphosphate","geranylgeranyl diphosphate","Geranylgeranyl pyrophosphate","trans-Geranylgeranyl pyrophosphate","(E,E,E)-geranylgeranyl diphosphate","(2Z)-geranylgeranyl diphosphate","(E,E,E)-Geranylgeranyl diphosphate"]}
  },
  {
    "InChI=1S/C20H36O7P2/c1-17(2)9-6-10-18(3)11-7-12-19(4)13-8-14-20(5)15-16-26-29(24,25)27-28(21,22)23/h9,11,13,15H,6-8,10,12,14,16H2,1-5H3,(H,24,25)(H2,21,22,23)",
    "Diterpenes",
    // "names":{"synonyms":["GRG"]}
  },
  {
    "InChI=1S/C20H36O7P2/c1-17(2)9-6-10-18(3)11-7-12-19(4)13-8-14-20(5)15-16-26-29(24,25)27-28(21,22)23/h9,11,13,15H,6-8,10,12,14,16H2,1-5H3,(H,24,25)(H2,21,22,23)/b18-11+,19-13+,20-15-",
    "Diterpenes",
    // "names":{"synonyms":["trans,trans,cis-Geranylgeranyl diphosphate","trans,trans,cis-Geranylgeranyl pyrophosphate","Geranylneryl diphosphate","2-cis,6-trans,10-trans-Geranylgeranyl diphosphate"]}
  },
  {
    "InChI=1S/C20H36O7P2/c1-17(2)9-6-10-18(3)11-7-12-19(4)13-8-14-20(5)15-16-26-29(24,25)27-28(21,22)23/h9,11,13,15H,6-8,10,12,14,16H2,1-5H3,(H,24,25)(H2,21,22,23)/b18-11+,19-13-,20-15-",
    "Diterpenes",
    // "names":{"synonyms":["trans,polycis-Polyprenyl diphosphate"]}
  },


  {
    "InChI=1S/C24H38N7O19P3S/c1-24(2,19(37)22(38)27-4-3-13(32)26-5-6-54-15(35)7-14(33)34)9-47-53(44,45)50-52(42,43)46-8-12-18(49-51(39,40)41)17(36)23(48-12)31-11-30-16-20(25)28-10-29-21(16)31/h10-12,17-19,23,36-37H,3-9H2,1-2H3,(H,26,32)(H,27,38)(H,33,34)(H,42,43)(H,44,45)(H2,25,28,29)(H2,39,40,41)/t12-,17-,18-,19+,23-/m1/s1",
    "Malonyl-CoA fanout (contains all PK/FA)",
    // "names":{"synonyms":["1-[5-(6-amino-9h-purin-9-yl)-4-hydroxy-3-(phosphonooxy)tetrahydro-2-furanyl]-3,5,9-trihydroxy-8,8-dimethyl-10,14,19-trioxo-2,4,6-trioxa-18-thia-11,15-diaza-3,5-diphosphahenicosan-21-s","AC1L1A7K","AC1Q5V7R","KST-1B4963","AR-1B9382","3-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethylsulfanyl]-3-oxopropanoic acid","Malonyl-CoA","Malonyl coenzyme A","omega-Carboxyacyl-CoA"]}
  },
  {
    "InChI=1S/C24H38N7O19P3S/c1-24(2,19(37)22(38)27-4-3-13(32)26-5-6-54-15(35)7-14(33)34)9-47-53(44,45)50-52(42,43)46-8-12-18(49-51(39,40)41)17(36)23(48-12)31-11-30-16-20(25)28-10-29-21(16)31/h10-12,17-19,23,36-37H,3-9H2,1-2H3,(H,26,32)(H,27,38)(H,33,34)(H,42,43)(H,44,45)(H2,25,28,29)(H2,39,40,41)/t12-,17+,18-,19-,23+/m0/s1",
    "Malonyl-CoA fanout (contains all PK/FA)",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C24H38N7O19P3S/c1-24(2,19(37)22(38)27-4-3-13(32)26-5-6-54-15(35)7-14(33)34)9-47-53(44,45)50-52(42,43)46-8-12-18(49-51(39,40)41)17(36)23(48-12)31-11-30-16-20(25)28-10-29-21(16)31/h10-12,17-19,23,36-37H,3-9H2,1-2H3,(H,26,32)(H,27,38)(H,33,34)(H,42,43)(H,44,45)(H2,25,28,29)(H2,39,40,41)/t12-,17-,18-,19?,23-/m1/s1",
    "Malonyl-CoA fanout (contains all PK/FA)",
    // "names":{"synonyms":[]}
  },


  {
    "InChI=1S/C28H40N7O17P3S/c1-28(2,22(38)25(39)31-9-8-18(36)30-10-11-56-27(40)16-6-4-3-5-7-16)13-49-55(46,47)52-54(44,45)48-12-17-21(51-53(41,42)43)20(37)26(50-17)35-15-34-19-23(29)32-14-33-24(19)35/h3-7,14-15,17,20-22,26,37-38H,8-13H2,1-2H3,(H,30,36)(H,31,39)(H,44,45)(H,46,47)(H2,29,32,33)(H2,41,42,43)/t17-,20-,21-,22+,26-/m1/s1",
    "Benzoylated products",
    // "names":{"synonyms":["benzoyl-coenzyme A","Benzoyl-coa","Coenzyme A","benzoyl-","Coenzyme A","S-benzoate","AC1L3U4G","6756-74-7","AR-1L3753","S-[2-[3-[[(2R)-4-[[[(2R,3S,4R)-5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] benzenecarbothioate","s-{(9r)-1-[(2r,3s,4r)-5-(6-amino-9h-purin-9-yl)-4-hydroxy-3-(phosphonooxy)tetrahydrofuran-2-yl]-3,5,9-trihydroxy-8,8-dimethyl-3,5-dioxido-10,14-dioxo-2,4,6-trioxa-11,15-diaza-3|E5,5|E5-diphosphaheptadecan-17-yl} benzenecarbothioate(non-preferred name)","S-Benzoate coenzyme A","Benzoyl-CoA"]}
  },


  {
    "InChI=1S/C30H42N7O19P3S/c1-30(2,25(43)28(44)33-8-7-20(40)32-9-10-60-21(41)6-4-16-3-5-17(38)18(39)11-16)13-53-59(50,51)56-58(48,49)52-12-19-24(55-57(45,46)47)23(42)29(54-19)37-15-36-22-26(31)34-14-35-27(22)37/h3-6,11,14-15,19,23-25,29,38-39,42-43H,7-10,12-13H2,1-2H3,(H,32,40)(H,33,44)(H,48,49)(H,50,51)(H2,31,34,35)(H2,45,46,47)/b6-4+/t19-,23-,24-,25?,29-/m0/s1",
    "Caffeoylated products",
    // "names":{"synonyms":["AC1L4O4Q","S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] 3-(3,4-dihydroxyphenyl)prop-2-enethioate"]}
  },
  {
    "InChI=1S/C30H42N7O19P3S/c1-30(2,25(43)28(44)33-8-7-20(40)32-9-10-60-21(41)6-4-16-3-5-17(38)18(39)11-16)13-53-59(50,51)56-58(48,49)52-12-19-24(55-57(45,46)47)23(42)29(54-19)37-15-36-22-26(31)34-14-35-27(22)37/h3-6,11,14-15,19,23-25,29,38-39,42-43H,7-10,12-13H2,1-2H3,(H,32,40)(H,33,44)(H,48,49)(H,50,51)(H2,31,34,35)(H2,45,46,47)/b6-4+/t19-,23-,24-,25+,29-/m1/s1",
    "Caffeoylated products",
    // "names":{"synonyms":["Caffeoyl-CoA"]}
  },


  {
    "InChI=1S/C31H44N7O19P3S/c1-31(2,26(43)29(44)34-9-8-21(40)33-10-11-61-22(41)7-5-17-4-6-18(39)19(12-17)52-3)14-54-60(50,51)57-59(48,49)53-13-20-25(56-58(45,46)47)24(42)30(55-20)38-16-37-23-27(32)35-15-36-28(23)38/h4-7,12,15-16,20,24-26,30,39,42-43H,8-11,13-14H2,1-3H3,(H,33,40)(H,34,44)(H,48,49)(H,50,51)(H2,32,35,36)(H2,45,46,47)/b7-5+/t20-,24-,25-,26+,30-/m1/s1",
    "Feruloylated products",
    // "names":{"synonyms":["AC1LCV2G","S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] 3-(4-hydroxy-3-methoxyphenyl)prop-2-enethioate","Feruloyl-CoA","trans-Feruloyl-CoA"]}
  },


  {
    "InChI=1S/C5H11NO2S/c1-9-3-2-4(6)5(7)8/h4H,2-3,6H2,1H3,(H,7,8)",
    "methionine fanout",
    // "names":{"synonyms":["DL-METHIONINE","methionine","Racemethionine","Acimetion","59-51-8","Mertionin","L-Methionine","DL-Methionine","Methionine","METHIONINE"]}
  },
  {
    "InChI=1S/C5H11NO2S/c1-9-3-2-4(6)5(7)8/h4H,2-3,6H2,1H3,(H,7,8)/t4-/m0/s1",
    "methionine fanout",
    // "names":{"synonyms":["L-methionine","methionine","Cymethion","Liquimeth","L-(-)-Methionine","Methilanin","L-Methioninum","S-Methionine","(L)-Methionine","h-Met-oh","L-Methionine","Methionine","L-2-Amino-4methylthiobutyric acid"]}
  },
  {
    "InChI=1S/C5H11NO2S/c1-9-3-2-4(6)5(7)8/h4H,2-3,6H2,1H3,(H,7,8)/t4-/m1/s1",
    "methionine fanout",
    // "names":{"synonyms":["D-Methionine","R-Methionine","D-Methionin","(R)-Methionine","AG-F-19717","D-2-Amino-4-(methylthio)butyric acid","D-2-Amino-4-(methylthio)butanoic acid","(R)-2-amino-4-(methylthio)butanoic acid","(R)-2-Amino-4-(methylmercapto)butyric acid","(2R)-2-amino-4-(methylsulfanyl)butanoic acid"]}
  },
  {
    "InChI=1S/C5H11NO2S/c1-9-3-2-4(6)5(7)8/h4H,2-3,6H2,1H3,(H,7,8)/p-1",
    "methionine fanout",
    // "names":{"synonyms":["Banthionine","Meonine","Cynaron","Lobamine"]}
  },
  {
    "InChI=1S/C5H11NO2S/c1-9-3-2-4(6)5(7)8/h4H,2-3,6H2,1H3,(H,7,8)/t4-/m0/s1/i5-1",
    "methionine fanout",
    // "names":{"synonyms":[]}
  },


  {
    "InChI=1S/C63H97N17O14S/c1-37(2)33-45(56(87)76-44(62(93)94)27-32-95-3)72-52(83)36-71-53(84)46(34-38-15-6-4-7-16-38)77-57(88)47(35-39-17-8-5-9-18-39)78-55(86)41(23-25-50(66)81)73-54(85)42(24-26-51(67)82)74-58(89)49-22-14-31-80(49)61(92)43(20-10-11-28-64)75-59(90)48-21-13-30-79(48)60(91)40(65)19-12-29-70-63(68)69/h4-9,15-18,37,40-49H,10-14,19-36,64-65H2,1-3H3,(H2,66,81)(H2,67,82)(H,71,84)(H,72,83)(H,73,85)(H,74,89)(H,75,90)(H,76,87)(H,77,88)(H,78,86)(H,93,94)(H4,68,69,70)/t40-,41-,42-,43-,44-,45-,46-,47-,48-,49-/m0/s1",
    "substance P (a specific subclade of peptide-like products)",
    // "names":{"synonyms":["CHEMBL262227"]}
  },


  {
    "InChI=1S/C5H9NO4/c6-3(5(9)10)1-2-4(7)8/h3H,1-2,6H2,(H,7,8)(H,9,10)/t3-/m0/s1",
    "Should be cofactors",
    // "names":{"synonyms":["L-glutamic acid","L-glutamate","glutacid","Glutamicol","Glutamidex","Glutaminol","Glutaton","L-Glutaminic acid","Aciglut","Glusate","L-Glutamate","L-Glutamic acid","Glutamate"]}
  },
  {
    "InChI=1S/C5H9NO4/c6-3(5(9)10)1-2-4(7)8/h3H,1-2,6H2,(H,7,8)(H,9,10)/t3-/m1/s1",
    "Should be cofactors",
    // "names":{"synonyms":["D-glutamic acid","(2R)-2-aminopentanedioic acid","Glutamic acid D-form","D-glutamate","D-Glutaminsaeure","6893-26-1","D-2-Aminopentanedioic acid","D-Glutaminic acid","(R)-2-aminopentanedioic acid","Tocris-0217","D-Glutamate","D-Glutamic acid","D-2-Aminoglutaric acid"]}
  },
  {
    "InChI=1S/C5H9NO4/c6-3(5(9)10)1-2-4(7)8/h3H,1-2,6H2,(H,7,8)(H,9,10)/p-2",
    "Should be cofactors",
    // "names":{"synonyms":["glutamate","DL-glutamate","L-glutamate","D-glutamate"]}
  },
  {
    "InChI=1S/C5H9NO4/c6-3(5(9)10)1-2-4(7)8/h3H,1-2,6H2,(H,7,8)(H,9,10)",
    "Should be cofactors",
    // "names":{"synonyms":["DL-Glutamic acid","GLUTAMIC ACID","Glutamic acid","DL-","617-65-2","Glutamic acid DL-form","(+-)-Glutamic acid","l-(5-14c)glutamic acid","CHEBI:18237","NSC 9967","L-Glutamic acid","glutamic acid","Glutamicacid","L-Glutamicacid","D-Glutamic acid","d-glutamic acid","DL-Glutamate","DL-Glutaminic acid","2-Aminoglutaric acid","Glutamate"]}
  },


  {
    "InChI=1S/C24H40N7O17P3S/c1-4-15(33)52-8-7-26-14(32)5-6-27-22(36)19(35)24(2,3)10-45-51(42,43)48-50(40,41)44-9-13-18(47-49(37,38)39)17(34)23(46-13)31-12-30-16-20(25)28-11-29-21(16)31/h11-13,17-19,23,34-35H,4-10H2,1-3H3,(H,26,32)(H,27,36)(H,40,41)(H,42,43)(H2,25,28,29)(H2,37,38,39)/t13-,17-,18-,19+,23-/m1/s1",
    "Should be cofactors",
    // "names":{"synonyms":["s-{1-[5-(6-amino-9h-purin-9-yl)-4-hydroxy-3-(phosphonooxy)tetrahydrofuran-2-yl]-3,5,9-trihydroxy-8,8-dimethyl-3,5-dioxido-10,14-dioxo-2,4,6-trioxa-11,15-diaza-3lambda~5~,5lambda~5~-diphosphaheptadecan-17-yl} propanethioate(non-preferred name)","AC1L1AKW","AC1Q68ZB","AR-1L3795","S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] propanethioate","Propanoyl-CoA","Propionyl-CoA","Propionyl coenzyme A","Long-chain acyl-CoA"]}
  },
  {
    "InChI=1S/C24H40N7O17P3S/c1-4-15(33)52-8-7-26-14(32)5-6-27-22(36)19(35)24(2,3)10-45-51(42,43)48-50(40,41)44-9-13-18(47-49(37,38)39)17(34)23(46-13)31-12-30-16-20(25)28-11-29-21(16)31/h11-13,17-19,23,34-35H,4-10H2,1-3H3,(H,26,32)(H,27,36)(H,40,41)(H,42,43)(H2,25,28,29)(H2,37,38,39)/t13?,17-,18-,19?,23?/m1/s1",
    "Should be cofactors",
    // "names":{"synonyms":[]}
  },


  {
    "InChI=1S/C3H4O3/c1-2(4)3(5)6/h1H3,(H,5,6)",
    "Should be cofactors",
    // "names":{"synonyms":["Pyruvic acid","2-Oxopropanoic acid","Pyroracemic acid","acetylformic acid","2-Oxopropionic acid","alpha-ketopropionic acid","2-Ketopropionic acid","2-oxo-","2-oxopropanoic acid","2-oxo-Propanoic acid","Pyruvicacid","Pyruvate","2-Oxopropanoate"]}
  },
  {
    "InChI=1S/C3H4O3/c1-2(4)3(5)6/h1H3,(H,5,6)/p-1",
    "Should be cofactors",
    // "names":{"synonyms":["pyruvate","2-Oxopropanoate","CHEBI:15361","pyruvat","2hzl","2-oxidanylidenepropanoate","AC1L32NM","ion(1-)","AR-1L2888","Pyruvate"]}
  },


  {
    "InChI=1S/C4H6O3/c1-2-3(5)4(6)7/h2H2,1H3,(H,6,7)",
    "Should be cofactors",
    // "names":{"synonyms":["2-ketobutyric acid","2-oxobutanoic acid","2-oxobutyric acid","alpha-ketobutyric acid","2-OXO-","2-oxo-","alpha-Oxo-n-butyric acid","2-Oxobutanoate","2-Ketobutyric acid","2-Oxobutyric acid","2-Oxobutyrate","2-Oxobutanoic acid","alpha-Ketobutyric acid","alpha-Ketobutyrate"]}
  },
  {
    "InChI=1S/C4H6O3/c1-2-3(5)4(6)7/h2H2,1H3,(H,6,7)/p-1",
    "Should be cofactors",
    // "names":{"synonyms":["2-ketobutyrate","2-oxobutanoate","alpha-ketobutyrate","alpha-Oxobutyrate","ethylglyoxylate","CHEBI:16763","2uyn","2-oxidanylidenebutanoate","AC1MT15O","TYEYBOSBBBHJIV-UHFFFAOYSA-","oxobutyrate","oxobutanoate","2-oxo-butyrate","2-oxo-butanoate","2-oxo-n-butanoate","2-oxo-3-methylpropanoate","2-ketobutanoate","Ketobutyrate","3-methyl-2-oxopropanoate","alpha-ketobutanoate","2-oxobutyrate"]}
  },


  {
    "InChI=1S/C5H6O5/c6-3(5(9)10)1-2-4(7)8/h1-2H2,(H,7,8)(H,9,10)",
    "Should be cofactors",
    // "names":{"synonyms":["2-Oxopentanedioic acid","2-ketoglutaric acid","2-oxoglutaric acid","alpha-ketoglutaric acid","Oxoglutaric acid","328-50-7","Alphaketoglutaric acid","2-Oxo-1,5-pentanedioic acid","alpha keto","2-oxopentanedionic acid","alpha-oxoglutarate","2-oxopentanedionate","2-Oxoglutarate","2-Ketoglutaric acid","alpha-Ketoglutaric acid"]}
  },
  {
    "InChI=1S/C5H6O5/c6-3(5(9)10)1-2-4(7)8/h1-2H2,(H,7,8)(H,9,10)/p-2",
    "Should be cofactors",
    // "names":{"synonyms":["ketoglutarate","2-oxoglutarate","alpha-ketoglutarate","2-oxopentanedioate","2-ketoglutarate","2-Oxoglutamate",".alpha.-ketoglutarate","2-oxo-glutarate"]}
  },


  {
    "InChI=1S/C25H42N7O17P3S/c1-4-5-16(34)53-9-8-27-15(33)6-7-28-23(37)20(36)25(2,3)11-46-52(43,44)49-51(41,42)45-10-14-19(48-50(38,39)40)18(35)24(47-14)32-13-31-17-21(26)29-12-30-22(17)32/h12-14,18-20,24,35-36H,4-11H2,1-3H3,(H,27,33)(H,28,37)(H,41,42)(H,43,44)(H2,26,29,30)(H2,38,39,40)/t14-,18-,19-,20+,24-/m1/s1",
    "Unidentified",
    // "names":{"synonyms":["butanoyl-CoA","butyryl-CoA","butanoyl-coenzyme A","9h-purin-6-amine","9-[5-o-[hydroxy[[hydroxy[3-hydroxy-2,2-dimethyl-4-oxo-4-[[3-oxo-3-[[2-[(1-oxobutyl)thio]ethyl]amino]propyl]amino]butoxy]phosphinyl]oxy]phosphinyl]-3-o-phosphonopentofuranosyl]-","AC1L18UP","AC1Q68ZE","HMDB01088","AR-1H5731","S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] butanethioate","Butanoyl-CoA","Butyryl-CoA"]}
  },
  {
    "InChI=1S/C25H42N7O17P3S/c1-4-5-16(34)53-9-8-27-15(33)6-7-28-23(37)20(36)25(2,3)11-46-52(43,44)49-51(41,42)45-10-14-19(48-50(38,39)40)18(35)24(47-14)32-13-31-17-21(26)29-12-30-22(17)32/h12-14,18-20,24,35-36H,4-11H2,1-3H3,(H,27,33)(H,28,37)(H,41,42)(H,43,44)(H2,26,29,30)(H2,38,39,40)",
    "Unidentified",
    // "names":{"synonyms":["S-(3-methylpropanoyl)-CoA"]}
  },


  {
    "InChI=1S/H3NO/c1-2/h2H,1H2",
    "Unidentified",
    // "names":{"synonyms":["hydroxylamine","Oxammonium","Nitroxide","Oxyammonia","NH2OH","7803-49-8","dihydridohydroxidonitrogen","HSDB 579","EINECS 232-259-2","Hydroxylamine solution","Hydroxylamine","NO"]}
  },



  /*
   * Section on carbohydrates is below.
   * this is especially where stereochemistry comes into major play. coz that is mostly the reactions around these manipulate anyway
   */



  {
    "InChI=1S/C15H24N2O17P2/c18-3-5-8(20)10(22)12(24)14(32-5)33-36(28,29)34-35(26,27)30-4-6-9(21)11(23)13(31-6)17-2-1-7(19)16-15(17)25/h1-2,5-6,8-14,18,20-24H,3-4H2,(H,26,27)(H,28,29)(H,16,19,25)/t5-,6-,8+,9-,10+,11-,12-,13?,14-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["UDP-alpha-D-galactose","UDP-D-galactose","UDP-galactose","UDP-D-galactopyranose","UDP-alpha-D-galactopyranose"]}
  },
  {
    "InChI=1S/C15H24N2O17P2/c18-3-5-8(20)10(22)12(24)14(32-5)33-36(28,29)34-35(26,27)30-4-6-9(21)11(23)13(31-6)17-2-1-7(19)16-15(17)25/h1-2,5-6,8-14,18,20-24H,3-4H2,(H,26,27)(H,28,29)(H,16,19,25)/p-2/t5-,6-,8+,9-,10+,11-,12-,13-,14-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["Uridine 5 -diphosphogalactose","Uridine 5'-(trihydrogen diphosphate)","P'-(alpha-D-galactopyranosyl) ester"]}
  },
  {
    "InChI=1S/C15H24N2O17P2/c18-3-5-8(20)10(22)12(24)14(32-5)33-36(28,29)34-35(26,27)30-4-6-9(21)11(23)13(31-6)17-2-1-7(19)16-15(17)25/h1-2,5-6,8-14,18,20-24H,3-4H2,(H,26,27)(H,28,29)(H,16,19,25)/t5-,6-,8+,9-,10+,11-,12-,13-,14-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["UDP-Glc","uridinediphosphoglucose","AC1L96S5","CHEBI:18066","uridine 5'-[3-D-glucopyranosyl dihydrogen diphosphate]","[[(2R,3S,4R,5R)-5-(2,4-dioxopyrimidin-1-yl)-3,4-dihydroxyoxolan-2-yl]methoxy-hydroxyphosphoryl] [(3R,4S,5S,6R)-3,4,5-trihydroxy-6-(hydroxymethyl)oxan-2-yl] hydrogen phosphate"]}
  },
  {
    "InChI=1S/C15H24N2O17P2/c18-3-5-8(20)10(22)12(24)14(32-5)33-36(28,29)34-35(26,27)30-4-6-9(21)11(23)13(31-6)17-2-1-7(19)16-15(17)25/h1-2,5-6,8-14,18,20-24H,3-4H2,(H,26,27)(H,28,29)(H,16,19,25)",
    "Carbohydrates",
    // "names":{"synonyms":["Mono-alpha-D-glucopyranosyl ester","P-alpha-D-glucopyranosyl ester","UDP-alpha-D-galactose","UpG"]}
  },
  {
    "InChI=1S/C15H24N2O17P2/c18-3-5-8(20)10(22)12(24)14(32-5)33-36(28,29)34-35(26,27)30-4-6-9(21)11(23)13(31-6)17-2-1-7(19)16-15(17)25/h1-2,5-6,8-14,18,20-24H,3-4H2,(H,26,27)(H,28,29)(H,16,19,25)/t5-,6-,8-,9-,10+,11-,12-,13-,14-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["UDP-glucose","UDPglucose","UDP-D-glucose","Uridine diphosphate glucose","UDP-alpha-D-glucose"]}
  },
  {
    "InChI=1S/C15H24N2O17P2/c18-3-5-8(20)10(22)12(24)14(32-5)33-36(28,29)34-35(26,27)30-4-6-9(21)11(23)13(31-6)17-2-1-7(19)16-15(17)25/h1-2,5-6,8-14,18,20-24H,3-4H2,(H,26,27)(H,28,29)(H,16,19,25)/t5-,6-,8-,9-,10+,11+,12-,13+,14+/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },


  {
    "InChI=1S/C12H22O11/c13-1-4-6(16)8(18)9(19)11(21-4)23-12(3-15)10(20)7(17)5(2-14)22-12/h4-11,13-20H,1-3H2/t4-,5-,6-,7-,8+,9-,10+,11-,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["Sucrose","Cane sugar","Saccharose","1-alpha-D-Glucopyranosyl-2-beta-D-fructofuranoside"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-4-6(16)8(18)9(19)11(21-4)23-12(3-15)10(20)7(17)5(2-14)22-12/h4-11,13-20H,1-3H2/t4-,5+,6+,7+,8+,9-,10-,11+,12-/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-4-6(16)8(18)9(19)11(21-4)23-12(3-15)10(20)7(17)5(2-14)22-12/h4-11,13-20H,1-3H2/t4-,5+,6-,7-,8+,9-,10+,11-,12-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["Allosucrose","AC1L4W5N","AR-1L8547","ZINC05224791","|A-d-allopyranosyl |A-d-fructofuranoside","alpha-D-Allopyranosyl beta-D-fructofuranoside","alpha-D-Allopyranoside","beta-D-fructofuranosyl","(2R,3R,4R,5S,6R)-2-[(2S,3S,4S,5R)-3,4-dihydroxy-2,5-bis(hydroxymethyl)oxolan-2-yl]oxy-6-(hydroxymethyl)oxane-3,4,5-triol"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-4-6(16)8(18)9(19)11(21-4)23-12(3-15)10(20)7(17)5(2-14)22-12/h4-11,13-20H,1-3H2/t4-,5-,6-,7-,8+,9-,10+,11+,12+/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-4-6(16)8(18)9(19)11(21-4)23-12(3-15)10(20)7(17)5(2-14)22-12/h4-11,13-20H,1-3H2",
    "Carbohydrates",
    // "names":{"synonyms":["Sugar","D-sucrose","beta-D-Fructofuranosyl-alpha-D-glucopyranoside"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-4-6(16)8(18)9(19)11(21-4)23-12(3-15)10(20)7(17)5(2-14)22-12/h4-11,13-20H,1-3H2/t4-,5-,6-,7-,8+,9+,10+,11-,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["beta-D-Fructofuranosyl-alpha-D-mannopyranoside","Mannosylfructose"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-4-6(16)8(18)9(19)11(21-4)23-12(3-15)10(20)7(17)5(2-14)22-12/h4-11,13-20H,1-3H2/t4-,5-,6?,7-,8?,9?,10+,11-,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["alpha-D-Aldosyl beta-D-fructoside","alpha-D-Aldosyl1 beta-D-fructoside","alpha-D-Aldosyl2 beta-D-fructoside"]}
  },


  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2",
    "Carbohydrates",
    // "names":{"synonyms":["cellobiose","Lactose",".beta.-","lactose","maltose","4-O-Hexopyranosylhexopyranose","NSC2559","D-Cellobiose","Maltose solution","4-O-.beta.-D-galactopyranosyl-","D-Maltose","D-LACTOSE","starch","D-cellobiose","alpha-cellobiose","Maltose","mannobiose","galactobiose"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4+,5-,6+,7+,8-,9-,10?,11?,12-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["alpha-lactose","lactose","Anhydrous lactose","Milk sugar","GLC-(4-1)GAL","CHEBI:36219","4-O-beta-D-Galactopyranosyl-alpha-D-glucopyranose","Lactobiose","Osmolactan","1-beta-D-Galactopyranosyl-4-alpha-D-glucopyranose"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5+,6+,7-,8-,9-,10-,11?,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["Lactose","1-beta-D-Galactopyranosyl-4-D-glucopyranose","beta-D-Gal-(1->4)-D-Glc","Milk sugar"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7+,8+,9-,10+,11+,12-/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7-,8-,9-,10-,11?,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["Cellobiose","1-beta-D-Glucopyranosyl-4-D-glucopyranose"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7-,8-,9-,10-,11?,12-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["Maltose","Malt sugar","alpha-D-Glucopyranosyl-(1->4)-D-glucopyranose"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7-,8-,9-,10-,11+,12-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["alpha-Maltose","alpha-Malt sugar"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5+,6+,7-,8-,9-,10-,11-,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7-,8-,9-,10-,11-,12-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["beta-Maltose"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7-,8+,9-,10-,11-,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["D-Glucosyl-D-mannose"]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7-,8-,9-,10-,11+,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7-,8-,9-,10-,11-,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2/t3-,4-,5-,6+,7-,8-,9+,10-,11?,12+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["4-O-beta-D-Mannopyranosyl-D-glucopyranose"]}
  },


  {
    "InChI=1S/C5H10O5/c6-1-2-3(7)4(8)5(9)10-2/h2-9H,1H2/t2-,3+,4+,5?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["D-(-)-Ribose","alpha-D-ribose","alpha-D-ribose-5","ribofuranose","AI3-52667","Ribo-2,3,4,5-tetrahydroxyvaleraldehyde","D-","alpha-D-Ribofuranose","SMR000857325","delta-Ribose","alpha D-ribose","D-Ribose"]}
  },
  {
    "InChI=1S/C5H10O5/c6-1-2-3(7)4(8)5(9)10-2/h2-9H,1H2/t2-,3+,4-,5?/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":["l-arabinofuranose","Arabinofuranose","L-Arabinofuranose(9CI)","AC1L9A71","CHEBI:6178","HMDB12325","AG-G-86066","C06115","(3R,4R,5S)-5-(hydroxymethyl)oxolane-2,3,4-triol","D7CE7DEF-6F98-4734-82A2-31EE7E9F9583","L-Xylose","L-Xylofuranose"]}
  },
  {
    "InChI=1S/C5H10O5/c6-1-2-3(7)4(8)5(9)10-2/h2-9H,1H2",
    "Carbohydrates",
    // "names":{"synonyms":["beta-D-Ribofuranose","beta-D-ribofuranose","D-ribofuranose","Ribofuranose","D-Ribofuranose"]}
  },
  {
    "InChI=1S/C5H10O5/c6-1-2-3(7)4(8)5(9)10-2/h2-9H,1H2/t2-,3-,4+,5+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C5H10O5/c6-1-2-3(7)4(8)5(9)10-2/h2-9H,1H2/t2-,3-,4-,5-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["beta-D-Ribofuranose"]}
  },

  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3+,4+,5-,6+/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":["L-fucose","6-deoxy-L-galactose","L-galactomethylose","L-(-)-Fucose","6-Desoxygalactose","(-)-Fucose","(-)-L-Fucose","L-fucopyranose","6-Deoxy-L-beta-galactose","6-Deoxy-L-galactopyranose","6-Deoxy-L-galactose","L-Fucose"]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3+,4+,5-,6+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["D-Mannomethylose","D-Rhamnose","6-Deoxy-D-mannose","alpha-D-Rhap","alpha-D-Rha","AC1L97KK","CHEBI:63152","MolPort-002-507-419","BB_NC-1426","ZINC01532676"]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3-,4+,5+,6+/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3",
    "Carbohydrates",
    // "names":{"synonyms":["14807-05-7","L-rhamnulose","rhamnulose","beta-D-fucoside","L-Rhamnulose","Fucose","D-Rhamnose","7658-08-4"]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3-,4+,5-,6-/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3-,4+,5+,6?/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":["L-Rhamnose","6-Deoxy-L-mannose","L-Mannomethylose"]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3+,4+,5-,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3+,4+,5-,6-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3-,4+,5+,6-/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3-,4+,5-,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3/t2-,3-,4+,5-,6+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },


  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5-,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["D-Glucose","Grape sugar","Dextrose","Glucose"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5+,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["D-Mannose","Mannose","Seminose","Carubinose"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3+,4+,5-,6-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3+,4+,5+,6?/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":["GXL","alpha-L-galactose","AC1L97MT","ALPHA-L-GALACTOPYRANOSE","CHEBI:42905","CPD-13428","ZINC01532549","C01825","(2R,3S,4R,5S,6S)-6-(hydroxymethyl)oxane-2,3,4,5-tetrol","L-Galactose"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3+,4+,5-,6+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["alpha-D-glucose","alpha-Dextrose","alpha-D-Glucopyranose","glucose","D-glucose","dextrose","alpha-glucose","Glucopyranose","alpha-D-","492-62-6","glucoses","alpha-D-Glucose"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3?,4?,5?,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["D-Aldose","D-Aldose1","D-Aldose2"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2−,3+,4+,5−,6+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5-,6-/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4-,5-,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["D-Allose"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4-,5+,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2",
    "Carbohydrates",
    // "names":{"synonyms":["Hexopyranose","D-hexose",".alpha.-D-Glucose",".beta.-D-Glucopyranose",".alpha.-D-Glucopyranose","mannopyranose","Hexose","NSC8102","Glucoside","Cerelose","Dextropur","hexopyranose",".beta.-D-Galactopyranose","Glucopyranose",".beta.-D-Mannopyranose","beta-D-Mannopyranose","alpha-D-Galactopyranose","beta-D-Galactopyranose","beta-D-glucopyranose","alpha-D-Glucopyranose","beta-D-Glucopyranose","D-glucopyranose","beta-D-galactoside","alpha-D-Mannopyranose","(2R,3R,4R,5S,6R)-6-methyloltetrahydropyran-2,3,4,5-tetrol","(2R,3R,4R,5S,6R)-6-(hydroxymethyl)oxane-2,3,4,5-tetrol","(2R,3R,4R,5S,6R)-6-(hydroxymethyl)tetrahydropyran-2,3,4,5-tetrol","alpha-D-glucopyranose","D-Hexose"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3+,4+,5-,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["D-Galactose"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5-,6-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["beta-D-Glucose"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5+,6+/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5+,6-/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":["1,4-beta-D-Mannooligosaccharide"]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3+,4-,5-,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3+,4-,5+,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3+,4+,5+,6?/m1/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3+,4-,5-,6?/m0/s1",
    "Carbohydrates",
    // "names":{"synonyms":[]}
  },

  /*
   * End: Section on carbohydrates.
   */


  };


  // the following explicitly hardcode the "consistentInChI". Instead
  // we should encode the inchi that comes in from the raw data, and
  // use ConsistentInChI.consistentInChI to get the real lookup value.
  public static String[][] InChI2CategoryNameOutdated = {
  {
    "InChI=1S/C30H50O/c1-24(2)14-11-17-27(5)20-12-18-25(3)15-9-10-16-26(4)19-13-21-28(6)22-23-29-30(7,8)31-29/h14-16,20-21,29H,9-13,17-19,22-23H2,1-8H3",
    "Steroids",
//    7268    [(3S)-2,3-oxidosqualene, (3S)-squalene-2,3-epoxide, (S)-2,3-oxidosqualene, (3R)-Squalene epoxide, 2,3-Epoxysqualene, (S)-2,3-Epoxysqualene, 2,3-oxidosqualene, (S)-squalene-2,3-epoxide, (S)-2,3-epoxysqualene];[2,3-Oxidosqualene, Squalene 2,3-oxide, Squalene 2,3-epoxide, 2,3-Edsq, (S)-Squalene-2,3-epoxide, 2,3-epoxisqualene, AC1NQWZH, (3S)-2,2-dimethyl-3-[(7E)-3,7,12,16,20-pentamethylhenicosa-3,7,11,15,19-pentaenyl]oxirane]
  },
  {
    "InChI=1S/C15H28O7P2/c1-13(2)7-5-8-14(3)9-6-10-15(4)11-12-21-24(19,20)22-23(16,17)18/h7,9,11H,5-6,8,10,12H2,1-4H3,(H,19,20)(H2,16,17,18)",
    "Sesuiterpenes",
//    3568    [trans-farnesyl diphosphate, a poly-cis-polyprenyl diphosphate longer by one C5 unit, 2-trans,6-trans-farnesyl diphosphate, trans,trans-farnesyl diphosphate];[2-cis,6-trans-farnesyl diphosphate, CHEBI:19515, (2Z,6E)-3,7,11-trimethyldodeca-2,6,10-trien-1-yl trihydrogen diphosphate, 1fpp, AC1L9IDX, CHEMBL1160060, LMPR0103010010, C16826, phosphono [(2Z,6E)-3,7,11-trimethyldodeca-2,6,10-trienyl] hydrogen phosphate, 3,7,11-trimethyldodeca-2,6,10-trien-1-yl trihydrogen diphosphate, phosphono 3,7,11-trimethyldodeca-2,6,10-trienyl hydrogen phosphate, AC1L19U8, FPP003, all-trans Farnesyl pyrophosphate, CHEBI:50277, 13058-04-3]
  },
  {
    "InChI=1S/C10H20O7P2/c1-9(2)5-4-6-10(3)7-8-16-19(14,15)17-18(11,12)13/h5,7H,4,6,8H2,1-3H3,(H,14,15)(H2,11,12,13)",
    "Monoterpenes",
//    1047    [];[Polyprenyl diphosphate, trans-Polyisopentenyldiphosphate, trans-Geranyl pyrophosphate, geranyl-PP, GPP, Gpp]
  },
  {
    "InChI=1S/C15H24N2O17P2/c18-3-5-8(20)10(22)12(24)14(32-5)33-36(28,29)34-35(26,27)30-4-6-9(21)11(23)13(31-6)17-2-1-7(19)16-15(17)25/h1-2,5-6,8-14,18,20-24H,3-4H2,(H,26,27)(H,28,29)(H,16,19,25)",
    "Carbohydrates",
//    3497    [UDPgalactose, UDP-mannose, UDP-monosaccharide, UDPmannose, alpha-UDP-D-galactose, Uridine 5'-(trihydrogen diphosphate) P'-alpha-D-galactopyranosyl ester, UDP-Man, UDP-alpha-D-glucose, UDP-galactose, UDP-D-galactopyranose, UDP-D-galactose, UDP-D-glucose, UDP-galactopyranose, UDPglucose, UDP-glucose, UDP galactose];[uridinediphosphoglucose, AC1L96S5, CHEBI:18066, uridine 5'-[3-D-glucopyranosyl dihydrogen diphosphate], [[(2R,3S,4R,5R)-5-(2,4-dioxopyrimidin-1-yl)-3,4-dihydroxyoxolan-2-yl]methoxy-hydroxyphosphoryl] [(3R,4S,5S,6R)-3,4,5-trihydroxy-6-(hydroxymethyl)oxan-2-yl] hydrogen phosphate, Mono-alpha-D-glucopyranosyl ester, P-alpha-D-glucopyranosyl ester, UDP-alpha-D-galactose, UpG]
  },
  {
    "InChI=1S/C12H22O11/c13-1-4-6(16)8(18)9(19)11(21-4)23-12(3-15)10(20)7(17)5(2-14)22-12/h4-11,13-20H,1-3H2",
    "Carbohydrates",
//    2223    [sugars, D-aldosyl beta-D-fructoside, beta-D-fructofuranosyl-2,1-D-galactopyranoside, galactosylfructoside, beta-D-fructofuranosyl-beta-L-glucopyranoside, beta-D-fructofuranosyl-beta-L-galactopyranoside, beta-D-fructofuranosyl-alpha-D-mannpyranoside, beta-D-fructofuranosyl-alpha-D-galactopyranoside, beta-D-fructofuranosyl-(2,1)-D-mannopyranoside, beta-D-fructofuranosyl-(2->1)-D-mannopyranoside, beta-D-fructofuranosyl-(2,1)-D-galactopyranoside, beta-D-fructofuranosyl-(2->1)-D-galactopyranoside, alpha-D-glucosyl-alpha-L-sorbose, alpha-D-glucopyranosyl-D-xylulofuranoside, alpha-D-glucopyranosyl-(1,2)-beta-D-fructofuranoside, sucrose, saccharose, sugar, Sucrose, beta-D-fructofuranosyl-alpha-D-glucopyranoside];[Allosucrose, AC1L4W5N, AR-1L8547, ZINC05224791, |A-d-allopyranosyl |A-d-fructofuranoside, alpha-D-Allopyranosyl beta-D-fructofuranoside, alpha-D-Allopyranoside, beta-D-fructofuranosyl, (2R,3R,4R,5S,6R)-2-[(2S,3S,4S,5R)-3,4-dihydroxy-2,5-bis(hydroxymethyl)oxolan-2-yl]oxy-6-(hydroxymethyl)oxane-3,4,5-triol, Sugar, D-sucrose, beta-D-Fructofuranosyl-alpha-D-glucopyranoside]
  },
  {
    "InChI=1S/C12H22O11/c13-1-3-5(15)6(16)9(19)12(22-3)23-10-4(2-14)21-11(20)8(18)7(10)17/h3-20H,1-2H2",
    "Carbohydrates",
//    2212    [Manbeta(1-4)Man, beta-D-cellobiose, disaccharide, Glcbeta(1-4)Glc, Glc(beta1-4)Glc, Galbeta1-4Glc, Galbeta(1-4)Glc, maltose/out, maltose/in, Galbeta(1,4)Glc, Manbeta1-4Man, lactose/out, lactose/in, Galbeta(1,4)-Glc, Gal-beta-1,4-Glc, Gal-beta-(1-4)-Glc, D-mannobiose, Disaccharide, D-galactosyl-1,4-beta-D-glucose, D(+)-cellobiose, cellobiose/out, cellobiose/in, beta-mannobiose, beta-D-mannosyl-1,4-beta-D-glucoside, beta-D-galactosyl-1,4-D-glucose, alpha-D-maltose, 4-O-beta-D-galactopyranosyl-D-glucopyranoside, 4-O-alpha-isomaltosyl-D-glucose, 4-O-alpha-D-mannopyranosyl-D-mannopyranose, 4-O-alpha-D-galactopyranosyl-D-mannopyranose, (1,4-alpha-D-galacturonide)2, beta-D-lactose, lactose, alpha-lactose, alpha-maltose, alpha-Lactose, D-lactose, beta-maltose, beta-cellobiose, beta-lactose, 4-O-beta-D-galactopyranosyl-D-glucose, 4-O-beta-D-galactopyranosyl-D-mannopyranose, cellobiose, Cellobiose, 4-O-beta-D-glucopyranosyl-D-mannopyranose, maltose, Maltose, 4-O-beta-D-glucopyranosyl-D-glucose, D-maltose, alpha-Maltose, beta-Maltose, 4-O-alpha-D-glucopyranosyl-D-glucopyranose, D-(+)-cellobiose, beta-Lactose];[alpha-lactose, lactose, Anhydrous lactose, GLC-(4-1)GAL, CHEBI:36219, 4-O-beta-D-Galactopyranosyl-alpha-D-glucopyranose, Lactobiose, Osmolactan, 1-beta-D-Galactopyranosyl-4-alpha-D-glucopyranose, cellobiose, Lactose, .beta.-, maltose, 4-O-Hexopyranosylhexopyranose, NSC2559, D-Cellobiose, Maltose solution, 4-O-.beta.-D-galactopyranosyl-, D-Maltose, D-LACTOSE, starch, D-cellobiose, alpha-cellobiose, Maltose, mannobiose, galactobiose]
  },
  {
    "InChI=1S/C5H10O5/c6-1-2-3(7)4(8)5(9)10-2/h2-9H,1H2",
    "Carbohydrates",
//    9147    [ribose/out, ribose/in, L-arabinofuranoside, D-ribose/out, D-ribose/in, arabinose/out, arabinose/in, arabinofuranose, alpha,beta-L-arabinose, D-ribofuranose, L-arabinofuranose, alpha-L-arabinofuranose, beta-D-arabinofuranose];[alpha-D-ribose, alpha-D-ribose-5, ribofuranose, AI3-52667, Ribo-2,3,4,5-tetrahydroxyvaleraldehyde, D-, alpha-D-Ribofuranose, SMR000857325, delta-Ribose, alpha D-ribose, l-arabinofuranose, Arabinofuranose, L-Arabinofuranose(9CI), AC1L9A71, CHEBI:6178, HMDB12325, AG-G-86066, C06115, (3R,4R,5S)-5-(hydroxymethyl)oxolane-2,3,4-triol, D7CE7DEF-6F98-4734-82A2-31EE7E9F9583, beta-D-Ribofuranose, beta-D-ribofuranose, D-ribofuranose, Ribofuranose, D-Ribofuranose]
  },
  {
    "InChI=1S/C6H12O5/c1-2-3(7)4(8)5(9)6(10)11-2/h2-10H,1H3",
    "Carbohydrates",
//    10136    [fucopyranose, D-6-deoxyglucose, beta-D-quinovose, alpha-L-rhamnopyranoside, alpha-D-rhamnose, alpha-D-quinovose, 6-deoxy-alpha-D-galactose, alpha-L-rhamnose, alpha-D-fucose, beta-D-fucose, alpha-L-fucose, alpha-L-fucopyranose, L-Fucose, alpha-D-Fucose, beta-D-fucopyranose, alpha-L-rhamnopyranose, alpha-D-fucopyranose, beta-L-fucose, rhamnose, beta-D-Fucose, D-fucose, L-fucose, fucose];[L-fucose, L-galactomethylose, L-(-)-Fucose, (-)-Fucose, (-)-L-Fucose, L-fucopyranose, 6-Deoxy-L-beta-galactose, 6-Deoxy-L-galactopyranose, D-Mannomethylose, D-Rhamnose, 6-Deoxy-D-mannose, alpha-D-Rhap, alpha-D-Rha, AC1L97KK, CHEBI:63152, MolPort-002-507-419, BB_NC-1426, ZINC01532676, 14807-05-7, L-rhamnulose, rhamnulose, beta-D-fucoside, L-Rhamnulose, Fucose, 7658-08-4]
  },
  {
    "InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2",
    "Carbohydrates",
//    10153    [aldopyranose, corresponding D-aldose, alpha-D-mannopyranoside, pyranose, D-galactosylpyranoside, alpha-mannopyranose, hexose, glucose/out, glucose/in, galactose/out, galactose/in, D-glucosylpyranoside, beta-galactose, a sugar, alpha-L-galactose, alpha-D-mannosylpyranose, 2 alpha-D-glucose, D-mannopyranose, alpha-D-Glucose, beta-d-glucose, beta-D-Galactose, D-aldose, alpha-D-galactose, galactopyranoside, beta-D-galactose, alpha-D-glucopyranose, D-galactopyranose, beta-D-mannopyranose, alpha-D-glucose, beta-D-galactopyranose, beta-D-glucose, alpha-D-mannose, D-hexose, D-glucopyranose, alpha-D-Galactose, alpha-mannose, beta-D-Glucose, beta-D-glucopyranose, beta-D-mannose, beta-glucose, alpha-D-mannopyranose, alpha-D-galactopyranose];[alpha-D-glucose, alpha-Dextrose, alpha-D-Glucopyranose, alpha-glucose, Glucopyranose, alpha-D-, 492-62-6, glucoses, GXL, alpha-L-galactose, AC1L97MT, ALPHA-L-GALACTOPYRANOSE, CHEBI:42905, CPD-13428, ZINC01532549, C01825, (2R,3S,4R,5S,6S)-6-(hydroxymethyl)oxane-2,3,4,5-tetrol, Hexopyranose, D-hexose, .alpha.-D-Glucose, .beta.-D-Glucopyranose, .alpha.-D-Glucopyranose, mannopyranose, Hexose, NSC8102, Glucoside, Cerelose, Dextropur, hexopyranose, .beta.-D-Galactopyranose, .beta.-D-Mannopyranose, beta-D-Mannopyranose, alpha-D-Galactopyranose, beta-D-Galactopyranose, beta-D-glucopyranose, beta-D-Glucopyranose, D-glucopyranose, beta-D-galactoside, alpha-D-Mannopyranose, (2R,3R,4R,5S,6R)-6-methyloltetrahydropyran-2,3,4,5-tetrol, (2R,3R,4R,5S,6R)-6-(hydroxymethyl)oxane-2,3,4,5-tetrol, (2R,3R,4R,5S,6R)-6-(hydroxymethyl)tetrahydropyran-2,3,4,5-tetrol, alpha-D-glucopyranose]
  },
  {
    "InChI=1S/C15H12O5/c16-9-3-1-8(2-4-9)13-7-12(19)15-11(18)5-10(17)6-14(15)20-13/h1-6,13,16-18H,7H2",
    "Flavanoids",
//    3227    [(2R)-naringenin, 5,7-dihydroxy-2-(4-hydroxy-phenyl)-chroman-4-one, (2S)-5,7-dihydroxy-2-(4-hydroxyphenyl)-2,3-dihydro-4H-chromen-4-one, 5,7,4'-Trihydroxyflavanone, (2S)-naringenin, 5,7,4'-trihydroxyflavanone, naringenin];[(2R)-5,7-dihydroxy-2-(4-hydroxyphenyl)-2,3-dihydro-4H-chromen-4-one, AC1LDI7C, CHEBI:50201, MolPort-002-507-277, BB_NC-1001, 480-41-1, BBL010488, STK801623, ZINC00001785, AKOS004119880, naringenin, Salipurol, naringetol, salipurpol, pelargidanon, Asahina, Naringenine, (S)-Naringenin, YSO1, (-)-(2S)-Naringenin, (-)-Naringenin, NARIGENIN, 4',5,7-Trihydroxyflavanone, MLS000738094, NSC11855, NSC34875, 4',7-Trihydroxyflavanone]
  },
  {
    "InChI=1S/C15H10O7/c16-7-4-10(19)12-11(5-7)22-15(14(21)13(12)20)6-1-2-8(17)9(18)3-6/h1-5,16-19,21H",
    "Flavanoids",
//    3172    [2R,3S-cis-dihydroquercetin, 3,3',4',5,7-pentahydroxy flavone, 3,5,7,3',4'-pentahydroxyflavone, quercetin];[quercetin, Meletin, Sophoretin, Quercetine, Quercetol, Xanthaurine, Quercitin, Quertine, Flavin meletin]
  },
  {
    "InChI=1S/C15H10O6/c16-8-3-1-7(2-4-8)15-14(20)13(19)12-10(18)5-9(17)6-11(12)21-15/h1-6,16-18,20H",
    "Flavanoids",
//    3163    [kaempferol];[kaempferol, Rhamnolutein, Kempferol, Populnetin, Robigenin, Trifolitin, Pelargidenolon, Kaempherol, Rhamnolutin, Swartziol, 3,5,7-triOH-Flavone]
  },
  {
    "InChI=1S/C30H42N7O18P3S/c1-30(2,25(42)28(43)33-10-9-20(39)32-11-12-59-21(40)8-5-17-3-6-18(38)7-4-17)14-52-58(49,50)55-57(47,48)51-13-19-24(54-56(44,45)46)23(41)29(53-19)37-16-36-22-26(31)34-15-35-27(22)37/h3-8,15-16,19,23-25,29,38,41-42H,9-14H2,1-2H3,(H,32,39)(H,33,43)(H,47,48)(H,49,50)(H2,31,34,35)(H2,44,45,46)",
    "Coumarines and isoflavonoids",
//    7219    [trans-4-coumaroyl-CoA, coumaroyl-CoA, 4-coumaryl-CoA, 4-hydroxycinnamoyl-CoA, 4-coumaroyl-CoA, p-coumaroyl-CoA];[AC1L18ZJ, S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] 3-(4-hydroxyphenyl)prop-2-enethioate]
  },
  {
    "InChI=1S/C16H25N5O15P2/c1-4-7(22)9(24)11(26)15(33-4)35-38(30,31)36-37(28,29)32-2-5-8(23)10(25)14(34-5)21-3-18-6-12(21)19-16(17)20-13(6)27/h3-5,7-11,14-15,22-26H,2H2,1H3,(H,28,29)(H,30,31)(H3,17,19,20,27)",
    "Oligosaccharides/carbohydrates/fucrose-fanout",
//    3885    [GDP-beta-fucose, GDP-alpha-L-fucose, GDP-6-deoxy-L-galactose, GDP-beta-L-fucose, GDP-fucose, GDP-Fucose, GDP-6-deoxy-D-talose, GDP-6-deoxy-D-mannose, GDP-L-fucose];[Gdp fucose, Guanosine diphosphofucose, GDP-L-fucose, GDP-beta-L-fucose, guanosine diphosphate fucose, AC1L96W5, HMDB01095, (6-deoxy-beta-l-galactopyranosyl) ester]
  },
  {
    "InChI=1S/C18H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18(19)20/h6-7,9-10H,2-5,8,11-17H2,1H3,(H,19,20)",
    "Lipids",
//    4535    [linolate, linoic acid, octadec-9,12-dienoic acid, alpha-linoleic acid, linolic acid, octadeca-9,12-dienoic acid, (9Z,12Z)-octadeca-9,12-dienoic acid, 9-cis,12-cis-linoleic acid, cis-linoleic acid, Linoleic acid, (9Z,12Z)-octadecadienoic acid, linoleic acid, cis,cis-9,12-octadecadienoic acid];[linoleic acid, Linolic acid, cis,cis-Linoleic acid, Telfairic acid, cis-9,cis-12-Octadecadienoic acid, Emersol 315, Grape seed oil, Unifac 6550, cis,cis-9,12-Octadecadienoic acid, 9,12-Octadecadienoic acid, cis-Linoleic acid, Linoleic acid, 9,12-Octadecadienoic acid (Z,Z)-, 60-33-3, 10-trans,12-cis-linoleic acid]
  },
  {
    "InChI=1S/C33H58N7O17P3S/c1-4-5-6-7-8-9-10-11-12-13-24(42)61-17-16-35-23(41)14-15-36-31(45)28(44)33(2,3)19-54-60(51,52)57-59(49,50)53-18-22-27(56-58(46,47)48)26(43)32(55-22)40-21-39-25-29(34)37-20-38-30(25)40/h20-22,26-28,32,43-44H,4-19H2,1-3H3,(H,35,41)(H,36,45)(H,49,50)(H,51,52)(H2,34,37,38)(H2,46,47,48)",
    "Lipids",
//    7499    [n-lauroyl-CoA, n-dodecanoyl-CoA, dodecanoyl-CoA, lauroyl-CoA];[Dodecanoyl-coa, Lauroyl-coa, Lauroyl-coenzyme A, Dodecanoyl-coenzyme A, Lauroyl coenzyme A, S-dodecanoate, 6244-92-4, lauroyl-, Lauryl-CoA, lauryl-CoA]
  },
  {
    "InChI=1S/C37H66N7O17P3S/c1-4-5-6-7-8-9-10-11-12-13-14-15-16-17-28(46)65-21-20-39-27(45)18-19-40-35(49)32(48)37(2,3)23-58-64(55,56)61-63(53,54)57-22-26-31(60-62(50,51)52)30(47)36(59-26)44-25-43-29-33(38)41-24-42-34(29)44/h24-26,30-32,36,47-48H,4-23H2,1-3H3,(H,39,45)(H,40,49)(H,53,54)(H,55,56)(H2,38,41,42)(H2,50,51,52)",
    "Lipids",
//    7729    [palmitoyl-SCoA, hexadecanoyl CoA, Palmitoyl-CoA, palmitoyl-CoA, hexadecanoyl-CoA];[palmitoyl-CoA, Hexadecanoyl-CoA, s-{1-[5-(6-amino-9h-purin-9-yl)-4-hydroxy-3-(phosphonooxy)tetrahydrofuran-2-yl]-3,5,9-trihydroxy-8,8-dimethyl-3,5-dioxido-10,14-dioxo-2,4,6-trioxa-11,15-diaza-3lambda~5~,5lambda~5~-diphosphaheptadecan-17-yl} hexadecanethioate(non-preferred name), AC1L1AH5, AC1Q68ZG, P9804_SIGMA, AR-1L3794, Palmitoyl-(carbonyl-14C)-coenzyme A, [(2R,3S,4R,5R)-5-(6-aminopurin-9-yl)-2-[[[[(3R)-3-[2-(2-hexadecanoylsulfanylethylcarbamoyl)ethylcarbamoyl]-3-hydroxy-2,2-dimethyl-propoxy]-hydroxy-phosphoryl]oxy-hydroxy-phosphoryl]oxymethyl]-4-hydroxy-oxolan-3-yl]oxyphosphonic acid, S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] hexadecanethioate]
  },
  {
    "InChI=1S/C12H22R2NO8P/c1-15(2,3)6-7-21-24(18,19)22-9-10(23-12(14)17)8-20-11(16)4-5-13/h10H,4-9H2,1-3H3/p+1/t10-/m0/s1",
    "Glycolipids (a subclade of lipids)",
//    15556    [];[]
  },
  {
    "InChI=1S/C18H37NO2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-18(21)17(19)16-20/h14-15,17-18,20-21H,2-13,16,19H2,1H3",
    "Sphingolipids (a subclass of lipids)",
//    4621    [DL-erythro-trans-sphingosine, sphingosine, D-erythro-sphingosine];[SPH, AC1L9L4S, (E,2R,3R)-2-aminooctadec-4-ene-1,3-diol, sphingosine, 4-Sphingenine, D-erythro-Sphingosine, D-Sphingosine, Sphing-4-enine, Sphingenine, Sphingoid, cerebroside, Erythrosphingosine, (4E)-Sphingenine, Sphingosine]
  },
  {
    "InChI=1S/C20H32O2/c1-2-3-4-5-6-7-8-9-10-11-12-13-14-15-16-17-18-19-20(21)22/h6-7,9-10,12-13,15-16H,2-5,8,11,14,17-19H2,1H3,(H,21,22)",
    "Signalling molecules (a subclass of lipids)",
//    5256    [(5Z,8Z,11Z,14Z)-eicosatetraenoic acid, arachidonic acid, eicosa-5,8,11,14-tetraenoic acid, (5E,8E,11E,14E)-icosa-5,8,11,14-tetraenoic acid];[arachidonic acid, 506-32-1, 5,8,11,14-Eicosatetraenoic acid, (all-Z)-, (5Z,8Z,11Z,14Z)-icosa-5,8,11,14-tetraenoic acid, all-cis-5,8,11,14-eicosatetraenoic acid, CHEBI:15843, 5Z,8Z,11Z,14Z-eicosatetraenoic acid, cis-5,8,11,14-Eicosatetraenoic acid, ST069383, AG-F-70356, AG-H-11197, Spectrum_000091, SpecPlus_000727, Spectrum4_000905, AC1L18RV, CBiol_001948, KBioGR_000259, KBioGR_001370, 5,8,11,14-Eicosatetraenoicacid, Arachidonic acid, ARACHIDONIC_ACID]
  },
  {
    "InChI=1S/C20H32O5/c1-2-3-6-9-15(21)12-13-17-16(18-14-19(17)25-24-18)10-7-4-5-8-11-20(22)23/h4,7,12-13,15-19,21H,2-3,5-6,8-11,14H2,1H3,(H,22,23)",
    "Prostaglandins (a subclass of lipids)",
//    5295    [8-Isoprostaglandin H2, Prostaglandin H2, prostaglandin H2, (5Z,13E)-(15S)-9alpha,11alpha-epidioxy-15-hydroxyprosta-5,13-dienoate];[prostaglandin h2, PGH2, 9,11-Epoxymethano-pgh2, Endoperoxide H2, Prostaglandin R2, prostaglandin-H2, 9S,11R-epidioxy-15S-hydroxy-5Z,13E-prostadienoic acid, 15-Hydroxy-9alpha,11alpha-peroxidoprosta-5,13-dienoic acid, (15S)Hydroxy-9alpha,11alpha-(epoxymethano)prosta-5,13-dienoic acid, (5Z,13E)-(15S)-9alpha,11alpha-Epidioxy-15-hydroxyprosta-5,13-dienoate]
  },
  {
    "InChI=1S/C3H9O6P/c4-1-3(5)2-9-10(6,7)8/h3-5H,1-2H2,(H2,6,7,8)",
    "Glycerophospholipids (a subclade of phospholipids and more generally lipids)",
//    8132    [sn-glycero-3-phosphate, L-glycerol-3-phosphate, sn-glycero-1-phosphate, L-alpha-glycerol 3-phosphate, glycerolphosphate, sn-glyceryl phosphate, sn-glycero 3-phosphate, glycerol-3-phosphate/out, glycerol-3-phosphate/in, gamma-glycerophosphate, DL-glycerol-3-phosphate, DL-glycerol-1-phosphate, sn-glycerol 3-phosphate, glycerol 3-phosphate, L-glycerol 1-phosphate, 1-glycerophosphate, L-alpha-glycerophosphate, DL-glycerol 3-phosphate, L-glycerol 3-phosphate, glycerol 1-phosphate, glycerol-3-phosphate, glycerol-1-phosphate, sn-glycerol-3-phosphate, sn-glycerol-1-phosphate];[sn-Glycerol 3-phosphate, glycerol-3-phosphate, Glycerol 3-phosphate, GLYCEROPHOSPHATE, sn-Gro-1-P, Glycerophosphoric acid I, DL-Glycerol 1-phosphate, DL-Glycerol 3-phosphate, a-Phosphoglycerol, sn-Glycerol 1-phosphate, 1GP, 3-phosphoglycerol, 6tim, L-Glycerol 1-phosphate, D-(glycerol 3-phosphate), L-(glycerol 1-phosphate), CHEBI:16221, AC1L9712, 3-Glycerophosphate, 1-Glycerophosphate, 1-Glycerophosphoric acid, alpha-glycerophosphoric acid, alpha-Phosphoglycerol, Glycerophosphoric acid, Glycerol alpha-phosphate, Glycerol 1-phosphate, 2,3-dihydroxypropyl dihydrogen phosphate]
  },
  {
    "InChI=1S/C39H72O5/c1-3-5-7-9-11-13-15-17-19-21-23-25-27-29-31-33-38(41)43-36-37(35-40)44-39(42)34-32-30-28-26-24-22-20-18-16-14-12-10-8-6-4-2/h17-20,37,40H,3-16,21-36H2,1-2H3",
    "Phosphoglycolipids",
//    7807    [dielaidin, 2,3-dioleoyl-sn-glycerol, 2,3-dioleoylglycerol, 1,2-dioleolylglycerol, rac-1,2-dioleoylglycerol, sn-1,2-diolein, sn-1,2-dioleoylglycerol, 1,2-dioleoyl-sn-glycerol, 1,2-dioleoylglycerol];[Sn-1,2-Diolein, sn-1,2-Dioleoylglycerol, BRN 1730457, 1,2-di-, (S)-(-)-, AC1O5SPD, LS-98318, 4-02-00-01662 (Beilstein Handbook Reference), [(2S)-3-hydroxy-2-[(Z)-octadec-9-enoyl]oxypropyl] (E)-octadec-9-enoate, 1-(hydroxymethyl)-1,2-ethanediyl ester, 1-(hydroxymethyl)-1,2-ethanediyl ester, (S)- (9CI), AC1NSUWJ, [3-hydroxy-2-[(E)-octadec-9-enoyl]oxypropyl] (E)-octadec-9-enoate, (Z)-octadec-9-enoic acid [3-hydroxy-2-[(E)-octadec-9-enoyl]oxy-propyl] ester, (Z)-9-octadecenoic acid [3-hydroxy-2-[(E)-1-oxooctadec-9-enoxy]propyl] ester, [2-[(E)-octadec-9-enoyl]oxy-3-oxidanyl-propyl] (Z)-octadec-9-enoate, [3-hydroxy-2-[(E)-octadec-9-enoyl]oxypropyl] (Z)-octadec-9-enoate, [3-hydroxy-2-[(E)-octadec-9-enoyl]oxy-propyl] (Z)-octadec-9-enoate, 1,2-diolein]
  },
  {
    "InChI=1S/C9H11NO4/c10-6(9(13)14)3-5-1-2-7(11)8(12)4-5/h1-2,4,6,11-12H,3,10H2,(H,13,14)",
    "Catechols",
//    13136    [3,4-dihydroxyphenyl-L-Ala, DL-DOPA, L-3,4-dihydroxyphenylalanine, 3-(3,4-dihydroxyphenyl)-DL-alanine, DOPA, 3,4-dihydroxyphenylalanine, L-dopa, L-DOPA, L-dihydroxyphenylalanine, dopa, D-dopa, 3-(3,4-dihydroxyphenyl)-L-alanine, D-DOPA, 3,4-Dihydroxyphenylalanine, beta-(3,4-dihydroxyphenyl)alanine, D-3,4-dihydroxyphenylalanine, 3,4-dihydroxy-D-phenylalanine, L-3,4-Dihydroxyphenylalanine, 3,4-dihydroxy-L-phenylalanine, DL-dopa, L-(3,4-dihydroxyphenyl)alanine, L-Dopa];[D-Dopa, Dopa D-form, 3,4-Dihydroxy-D-phenylalanine, 3-Hydroxy-D-tyrosine, D-3,4-Dihydroxyphenylalanine, CHEBI:49169, (+)-3,4-Dihydroxyphenylalanine, D-3-(3,4-Dihydroxyphenyl)alanine, (2R)-2-amino-3-(3,4-dihydroxyphenyl)propanoic acid, (+)-3-(3,4-Dihydroxyphenyl)alanine, levodopa, L-dopa, Dopar, Bendopa, Larodopa, Levopa, 3-Hydroxy-L-tyrosine, Cidandopa, Dopaidan, Dopalina, DL-DOPA, DL-Dioxyphenylalanine, 3-Hydroxytyrosine, 3-Hydroxy-DL-tyrosine, DL-Dihydroxyphenylalanine, DL-3,4-Dopa, 3-hydroxy-, (R,S)-Dopa, 3,4-Dihydroxy-DL-phenylalanine]
  },
  {
    "InChI=1S/C5H12O7P2/c1-5(2)3-4-11-14(9,10)12-13(6,7)8/h3H,4H2,1-2H3,(H,9,10)(H2,6,7,8)",
    "Prenylated products (downstream includes all terpenes)",
//    9306    [DELTA2-isopentenyl diphosphate];[DMAPP, dimethylallyl-PPi, 2-Isopentenyl diphosphate, dimethylallyl-PP]
  },
  {
    "InChI=1S/C20H36O7P2/c1-15(11-14-26-29(24,25)27-28(21,22)23)7-9-17-16(2)8-10-18-19(3,4)12-6-13-20(17,18)5/h11,17-18H,2,6-10,12-14H2,1,3-5H3,(H,24,25)(H2,21,22,23)",
    "Diterpenes subclass",
//    5364    [(+)-copalyl-diphosphate, copalyl diphosphate, (+)-copalyl diphosphate, syn-copalyl diphosphate, 9alpha-copalyl diphosphate, 9,10-syn-copalyl diphosphate, ent-copalyl diphosphate];[ent-Copalyl diphosphate, (-)-Copalyl diphosphate, Copalyl pyrophosphate, AC1NQXR1, 9,10-Syn-cPP, 9,10-syn-Copalyl diphosphate, CHEBI:28151, LMPR0104030001, C06089, 9betaH-Labda-9(17),13-dien-15-ol diphosphate ester, Copalyl diphosphate, Labdadienyl diphosphate, AC1NQZD5, CHEBI:30939, LMPR0104030002, C11901, [(E)-5-[(1S,4aS,8aS)-5,5,8a-trimethyl-2-methylidene-3,4,4a,6,7,8-hexahydro-1H-naphthalen-1-yl]-3-methylpent-2-enyl] phosphono hydrogen phosphate]
  },
  {
    "InChI=1S/C21H30O2/c1-13(22)17-6-7-18-16-5-4-14-12-15(23)8-10-20(14,2)19(16)9-11-21(17,18)3/h12,16-19H,4-11H2,1-3H3",
    "Steroids (and a specific subclass therein of androgen steroids)",
//    5633    [4-pregnen-3,20-dione, progesterone/in, progesterone/out, pregn-4-en-3,20-dione, pregn-4-ene-3,20-dione, 4-Pregnene-3,20-dione, Progesterone, progesterone, luteol, 4-pregnene-3,20-dione];[progesterone, Luteohormone, Agolutin, Crinone, Syngesterone, Cyclogest, Utrogestan, Luteol, Corlutina, (4-14c)pregn-4-ene-3,20-dione, STK374710, Delta4 -Pregnen-3,20-dione, Decolorizing Carbon, 17-Isoprogesterone, 17-acetyl-10,13-dimethyl-1,2,6,7,8,9,11,12,14,15,16,17-dodecahydrocyclopenta[a]phenanthren-3-one, NORIT A(R), AC1L1J8O, NCIOpen2_008018, AC1Q1K19, Pregn-4-ene-3,20-dione, Progesterone]
  },
  {
    "InChI=1S/C20H36O7P2/c1-17(2)9-6-10-18(3)11-7-12-19(4)13-8-14-20(5)15-16-26-29(24,25)27-28(21,22)23/h9,11,13,15H,6-8,10,12,14,16H2,1-5H3,(H,24,25)(H2,21,22,23)",
    "Diterpenes",
//    5366    [];[geranylgeranyl-PP, GGDP, GGPP, all-trans-Geranylgeranyl diphosphate, all-trans-Geranylgeranyl pyrophosphate, GRG]
  },
  {
    "InChI=1S/C24H38N7O19P3S/c1-24(2,19(37)22(38)27-4-3-13(32)26-5-6-54-15(35)7-14(33)34)9-47-53(44,45)50-52(42,43)46-8-12-18(49-51(39,40)41)17(36)23(48-12)31-11-30-16-20(25)28-10-29-21(16)31/h10-12,17-19,23,36-37H,3-9H2,1-2H3,(H,26,32)(H,27,38)(H,33,34)(H,42,43)(H,44,45)(H2,25,28,29)(H2,39,40,41)",
    "Malonyl-CoA fanout (contains all PK/FA)",
//    6153    [malonyl CoA, Malonyl-CoA, malonyl-CoA];[1-[5-(6-amino-9h-purin-9-yl)-4-hydroxy-3-(phosphonooxy)tetrahydro-2-furanyl]-3,5,9-trihydroxy-8,8-dimethyl-10,14,19-trioxo-2,4,6-trioxa-18-thia-11,15-diaza-3,5-diphosphahenicosan-21-s, AC1L1A7K, AC1Q5V7R, KST-1B4963, AR-1B9382, 3-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethylsulfanyl]-3-oxopropanoic acid]
  },
  {
    "InChI=1S/C28H40N7O17P3S/c1-28(2,22(38)25(39)31-9-8-18(36)30-10-11-56-27(40)16-6-4-3-5-7-16)13-49-55(46,47)52-54(44,45)48-12-17-21(51-53(41,42)43)20(37)26(50-17)35-15-34-19-23(29)32-14-33-24(19)35/h3-7,14-15,17,20-22,26,37-38H,8-13H2,1-2H3,(H,30,36)(H,31,39)(H,44,45)(H,46,47)(H2,29,32,33)(H2,41,42,43)",
    "Benzoylated products",
//    6811    [Benzoyl-CoA, benzoyl-CoA, benzoyl-S-CoA];[benzoyl-coenzyme A, Benzoyl-coa, benzoyl-, S-benzoate, AC1L3U4G, 6756-74-7, AR-1L3753, S-[2-[3-[[(2R)-4-[[[(2R,3S,4R)-5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] benzenecarbothioate, s-{(9r)-1-[(2r,3s,4r)-5-(6-amino-9h-purin-9-yl)-4-hydroxy-3-(phosphonooxy)tetrahydrofuran-2-yl]-3,5,9-trihydroxy-8,8-dimethyl-3,5-dioxido-10,14-dioxo-2,4,6-trioxa-11,15-diaza-3|E5,5|E5-diphosphaheptadecan-17-yl} benzenecarbothioate(non-preferred name)]
  },
  {
    "InChI=1S/C30H42N7O19P3S/c1-30(2,25(43)28(44)33-8-7-20(40)32-9-10-60-21(41)6-4-16-3-5-17(38)18(39)11-16)13-53-59(50,51)56-58(48,49)52-12-19-24(55-57(45,46)47)23(42)29(54-19)37-15-36-22-26(31)34-14-35-27(22)37/h3-6,11,14-15,19,23-25,29,38-39,42-43H,7-10,12-13H2,1-2H3,(H,32,40)(H,33,44)(H,48,49)(H,50,51)(H2,31,34,35)(H2,45,46,47)",
    "Caffeoylated products",
//    7220    [3,4-dihydroxycinnamoyl-CoA, caffeoyl-CoA];[AC1L4O4Q, S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] 3-(3,4-dihydroxyphenyl)prop-2-enethioate]
  },
  {
    "InChI=1S/C31H44N7O19P3S/c1-31(2,26(43)29(44)34-9-8-21(40)33-10-11-61-22(41)7-5-17-4-6-18(39)19(12-17)52-3)14-54-60(50,51)57-59(48,49)53-13-20-25(56-58(45,46)47)24(42)30(55-20)38-16-37-23-27(32)35-15-36-28(23)38/h4-7,12,15-16,20,24-26,30,39,42-43H,8-11,13-14H2,1-3H3,(H,33,40)(H,34,44)(H,48,49)(H,50,51)(H2,32,35,36)(H2,45,46,47)",
    "Feruloylated products",
//    7337    [4-feruloyl-CoA, ferulyl-CoA, feruloylCoA, feruloyl-CoA, trans-feruloyl-CoA];[AC1LCV2G, S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] 3-(4-hydroxy-3-methoxyphenyl)prop-2-enethioate]
  },
  {
    "InChI=1S/C5H11NO2S/c1-9-3-2-4(6)5(7)8/h4H,2-3,6H2,1H3,(H,7,8)",
    "methionine fanout",
//    9216    [L-Met, D-Met, L-methionine/out, L-methionine/in, methionine, DL-methionine, Met, D-methionine, L-methionine, D-Methionine, L-Methionine];[D-Methionine, R-Methionine, D-Methionin, (R)-Methionine, AG-F-19717, D-2-Amino-4-(methylthio)butyric acid, D-2-Amino-4-(methylthio)butanoic acid, (R)-2-amino-4-(methylthio)butanoic acid, (R)-2-Amino-4-(methylmercapto)butyric acid, (2R)-2-amino-4-(methylsulfanyl)butanoic acid, L-methionine, methionine, Cymethion, Liquimeth, L-(-)-Methionine, Methilanin, L-Methioninum, S-Methionine, (L)-Methionine, h-Met-oh, DL-METHIONINE, Racemethionine, Acimetion, 59-51-8, Mertionin, L-Methionine, DL-Methionine, Methionine, METHIONINE]
  },
  {
    "InChI=1S/C63H97N17O14S/c1-37(2)33-45(56(87)76-44(62(93)94)27-32-95-3)72-52(83)36-71-53(84)46(34-38-15-6-4-7-16-38)77-57(88)47(35-39-17-8-5-9-18-39)78-55(86)41(23-25-50(66)81)73-54(85)42(24-26-51(67)82)74-58(89)49-22-14-31-80(49)61(92)43(20-10-11-28-64)75-59(90)48-21-13-30-79(48)60(91)40(65)19-12-29-70-63(68)69/h4-9,15-18,37,40-49H,10-14,19-36,64-65H2,1-3H3,(H2,66,81)(H2,67,82)(H,71,84)(H,72,83)(H,73,85)(H,74,89)(H,75,90)(H,76,87)(H,77,88)(H,78,86)(H,93,94)(H4,68,69,70)",
    "substance P (a specific subclade of peptide-like products)",
//    9750    [substance P(free acid), RPKPQQFFGLM];[CHEMBL262227]
  },
  {
    "InChI=1S/C5H9NO4/c6-3(5(9)10)1-2-4(7)8/h3H,1-2,6H2,(H,7,8)(H,9,10)",
    "Should be cofactors",
//    9713    [alpha-L-Glu, L-glutamate/out, L-glutamate/in, L-Glu/out, L-Glu/in, glutamate/out, glutamate/in, L-Glu, Glu, glutamic acid, D-Glu, D-glutamic acid, L-glutamic acid];[D-glutamic acid, (2R)-2-aminopentanedioic acid, Glutamic acid D-form, D-Glutaminsaeure, 6893-26-1, D-2-Aminopentanedioic acid, (R)-2-aminopentanedioic acid, Tocris-0217, L-glutamic acid, glutacid, Glutamicol, Glutamidex, Glutaminol, Glutaton, L-Glutaminic acid, Aciglut, Glusate, DL-Glutamic acid, GLUTAMIC ACID, Glutamic acid, DL-, 617-65-2, Glutamic acid DL-form, (+-)-Glutamic acid, l-(5-14c)glutamic acid, CHEBI:18237, NSC 9967, L-Glutamic acid, glutamic acid, Glutamicacid, L-Glutamicacid, D-Glutamic acid, d-glutamic acid]
  },
  {
    "InChI=1S/C24H40N7O17P3S/c1-4-15(33)52-8-7-26-14(32)5-6-27-22(36)19(35)24(2,3)10-45-51(42,43)48-50(40,41)44-9-13-18(47-49(37,38)39)17(34)23(46-13)31-12-30-16-20(25)28-11-29-21(16)31/h11-13,17-19,23,34-35H,4-10H2,1-3H3,(H,26,32)(H,27,36)(H,40,41)(H,42,43)(H2,25,28,29)(H2,37,38,39)",
    "Should be cofactors",
//    6182    [propionyl-SCoA, Propanoyl-CoA, n-propionyl-CoA, Propionyl-CoA, propanoyl-CoA, propionyl-CoA];[s-{1-[5-(6-amino-9h-purin-9-yl)-4-hydroxy-3-(phosphonooxy)tetrahydrofuran-2-yl]-3,5,9-trihydroxy-8,8-dimethyl-3,5-dioxido-10,14-dioxo-2,4,6-trioxa-11,15-diaza-3lambda~5~,5lambda~5~-diphosphaheptadecan-17-yl} propanethioate(non-preferred name), AC1L1AKW, AC1Q68ZB, AR-1L3795, S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] propanethioate]
  },
  {
    "InChI=1S/C3H4O3/c1-2(4)3(5)6/h1H3,(H,5,6)",
    "Should be cofactors",
//    7883    [CH3C(O)CO2-, 2-oxo-propanoic acid, Pyruvic acid, 2-oxopropanoic acid, pyruvic acid];[Pyruvic acid, 2-Oxopropanoic acid, Pyroracemic acid, acetylformic acid, 2-Oxopropionic acid, alpha-ketopropionic acid, 2-Ketopropionic acid, 2-oxo-, 2-oxopropanoic acid, 2-oxo-Propanoic acid, Pyruvicacid]
  },
  {
    "InChI=1S/C4H6O3/c1-2-3(5)4(6)7/h2H2,1H3,(H,6,7)",
    "Should be cofactors",
//    8683    [3-methylpyruvate, 2-oxo-butyric acid, 2-oxobutyric acid, 2-ketobutanoic acid, 2-oxobutanoic acid, alpha-ketobutyric acid];[2-ketobutyric acid, 2-oxobutanoic acid, 2-oxobutyric acid, alpha-ketobutyric acid, 2-OXO-, 2-oxo-, alpha-Oxo-n-butyric acid]
  },
  {
    "InChI=1S/C5H6O5/c6-3(5(9)10)1-2-4(7)8/h1-2H2,(H,7,8)(H,9,10)",
    "Should be cofactors",
//    9493    [L-2-oxoglutarate, 2-oxoglutaric acid, 2-oxopentanedioic acid];[2-Oxopentanedioic acid, 2-ketoglutaric acid, 2-oxoglutaric acid, alpha-ketoglutaric acid, Oxoglutaric acid, 328-50-7, Alphaketoglutaric acid, 2-Oxo-1,5-pentanedioic acid, alpha keto, 2-oxopentanedionic acid, alpha-oxoglutarate, 2-oxopentanedionate]
  },
  {
    "InChI=1S/C25H42N7O17P3S/c1-4-5-16(34)53-9-8-27-15(33)6-7-28-23(37)20(36)25(2,3)11-46-52(43,44)49-51(41,42)45-10-14-19(48-50(38,39)40)18(35)24(47-14)32-13-31-17-21(26)29-12-30-22(17)32/h12-14,18-20,24,35-36H,4-11H2,1-3H3,(H,27,33)(H,28,37)(H,41,42)(H,43,44)(H2,26,29,30)(H2,38,39,40)",
    "Unidentified",
//    6337    [n-butyryl-CoA, butanoyl-CoA, butyryl-CoA];[butanoyl-CoA, butyryl-CoA, butanoyl-coenzyme A, 9-[5-o-[hydroxy[[hydroxy[3-hydroxy-2,2-dimethyl-4-oxo-4-[[3-oxo-3-[[2-[(1-oxobutyl)thio]ethyl]amino]propyl]amino]butoxy]phosphinyl]oxy]phosphinyl]-3-o-phosphonopentofuranosyl]-, AC1L18UP, AC1Q68ZE, HMDB01088, AR-1H5731, S-[2-[3-[[4-[[[5-(6-aminopurin-9-yl)-4-hydroxy-3-phosphonooxyoxolan-2-yl]methoxy-hydroxyphosphoryl]oxy-hydroxyphosphoryl]oxy-2-hydroxy-3,3-dimethylbutanoyl]amino]propanoylamino]ethyl] butanethioate, S-(3-methylpropanoyl)-CoA]
  },
  {
    "InChI=1S/H3NO/c1-2/h2H,1H2",
    "Unidentified",
//    14038    [hydroxylamine, Hydroxylamine, NH2OH];[hydroxylamine, Oxammonium, Nitroxide, Oxyammonia, NH2OH, 7803-49-8, dihydridohydroxidonitrogen, HSDB 579, EINECS 232-259-2, Hydroxylamine solution, Hydroxylamine, NO]
  },
  };
}
