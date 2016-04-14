package com.act.biointerpretation.step3_cofactorremoval;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.step2_desalting.Desalter;
import com.act.biointerpretation.step4_mechanisminspection.MechanisticValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

/**
 *
 * This class reads in reactions from a read DB and processes each one such that cofactors are binned together
 * in either substrate/product cofactor lists.
 *
 * Created by jca20n on 2/15/16.
 */
public class CofactorRemover {
  private static final String WRITE_DB = "jarvis";
  private static final String READ_DB = "synapse";
  private NoSQLAPI api;
  private CofactorsCorpus cofactorsCorpus;
  private static final Logger LOGGER = LogManager.getLogger(Desalter.class);

  Map<ExistingRxn, Long> rxnToNewRxnId;
  Map<String, Long> inchiToNewChemId;
  List<String> cofactorNames;

  long position = 0;

  private  int rxncount = 0;
  private transient MechanisticValidator validator;

  public static void main(String[] args) throws Exception {
//    CofactorsCorpus test = new CofactorsCorpus();




//
//    File dir = new File("output/cofactors");
//    if(!dir.exists()) {
//      dir.mkdir();
//    }
//
//    //Load existing data, or start over
//    CofactorRemover remover = null;
//    try {
//      remover = CofactorRemover.fromFile("output/cofactors/CofactorRemover.ser");
//      remover.api = new NoSQLAPI();
//      remover.validator = new MechanisticValidator(remover.api);
//    } catch(Exception err) {}
//    if(remover == null) {
//      remover = new CofactorRemover();
//    }
//
//    remover.transferChems();
//    remover.transferRxns();
//
//    remover.save("output/cofactors/CofactorRemover.ser");
//    System.out.println("done");
  }


  public CofactorRemover() throws IOException {
    // Delete all records in the WRITE_DB
    NoSQLAPI.dropDB(WRITE_DB);
    api = new NoSQLAPI(READ_DB, WRITE_DB);

    rxnToNewRxnId = new HashMap<>();
    inchiToNewChemId = new HashMap<>();
    cofactorNames = new ArrayList<>();
    validator = new MechanisticValidator(api);
    validator.initiate();

    cofactorsCorpus = new CofactorsCorpus();
  }

  public void run() {
    LOGGER.debug("Starting Reaction Desalter");
    long startTime = new Date().getTime();

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
    while (iterator.hasNext()) {

      //Reaction newRxn = new Reaction();
      Reaction rxn = iterator.next();


    }

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  private Reaction abstractCofactorsFromReaction(Reaction rxn) {
    Reaction fr = rxn; // fr = First reaction; we'll refer to it a lot in a moment.
    Reaction newReaction = new Reaction(
        -1, // Assume the id will be set when the reaction is written to the DB.
        fr.getSubstrates(),
        fr.getProducts(),
        fr.getSubstrateCofactors(),
        fr.getProductCofactors(),
        fr.getCoenzymes(),
        fr.getECNum(),
        fr.getConversionDirection(),
        fr.getPathwayStepDirection(),
        fr.getReactionName(),
        fr.getRxnDetailType()
    );

    

    newReaction.setDataSource(fr.getDataSource());













    // Write stub reaction to DB to get its id, which is required for migrating sequences.
    //int newId = api.writeToOutKnowlegeGraph(newReaction);









  }



  /**
   * Transfer all the chemicals to the new database
   * Record the inchi to new id mapping
   */
  public void transferChems() {
    Iterator<Chemical> iterator = api.readChemsFromInKnowledgeGraph();
    while(iterator.hasNext()) {
      Chemical achem = iterator.next();
      long result = api.writeToOutKnowlegeGraph(achem);
      inchiToNewChemId.put(achem.getInChI(), result);
    }
  }

  /**
   * Transfer all the reactions to the new database
   * Maintains uniqueness of substrate/products/cofactors
   * Abstracts out any cofactors
   */
  public void transferRxns() {
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
    //Find the end of the ids (this is done so can restart from pre-saved index)
    long highest = 0;
    while (iterator.hasNext()) {
      iterator.next();
      try {
        Reaction rxn = iterator.next();
        long id = rxn.getUUID();
        if (id > highest) {
          highest = id;
        }
      } catch (Exception err) {
        break;
      }
    }

    //Scan through each reaction, starting with a pre-saved index
    for (long i = position; i < highest; i++) {
      position = i;

      //Retrieve the next reaction
      Reaction rxn = null;
      try {
        rxn = api.readReactionFromInKnowledgeGraph(i);
      } catch (Exception er2) {
        System.out.println("err 1 - " + i);
        continue;
      }
      if (rxn == null) {
        System.out.println("err 2 - " + i);
        continue;
      }
      rxncount++;

      System.out.println("working: " + rxn.getUUID());

      //Abstract, modify, and save rxn to new db
      processRxn(rxn);

      if (rxncount % 100 == 0) {
        try {
          this.save("output/cofactors/CofactorRemover.ser");
        } catch (Exception err) {
        }
      }
    }
  }

  private void processRxn(Reaction rxn) {
      ExistingRxn out = new ExistingRxn();

      //Use MechanisticValidator to remove the cofactors
      MechanisticValidator.Report report = new MechanisticValidator.Report();
      try {
          validator.preProcess(rxn, report);
      } catch(Exception err) {
          report.log.add("Failure to preProcess " + rxn.getUUID());
      }

      //Abstract the cofactors
      for(String cofactor : report.subCofactors) {
          if(!cofactorNames.contains(cofactor)) {
              cofactorNames.add(cofactor);
          }
          out.subCos.add(cofactorNames.indexOf(cofactor));
      }
      for(String cofactor : report.prodCofactors) {
          if(!cofactorNames.contains(cofactor)) {
              cofactorNames.add(cofactor);
          }
          out.pdtCos.add(cofactorNames.indexOf(cofactor));
      }

      //Abstract the inchis for the substate and product
      for(String inchi : report.subInchis) {
          long newid = inchiToNewChemId.get(inchi);
          out.subIds.add(newid);
      }
      for(String inchi : report.prodInchis) {
          long newid = inchiToNewChemId.get(inchi);
          out.pdtIds.add(newid);
      }

      //Stop if it's a repeated reaction
      if(rxnToNewRxnId.containsKey(out)) {
          //TODO:  deal with merges if two reactions now collapse to one
          //Pull the existing reaction, add the new additional data, update the db
          return;
      }

      //Set substrates
      Long[] substrates = new Long[out.subIds.size()];
      int count = 0;
      for(long newid : out.subIds) {
          substrates[count] = newid;
          count++;
      }
      rxn.setSubstrates(substrates);

      //Set products
      Long[] products = new Long[out.pdtIds.size()];
      count = 0;
      for(long newid : out.pdtIds) {
          products[count] = newid;
          count++;
      }
      rxn.setProducts(products);

      //Set the sub cofactors
      for(int coid : out.subCos) {

      }

      //Set the prod cofactors


  }

  public void save(String path) throws Exception {
      FileOutputStream fos = new FileOutputStream(path);
      ObjectOutputStream out = new ObjectOutputStream(fos);
      out.writeObject(this);
      out.close();
      fos.close();
  }

  public static CofactorRemover fromFile(String path) throws Exception {
      FileInputStream fis = new FileInputStream(path);
      ObjectInputStream ois = new ObjectInputStream(fis);
      CofactorRemover out = (CofactorRemover) ois.readObject();
      ois.close();
      fis.close();
      return out;
  }
}
