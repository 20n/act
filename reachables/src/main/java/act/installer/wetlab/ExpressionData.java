package act.installer.wetlab;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import act.server.Logger;
import act.server.SQLInterface.MongoDB;
import act.server.Search.ConfidenceMetric;
import act.server.Search.InitialSetGenerator;
import act.server.Search.PathBFS;
import act.server.Search.ReactionsHypergraph;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;
import au.com.bytecode.opencsv.CSVReader;

public class ExpressionData {

  static public void compareWithExisting(String filename, Integer cutoff) throws IOException {
    MongoDB db = new MongoDB();

    Map<P<String, String>, Boolean> cutoffSet = parse(filename, cutoff);

      System.out.println("Cutoff set size " + cutoffSet.size());
      int truePositive = 0, falseNegative = 0, falsePositive = 0, trueNegative = 0;
      for (P<String, String> orgEcnum : cutoffSet.keySet()) {
        Set<Reaction> reactions = getReactions(db, orgEcnum);
        if (reactions.isEmpty()) {
          //System.err.println("Cannot find org " + orgEcnum);
          continue;
        }

        String org = orgEcnum.fst();
        Long nativeOrgID = db.getOrganismId(org);
        if (nativeOrgID == -1) {
          String[] temp = org.split("\\s");
          org = temp[0] + " " + temp[1];
        }
        nativeOrgID = db.getOrganismId(org);
        //System.out.println(nativeOrgID);
        if (nativeOrgID == -1L) System.err.println("can't find " + org);
        for (Reaction reaction : reactions) {
          boolean express = false;
          boolean expressAny = false;

          int count = ConfidenceMetric.expressionCount(reaction, nativeOrgID);
          boolean isNative = ConfidenceMetric.isNativeEnzyme(reaction);

          if (count > 0 || (isNative && 562L == nativeOrgID)) {
            express = true;
          }

          int countAnyExpress = ConfidenceMetric.expressionCount(reaction);
          if (countAnyExpress > 0 || isNative) {
            //some enzyme for reaction expresses,
            //not necessarily enzyme from wetlab
            expressAny = true;
          }
          System.out.print(orgEcnum);
          System.out.print("\t" + cutoffSet.get(orgEcnum) + "\t" + express + "\t" + expressAny + "\t");
          System.out.println(reaction.getReactionName());


          if (express && cutoffSet.get(orgEcnum)) truePositive++;
          else if (express) falseNegative++;
          else if (cutoffSet.get(orgEcnum)) falsePositive++;
          else trueNegative++;
        }
        //System.out.println();
        //System.out.println(reactions.size());
        //System.out.println(orgEcnum);
        //else System.out.println(orgEcnum);
      }
      //ConfidenceMetric.expressionCount(reaction);

      System.out.println("TP " + truePositive);
      System.out.println("FP " + falsePositive);
      System.out.println("TN " + trueNegative);
      System.out.println("FN " + falseNegative);
  }

  private static Set<Reaction> getReactions(MongoDB db,
      P<String, String> orgEcnum) {
    Long orgId = db.getOrganismId(orgEcnum.fst());
    Map<String, Object> query = new HashMap();
    query.put("ecnum", orgEcnum.snd());
    if (orgId == null || orgId < 0) {
      query.put("easy_desc", Pattern.compile("^.*" + orgEcnum.fst() + ".*$"));
    } else {
      query.put("organisms.id", orgId);
    }

    Set<Reaction> reactions = db.getReactionsConstrained(query);
    return reactions;
  }

  private static Map<P<String, String>, Boolean> parse(String filename, Integer cutoff)
      throws FileNotFoundException, IOException {
    CSVReader reader = new CSVReader(new FileReader(filename));
    reader.readNext(); //remove header
    Map<P<String, String>, Boolean> cutoffSet = new HashMap<P<String, String>, Boolean>();
      String [] nextLine;
      while ((nextLine = reader.readNext()) != null) {
        String json = nextLine[2];
        DBObject obj = (DBObject) JSON.parse(json);
        System.out.println(obj.get("ecnum"));
        if (cutoff == null || nextLine[cutoff + 2].equals("True")) {
          cutoffSet.put(new P(obj.get("org"), obj.get("ecnum")), true);
        } else {
          cutoffSet.put(new P(obj.get("org"), obj.get("ecnum")), false);
        }
      }
      reader.close();
    return cutoffSet;
  }

  //TODO: fix parameters
  public static void assignConfidence(MongoDB db) throws FileNotFoundException, IOException {
    Map<P<String, String>, Boolean> cutoffSet = parse("data/wetlab/expression.csv", 1);
    for (P<String, String> orgEcnum : cutoffSet.keySet()) {
      Set<Reaction> reactions = getReactions(db, orgEcnum);
      for (Reaction reaction : reactions) {
        ConfidenceMetric.setInconfidence(new Long(reaction.getUUID()), 0);
      }
    }
  }

  //TODO: fix parameters
  static public void compareReachables(MongoDB db) throws FileNotFoundException, IOException {
    Logger.setMaxImpToShow(-1);
    Set<Long> natives = new HashSet<Long>();
    natives.addAll(InitialSetGenerator.natives(db));

    ConfidenceMetric.hardConstraints.add("expression");
    //ConfidenceMetric.hardConstraints.add("invalid chemical");
    Map<P<String, String>, Boolean> cutoffSet = parse("data/wetlab/expression.csv", 1);
    Set<Long> reactionWithWetlabExpression = new HashSet<Long>();
    Set<Long> productsOfNew = new HashSet<Long>();
    for (P<String, String> orgEcnum : cutoffSet.keySet()) {
      Set<Reaction> reactions = getReactions(db, orgEcnum);
      for (Reaction reaction : reactions) {
        if (ConfidenceMetric.expressionScore(reaction) < 1) { //ConfidenceMetric.inconfidence(reaction, db) == 1000) {
          reactionWithWetlabExpression.add(new Long(reaction.getUUID()));
          if (ConfidenceMetric.isBalanced(reaction)) {
            System.out.println("NEW_REACTION: " + reaction.getUUID() + "|" + reaction.getReactionName());
          } else {
            System.out.println(reaction.getSubstratesWCoefficients());
            System.out.println("NOT_BALANCED: " + reaction.getUUID() + "|" + reaction.getReactionName());
          }
            /*for (Long p : reaction.getProducts()) {
            if (!natives.contains(p)) {
              productsOfNew.add(p);
            }
          }*/
        }
      }
    }

    //ConfidenceMetric.hardConstraints.add("invalid chemical");
    ConfidenceMetric.hardConstraints.add("balance");
    ConfidenceMetric.hardConstraints.add("reversibility");
    Set<Long> restrictedSet = ConfidenceMetric.getLegalReactionIDs(db);
      System.out.println("OLD: Number of reactions to be used: " + restrictedSet.size());
      PathBFS loader = new PathBFS(db, natives);
      loader.setRestrictedReactions(restrictedSet);
      loader.setChemicalsToAvoid(productsOfNew);
      loader.setReverse(true);
      loader.initTree();
      ReactionsHypergraph<Long, Long> g = loader.getGraph();
      Set<Long> oldReachables = g.getChemicals();
      System.out.println("OLD: " + oldReachables.size());

      restrictedSet.addAll(reactionWithWetlabExpression);
      System.out.println("Size of wetlab reactions with expression: " + reactionWithWetlabExpression.size());
      System.out.println("NEW: Number of reactions to be used: " + restrictedSet.size());
      loader = new PathBFS(db, natives);
      loader.setRestrictedReactions(restrictedSet);
      loader.setReverse(true);
      loader.initTree();
      g = loader.getGraph();

      Set<Long> reachedReactionIDs = g.getReactions();
      for (Long r : reactionWithWetlabExpression) {
        if (reachedReactionIDs.contains(r)) {
          Reaction reaction = db.getReactionFromUUID(r);
          System.out.println("reached " + r + " " + reaction.getReactionName());
        }
      }
      /*for (Long r : reactionWithWetlabExpression) {
        Reaction reaction = db.getReactionFromUUID(r);
        g.addReaction(r, reaction.getSubstrates(), reaction.getProducts());
      }
      g = g.reachableGraph();
      g.setIdTypeDB_ID();*/
      Set<Long> newReachables = g.getChemicals();
      System.out.println("NEW: " + newReachables.size());

      newReachables.removeAll(oldReachables);
      for (Long c : newReachables) {
        Chemical chemical = db.getChemicalFromChemicalUUID(c);
        System.out.println(c + " " + chemical.getFirstName());
      }
  }

  static public void main(String[] args) throws IOException {
    //compareReachables(new MongoDB());
    compareWithExisting("data/wetlab/expression.csv", 4);
  }
}
