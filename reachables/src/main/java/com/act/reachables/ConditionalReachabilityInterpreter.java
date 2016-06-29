package com.act.reachables;

import act.server.NoSQLAPI;

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

  private Map<Long, Set<Long>> constructParentToChildrenAssociations() {

    Set<Long> rootLevelChemicals = new HashSet<>();
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

    for (Long id : rootLevelChemicals) {
      System.out.println(id);
    }

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
