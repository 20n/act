package com.act.reachables;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CladeTraversal {
  Tree<Long> tree;
  WavefrontExpansion wavefrontExpansion;
  Set<Long> reachableIds;
  private static final Logger LOGGER = LogManager.getLogger(CladeTraversal.class);
  private static final NoSQLAPI db = new NoSQLAPI("marvin_v2", "marvin_v2");
  private Network network;
  private Map<Long, Set<Long>> parentToChildren = new HashMap<>();

  public CladeTraversal() {
    this.wavefrontExpansion = new WavefrontExpansion();
    this.tree = wavefrontExpansion.expandAndPickParents();
    this.tree.ensureForest();
    this.network = ActData.instance().getActTree();
    this.reachableIds = new HashSet<>();
    for (Map.Entry<Node, Long> nodeAndId : network.nodesAndIds().entrySet()) {
      this.reachableIds.add(nodeAndId.getValue());
    }

    this.preProcessNetwork();
  }

  public static void main(String[] args) throws Exception {
    ActData.instance().deserialize("result.actdata");
    CladeTraversal test = new CladeTraversal();
    Set<Long> results =
        test.traverseTreeFromParent(test.findIdFromInchi("InChI=1S/C7H7NO2/c8-6-3-1-5(2-4-6)7(9)10/h1-4H,8H2,(H,9,10)"));
    test.printInchis(results);
  }

  private void preProcessNetwork() {
    for (Map.Entry<Long, Long> childToParent : this.network.parents.entrySet()) {
      Set<Long> res = this.parentToChildren.get(childToParent.getValue());
      if (res == null) {
        res = new HashSet<>();
      }
      res.add(childToParent.getKey());
      this.parentToChildren.put(childToParent.getValue(), res);
    }
  }

  private Long findIdFromInchi(String inchi) {
    for (Map.Entry<Node, Long> nodeAndId : this.network.nodesAndIds().entrySet()) {
      Chemical chemical = db.readChemicalFromInKnowledgeGraph(nodeAndId.getValue());
      if (chemical != null && chemical.getInChI().equals(inchi)) {
        return nodeAndId.getKey().id;
      }
    }

    return -1L;
  }

  private Set<Long> traverseTreeFromParent(Long id) throws Exception {
    LinkedList<Long> queue = new LinkedList<>();
    queue.addAll(this.parentToChildren.get(id));
    PrintWriter writer = new PrintWriter("Reactions.txt", "UTF-8");

    Set<Long> result = new HashSet<>();
    while (!queue.isEmpty()) {
      Long candidateId = queue.pop();

      writer.println(printPathFromSrcToDst(id, candidateId));

      result.add(candidateId);
      if (this.parentToChildren.get(candidateId) != null) {
        queue.addAll(this.parentToChildren.get(candidateId));
      }
    }

    writer.close();
    return result;
  }

  private void printInchis(Set<Long> chemIds) throws Exception {
    PrintWriter writer = new PrintWriter("Inchis.txt", "UTF-8");
    for (Long id : chemIds) {
      writer.println(db.readChemicalFromInKnowledgeGraph(id).getInChI());
    }
    writer.close();
  }

  public List<Long> pathFromSrcToDst(Long src, Long dst) {
    List<Long> result = new ArrayList<>();
    Long id = dst;
    result.add(id);

    while (!id.equals(src)) {
      Long newId = this.network.parents.get(id);
      result.add(newId);
      id = newId;
    }

    Collections.reverse(result);
    return result;
  }

  public Set<Long> rxnIdForEdge(Long src, Long dst) {
    Set<Long> rxnThatProduceChem = ActData.instance().rxnClassesThatProduceChem.get(dst);
    Set<Long> rxnThatConsumeChem = ActData.instance().rxnClassesThatConsumeChem.get(src);
    Set<Long> intersection = new HashSet<>(rxnThatProduceChem);
    intersection.retainAll(rxnThatConsumeChem);
    return intersection;
  }

  public String printPathFromSrcToDst(Long src, Long dst) {
    String result = "";
    List<Long> path = pathFromSrcToDst(src, dst);
    for (int i = 0; i < path.size() - 1; i++) {
      result += db.readChemicalFromInKnowledgeGraph(path.get(i)).getInChI();
      result += " --- ";
      Set<Long> rxnIds = rxnIdForEdge(path.get(i), path.get(i + 1));
      result += StringUtils.join(rxnIds, ",");
      result += " ---> ";
    }

    result += db.readChemicalFromInKnowledgeGraph(path.get(path.size() - 1)).getInChI();
    return result;
  }
}

