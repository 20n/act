package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This creates Synapse from Dr. Know.  Synapse is the database in which all Chemicals have either been
 * repaired or deleted.
 * <p>
 * Created by jca20n on 9/12/15.
 */
public class ChemicalCleaner {

  private NoSQLAPI api;
  private IndigoInchi iinchi;

  private Map<Long, Boolean> preChecked;
  private Map<Long, Long> oldToNew;

  private Map<Long, List<Long>> tossed;

  private Map<String, String> correctedInchis;

  private long chemCount = 0;
  private long rxnCount = 0;

  public static void main(String[] args) {
    System.out.println("Hi there!");
    ChemicalCleaner cclean = new ChemicalCleaner();
    cclean.run();
  }

  public ChemicalCleaner() {
    this.api = new NoSQLAPI("drknow", "synapse");
    Indigo indigo = new Indigo();
    iinchi = new IndigoInchi(indigo);
    preChecked = new HashMap<>();
    oldToNew = new HashMap<>();
    tossed = new HashMap<>();
    correctedInchis = new HashMap<>();

    //Read in the corrected inchis
    String data = FileUtils.readFile("data/corrected_inchis.txt");
    String[] lines = data.split("\n");
    for (String aline : lines) {
      String[] splitted = aline.split("\t");
      correctedInchis.put(splitted[0], splitted[1]);
    }
  }

  public void run() {
    long start = new Date().getTime();

    //Gather up any dud reactions
    Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
    Outer:
    while (rxns.hasNext()) {
      try {
        Reaction rxn = rxns.next();

        List<Long> allChemIds = new ArrayList(Arrays.asList(rxn.getSubstrates()));
        allChemIds.addAll(Arrays.asList(rxn.getProducts()));

        for (long chemId : allChemIds) {
          if (!isClean(chemId, rxn)) {
            continue Outer;
          }
        }

        //The reaction's chemicals passed inspection, so transfer them to new DB
        for (long chemId : allChemIds) {
          //If it's already in preChecked, then it must be "true" and already saved to new DB
          if (oldToNew.containsKey(chemId)) {
            continue;
          }

          Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
//                    long newid = api.writeToOutKnowlegeGraph(achem);
          chemCount++;
          preChecked.put(chemId, true);
//                    oldToNew.put(chemId, newid);
          oldToNew.put(chemId, (long) 5);
        }

        //Update the reaction's chem ids, put it in the db
        Long[] chems = rxn.getSubstrates();
        Long[] newIds = new Long[chems.length];
        for (int i = 0; i < chems.length; i++) {
          newIds[i] = oldToNew.get(chems[i]);
        }
        rxn.setSubstrates(newIds);

        chems = rxn.getProducts();
        newIds = new Long[chems.length];
        for (int i = 0; i < chems.length; i++) {
          newIds[i] = oldToNew.get(chems[i]);
        }
        rxn.setProducts(newIds);

//                api.writeToOutKnowlegeGraph(rxn);
        rxnCount++;

      } catch (Exception err) {
        err.printStackTrace();
      }
    }

    long end = new Date().getTime();

    System.out.println("Cleaning out dud chemicals: " + (end - start) / 1000 + " seconds and created x,x chems and rxns: " + chemCount + " , " + rxnCount);

    //Tally up the error-logged inchis and sort them by count
    Map<Long, Integer> rxnCounts = new HashMap<>();  //<inchi, num reactions>
    for (Long chemId : tossed.keySet()) {
      List<Long> ids = tossed.get(chemId);
      rxnCounts.put(chemId, ids.size());
    }
    MyComparator comparator = new MyComparator(rxnCounts);

    TreeMap<Long, Integer> sorted = new TreeMap<Long, Integer>(comparator);
    sorted.putAll(rxnCounts);

    //Print out the top 200
    int count = 0;
    StringBuilder sb = new StringBuilder();
    for (Long chemId : sorted.descendingKeySet()) {
      count++;
      Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
      sb.append("BADINCHI~");
      sb.append(achem.getInChI());
      sb.append("~BADINCHI");
      sb.append("\n");
      sb.append("REPLACEWITH~");
      sb.append("~REPLACEWITH");
      sb.append("\n");
      sb.append("SCENARIO~");
      sb.append("~SCENARIO");
      sb.append("\n");
      sb.append("counts: " + rxnCounts.get(chemId));
      sb.append("\n");
      sb.append("id: " + achem.getUuid());
      sb.append("\n");
      List<Long> rxnIds = tossed.get(chemId);
      int limit = 0;
      for (int i = 0; i < 10; i++) {
        Reaction rxn = api.readReactionFromInKnowledgeGraph(rxnIds.get(i));
        sb.append(rxn.toString());
        sb.append("\n");
      }
      sb.append("\n");
      sb.append("\n");
      sb.append("\n");
      sb.append("\n");
      if (count > 200) {
        break;
      }
    }
    FileUtils.writeFile(sb.toString(), "output/dud_chems.txt");
  }

  private void log(Long chemId, Reaction rxn) {
    List<Long> ids = tossed.get(chemId);
    if (ids == null) {
      ids = new ArrayList<>();
    }
    ids.add(Long.valueOf(rxn.getUUID()));
    tossed.put(chemId, ids);
  }


  private boolean isClean(long chemId, Reaction rxn) {
    if (preChecked.containsKey(chemId)) {
      boolean checked = preChecked.get(chemId);
      if (checked == false) {
        log(chemId, rxn);
      }
      return checked;
    }

    Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
    String inchi = achem.getInChI();
    if (inchi == null) {
      System.err.println("Got a null inchi, not expecting");
      return false;
    }

    //If it has "FAKE", or has a ? or R don't bother parsing
    if (inchi.contains("FAKE") || inchi.contains("r") || inchi.contains("?") || !inchi.startsWith("InChI=1S")) {
      preChecked.put(chemId, false);
      log(chemId, rxn);
      return false;
    }

    //Try to parse the structure
    try {
      IndigoObject mol = iinchi.loadMolecule(inchi);
    } catch (Exception err) {
      preChecked.put(chemId, false);
      log(chemId, rxn);
      return false;
    }
    return true;
  }

  class MyComparator implements Comparator<Object> {
    Map<Long, Integer> map;

    public MyComparator(Map<Long, Integer> map) {
      this.map = map;
    }

    public int compare(Object o1, Object o2) {
      int i1 = map.get(o1);
      int i2 = map.get(o2);

      if (i1 < i2) {
        return -1;
      } else return 1;
    }
  }
}
