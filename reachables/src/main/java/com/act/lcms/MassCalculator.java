package com.act.lcms;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapted from Chris's org.twentyn.services.emerald.IonChooser#calculateMass in the Experimental project.
 *
 * For additional masses and confirmation of the values used below, see
 * http://www.sisweb.com/referenc/tools/exactmass.js (the js that drives
 * http://www.sisweb.com/referenc/tools/exactmass.htm).
 */
public class MassCalculator {
  private static final Indigo indigo = new Indigo();
  private static final IndigoInchi iinchi = new IndigoInchi(indigo);;


  public static Pair<Double, Integer> calculateMassAndCharge(String inchi) {
    IndigoObject mol = iinchi.loadMolecule(inchi);
    Double mass = calculateMass(mol);
    Integer charge = calculateCharge(mol);
    return Pair.of(mass, charge);
  }

  public static Integer calculateCharge(String inchi) {
    return calculateCharge(iinchi.loadMolecule(inchi));
  }

  public static Integer calculateCharge(IndigoObject mol) {
    int out = 0;
    for (int i =  0; i < mol.countAtoms(); i++) {
      IndigoObject atom = mol.getAtom(i);
      int charge = atom.charge();
      out += charge;
    }
    return out;
  }

  public static final Map<String, Double> ATOMIC_WEIGHTS = Collections.unmodifiableMap(new HashMap<String, Double>() {{
    put("Ag", 106.905095d);
    put("Al", 26.981541d);
    put("Ar", 39.962383d);
    put("As", 74.921596d);
    put("Au", 196.966560d);
    put("B",  11.009305d);
    put("Ba", 137.905236d);
    put("Be", 9.012183d);
    put("Bi", 208.980388d);
    put("Br", 78.918336d);
    put("C",  12.000000d);
    put("Ca", 39.962591d);
    put("Cd", 113.903361d);
    put("Ce", 139.905442d);
    put("Cl", 34.968853d);
    put("Co", 58.933198d);
    put("Cr", 51.940510d);
    put("Cs", 132.905433d);
    put("Cu", 62.929599d);
    put("Dy", 163.929183d);
    put("Er", 165.930305d);
    put("Eu", 152.921243d);
    put("F",  18.998403d);
    put("Fe", 55.934939d);
    put("Ga", 68.925581d);
    put("Gd", 157.924111d);
    put("Ge", 73.921179d);
    put("H",  1.007825d);
    put("He", 4.002603d);
    put("Hf", 179.946561d);
    put("Hg", 201.970632d);
    put("Ho", 164.930332d);
    put("I",  126.904477d);
    put("In", 114.903875d);
    put("Ir", 192.962942d);
    put("K",  38.963708d);
    put("Kr", 83.911506d);
    put("La", 138.906355d);
    put("Li", 7.016005d);
    put("Lu", 174.940785d);
    put("Mg", 23.985045d);
    put("Mn", 54.938046d);
    put("Mo", 97.905405d);
    put("N",  14.003074d);
    put("Na", 22.989770d);
    put("Nb", 92.906378d);
    put("Nd", 141.907731d);
    put("Ne", 19.992439d);
    put("Ni", 57.935347d);
    put("O",  15.994915d);
    put("Os", 191.961487d);
    put("P",  30.973763d);
    put("Pb", 207.976641d);
    put("Pd", 105.903475d);
    put("Pr", 140.907657d);
    put("Pt", 194.964785d);
    put("Rb", 84.911800d);
    put("Re", 186.955765d);
    put("Rh", 102.905503d);
    put("Ru", 101.90434d);
    put("S",  31.972072d);
    put("Sb", 120.903824d);
    put("Sc", 44.955914d);
    put("Se", 79.916521d);
    put("Si", 27.976928d);
    put("Sm", 151.919741d);
    put("Sn", 119.902199d);
    put("Sr", 87.905625d);
    put("Ta", 180.948014d);
    put("Tb", 158.925350d);
    put("Te", 129.906229d);
    put("Th", 232.038054d);
    put("Ti", 47.947947d);
    put("Tl", 204.974410d);
    put("Tm", 168.934225d);
    put("U",  238.050786d);
    put("V",  50.943963d);
    put("W",  183.950953d);
    put("Xe", 131.904148d);
    put("Y",  88.905856d);
    put("Yb", 173.938873d);
    put("Zn", 63.929145d);
    put("Zr", 89.904708d);
  }});

  public static Double calculateMass(String inchi) {
    return calculateMass(iinchi.loadMolecule(inchi));
  }

  private static final Pattern MOL_COUNT_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)?$");
  public static Double calculateMass(IndigoObject mol) {
    String formula = mol.grossFormula();
    double out = 0.0;
    String[] molCounts = StringUtils.split(formula, " ");
    for (String atomEntry : molCounts) {
      //Extract the atom count
      Matcher matcher = MOL_COUNT_PATTERN.matcher(atomEntry);
      if (!matcher.matches()) {
        throw new RuntimeException("Found unexpected malformed atomEntry: " + atomEntry);
      }
      String element = matcher.group(1);
      String countStr = matcher.group(2);

      Integer count = 1;
      if (countStr != null && !countStr.isEmpty()) {
        count = Integer.parseInt(countStr);
      }

      if (!ATOMIC_WEIGHTS.containsKey(element)) {
        throw new RuntimeException("Atomic weights table is missing an expected element: " + element);
      }

      out += ATOMIC_WEIGHTS.get(element) * count.doubleValue();
    }

    // TODO: log difference between mol.molecularWeight(), mol.monoisotopicMass(), and our value.
    return out;
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.format("Usage: %s [InChI [...]]\n", MassCalculator.class.getCanonicalName());
      return;
    }

    System.out.format("InChI\tMass\tCharge\n");
    for (String arg : args) {
      Pair<Double, Integer> massCharge = calculateMassAndCharge(arg);
      System.out.format("%s\t%.6f\t%d\n", arg, massCharge.getLeft(), massCharge.getRight());
    }
  }

}
