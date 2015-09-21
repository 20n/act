package act.server.Molecules;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;
import act.shared.helpers.T;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class BondEnergy {
  private static Indigo indigo;
  private static IndigoInchi indigoInchi;

  private static Map<Bond, Integer> table;

  private static Map<String, Integer> moleculeEnergyCache;

  //tables taken from
  //http://wiki.chemprime.chemeddl.org/images/1/1d/Average_bond_energies.jpg
  //http://staff.norman.k12.ok.us/~cyohn/index_files/ThermochemistryNotes.htm
  private static Object[][] singleBonds = {
    {"H"}, //with H
    {
      "H", 432,
      "C", 416,
      "N", 391,
      "O", 467,
      "F", 566,
      "Si", 323,
      "P", 322,
      "S", 347,
      "Cl", 431,
      "Br", 366,
      "I", 299
    },
    {"C"}, //with C
    {
      "C", 356,
      "N", 285,
      "O", 336,
      "F", 486,
      "Si", 301,
      "P", 264,
      "S", 272,
      "Cl", 327,
      "Br", 285,
      "I", 213
    },
    {"N"},
    {
      "N", 160,
      "O", 201,
      "F", 272,
      "Si", 355,
      "P", 200,
      "Cl", 205
    },
    {"O"},
    {
      "O", 146,
      "F", 190,
      "Si", 368,
      "P", 340,
      "Cl", 205,
      "I", 201
    },
    {"F"},
    {
      "F", 148,
      "Si", 582,
      "P", 490,
      "S", 326,
      "Cl", 255
    },
    {"Si"},
    {
      "Si", 226,
      "S", 226,
      "Cl", 391,
      "Br", 310,
      "I", 234
    },
    {"P"},
    {
      "P", 209,
      "Cl", 319,
      "Br", 213
    },
    {"S"},
    {
      "S", 226,
      "Cl", 255,
      "Br", 213
    },
    {"Cl"},
    {
      "Cl", 242,
      "Br", 217,
      "I", 209
    },
    {"Br"},
    {
      "Br", 193,
      "I", 180
    },
    {"I"},
    {
      "I", 151
    }
  };

  private static Object[][] doubleBonds = {
    {"C"},
    {
      "C", 598,
      "N", 616,
      "O", 695, //special case with CO2
    },
    {"N"},
    {
      "N", 418,
      "O", 607
    },
    {"O"},
    {
      "O", 498
    }
  };

  private static Object[][] tripleBonds = {
    {"C"},
    {
      "C", 813,
      "O", 1073,
      "N", 891
    },
    {"N"},
    {
      "N", 946
    }
  };

  private static void initTable() {
    table = new HashMap<Bond, Integer>();
    initBondType(BondType.Single, singleBonds);
    initBondType(BondType.Double, doubleBonds);
    initBondType(BondType.Triple, tripleBonds);
  }

  private static void initBondType(BondType type, Object[][] energies) {
    for (int i = 0; i < energies.length; i+=2) {
      Element e0 = Element.valueOf((String) energies[i][0]);
      for (int j = 0; j < energies[i+1].length; j+=2) {
        Element e1 = Element.valueOf((String) energies[i+1][j]);
        table.put(new Bond(new Atom(e0), type, new Atom(e1)),
            (Integer) energies[i+1][j+1]);
      }
    }
  }

  static {
    initTable();
    moleculeEnergyCache = new HashMap<String, Integer>();

    indigo = new Indigo();
    indigoInchi = new IndigoInchi(indigo);
  }

  /**
   * Given an molecular (as an inchi),
   * return sum of bond energies.
   * @param inchi
   * @return null if fails, otherwise the energy
   */
  public static Integer getEnergy(String inchi) {
    Integer energy = 0;
    if (!moleculeEnergyCache.containsKey(inchi)) {
      IndigoObject mol = indigoInchi.loadMolecule(inchi);
      MolGraph g = SMILES.ToGraph(mol);
      Set<T<Integer, Integer, BondType>> edges = g.ComputeEdges();

      for (T<Integer, Integer, BondType> e : edges) {
        Bond b = new Bond(g.GetNodeType(e.fst()), e.third(), g.GetNodeType(e.snd()));
        Integer bondEnergy = table.get(b);
        if  (bondEnergy == null) {
          //System.err.println("no energy found for " + b);
          return null;
        } else {
          energy += table.get(b);
        }
      }
      moleculeEnergyCache.put(inchi, energy);
    } else {
      energy = moleculeEnergyCache.get(inchi);
    }

    return energy;
  }

  /**
   *
   * @param reaction
   * @param db
   * @return null if fails, otherwise H delta
   */
  public static Integer getEnergyDelta(Reaction reaction, MongoDB db) {
    Map<String, Integer> reactantCoefficients, productCoefficients;
    reactantCoefficients = new HashMap<String, Integer>();
    productCoefficients = new HashMap<String, Integer>();
    Long[] substrates = reaction.getSubstrates();
    Long[] products = reaction.getProducts();
    boolean success = createCoefficientsMap(reaction, db, reactantCoefficients, substrates, true);
    success &= createCoefficientsMap(reaction, db, productCoefficients, products, false);

    if (!success) return null;
    return getEnergyDelta(reactantCoefficients, productCoefficients);
  }

  private static boolean createCoefficientsMap(Reaction reaction, MongoDB db,
      Map<String, Integer> coefficients, Long[] substrates, boolean isReactant) {
    for (Long s : substrates) {
      String inchi = db.getChemicalFromChemicalUUID(s).getInChI();
      Integer c = isReactant ?
          reaction.getSubstrateCoefficient(s) :
          reaction.getProductCoefficient(s);
      if (c == null) {
        //System.err.println("No coefficient for " + s + ", skipping");
        return false;
      } else {
        coefficients.put(inchi, c);
      }
    }
    return true;
  }

  /**
   * Given reactant inchis and product inchis mapped to their coefficients, calculate deltaH
   * @param reactants
   * @param products
   * @return delta H, or null if failed
   */
  public static Integer getEnergyDelta(Map<String, Integer> reactants, Map<String, Integer> products) {
    int reactantEnergy = 0;
    for (String r : reactants.keySet()) {
      Integer energy = getEnergy(r);
      if (energy == null) return null;
      reactantEnergy += energy * reactants.get(r);
    }
    int productEnergy = 0;
    for (String p : products.keySet()) {
      Integer energy = getEnergy(p);
      if (energy == null) return null;
      productEnergy += energy * products.get(p);
    }

    return productEnergy - reactantEnergy;
  }

  public static void main(String[] args) {
    MongoDB db = new MongoDB();
    List<Long> reactionIDs = db.getAllReactionUUIDs();
    int numPos = 0, numNeg = 0, numZero = 0, numFailed = 0;
    int i = 0;
    for (Long r : reactionIDs) {
      Reaction reaction = db.getReactionFromUUID(r);
      Integer diff = getEnergyDelta(reaction, db);
      if (diff == null) {
        numFailed++;
      } else if (diff > 0) {
        numPos++;
      } else if (diff < 0) {
        numNeg++;
      } else {
        numZero++;
      }
      i++;
      if (i % 1000 == 0) System.out.println("done with " + i);
    }
    System.out.printf("numPos %d, numNeg %d, numZero %d, numFailed %d",
        numPos, numNeg, numZero, numFailed);
  }
}
