package act.installer.kegg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import org.json.JSONObject;
import org.json.JSONArray;

import act.shared.ConsistentInChI;
import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;

import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;

public class KeggParser {
  private static final String keggXrefUrlPrefix = "http://www.kegg.jp/entry/";

  /**
   * All the params are file names from KEGG
   * See data/kegg
   * @param reactionList
   * @param compound
   * @param reactions
   * @param cofactors
   * @throws IOException
   */
  public static void parseKegg(String reactionList, String compoundInchi,
      String compound, String reactions, String cofactors, MongoDB db) {

    try {
      // First figure out what compounds are used in reactions
      Set<String> requiredKeggCompounds = parseReactions(reactionList, db);

      // Get kegg compounds to inchi mapping
      Map<String, String> keggIDInchi = parseChemicalInchis(compoundInchi, db);

      // Get required kegg compounds to inchi mapping
      Map<String, String> requiredKeggInchi = new HashMap<String, String>();
      for (String c : requiredKeggCompounds) requiredKeggInchi.put(c, keggIDInchi.get(c));

      Set<String> cofactorsSet = parseCofactors(cofactors);
      // Add chemicals to db
      parseChemicalsDetailed(compound, db, requiredKeggInchi, cofactorsSet);

      // Add reactions to db
      parseReactionsDetailed(reactions, db);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * @param filename - File containing list of KEGG ids marked as cofactors
   * @return set of KEGG ids marked as cofactors
   */
  public static Set<String> parseCofactors(String filename) {
    Set<String> result = new HashSet<String>();
    try{
      BufferedReader br = new BufferedReader(
          new InputStreamReader(
              new DataInputStream(new FileInputStream(filename))));
      String strLine = "";
      while ((strLine = br.readLine()) != null) {
        result.add(strLine.trim());
      }
      br.close();
    } catch(Exception e) {

    }
    return result;
  }

  /**
   * Takes the KEGG reaction.lst file.
   * @param filename
   * @param db
   * @return set of KEGG compounds involved in kegg reactions
   * @throws IOException
   */
  public static Set<String> parseReactions(String filename, MongoDB db) throws IOException {
    //set up existing reactions
    List<Long> ids = db.getAllReactionUUIDs();
    Set<Long> cofactorIDs = Chemical.getChemicalIDs(db.getCofactorChemicals());
    Set<P> pairs = new HashSet<P>();
    for (Long id : ids) {
      Reaction reaction = db.getReactionFromUUID(id);
      Set<Long> substrateIDs = new HashSet(Arrays.asList(reaction.getSubstrates()));
      substrateIDs.removeAll(cofactorIDs);
      Set<Long> productIDs = new HashSet(Arrays.asList(reaction.getProducts()));
      productIDs.removeAll(cofactorIDs);
      pairs.add(new P(substrateIDs, productIDs));
    }
    int numExisting = pairs.size();

    Set<String> newReactionsToAdd = new HashSet<String>(); //keggIDs to add
    Set<String> missingKeggCompounds = new HashSet<String>(); //kegg compound ids we don't have
    Set<String> reactionsMissingKeggCompounds = new HashSet<String>(); //kegg reactions that require the above ids
    Set<String> allRequiredKeggCompounds = new HashSet<String>();

    Map<String, Long> keggID_ActID = db.getKeggID_ActID(false);
    try{
      BufferedReader br = new BufferedReader(
          new InputStreamReader(
              new DataInputStream(new FileInputStream(filename))));

      String strLine;
      int numGood = 0, numNew = 0, i = 0;
      while ((strLine = br.readLine()) != null) {
        String[] splitted = strLine.split("\\s+");
        String keggID = splitted[0].substring(0, 6); //removes colon after id
        boolean bad = false, productSide = false;

        Set<Long> reactantIDs = new HashSet<Long>();
        Set<Long> productIDs = new HashSet<Long>();
        for (String s : splitted) {
          if (s.charAt(0) == 'C' || s.charAt(0) == 'G') { //is it a chemical or glycan?
            s = s.substring(0, 6); //remove the possible parameters
            allRequiredKeggCompounds.add(s);
            if (!keggID_ActID.containsKey(s)) {
              bad = true;
              missingKeggCompounds.add(s);
            } else if (!productSide)
              reactantIDs.add(keggID_ActID.get(s));
            else
              productIDs.add(keggID_ActID.get(s));
          } else if (s.contains("=")) {
            productSide = true;
          }
        }

        if (!bad) {
          numGood++;
          if (reactantIDs.isEmpty() || productIDs.isEmpty()) {
            System.out.println("KeggParser.parseReactions: no reactants or products" + strLine);

          } else {
            Reaction toAdd = new Reaction(-1,
                (Long[]) reactantIDs.toArray(new Long[0]),
                (Long[]) productIDs.toArray(new Long[0]),
                null, // ecnum
                ConversionDirectionType.LEFT_TO_RIGHT,
                StepDirection.LEFT_TO_RIGHT,
                keggID, // readable name
                Reaction.RxnDetailType.CONCRETE
                );
            reactantIDs.removeAll(cofactorIDs);
            productIDs.removeAll(cofactorIDs);
            P<Set, Set> pair = new P(reactantIDs, productIDs);
            if (!pairs.contains(pair))
              pair = new P(productIDs, reactantIDs);

            if (pairs.add(pair)) {
              newReactionsToAdd.add(keggID);
            }
          }
        } else {
          // add reactions with a chemical not in database into this set
          reactionsMissingKeggCompounds.add(keggID);
        }
        if (i % 1000 == 0) System.out.println("KeggParser.parseReactions: Done " + i);
        i++;
      }
      System.out.println("KeggParser.parseReactions: Num Missing KEGG Chemical IDs: " + missingKeggCompounds.size());
      System.out.println("KeggParser.parseReactions: Num Missing KEGG Reactions: " + reactionsMissingKeggCompounds.size());
      System.out.println("KeggParser.parseReactions: Num Found Reactions: " + numGood + " New: " + (pairs.size() - numExisting));
    } catch (Exception e){
      e.printStackTrace();
    }
    return allRequiredKeggCompounds;
  }


  public static Set<String> getAllKeggIDsWInchis(String filename, MongoDB db) {
    return parseChemicalInchis(filename, db).keySet();
  }

  /**
   * Parses the KEGG file mapping from KEGG ID to InChIs
   * and creates a chemical entry in db for each not already in db
   * @param filename
   * @param db
   * @return map from KEGG id to consistent InChI
   */
  public static Map<String, String> parseChemicalInchis(String filename, MongoDB db) {
    Map<String, String> keggID_InChI = new HashMap<String, String>();
    DBIterator it = db.getIteratorOverChemicals();
    Map<String, Long> inchi_ID = new HashMap<String, Long>();
    while (it.hasNext()) {
      Chemical chemical = db.getNextChemical(it);
      inchi_ID.put(chemical.getInChI(), chemical.getUuid());
    }

    try{
      BufferedReader br = new BufferedReader(
          new InputStreamReader(
              new DataInputStream(new FileInputStream(filename))));
      FileWriter fstream = new FileWriter("keggCompoundsNotFound.log.html");
      BufferedWriter notFound = new BufferedWriter(fstream);
      notFound.write("<html><head></head><body>");
      String strLine;
      int i = 0;
      int numFound = 0;
      Indigo indigo = new Indigo();
      IndigoInchi indigoInchi = new IndigoInchi(indigo);
      while ((strLine = br.readLine()) != null) {
        String[] splitted = strLine.split("\\s+");
        String keggID = splitted[0];
        String inchi = splitted[1];
        keggID_InChI.put(keggID, inchi);
        inchi = ConsistentInChI.consistentInChI(inchi, "Kegg Parser");
        String inchiKey = indigoInchi.getInchiKey(inchi);
        Chemical chemical = db.getChemicalFromInChIKey(inchiKey);
        i++;
        if (chemical == null) {
          try {
            if (inchi_ID.containsKey(inchi)) {
              chemical = db.getChemicalFromChemicalUUID(inchi_ID.get(inchi));
            }
            if (chemical == null) {
              IndigoObject test = indigoInchi.loadMolecule(inchi);
              if (keggID.startsWith("G"))
                notFound.write("<a href=\"http://www.kegg.jp/dbget-bin/www_bget?gl:" + keggID + "\">" + keggID + "</a>\n");
              else
                notFound.write("<a href=\"http://www.kegg.jp/dbget-bin/www_bget?cpd:" + keggID + "\">" + keggID + "</a>\n");
              Chemical newChemical = new Chemical(inchi); // calls setInchi which sets the inchikey
              // newChemical.setInchiKey(inchiKey);
              newChemical.setSmiles(test.canonicalSmiles());
              addKeggRef(keggID, newChemical);
              db.submitToActChemicalDB(newChemical, db.getNextAvailableChemicalDBid());
              continue;
            }
          } catch (Exception e){
            if (keggID.startsWith("G"))
              notFound.write("<i><a href=\"http://www.kegg.jp/dbget-bin/www_bget?gl:" + keggID + "\">" + keggID + "</a></i>\n");
            else
              notFound.write("<i><a href=\"http://www.kegg.jp/dbget-bin/www_bget?cpd:" + keggID + "\">" + keggID + "</a></i>\n");
            Chemical newChemical = new Chemical(inchi);
            addKeggRef(keggID, newChemical);
            db.submitToActChemicalDB(newChemical, db.getNextAvailableChemicalDBid());
            continue;
          }
        }

        numFound++;
        addKeggRef(keggID, chemical);
        db.updateActChemical(chemical, chemical.getUuid());
        if (i % 1000 == 0) System.out.println("KeggParser.parseChemicalInchis: Done " + i);
      }
      notFound.write("</body><html>");

      br.close();
      notFound.close();
      System.out.println("KeggParser.parseChemicalInchis: # already in DB " + numFound);

    } catch (Exception e){
      e.printStackTrace();
    }
    return keggID_InChI;
  }

  /**
   * Adds keggID reference to chemical if it doesn't exist already
   * @param keggID
   * @param chemical
   */
  private static void addKeggRef(String keggID, Chemical chemical) {
    JSONObject existing = (JSONObject) chemical.getRef(Chemical.REFS.KEGG);
    if (existing != null) {
      JSONArray ids = (JSONArray) existing.get("id");
      if (!jsonArrayContains(ids, keggID))
        ids.put(keggID);
      if (!existing.has("url"))
        existing.put("url", keggXrefUrlPrefix + keggID);
    } else {
      JSONObject entry = new JSONObject();
      JSONArray ids = new JSONArray();
      ids.put(keggID);
      entry.put("id", ids);
      if (!entry.has("url"))
        entry.put("url", keggXrefUrlPrefix + keggID);
      chemical.putRef(Chemical.REFS.KEGG, entry);
    }
  }

  private static boolean jsonArrayContains(JSONArray a, Object contains) {
    for (int i = 0; i < a.length(); i++) {
      if (a.get(i).equals(contains))
        return true;
    }
    return false;
  }

  private static void jsonArrayRemove(JSONArray a, Object toRemove) {
    for (int i = 0; i< a.length(); i++) {
      if (a.get(i).equals(toRemove))
        a.remove(i);
    }
  }

  /**
   * Parses file KEGG file with chemical details.
   * Anything in requiredIdInchi not already in db is added.
   * @param filename
   * @param db
   * @param requiredIdInchi
   * @param cofactorsSet
   * @throws IOException
   */
  public static void parseChemicalsDetailed(String filename, MongoDB db,
      Map<String, String> requiredIdInchi, Set<String> cofactorsSet) throws IOException {
    System.out.println("KeggParser.parseChemicalsDetailed: start");
    BufferedReader br = new BufferedReader(
        new InputStreamReader(
            new DataInputStream(new FileInputStream(filename))));
    Map<String, Long> keggID_ActID = db.getKeggID_ActID(false);
    int numEntriesUpdated = 0;
    int numFailedToFind = 0;
    int numNewKegg = 0;
    // set of variables to keep track of when parsing one entry
    String currKeggID = null;
    Long currActID = null;
    String currFormula = null;
    List<String> currSynonyms = null;

    String strLine;
    while ((strLine = br.readLine()) != null) {
      if (strLine.startsWith(" ")) continue; //in middle of a field we don't need now

      String[] field_val = strLine.split(" +", 2);

      String key = field_val[0];
      if (key.equals("ENTRY")) {
        currKeggID = field_val[1].split(" +")[0];
        currActID = keggID_ActID.get(currKeggID);
        if (currActID == null) {
          currActID = keggID_ActID.get(currKeggID + "n");
          keggID_ActID.put(currKeggID, currActID);
        }
        currFormula = null;
        currSynonyms = null;
      } else if (key.equals("FORMULA")) {
        currFormula = field_val[1];
      } else if (key.equals("NAME")) {
        currSynonyms = new ArrayList<String>();
        String currName = field_val[1];
        while (true) {
          if (currName.endsWith(";")) {
            currSynonyms.add(currName.substring(0, currName.length() - 1));
            strLine = br.readLine();
            currName = strLine.split(" +", 2)[1];
          } else {
            currSynonyms.add(currName);
            break;
          }
        }
      } else if (key.equals("///")) {
        if (requiredIdInchi.containsKey(currKeggID)) {
          boolean needUpdate = false;
          //if (requiredIdInchi.get(currKeggID) == null)
          //  System.out.println("NAMES " + currSynonyms + " ACT_ID " + currActID + " FOR " + currKeggID + " " + currFormula);
          if (currActID == null) {
            if (currSynonyms != null) {
              for (String s : currSynonyms) {
                Long id = db.getChemicalIDFromName(s);
                if (id != null && id != -1L) {
                  Chemical chemical = db.getChemicalFromChemicalUUID(id);
                  addKeggRef(currKeggID, chemical);
                  db.updateActChemical(chemical, id);
                  keggID_ActID.put(currKeggID, id);
                  currActID = id;
                  break;
                }
              }
            }

            if (currActID == null ) {
              // chemical not in compound.inchi and not in db
              currActID = db.getNextAvailableChemicalDBid();
              Chemical chemical = new Chemical(currActID);
              chemical.setInchi("none " + currKeggID);
              addKeggRef(currKeggID, chemical);
              db.submitToActChemicalDB(chemical, currActID);
              numFailedToFind++;
            }
            needUpdate = true;
          }

          Chemical toUpdate = db.getChemicalFromChemicalUUID(currActID);
          if (cofactorsSet.contains(currKeggID) && !toUpdate.isCofactor()) {
            toUpdate.setAsCofactor();
            needUpdate = true;
          }
          if (currSynonyms == null) currSynonyms = new ArrayList<String>();
          List<String> existingSynonyms = toUpdate.getSynonyms();
          currSynonyms.removeAll(existingSynonyms);
          for (String syn : currSynonyms) {
            toUpdate.addSynonym(syn);
            needUpdate = true;
          }

          // if no formula or formula contains n, append "n" to keggid for now.
          // this avoids adding any reactions that'll involve these chemicals
          if ((currFormula == null || currFormula.contains(")n")) && !toUpdate.isCofactor()) {
            JSONObject o = (JSONObject) toUpdate.getRef(Chemical.REFS.KEGG);
            JSONArray list = (JSONArray) o.get("id");
            jsonArrayRemove(list, currKeggID);
            System.out.printf("KeggParser.parseChemicalsDetailed: Needs parameter: %s, %s\n", currKeggID, currFormula);
            if (!jsonArrayContains(list, currKeggID + "n")) {
              list.put(currKeggID + "n");
            }
            if (!o.has("url")) {
              o.put("url", keggXrefUrlPrefix + currKeggID);
            }
            needUpdate = true;
          }
          if (needUpdate) {
            numEntriesUpdated++;
            db.updateActChemical(toUpdate, currActID);
          }
        }
        currKeggID = null;
        currSynonyms = null;
        currFormula = null;
      }
    }
    br.close();
    System.out.format("KeggParser.parseChemicalsDetailed: Num total entries added %d, Num no inchi but added %d\n", numEntriesUpdated, numFailedToFind);
  }

  /**
   * Parses KEGG file with reaction details.
   * Add all reactions that involve only chemicals in our database.
   * @param filename
   * @param db
   * @throws IOException
   */
  public static void parseReactionsDetailed(String filename, MongoDB db) throws IOException {
    Map<String, Long> keggID_ActID = db.getKeggID_ActID(false);
    BufferedReader br = new BufferedReader(
        new InputStreamReader(
            new DataInputStream(new FileInputStream(filename))));

    int numEntriesAdded = 0;
    int failed = 0;
    // set of variables to keep track of when parsing one entry
    String currKeggID = null;
    String currName = null;
    Map<Long, Integer> currProducts = null, currReactants = null; //maps chemicals to coefficients
    String currECNum = null;

    String strLine;
    while ((strLine = br.readLine()) != null) {
      if (strLine.startsWith(" ")) continue; //in middle of a field we don't need now

      String[] field_val = strLine.split(" +", 2);

      String key = field_val[0];
      if (key.equals("ENTRY")) {
        currName = null;
        currProducts = null;
        currReactants = null;
        currECNum = null;

        currKeggID = field_val[1].split(" +")[0];
      } else if (key.equals("ENZYME")) {
        currECNum = field_val[1];
      } else if (key.equals("///")) {
        if (currKeggID == null) {
          failed++;
          continue;
        }
        if (currProducts == null || currReactants == null) continue;

        Long[] productArr = (Long[]) currProducts.keySet().toArray(new Long[1]);
        Long[] reactantArr = (Long[]) currReactants.keySet().toArray(new Long[1]);
        Reaction toAdd = new Reaction(-1L,
            reactantArr,
            productArr,
            currECNum,
            ConversionDirectionType.LEFT_TO_RIGHT,
            StepDirection.LEFT_TO_RIGHT,
            currName,
            Reaction.RxnDetailType.CONCRETE
            );
        toAdd.addReference(Reaction.RefDataSource.KEGG, currKeggID);
        toAdd.addReference(Reaction.RefDataSource.KEGG, keggXrefUrlPrefix + currKeggID);
        for (Long p : productArr) toAdd.setProductCoefficient(p, currProducts.get(p));
        for (Long r : reactantArr) toAdd.setSubstrateCoefficient(r, currReactants.get(r));
        numEntriesAdded++;
        toAdd.setDataSource(Reaction.RxnDataSource.KEGG);
        db.submitToActReactionDB(toAdd);
        currKeggID = null;
      } else if (key.equals("DEFINITION")) {
        currName = field_val[1];
        if (currName == null) currName = "";
        currName = "{} " + currName.replace("<=>", "->") + " <KEGG:" + currKeggID + ">";
      } else if (key.equals("EQUATION")) {
        String[] tokens = field_val[1].split(" +");
        currProducts = new HashMap<Long, Integer>();
        currReactants = new HashMap<Long, Integer>();
        boolean isProducts = false;
        for (int t = 0; t < tokens.length; t++) {
          String token = tokens[t];
          if (token.equals("+")) continue;
          if (token.equals("<=>")) {
            isProducts = true;
            continue;
          }

          Integer coeff = 1;
          if (token.matches("\\d+")) { //is numeric
            coeff = Integer.parseInt(token);
            t++;
            token = tokens[t];
          }

          if (token.startsWith("C") || token.startsWith("G")) {
            token = token.substring(0, 6); //remove the possible parameters
            Long actID = keggID_ActID.get(token);
            if (actID == null) { //allow use of chemicals with parameter
              actID = keggID_ActID.get(token + "n");
            }
            if (actID != null) {
              if (isProducts) {
                currProducts.put(actID, coeff);
              } else {
                currReactants.put(actID, coeff);
              }
            } else {
              /*if (token.startsWith("G") && currKeggID != null) {
                failed--; //not parsing glycans now so ignoring those failures
              }*/
              currKeggID = null;
            }

          }
        }
        if (currKeggID == null) {
          System.out.println("KeggParser.parseReactionsDetailed failed" + currName);
        }
      }
    }
    br.close();
    System.out.format("KeggParser.parseReactionsDetailed: Num entries added %d, Failed %d\n", numEntriesAdded, failed);
  }

  public static void main(String[] args) {
    MongoDB db = new MongoDB();
    parseKegg("data/kegg/reaction.lst", "data/kegg/compound.inchi", "data/kegg/compound", "data/kegg/reaction", "data/kegg/cofactors.txt", db);
  }
}
