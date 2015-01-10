package act.server.Molecules;

import java.util.ArrayList;
import java.util.List;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import act.client.CommandLineRun;

public class RxnTxCmdLine {
  
  /* 
   * Wrapper to call the right RxnTx function
   * Input: RO_String Substrate_SMILES_1 Substrate_SMILES_2 ...
   *
   * Before calling the RxnTx function converts input:
   * Converts all substrates to InChIs
   * Converts RO to DotNotation
   */
  public static List<List<String>> expand(List<String> args) {
    String dotNotationRO = args.get(0);
    List<String> substrate_smiles = args.subList(1, args.size());
    List<String> substrates = smiles2inchi(substrate_smiles);
    
    List<List<String>> prd_sets = RxnTx.expandChemical2AllProductsNormalMol(
                                    substrates, dotNotationRO
                                  );

    List<List<String>> smiles = null;
    if (prd_sets != null) {
      smiles = new ArrayList<List<String>>();
      for (List<String> prds : prd_sets) 
          smiles.add(inchi2smiles(prds));
    }

    return smiles;
  }

  /*
   * Smiles -> InChI
   * Helper to above; and can also be directly called from the cmdline
   */
  public static List<String> smiles2inchi(List<String> args) {
    Indigo indigo = new Indigo();
    IndigoInchi inchi = new IndigoInchi(indigo);
    List<String> inchis = new ArrayList<String>();
    for (String smiles : args)
      inchis.add(inchi.getInchi(indigo.loadMolecule(smiles)));
    return inchis;
  }

  /*
   * Inchi -> SMILES
   * Helper to above; and can also be directly called from the cmdline
   */
  public static List<String> inchi2smiles(List<String> args) {
    Indigo indigo = new Indigo();
    IndigoInchi inchi = new IndigoInchi(indigo);
    List<String> smiles = new ArrayList<String>();
    for (String inc : args)
      smiles.add(inchi.loadMolecule(inc).smiles());
    return smiles;
  }

  /*
   * SMILES in normal notation -> SMILES in dot notation
   */
  public static List<String> toDotNotation(List<String> args) {
    Indigo indigo = new Indigo();
    List<String> dots = new ArrayList<String>();
    for (String sml : args) {
      IndigoObject normalMol = indigo.loadMolecule(sml);
      IndigoObject dotMol = DotNotation.ToDotNotationMol(normalMol);
      dots.add(dotMol.smiles());
    }
    return dots;
  }

  /*
   * SMILES in dot notation -> SMILES in normal notation
   */
  public static List<String> toNormalNotation(List<String> args) {
    Indigo indigo = new Indigo();
    List<String> norms = new ArrayList<String>();
    for (String sml : args) {
      IndigoObject dotMol = indigo.loadMolecule(sml);
      String normalMol = DotNotation.ToNormalMol(dotMol, indigo);
      norms.add(normalMol);
    }
    return norms;
  }

  /*
   * renders molecules or reactions
   * given in either SMILES or InChI for molecules
   * and as SMILES with >> for reactions
   * returns filenames to which the output went
   */
  public static List<String> render(List<String> args) {
    List<String> fnames = new ArrayList<String>();
    int cnt = 1;
    for (String sml : args) {
      String fname = "render_" + (cnt++) + ".png";
      CommandLineRun.render(sml, fname, "Rendered from: " + sml);
      fnames.add(fname);
    }
    return fnames;
  }

  /* 
   * Function to take commands from cmdline
   * EXPAND ro substrate1 substrate2 ...
   * SMILES2INCHI smiles1 smiles2 ...
   * INCHI2SMILES inchi1 inchi2 ...
   */
  public static void main(String[] args) {
    String cmd = args[0];

    List<String> params = new ArrayList<String>();
    for (int i=1; i<args.length; i++)
      params.add(args[i]);

    if (cmd.equals("EXPAND")) {
      List<List<String>> prd_sets = expand(params);
      if (prd_sets == null) {
        System.out.println("No products: RO not applicable.");
      } else {
        for (List<String> prds : prd_sets) {
          for (String product_inchi : prds) {
            System.out.print(product_inchi + " ");
          }
          System.out.println();
        }
      }
    } else if (cmd.equals("SMILES2INCHI")) {
      List<String> inchis = smiles2inchi(params);
      for (String i : inchis)
        System.out.print(i + " ");
      System.out.println();
    } else if (cmd.equals("INCHI2SMILES")) {
      List<String> smiles = inchi2smiles(params);
      for (String i : smiles)
        System.out.print(i + " ");
      System.out.println();
    } else if (cmd.equals("FROMDOTNOTATION")) {
      List<String> toNorm = toNormalNotation(params);
      for (String s : toNorm)
        System.out.print(s + " ");
      System.out.println();
    } else if (cmd.equals("TODOTNOTATION")) {
      List<String> toDot = toDotNotation(params);
      for (String s : toDot)
        System.out.print(s + " ");
      System.out.println();
    } else if (cmd.equals("RENDER")) {
      List<String> files = render(params);
      System.out.print("Rendered to: ");
      for (String f : files)
        System.out.print(f + " ");
      System.out.println();
    } else {
      System.out.println("Unsupported operation: " + cmd);
    }
  }

}
