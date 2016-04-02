package com.act.biointerpretation.step2_desalting;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ReactionDesalter itself does the processing of the database using an instance of Desalter.
 * This class creates Synapse from Dr. Know.  Synapse is the database in which the chemicals
 * have been inspected for containing multiple species or ionized forms, and corrected.
 *
 * Created by jca20n on 10/22/15.
 */
public class ReactionDesalter {
  private NoSQLAPI api;
  private Desalter desalter;
  private Map<Long,Long> oldChemIdToNew;
  private Map<String, Long> inchiToNewId;
  private static final Logger LOGGER = LogManager.getLogger(Desalter.class);

  public static void main(String[] args) {
    ReactionDesalter runner = new ReactionDesalter();
    runner.run();
  }

  public ReactionDesalter() {
    NoSQLAPI.dropDB("synapse");
    this.api = new NoSQLAPI("drknow", "synapse");
    this.desalter = new Desalter();
    this.oldChemIdToNew = new HashMap<>();
    this.inchiToNewId = new HashMap<>();
  }

  public void run() {
    System.out.println("Starting ReactionDesalter");
    long start = new Date().getTime();

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
    while(iterator.hasNext()) {
      Reaction rxn = iterator.next();
      Long[] newSubs = handleChems(rxn.getSubstrates());
      rxn.setSubstrates(newSubs);
      Long[] newProds = handleChems(rxn.getProducts());
      rxn.setProducts(newProds);

      //Write the modified Reaction to the db
      api.writeToOutKnowlegeGraph(rxn);
    }

    long end = new Date().getTime();
    double duration = (end-start) / 1000;
    System.out.println("Time in seconds: " + duration);
    System.out.println("done");
  }

  private Long[] handleChems(Long[] chemIds) {

    Set<Long> newIds = new HashSet<>();

    outer: for(int i=0; i<chemIds.length; i++) {
      long oldid = chemIds[i];

      //If the chemical's ID maps to a single pre-seen entry, use its existing oldid
      if(oldChemIdToNew.containsKey(oldid)) {
        long prerun = oldChemIdToNew.get(oldid);
        newIds.add(prerun);
        continue outer;
      }

      //Otherwise need to clean the chemical
      Set<String> cleanedInchis = null;
      Chemical achem = api.readChemicalFromInKnowledgeGraph(oldid);
      String inchi = achem.getInChI();

      //If it's FAKE, just go with it
      if(inchi.contains("FAKE")) {
        long newid = api.writeToOutKnowlegeGraph(achem); //Write to the db
        inchiToNewId.put(inchi, newid);
        newIds.add(newid);
        oldChemIdToNew.put(oldid, newid);
        continue outer;
      }

      try {
        cleanedInchis = Desalter.desaltMolecule(inchi);
      } catch (Exception e) {
        //TODO:  probably should handle this error differently, currently just letting pass unaltered
        long newid = api.writeToOutKnowlegeGraph(achem); //Write to the db
        inchiToNewId.put(inchi, newid);
        newIds.add(newid);
        oldChemIdToNew.put(oldid, newid);
        continue outer;
      }

      //For each cleaned chemical, put in DB or update ID
      for(String cleanInchi : cleanedInchis) {

        //If the cleaned inchi is already in DB, use existing ID, and hash the id
        if(inchiToNewId.containsKey(cleanInchi)) {
          long prerun = inchiToNewId.get(cleanInchi);
          newIds.add(prerun);
          oldChemIdToNew.put(oldid, prerun);
        }

        //Otherwise update the chemical, put into DB, and hash the id and inchi
        else {
          achem.setInchi(cleanInchi);
          long newid = api.writeToOutKnowlegeGraph(achem); //Write to the db
          inchiToNewId.put(cleanInchi, newid);
          newIds.add(newid);
          oldChemIdToNew.put(oldid, newid);
        }
      }
    }

    //Return the newIds as an array
    Long[] out = new Long[newIds.size()];
    List<Long> tempList = new ArrayList<>();
    tempList.addAll(newIds);
    for(int i=0; i<tempList.size(); i++) {
      out[i] = tempList.get(i);
    }
    return out;
  }

  /**
   * This method is used for testing Desalter
   * It pulls 10,000 salty inchis from the database
   * that are in reactions,
   * then cleans them and sorts them as to whether
   * they fail, clean to the same inchi, or get modified
   */
  public static void examineReactionChems() {
    //Grab a large sample of chemicals that are in reactions
    List<String> saltyChemical = getSaltyReactions(10000);
    LOGGER.debug(String.format("Total number of reactions being processed: %d", saltyChemical.size()));
    sortSalties(saltyChemical);
  }

  /**
   * This function extracts reactions from a DB
   * @param numberOfChemicals
   * @return
   */
  private static List<String> getSaltyReactions(Integer numberOfChemicals) {
    Set<String> saltyChemicals = new HashSet<>();
    NoSQLAPI api = new NoSQLAPI("lucille", "synapse");  //just reading lucille
    Iterator<Reaction> allReactions = api.readRxnsFromInKnowledgeGraph();

    Set<Long> previouslyEncounteredChemicalIDs = new HashSet<>();
    List<String> outputSaltyChemicals = new ArrayList<>();

    while(allReactions.hasNext()) {
      Reaction reaction = allReactions.next();
      Set<Long> reactionParticipants = new HashSet<>();

      for (Long substrateOrProductId : Stream.concat(Arrays.asList(reaction.getSubstrates()).stream(),
          Arrays.asList(reaction.getProducts()).stream()).collect(Collectors.toList())) {
        reactionParticipants.add(substrateOrProductId);
      }

      for (Long reactionId : reactionParticipants) {

        if (saltyChemicals.size() >= numberOfChemicals) {
          outputSaltyChemicals.addAll(saltyChemicals);
          return outputSaltyChemicals;
        }

        if (previouslyEncounteredChemicalIDs.contains(reactionId)) {
          continue;
        } else {
          previouslyEncounteredChemicalIDs.add(reactionId);
        }

        Chemical achem = api.readChemicalFromInKnowledgeGraph(reactionId);
        String inchi = achem.getInChI();

        if (inchi.contains("FAKE")) {
          continue;
        }

        try {
          Desalter.InchiToSmiles(inchi);
        } catch (Exception err) {
          LOGGER.error("Exception message: %s", err.getMessage());
          continue;
        }

        saltyChemicals.add(inchi);
      }
    }

    outputSaltyChemicals.addAll(saltyChemicals);
    return outputSaltyChemicals;
  }


  private static void sortSalties(List<String> salties) {

    //Clean the salties
    StringBuilder sbModified = new StringBuilder();
    StringBuilder sbUnchanged = new StringBuilder();
    StringBuilder sbErrors = new StringBuilder();
    StringBuilder sbComplex = new StringBuilder();
    for (int i = 0; i < salties.size(); i++) {
      String salty = salties.get(i);
      String saltySmile = null;
      try {
        saltySmile = Desalter.InchiToSmiles(salty);
      } catch (Exception err) {
        sbErrors.append("InchiToSmiles1\t" + salty + "\r\n");
        continue;
      }

      Set<String> results = null;
      try {
        results = Desalter.desaltMolecule(salty);
      } catch (Exception err) {
        sbErrors.append("cleaned\t" + salty + "\r\n");
        err.printStackTrace();
        continue;
      }

      //Not sure results can be size zero or null, but check anyway
      if (results == null) {
        sbErrors.append("clean results are null:\t" + salty + "\r\n");
        continue;
      }
      if (results.isEmpty()) {
        sbErrors.append("clean results are empty:\t" + salty + "\r\n");
        continue;
      }

      //If cleaning resulted in a single organic product
      if (results.size() == 1) {
        String cleaned = results.iterator().next();
        String cleanSmile = null;
        try {
          cleanSmile = Desalter.InchiToSmiles(cleaned);
        } catch (Exception err) {
          sbErrors.append("InchiToSmiles2\t" + salty + "\r\n");
        }

        if (!salty.equals(cleaned)) {
          sbModified.append(salty + "\t" + cleaned + "\t" + saltySmile + "\t" + cleanSmile + "\r\n");
        } else {
          sbUnchanged.append(salty + "\t" + saltySmile + "\r\n");
        }
      }
      //Otherwise there were multiple organic products
      else {
        sbComplex.append(">>\t" + salty + "\t" + saltySmile + "\r\n");
        for (String inchi : results) {
          sbComplex.append("\t" + inchi + "\t" + Desalter.InchiToSmiles(inchi) + "\r\n");
        }
      }
    }

//    File dir = new File("output/desalter");
//    if (!dir.exists()) {
//      dir.mkdir();
//    }
    /*
    FileUtils.writeFile(sbModified.toString(), "output/desalter/Desalter_" + mode + "_modified.txt");
    FileUtils.writeFile(sbUnchanged.toString(), "output/desalter/Desalter_" + mode + "_unchanged.txt");
    FileUtils.writeFile(sbErrors.toString(), "output/desalter/Desalter_" + mode + "_errors.txt");
    FileUtils.writeFile(sbComplex.toString(), "output/desalter/Desalter_" + mode + "_complex.txt");
    */
  }

}
