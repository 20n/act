package act.server.Search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import act.server.ActAdminServiceImpl;
import act.server.EnumPath.OperatorSet;
import act.server.Molecules.CRO;
import act.server.SQLInterface.MongoDB;
import act.shared.AugmentedReactionNetwork;
import act.shared.Chemical;
import act.shared.ROApplication;
import act.shared.ReactionType;
import act.shared.helpers.P;

/**
 * Starting from native chemicals, randomly apply ROs to generate some paths.
 *
 */

public class RandomROWalk {
  private static ActAdminServiceImpl actServer;
  public static List<String> randomWalk(Set<String> startingInchis, HashMap<Integer, OperatorSet> operators, int steps) {
    List<String> walk = new ArrayList<String>();
    List<String> walkROs = new ArrayList<String>();
    List<String> inchis = new ArrayList<String>();
    inchis.addAll(startingInchis);
    String inchi = inchis.get((int) (inchis.size() * Math.random()));
    walk.add(inchi);
    Set<String> explored = new HashSet<String>();

    for (int i = 0; i < steps; i++) {
      List<ROApplication> outInchis;
      List<ROApplication> allInchis = new ArrayList<ROApplication>();
      outInchis = actServer.applyROs(inchi, operators);
      for (ROApplication ro_inchis : outInchis) {
        if (ro_inchis.type == ReactionType.CRO) {
          allInchis.add(ro_inchis);
        }
      }
      if (allInchis.size() == 0) break;

      inchi = null;
      for (ROApplication next : allInchis) {
        for (String product : next.products) {
          if(explored.contains(product)) continue;
          explored.add(product);
          walk.add(product);
          inchi = product;
          break;
        }
      }
      if (inchi == null) break;

    }
    return walk;
  }

  public static void main(String[] args) {
    actServer = new ActAdminServiceImpl(true /* dont start game server */);
    //maybe a better way to specify the path to text file...
    HashMap<Integer, OperatorSet> operators = actServer.getOperators(
        "localhost", 27017, "actv01", 100, "../Installer/data/rxns-w-good-ros.txt"); // the rxns_list_file is "data/rxns-w-good-ros.txt"

    System.out.println("printing CROs");
    for (Integer arity : operators.keySet()) {
      OperatorSet ops = operators.get(arity); // get arity == 1 operators...
      for (CRO cro : ops.getAllCROs().values())
        System.out.println("CRO " + cro.rxn());
    }

    MongoDB db = new MongoDB();
    List<Chemical> natives = db.getNativeMetaboliteChems();
    Set<String> nativeInchis = new HashSet<String>();
    Indigo indigo = new Indigo();
    IndigoInchi indigoInchi = new IndigoInchi(indigo);

    System.out.println("start natives");
    for (Chemical chemical : natives) {
      IndigoObject o = indigoInchi.loadMolecule(chemical.getInChI());
      nativeInchis.add(indigoInchi.getInchi(o));
      System.out.println(o.smiles());
    }
    System.out.println("end natives");


    for (int iter = 1; iter < 20; iter++) {
      System.out.println("Walk " + iter);
      List<String> walk = randomWalk(nativeInchis, operators, 20);
      if (walk.size() == 1) continue;
      for (String w : walk) {
        System.out.println(indigoInchi.loadMolecule(w).smiles());
      }
    }

    //List<P<Integer, List<String>>> outInchis = applyROs(inchi, operators);
  }
}
