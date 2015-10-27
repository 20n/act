package com.act.lcms.db;

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
    put("C",  12.000000d);
    put("H",  1.007825d);
    put("N",  14.003074d);
    put("O",  15.994915d);
    put("P",  30.973763d);
    put("S",  31.972072d);
    put("I",  126.904477d);
    put("Cl", 34.968853d);
    put("Br", 78.918336d);
    put("Fe", 55.934939d);
    put("Hg", 201.970632d);
    put("Na", 22.989770d);
    put("Se", 79.916521d);
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
