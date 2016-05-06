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

public class CladeTraversal {

  Tree<Long> tree;
  WavefrontExpansion wavefrontExpansion;

  public CladeTraversal() {
    this.wavefrontExpansion = new WavefrontExpansion();
    this.tree = wavefrontExpansion.expandAndPickParents();
    this.tree.ensureForest();
    findChemicalAndAllItsDescendants("InChI=1S/C7H7NO2/c8-6-3-1-5(2-4-6)7(9)10/h1-4H,8H2,(H,9,10)");
  }

  public static void main(String[] args) {
    ActData.instance().deserialize("result.actdata");
    CladeTraversal test = new CladeTraversal();
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
            for (Long id : WavefrontExpansion.productsThatAreNotAbstract(products_dataset.get(entry.getKey()))) {
              // Only pick reactants that are similar to PABA and are reachable
              Integer diff = deltaBetweenChemical(id, targetId);
              if ((diff >= -1) && tree.allNodes().contains(id)) {
                products_made.add(id);
              }
            }
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

                  Integer diff = deltaBetweenChemical(id, targetId);
                  // Only pick reactants that are similar to PABA and are reachable
                  if ((deltaBetweenChemical(id, targetId) >= -1) && tree.allNodes().contains(id)) {
                    queue.add(id);
                  }
                }
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

  private Integer deltaBetweenChemical(Long p, Long targetId) {
    String prod = ActData.instance().chemId2Inchis.get(p);
    String target = ActData.instance().chemId2Inchis.get(targetId);
    Integer prodCount = countCarbons(prod);
    Integer subCount = countCarbons(target);
    return prodCount - subCount;
  }

  private Integer countCarbons(String inchi) {
    String[] spl = inchi.split("/");
    if (spl.length <= 2)
      return null;

    String formula = spl[1];
    Pattern regex = Pattern.compile("C([0-9]+)");
    Matcher m = regex.matcher(formula);
    if (m.find()) {
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
}
