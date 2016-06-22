package com.act.reachables;

import act.server.MongoDB;
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

    Set<Long> parents = new HashSet<>();
    Set<Long> children = new HashSet<>();
    for (Map.Entry<Long, Set<Long>> pToC : parentToChildren.entrySet()) {
      parents.add(pToC.getKey());
      children.addAll(pToC.getValue());
    }

    List<Long> roots = conditionalReachabilityInterpreter.getRoots(parents, children);
  }

  private Map<Long, Set<Long>> constructParentToChildrenAssociations() {
    Map<Long, Set<Long>> parentToChildrenAssociations = new HashMap<>();
    for (Map.Entry<Long, Long> childIdToParentId : this.actData.getActTree().parents.entrySet()) {
      Long parentId = childIdToParentId.getValue();
      Long childId = childIdToParentId.getKey();
      Set<Long> childIds = parentToChildrenAssociations.get(parentId);
      if (childIds == null) {
        childIds = new HashSet<>();
        parentToChildrenAssociations.put(parentId, childIds);
      }
      childIds.add(childId);
    }

    return parentToChildrenAssociations;
  }

  private List<Long> getRoots(Set<Long> parents, Set<Long> children) {
    List<Long> results = new ArrayList<>();
    for (Long parent : parents) {
      if (!children.contains(parent)) {
        System.out.println(db.readChemicalFromInKnowledgeGraph(parent).getInChI());
        results.add(parent);
      }
    }

    return results;
  }
}
