package com.act.reachables;

import act.installer.bing.BingSearchRanker;
import act.server.NoSQLAPI;
import act.shared.Reaction;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConditionalReachabilityInterpreter {

  private static final NoSQLAPI db = new NoSQLAPI("actv01", "actv01");
  private static final String BLACKLISTED_ROOT_INCHI = "InChI=1S/C10H16N5O13P3/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(26-10)1-25-30(21,22)28-31(23,24)27-29(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H,23,24)(H2,11,12,13)(H2,18,19,20)/t4-,6-,7-,10-/m1/s1";
  private ActData actData;

  public ConditionalReachabilityInterpreter(ActData actData) {
    this.actData = actData;
  }

  public static void main(String[] args) throws Exception {
    String actDataFile = "result.actdata";
    System.out.println("passed the act file");
    ActData.instance().deserialize(actDataFile);
    ActData actData = ActData.instance();
    ConditionalReachabilityInterpreter conditionalReachabilityInterpreter = new ConditionalReachabilityInterpreter(actData);
    conditionalReachabilityInterpreter.run();
  }

  private void run() throws Exception {

    Set<Long> rootChemicals = new HashSet<>();
    Map<Long, Set<Long>> parentToChildrenAssociations = new HashMap<>();

    // Create parent to child associations
    for (Map.Entry<Long, Long> childIdToParentId : this.actData.getActTree().parents.entrySet()) {
      Long parentId = childIdToParentId.getValue();
      Long childId = childIdToParentId.getKey();

      // If the parentId is null, that means the node is one of the roots of the forest.
      if (parentId == null) {
        rootChemicals.add(childId);
        continue;
      }

      Set<Long> childIds = parentToChildrenAssociations.get(parentId);
      if (childIds == null) {
        childIds = new HashSet<>();
        parentToChildrenAssociations.put(parentId, childIds);
      }
      childIds.add(childId);
    }

    // Cache chem ids to their inchis
    Map<Long, String> chemIdToInchi = new HashMap<>();

    // Record the depth of each (Root,Descendant) pair combination
    Map<Pair<String, String>, Integer> rootDescendantPairToDepth = new HashMap<>();

    // Construct trees from the root chemicals
    Map<Long, Set<Long>> rootToSetOfDescendants = new HashMap<>();
    for (Long rootId : rootChemicals) {

      // Record depth of each tree
      int depth = 1;
      String rootInchi = db.readChemicalFromInKnowledgeGraph(rootId < 0 ? Reaction.reverseNegativeId(rootId) : rootId).getInChI();
      chemIdToInchi.put(rootId, rootInchi);

      Set<Long> children = parentToChildrenAssociations.get(rootId);
      while (children != null && children.size() > 0) {

        Set<Long> descendants = rootToSetOfDescendants.get(rootId);
        if (descendants == null) {
          descendants = new HashSet<>();
          rootToSetOfDescendants.put(rootId, descendants);
        }
        descendants.addAll(children);

        Set<Long> newChildren = new HashSet<>();
        for (Long child : children) {
          String childInchi = db.readChemicalFromInKnowledgeGraph(child < 0 ? Reaction.reverseNegativeId(child) : child).getInChI();
          chemIdToInchi.put(child, childInchi);

          rootDescendantPairToDepth.put(Pair.of(rootInchi, childInchi), depth);

          // If all the children of this child and add it to the new set of children
          Set<Long> res = parentToChildrenAssociations.get(child);
          if (res != null) {
            newChildren.addAll(res);
          }
        }

        children = newChildren;
        depth++;
      }
    }

    Map<String, String> childInchiToRootInchi = new HashMap<>();

    for (Map.Entry<Long, Set<Long>> entry : rootToSetOfDescendants.entrySet()) {
      String rootInchi = chemIdToInchi.get(entry.getKey());

      if (rootInchi.equals(BLACKLISTED_ROOT_INCHI)) {
        continue;
      }

      for (Long descendant : entry.getValue()) {
        childInchiToRootInchi.put(chemIdToInchi.get(descendant), rootInchi);
      }
    }

    Set<String> allInchis = new HashSet<>(chemIdToInchi.values());

    System.out.println("The size of allInchis is " + allInchis.size());
    System.out.println("The size of rootToAllDescendants is " + rootToSetOfDescendants.size());

    // Update the Bing Search results in the Installer database
    BingSearchRanker bingSearchRanker = new BingSearchRanker();
    bingSearchRanker.addBingSearchResults(allInchis);
    bingSearchRanker.writeBingSearchRanksAsTSVModified(childInchiToRootInchi, rootDescendantPairToDepth, "result.tsv");
  }

  private Map<Long, Set<Long>> constructParentToChildrenAssociations() throws IOException {

    Set<Long> rootLevelChemicals = new HashSet<>();
    Map<Pair<String, String>, Integer> chemInchiToDepth = new HashMap<>();

    Map<Long, String> chemIndex = new HashMap<>();

    Map<Long, Set<Long>> parentToChildrenAssociations = new HashMap<>();
    for (Map.Entry<Long, Long> childIdToParentId : this.actData.getActTree().parents.entrySet()) {
      Long parentId = childIdToParentId.getValue();
      Long childId = childIdToParentId.getKey();

      if (parentId == null) {
        rootLevelChemicals.add(childId);
        continue;
      }

      Set<Long> childIds = parentToChildrenAssociations.get(parentId);
      if (childIds == null) {
        childIds = new HashSet<>();
        parentToChildrenAssociations.put(parentId, childIds);
      }
      childIds.add(childId);
    }

    Map<Long, Set<Long>> rootToAllDescendants = new HashMap<>();
    for (Long id : rootLevelChemicals) {
      Set<Long> children = parentToChildrenAssociations.get(id);
      int depth = 1;
      //System.out.println("chem id is: " + id);

      String rootInchi = db.readChemicalFromInKnowledgeGraph(id < 0 ? Reaction.reverseNegativeId(id) : id).getInChI();
      chemIndex.put(id, rootInchi);

      while (children != null && children.size() > 0) {
        Set<Long> descendants = rootToAllDescendants.get(id);
        if (descendants == null) {
          descendants = new HashSet<>();
          rootToAllDescendants.put(id, descendants);
        }
        descendants.addAll(children);
        Set<Long> newChildren = new HashSet<>();
        for (Long child : children) {
          String childInchi = db.readChemicalFromInKnowledgeGraph(child < 0 ? Reaction.reverseNegativeId(child) : child).getInChI();
          chemIndex.put(id, childInchi);

          chemInchiToDepth.put(Pair.of(rootInchi, childInchi), depth);
          Set<Long> res = parentToChildrenAssociations.get(child);
          if (res != null) {
            newChildren.addAll(res);
          }
        }

        children = newChildren;
        depth++;
      }
    }

//    List<String> header = new ArrayList<>();
//    header.add("Target Inchi");
//    header.add("Input Inchi");
//    header.add("Depth");
//
//    TSVWriter<String, String> writer = new TSVWriter<>(header);
//    writer.open(new File("result.tsv"));

    Map<String, String> childToRoot = new HashMap<>();

    for (Map.Entry<Long, Set<Long>> rootToDescendants : rootToAllDescendants.entrySet()) {
      Long rootId = rootToDescendants.getKey();
      String rootInchi = chemIndex.get(rootId);

      if (rootInchi.equals("InChI=1S/C10H16N5O13P3/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(26-10)1-25-30(21,22)28-31(23,24)27-29(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H,23,24)(H2,11,12,13)(H2,18,19,20)/t4-,6-,7-,10-/m1/s1")) {
        continue;
      }

      for (Long descendant : rootToDescendants.getValue()) {
        childToRoot.put(chemIndex.get(descendant), rootInchi);

//        Map<String, String> res = new HashMap<>();
//        res.put("Target Inchi", db.readChemicalFromInKnowledgeGraph(descendant).getInChI());
//        res.put("Input Inchi", rootInchi);
//        res.put("Depth", chemIdToDepth.get(Pair.of(rootToDescendants.getKey(), descendant)).toString());
//        writer.append(res);
//        writer.flush();
      }
    }

    Set<String> allInchis = new HashSet<>(chemIndex.values());

    System.out.println("The size of allInchis is " + allInchis.size());
    System.out.println("The size of rootToAllDescendants is " + rootToAllDescendants.size());

    // Update the Bing Search results in the Installer database
    BingSearchRanker bingSearchRanker = new BingSearchRanker();
    bingSearchRanker.addBingSearchResults(allInchis);
    bingSearchRanker.writeBingSearchRanksAsTSVModified(childToRoot, chemInchiToDepth, "result.tsv");


    //writer.close();

    return parentToChildrenAssociations;
  }

  private List<Long> getRoots(Set<Long> parents, Set<Long> children) {

    System.out.println("parent size is: " + parents.size());
    System.out.println("children size is: " + children.size());

    List<Long> results = new ArrayList<>();
    for (Long parent : parents) {
      if (!children.contains(parent)) {
        System.out.println(parent);
        results.add(parent);
      }
    }

    return results;
  }
}
