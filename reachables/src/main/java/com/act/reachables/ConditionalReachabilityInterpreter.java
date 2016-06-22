package com.act.reachables;

import act.server.NoSQLAPI;
import com.act.utils.TSVWriter;
import org.apache.commons.collections4.map.HashedMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConditionalReachabilityInterpreter {

  private ActData actData;
  private static final NoSQLAPI db = new NoSQLAPI("actv01", "actv01");

  public ConditionalReachabilityInterpreter(ActData actData) {
    this.actData = actData;
  }

  public static void main(String[] args) throws Exception {
    String actDataFile = "result.actdata";
    ActData.instance().deserialize(actDataFile);
    ActData actData = ActData.instance();
    ConditionalReachabilityInterpreter conditionalReachabilityInterpreter = new ConditionalReachabilityInterpreter(actData);
    Map<Long, Set<Long>> parentToChildren = conditionalReachabilityInterpreter.constructParentToChildrenAssociations();


//    Set<Long> parents = new HashSet<>();
//    Set<Long> children = new HashSet<>();
//
//    for (Map.Entry<Long, Set<Long>> pToC : parentToChildren.entrySet()) {
//      parents.add(pToC.getKey());
//      children.addAll(pToC.getValue());
//    }
//
//    List<Long> roots = conditionalReachabilityInterpreter.getRoots(parents, children);
  }

  private Map<Long, Set<Long>> constructParentToChildrenAssociations() throws IOException {
    Set<Long> rootLevelChemicals = new HashSet<>();
    Map<Long, Integer> chemIdToDepth = new HashMap<>();

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

      while (children != null && children.size() > 0) {
        Set<Long> descendants = rootToAllDescendants.get(id);
        if (descendants == null) {
          descendants = new HashSet<>();
          rootToAllDescendants.put(id, descendants);
        }
        descendants.addAll(children);

        Set<Long> newChildren = new HashSet<>();
        for (Long child : children) {
          chemIdToDepth.put(child, depth);
          Set<Long> res = parentToChildrenAssociations.get(child);
          if (res != null) {
            newChildren.addAll(res);
          }
        }

        children = newChildren;
        depth++;
      }
    }

    List<String> header = new ArrayList<>();
    header.add("Target Inchi");
    header.add("Input Inchi");

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File("result.tsv"));

    for (Map.Entry<Long, Set<Long>> rootToDescendants : rootToAllDescendants.entrySet()) {
      String rootInchi = db.readChemicalFromInKnowledgeGraph(rootToDescendants.getKey()).getInChI();

      if (rootInchi.equals("InChI=1S/C10H16N5O13P3/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(26-10)1-25-30(21,22)28-31(23,24)27-29(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H,23,24)(H2,11,12,13)(H2,18,19,20)/t4-,6-,7-,10-/m1/s1")) {
        continue;
      }

      for (Long descendant : rootToDescendants.getValue()) {
        Map<String, String> res = new HashMap<>();
        res.put(db.readChemicalFromInKnowledgeGraph(descendant).getInChI(), rootInchi);
        writer.append(res);
        writer.flush();
      }
    }

    writer.close();

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
