package act.shared;

import act.server.Molecules.SMILES;
import java.util.List;
import java.util.ArrayList;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class ConsistentInChI {

  public static String consistentInChI(String in, String debug_tag) {
    // This function has been disabled while we investigate some InChI data bugs.
    return in;
    /*
    String target = in;

    if (in.startsWith("InChI=")) {
      // load the molecule and then dump out the inchi
      // a round trip ensures that we will have it consistent
      // with the rest of the system's inchis
      target = removeProtonation(in);
    }

    String target_rt = null;

    // Do a round trip through Indigo to canonicalize edge
    // case inchis that we might get into the system
    Indigo indigo = new Indigo();
    IndigoInchi inchi = new IndigoInchi(indigo);

    try {
      IndigoObject o;

      if (target.startsWith("InChI="))
        o = inchi.loadMolecule(target);
      else
        o = indigo.loadMolecule(target);

      // * Current installer/integrator does not clear off
      // * stereo centers. That is left to downstream stages
      // * that will encode more the biochemical insights
      // * So DO NOT call clearAllStereo here.
      // clearAllStereoNotUsed(o);

      target_rt = inchi.getInchi(o);
    } catch (Exception e) {
      System.err.println("consistentInChI failed [" + debug_tag + "]: " + target);
      target_rt = target;
    }

    // We now forcefully remove the /p
    // Sometimes the +ve charge is legit, e.g., N+
    // (N in a hetero ring w/ 4 bonds)
    // E.g., InChI=1S/C10H12N4O4S/c15-1-4-6(16)7(17)10(18-4)14-3-13-9(19)5-8(14)12-2-11-5/h2-4,6-7,10,15-17H,1H2,(H,11,12,19)/p+1/t4-,6-,7-,10-/m1/s1
    // Even though legit, we want the DB to be completely
    // devoid of /p's so remove but report
    String outr = removeProtonation(target_rt);

    // send note to output for cases where forcable removal as required
    if (!outr.equals(target_rt))
      System.err.println("consistentInChI valid charge forcibly removed [" + debug_tag + "]: Data was:" + in + " Indigo RT: " + target_rt + " Final: " + outr);

    return outr;
    */
  }

  /*
  public static String removeProtonation(String inchi) {
    // do not remove the proton when the inchi is a single proton!
    if (inchi.equals("InChI=1S/p+1") || inchi.equals("InChI=1/p+1"))
      return inchi;

    return inchi.replaceAll("/p[\\-+]\\d+", "");
  }

  private static void clearAllStereoNotUsed(IndigoObject mol) {
    // the following three remove all stereo chemical
    // descriptors in the molecules.
    // That is good when we are calculating abstractions
    // of the molecule, but not when we are integrating
    // into the DB. we should keep the inchi as specified
    // in the reaction and the chemical's entry.

    // from the API:
    // clearStereocenters resets the chiral configurations of a molecule's atoms
    // clearAlleneCenters resets the chiral configurations of a molecule's allene-like fragments
    // clearCisTrans resets the cis-trans configurations of a molecule's bonds

    mol.clearAlleneCenters();
    mol.clearCisTrans();
    mol.clearStereocenters();
  }

  */

  private static String getSMILESFromInchiRxn(String inchiRxn, Indigo indigo) {
    // this is our internal hack when we want to use InChI's for substrates/products
    // the reaction is represented as s_inchi0(#)s_inchi1(#)>>(#)p_inchi0
    IndigoInchi inchi = new IndigoInchi(indigo);
    List<String> smiles = new ArrayList<String>();
    int end;
    int divide = 0;
    while ((end = inchiRxn.indexOf("(#)")) != -1) {
      String token = inchiRxn.substring(0,end);
      if (token.equals(">>")) {
        divide = smiles.size(); // remember which index the >> is at
        smiles.add(">>");
      } else {
        System.err.println("Converting to smiles: " + token);
        smiles.add(inchi.loadMolecule(token).canonicalSmiles());
      }
      inchiRxn = inchiRxn.substring(end + 3);
    }
    // add the last token to the end....
    smiles.add(inchi.loadMolecule(inchiRxn).canonicalSmiles());
    String smile = "";
    for (int i = 0; i < smiles.size(); i++) {
      smile += smiles.get(i);
      if (i != divide - 1 && i != divide)
        smile += ".";
    }
    return smile;
  }

  public static void render(String target, String fname, String comment) {
    Indigo indigo = new Indigo();
    IndigoObject o;
    // System.out.println("Indigo version " + indigo.version());
    // System.out.println("Rendering: " + target);
    if (target.contains("*") || target.contains("R")) {
      if (target.contains(">>")) {
        if (target.startsWith("InChI="))
          target = getSMILESFromInchiRxn(target, indigo);
        SMILES.renderReaction(o = indigo.loadQueryReaction(target), fname, comment, indigo);
      } else {
        SMILES.renderReaction(o = indigo.loadQueryMolecule(target), fname, comment, indigo);
      }
    } else {
      if (target.contains(">>")) {
        if (target.startsWith("InChI="))
          target = getSMILESFromInchiRxn(target, indigo);
        SMILES.renderReaction(o = indigo.loadReaction(target), fname, comment, indigo);
      } else {
        if (!target.startsWith("InChI="))
          SMILES.renderReaction(o = indigo.loadMolecule(target), fname, comment, indigo);
        else {
          IndigoInchi inchi = new IndigoInchi(indigo);
          SMILES.renderReaction(o = inchi.loadMolecule(target), fname, comment, indigo);
        }
      }
    }
    // System.out.println("Rendered: " + o.smiles());
  }
}
