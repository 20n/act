package act.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import act.render.RenderReactions;

import com.ggasoftware.indigo.IndigoException;

import act.server.Molecules.GCMEnergy;
import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;


public class EstimateEnergies {
  public static void printReactionStatistics(MongoDB db) {
    List<P<Long, Double>> sortedReactions = sortReactionsByEnergies(db, -1);
    int size = sortedReactions.size();
    int tenthPercent = size/1000;

    int count = 0;
    double sum = 0;
    for (P<Long, Double> r : sortedReactions) {
      if (count < tenthPercent) {
        System.out.println(r.fst() + " " + r.snd());
      } else if (count > (size - tenthPercent)) {
        System.out.println(r.fst() + " " + r.snd());
      }
      sum += r.snd();
      count++;
    }
    System.out.println("Average: " + sum/size);
    P<Long, Double> temp = sortedReactions.get(0);
    System.out.println("0th percentile: " + temp);
    renderReactionEnergy(db, temp.fst());
    temp = sortedReactions.get(size/10);
    System.out.println("10th percentile: " + temp);
    renderReactionEnergy(db, temp.fst());
    temp = sortedReactions.get(size/4);
    System.out.println("25th percentile: " + temp);
    renderReactionEnergy(db, temp.fst());
    temp = sortedReactions.get(size/2);
    System.out.println("50th percentile: " + temp);
    renderReactionEnergy(db, temp.fst());
    temp = sortedReactions.get(size - size/4);
    System.out.println("75th percentile: " + temp);
    renderReactionEnergy(db, temp.fst());
    temp = sortedReactions.get(size - size/10);
    System.out.println("90th percentile: " + temp);
    renderReactionEnergy(db, temp.fst());
    temp = sortedReactions.get(size - 1);
    System.out.println("100th percentile: " + temp);
    renderReactionEnergy(db, temp.fst());
  }
  public static void renderReactionEnergy(MongoDB db, long id) {
    renderReactionEnergy(db, id, "estimateEnergies.png");
  }

  public static void renderReactionEnergy(MongoDB db, long id, String filename) {
    Reaction r = db.getReactionFromUUID(id);
    RenderReactions.renderByRxnID(
        id,
        filename,
        r.getReactionName() + " " + r.getEstimatedEnergy() + " kcal",
        db);
  }

  public static List<P<Long, Double>> sortReactionsByEnergies(MongoDB db, Integer reversibility) {
    List<P<Long, Double>> retval = new ArrayList<P<Long, Double>>();
    List<Long> reactionIDs = db.getAllReactionUUIDs();
    for (Long reactionID : reactionIDs) {
      Reaction reaction = db.getReactionFromUUID(reactionID);
      Double e = reaction.getEstimatedEnergy();
      if (e == null) continue;
      if (reversibility == null || reaction.isReversible() == reversibility) {
        retval.add(new P<Long, Double>(reactionID, e));
      }
    }

    class EnergyReactionComparator implements Comparator<P<Long, Double>> {

      @Override
      public int compare(P<Long, Double> arg0, P<Long, Double> arg1) {
        return arg0.snd() < arg1.snd() ? -1 :
          arg0.snd() == arg1.snd() ? 0 : 1;
      }

    }
    Collections.sort(retval, new EnergyReactionComparator());
    return retval;
  }

  public static void estimateForChemicals(MongoDB db) {
    int success = 0, count = 0, noSmiles = 0, indigoExceptions = 0;
    long timeTaken = 0;
    long start = System.currentTimeMillis();

    DBIterator it = db.getIteratorOverChemicals();
    Chemical cur = db.getNextChemical(it);
    List<Long> chemicalIDs = new ArrayList<Long>();
    while(cur != null) {
      chemicalIDs.add(cur.getUuid());
      cur = db.getNextChemical(it);
    }
    Collections.sort(chemicalIDs);

    for (Long id : chemicalIDs) {
      cur = db.getChemicalFromChemicalUUID(id);
      //if (id < 30000) continue;
      String smiles = cur.getSmiles();
      if (smiles != null) {
        try {
          Double energy = GCMEnergy.calculate(smiles);
          if (energy != null) {
            success++;
            cur.setEstimatedEnergy(energy);
          }
          //System.out.println(smiles + " " + energy);
        } catch (IndigoException e) {
          indigoExceptions++;
          //e.printStackTrace();
        }
      } else {
        noSmiles++;
      }
      count++;
      if (count % 1000 == 0) {
        timeTaken = System.currentTimeMillis() - start;
        System.out.println("Success: " + success);
        System.out.println("No SMILES: " + noSmiles);
        System.out.println("Indigo Exceptions" + indigoExceptions);
        System.out.println("Total: " + count);
        System.out.println("Time taken: " + (timeTaken/1000));
      }

      db.updateEstimatedEnergy(cur);
      //if (count > 5000) break;
    }
    System.out.println("Success: " + success);
    System.out.println("No SMILES: " + noSmiles);
    System.out.println("Indigo Exceptions" + indigoExceptions);
    System.out.println("Total: " + count);
    System.out.println("Time taken: " + (timeTaken/1000));
  }

  public static void estimateForReactions(MongoDB db) {
    int success = 0, missingCoeff = 0, count = 0;

    DBIterator it = db.getIteratorOverReactions(true);
    Reaction reaction = db.getNextReaction(it);
    while (reaction != null) {
      boolean failed = false;
      double total = 0;
      if (reaction.getProductsWCoefficients().size() == 0) {
        missingCoeff++;
        failed = true;
      }
      if (!failed) {
        for (Long p : reaction.getProductsWCoefficients()) {
          Chemical product = db.getChemicalFromChemicalUUID(p);
          Double e = product.getEstimatedEnergy();
          Integer coeff = reaction.getProductCoefficient(p);
          if (e == null) {
            failed = true;
            break;
          }

          total += e * coeff;
        }

        for (Long r : reaction.getSubstratesWCoefficients()) {
          Chemical reactant = db.getChemicalFromChemicalUUID(r);
          Double e = reactant.getEstimatedEnergy();
          Integer coeff = reaction.getSubstrateCoefficient(r);
          if (e == null) {
            failed = true;
            break;
          }

          total -= e * coeff;
        }
      }

      if (!failed) {
        //System.out.println(total + " " + 0);
        success++;
        reaction.setEstimatedEnergy(total);
      } else {
        reaction.setEstimatedEnergy(null);
      }
      count++;
      db.updateEstimatedEnergy(reaction);
      reaction = db.getNextReaction(it);
    }

    System.out.println("Success: " + success);
    System.out.println("Missing Coefficients: " + missingCoeff);
    System.out.println("Total: " + count);
  }

  public static void main(String[] args) {
    MongoDB db = new MongoDB();
    //estimateForChemicals(db);
    estimateForReactions(db);
    printReactionStatistics(db);
  }

}
