package act.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Parameters.AnalyticsScripts;
import act.shared.Reaction;
import act.shared.ReactionDetailed;
import act.shared.ReactionWithAnalytics;

public class ActAnalytics {

  MongoDB DB;

  public ActAnalytics(MongoDB mongoDB) {
    this.DB = mongoDB;
  }

  public List<ReactionDetailed> executeScript(AnalyticsScripts script) {
    // run system command: ssh hz ". .profile; remoterun CmpdsMain.scala"
    // String command[] = new String[] { "ssh", "hz", "\". .profile; remoterun " + script + "\"" };
    // there is a problem yet with passing an "argumented string" to the command. So we use the hardcoded:
    String command[] = new String[] { "ssh", "hz", "./shellremoterun", script.name() + ".scala" };

    Process p;
    List<String> metadata = new ArrayList<String>();
    HashMap<Integer, List<String[]>> reactionStrings = new HashMap<Integer, List<String[]>>();
    try {
      System.out.println("Executing:" + Arrays.toString(command));
      p = Runtime.getRuntime().exec(command);
        BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        boolean reactionOutput = false;
        while((line=reader.readLine())!=null) {
          System.out.println(line);
          if (line.startsWith("-------")) {
            reactionOutput = true;
            continue;
          }
          if (line.startsWith("#")) {
            if (reactionOutput)
              metadata.add(line);
            continue;
          }
          if (reactionOutput) {
            String[] split = line.split("\t");
            Integer id = Integer.parseInt(split[0]);
            if (!reactionStrings.containsKey(id))
              reactionStrings.put(id, new ArrayList<String[]>());
            reactionStrings.get(id).add(split);
          }
        }
        int exitVal = p.waitFor();
        System.out.println("Script exited with value: " + exitVal);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    List<ReactionDetailed> outReactions = new ArrayList<ReactionDetailed>();

    switch (script) {
    case CmpdsMain:
      for (Integer i : reactionStrings.keySet()) {
        outReactions.add(toReactionDetailed(i, reactionStrings.get(i)));
      }
      break;

    case AssignRarity:
      for (Integer i : reactionStrings.keySet()) {
        updateActWithRarityProbability(i, reactionStrings.get(i));
      }
      break;
    }

    return outReactions;
  }

  private ReactionDetailed toReactionDetailed(int uuid, List<String[]> rxns) {
    List<Long> substr, prod;
    String ec, desc;

    // token 0: uuid
    // token 1: ec
    // token 2: desc
    // token 4: chemId
    // token 3: substrate(0) or product(1)
    // token 5,6: frequency/mins_frequency
    ec = rxns.get(0)[1];
    desc = rxns.get(0)[2];

    substr = new ArrayList<Long>();
    prod = new ArrayList<Long>();
    for (String[] rxn : rxns) {
      int sORp = Integer.parseInt(rxn[3]);
      if (sORp == 0)
        substr.add(Long.parseLong(rxn[4]));
      else
        prod.add(Long.parseLong(rxn[4]));
    }

    Reaction r = new Reaction(uuid, substr.toArray(new Long[0]), prod.toArray(new Long[0]), ec, desc);
    return convertToDetailedReaction(r);
  }

  private void updateActWithRarityProbability(int uuid, List<String[]> rxns) {
    List<Long> substr, prod;
    List<Float> substrProb, prodProb;
    String ec, desc;

    // token 0: uuid
    // token 1: ec
    // token 2: desc
    // token 4: chemId
    // token 3: substrate(0) or product(1)
    // token 5: probability of being a rare chemical
    ec = rxns.get(0)[1];
    desc = rxns.get(0)[2];

    substr = new ArrayList<Long>();
    prod = new ArrayList<Long>();
    substrProb = new ArrayList<Float>();
    prodProb = new ArrayList<Float>();
    for (String[] rxn : rxns) {
      int sORp = Integer.parseInt(rxn[3]);
      if (sORp == 0) {
        substr.add(Long.parseLong(rxn[4]));
        substrProb.add(Float.parseFloat(rxn[5]));
      } else {
        prod.add(Long.parseLong(rxn[4]));
        prodProb.add(Float.parseFloat(rxn[5]));
      }
    }

    Reaction r = new Reaction(uuid, substr.toArray(new Long[0]), prod.toArray(new Long[0]), ec, desc);
    // make sure that when we retrieve the reaction from the DB it is equal to this....
    Reaction rInDB = this.DB.getReactionFromUUID((long)uuid);

    if (!r.equals(rInDB)) {
      // same uuid but different reactions...
      System.out.println("Same reaction UUID but different reactions: \n" + r +"\n----\n" + rInDB  +"\n----\n");
      System.exit(-1);
    }

    //add the reaction with analytics.
    ReactionWithAnalytics rExtra = new ReactionWithAnalytics(r, substrProb.toArray(new Float[0]), prodProb.toArray(new Float[0]));
    this.DB.updateReaction(rExtra);
  }

  // helper function to convert compressed Reaction into more verbose ReactionDetailed for client consumption
  private ReactionDetailed convertToDetailedReaction(Reaction r) {
    int slen, plen;
    Chemical[] sC = new Chemical[slen = r.getSubstrates().length];
    String[] sU = new String[slen];
    Chemical[] pC = new Chemical[plen = r.getProducts().length];
    String[] pU = new String[plen];

    for (int i = 0; i< slen; i++) {
      sC[i] = this.DB.getChemicalFromChemicalUUID(r.getSubstrates()[i]);
      sU[i] = "http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + sC[i].getPubchemID();
    }
    for (int i = 0; i< plen; i++) {
      pC[i] = this.DB.getChemicalFromChemicalUUID(r.getProducts()[i]);
      pU[i] = "http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + pC[i].getPubchemID();
    }


    ReactionDetailed detail = new ReactionDetailed(r, sC, sU, pC, pU);

    return detail;
  }


}
