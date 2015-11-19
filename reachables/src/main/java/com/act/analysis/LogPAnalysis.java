package com.act.analysis;


import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.marvin.plugin.PluginException;
import chemaxon.marvin.space.BoundingBox;
import chemaxon.marvin.space.MSpaceEasy;
import chemaxon.marvin.space.MolecularSurfaceComponent;
import chemaxon.marvin.space.MoleculeComponent;
import chemaxon.marvin.space.SurfaceColoring;
import chemaxon.marvin.space.SurfaceComponent;
import chemaxon.struc.DPoint3;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import com.dreizak.miniball.highdim.Miniball;
import com.dreizak.miniball.model.ArrayPointSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogPAnalysis {
  String inchi;
  logPPlugin plugin = new logPPlugin();
  Molecule mol;
  // MolAtom objects don't seem to record their index in the parent molecule, so we'll build a mapping here.
  Map<MolAtom, Integer> atomToIndexMap = new HashMap<>();

  public LogPAnalysis() { }

  public void init(String licenseFilePath, String inchi)
      throws LicenseProcessingException, MolFormatException, PluginException {
    this.inchi = inchi;
    LicenseManager.setLicenseFile(licenseFilePath);
    Molecule importMol = MolImporter.importMol(this.inchi);
    plugin.setlogPMethod(LogPMethod.CONSENSUS);

    // TODO: do we need to explicitly specify ion concentration?
    plugin.setUserTypes("logPTrue,logPMicro,logPNonionic"); // These arguments were chosen via experimentation.

    plugin.standardize(importMol);
    plugin.setMolecule(importMol);
    plugin.run();
    this.mol = plugin.getResultMolecule();
    Cleaner.clean(this.mol, 3); // This will assign 3D atom coordinates to the MolAtoms in this.mol.

    MolAtom[] molAtoms = mol.getAtomArray();
    for (int i = 0; i < molAtoms.length; i++) {
      atomToIndexMap.put(molAtoms[i], i);
    }
  }

  public Pair<Integer, Integer> findFarthestAtomPair() {

    Double maxDist = 0.0d;
    Integer di1 = null, di2 = null; // Endpoint atoms of the diameter of the structure.
    for (int i = 0; i < mol.getAtomCount(); i++) {
      for (int j = 0; j < mol.getAtomCount(); j++) {
        if (i == j) {
          continue;
        }
        if (Double.isNaN(plugin.getAtomlogPIncrement(i))) {
          continue;
        }
        if (Double.isNaN(plugin.getAtomlogPIncrement(j))) {
          continue;
        }

        MolAtom m1 = mol.getAtom(i);
        MolAtom m2 = mol.getAtom(j);

        DPoint3 c1 = m1.getLocation();
        DPoint3 c2 = m2.getLocation();

        Double dist = c1.distance(c2);
        Double angle = c1.angle3D(c2);

        if (dist > maxDist) {
          maxDist = dist;
          di1 = i;
          di2 = j;
        }

        System.out.format("%d -> %d: %s %s, %f %f, %f %f\n", i, j, m1.getSymbol(), m2.getSymbol(), dist, angle,
            plugin.getAtomlogPIncrement(i), plugin.getAtomlogPIncrement(j));
      }
    }

    return Pair.of(di1, di2);
  }

  public static void main(String[] args) throws Exception {
    LicenseManager.setLicenseFile(args[0]);
    System.out.format("plugin list: %s\n", StringUtils.join(LicenseManager.getPluginList(), ", "));
    Molecule mol = MolImporter.importMol(args[1]);
    System.out.format("Output: %s\n", MolExporter.exportToFormat(mol, "smiles"));
    System.out.format("LogPPlugin class key: %s\n", logPPlugin.PLUGIN_CLASS_KEY);
    logPPlugin plugin = new logPPlugin();
    plugin.setlogPMethod(LogPMethod.CONSENSUS);

    //plugin.setCloridIonConcentration(0.2);
    //plugin.setNaKIonConcentration(0.2);
    plugin.setUserTypes("logPTrue,logPMicro,logPNonionic");

    System.out.format("Is licensed: %s\n", LicenseManager.isLicensed(plugin.getProductName()));

    plugin.standardize(mol);
    plugin.setMolecule(mol);
    plugin.run();
    System.out.format("True logp: %f\n", plugin.getlogPTrue());
    System.out.format("Micro logp: %f\n", plugin.getlogPMicro());
    Molecule mol2 = plugin.getResultMolecule();
    Cleaner.clean(mol2, 3);
    /*
    for (int i = 0; i < molAtoms.length; i++) {
      MolAtom molAtom = molAtoms[i];
      DPoint3 coords = molAtom.getLocation();
      System.out.format("%d: %s %s %f (%f, %f, %f)\n", i, molAtom.getSymbol(), molAtom.getExtraLabel(),
          plugin.getAtomlogPIncrement(i), coords.x, coords.y, coords.z);
      logPVals.add(plugin.getAtomlogPIncrement(i));
    }
    */
    ArrayList<Double> logPVals = new ArrayList<>();
    ArrayList<Double> hValues = new ArrayList<>();
    ArrayList<Integer> ids = new ArrayList<>();
    for (int i = 0; i < mol.getAtomCount(); i++) {
      ids.add(i);
      Double logP = plugin.getAtomlogPIncrement(i);
      logPVals.add(logP);
      MolAtom molAtom = mol2.getAtom(i);
      DPoint3 coords = molAtom.getLocation();
      System.out.format("%d: %s %s %f (%f, %f, %f)\n", i, molAtom.getSymbol(), molAtom.getExtraLabel(),
          plugin.getAtomlogPIncrement(i), coords.x, coords.y, coords.z);
      for (int j = 0; j < molAtom.getImplicitHcount(); j++) {
        hValues.add(logP);
      }

      MolBond[] bonds = molAtom.getBondArray();
      for (int j = 0; j < bonds.length; j++) {
        MolBond bond = bonds[j];
        System.out.format("  %d -> %d %s %s %s\n", bond.getAtom1().getAtomMap(), bond.getAtom2().getAtomMap(), bond.getBondType(),
            molAtom.equals(bond.getAtom1()), molAtom.equals(bond.getAtom2()));
      }

    }
    //for (int i = 0; i < mol.getImplicitHcount(); i++) {
//      logPVals.add(0d);
  //  }

    ArrayPointSet aps = new ArrayPointSet(3, mol2.getAtomCount());

    Double maxDist = 0.0d;
    Integer di1 = null, di2 = null; // Endpoint atoms of the diameter of the structure.
    for (int i = 0; i < mol2.getAtomCount(); i++) {
      for (int j = 0; j < mol2.getAtomCount(); j++) {
        if (i == j) {
          continue;
        }
        if (Double.isNaN(plugin.getAtomlogPIncrement(i))) {
          continue;
        }
        if (Double.isNaN(plugin.getAtomlogPIncrement(j))) {
          continue;
        }

        MolAtom m1 = mol2.getAtom(i);
        MolAtom m2 = mol2.getAtom(j);

        DPoint3 c1 = m1.getLocation();
        DPoint3 c2 = m2.getLocation();

        Double dist = c1.distance(c2);
        Double angle = c1.angle3D(c2);

        if (dist > maxDist) {
          maxDist = dist;
          di1 = i;
          di2 = j;
        }

        System.out.format("%d -> %d: %s %s, %f %f, %f %f\n", i, j, m1.getSymbol(), m2.getSymbol(), dist, angle,
            plugin.getAtomlogPIncrement(i), plugin.getAtomlogPIncrement(j));
      }
      if (!Double.isNaN(plugin.getAtomlogPIncrement(i))) {
        DPoint3 c1 = mol2.getAtom(i).getLocation();
        aps.set(i, 0, c1.x);
        aps.set(i, 1, c1.y);
        aps.set(i, 2, c1.z);
      } else {
        System.err.format("Atom %d has a logP increment of NaN\n", i);
      }
    }

    DPoint3 newOrigin = mol2.getAtom(di1).getLocation();
    List<DPoint3> coords = new ArrayList<>();
    for (int i = 0; i < mol2.getAtomCount(); i++) {
      DPoint3 c = mol2.getAtom(i).getLocation();
      c.subtract(newOrigin);
      coords.add(c);
    }

    System.out.format("Diameter (%d -> %d) length is %f\n", di1, di2, Math.sqrt(coords.get(di2).lengthSquare()));

    Map<Integer, Pair<Double, Double>> distAndAngleToDiameter = new HashMap<>();
    Double maxDistToDiameter = 0.0, meanDistToDiameter = 0.0;
    for (int i = 0; i < mol2.getAtomCount(); i++) {
      if (i == di1 || i == di2) {
        continue;
      }

      DPoint3 origin = coords.get(di1);
      DPoint3 diameter = coords.get(di2);
      DPoint3 exp = coords.get(i);

      Double dotProduct = diameter.x * exp.x + diameter.y * exp.y + diameter.z * exp.z;
      Double lengthProduct = Math.sqrt(diameter.lengthSquare()) * Math.sqrt(exp.lengthSquare());
      Double cosine = dotProduct / lengthProduct;
      Double sine = Math.sqrt(1 - cosine * cosine);
      Double dist = sine * Math.sqrt(exp.lengthSquare());

      Double angle = Math.acos(cosine) * 180.0d / (Math.PI) ;

      distAndAngleToDiameter.put(i, Pair.of(dist, angle));

      maxDistToDiameter = Math.max(maxDistToDiameter, dist);
      meanDistToDiameter += dist;

      System.out.format(
          "Dist %d: (%f, %f, %f) -> (%f, %f, %f); point (%f, %f, %f) has dist %f, angle %f, cos %f, sin %f (%f/%f), dist %f\n",
          i, origin.x, origin.y, origin.z, diameter.x, diameter.y, diameter.z, exp.x, exp.y, exp.z, Math.sqrt(exp.lengthSquare()),
          angle, cosine, sine, dotProduct, lengthProduct, dist);
    }

    meanDistToDiameter = meanDistToDiameter / Integer.valueOf(mol2.getAtomCount() - 2).doubleValue();

    System.out.format("mean dist: %f, max dist: %f, max dist ratio: %f\n",
        meanDistToDiameter, maxDistToDiameter, maxDistToDiameter / maxDist);


    List<Double> d1NeighborhoodLogPs = getNeighborhoodLogPs(mol2, di1, plugin);
    List<Double> d2NeighborhoodLogPs = getNeighborhoodLogPs(mol2, di2, plugin);
    exploreNeighborhoodLogPValues(mol2, di1, plugin, 2);
    exploreNeighborhoodLogPValues(mol2, di2, plugin, 2);

    Double lowestLogP = 0.0, highestLogP = 0.0;
    Integer lowestValAtom = null, highestValAtom = null;
    for (int i = 0; i < mol.getAtomCount(); i++) {
      // Give a little boost to the diameter points in case they have equal logP values to other atoms.
      double diameterBoost = i == di1 || i == di2 ? 0.00001 : 0;
      if (logPVals.get(i) - diameterBoost < lowestLogP) {
        lowestLogP = logPVals.get(i);
        lowestValAtom = i;
      }
      if (logPVals.get(i) + diameterBoost > highestLogP) {
        highestLogP = logPVals.get(i);
        highestValAtom = i;
      }
    }

    System.out.format("Lowest  logP: %f at %d\n", lowestLogP, lowestValAtom);
    System.out.format("Highest logP: %f at %d\n", highestLogP, highestValAtom);

    exploreNeighborhoodLogPValues(mol2, lowestValAtom, plugin, 2);
    exploreNeighborhoodLogPValues(mol2, highestValAtom, plugin, 2);

    Double lowDistToD1 = mol2.getAtom(lowestValAtom).getLocation().distance(mol2.getAtom(di1).getLocation());
    Double lowDistToD2 = mol2.getAtom(lowestValAtom).getLocation().distance(mol2.getAtom(di2).getLocation());
    Double highDistToD1 = mol2.getAtom(highestValAtom).getLocation().distance(mol2.getAtom(di1).getLocation());
    Double highDistToD2 = mol2.getAtom(highestValAtom).getLocation().distance(mol2.getAtom(di2).getLocation());

    System.out.format("Lowest logP atom distances/ratio: %f %f %f\n", lowDistToD1, lowDistToD2, lowDistToD1 / lowDistToD2);
    System.out.format("Highest logP atom distances/ratio: %f %f %f\n", highDistToD1, highDistToD2, highDistToD1 / highDistToD2);

    Miniball mb = new Miniball(aps);
    double[] c = mb.center();
    System.out.format("Minimum bounding ball: %f %s\n", mb.radius(), mb);
    for (int i = 0; i < c.length; i++) {
      System.out.format("  %d %f\n", i, c[i]);
    }
    DPoint3 center = new DPoint3(c[0], c[1], c[2]);

    for (int i = 0; i < mol2.getAtomCount(); i++) {
      MolAtom m = mol2.getAtom(i);
      DPoint3 p = m.getLocation();
      System.out.format("%d: %f %f %f\n", i, center.distance(p), center.angle3D(p),  center.distance(p) - mb.radius());

    }

    MSpaceEasy mspace = new MSpaceEasy(1, 2, true);

    JFrame jframe = new JFrame();
    jframe.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    mspace.addCanvas(jframe.getContentPane());
    mspace.setSize(1200, 600);

    MoleculeComponent mc1 = mspace.addMoleculeTo(mol2, 0);
          mspace.getEventHandler().createAtomLabels(mc1, ids);

    //msc.setDrawProperty("MacroMolecule.Hydrogens", "false");
    mspace.setProperty("MacroMolecule.Hydrogens", "false");
    MoleculeComponent mc2 = mspace.addMoleculeTo(mol2, 1);
    MolecularSurfaceComponent msc = mspace.computeSurface(mc2);
    SurfaceComponent sc = msc.getSurface();
    System.out.format("ColorF length: %d\n", sc.getColorF().length);
    System.out.format("Vertex count: %d\n", sc.getVertexCount());
    System.out.format("Surface primitives length: %d\n", sc.getPrimitives().length);

    msc.setPalette(SurfaceColoring.COLOR_MAPPER_BLUE_TO_RED);
    //mspace.getEventHandler().createAtomLabels(mc2, logPVals);
    msc.showVolume(true);
    msc.setSurfacePrecision("High");
    msc.setSurfaceType("van der Waals");
    msc.setDrawProperty("Surface.DrawType", "Dot");
    msc.setDrawProperty("Surface.Quality", "High");
    logPVals.addAll(hValues);
    msc.setAtomPropertyList(logPVals);
    msc.setDrawProperty("Surface.ColorType", "AtomProperty");
    mc1.draw();

    jframe.pack();
    jframe.setVisible(true);

    BoundingBox bb = mc2.getBoundingBox();
    System.out.format("Bounding box: ([%f - %f], [%f - %f], [%f - %f])\n",
        bb.getMinX(), bb.getMaxX(), bb.getMinY(), bb.getMaxY(), bb.getMinZ(), bb.getMaxZ());
  }

  public static List<Double> getNeighborhoodLogPs(Molecule mol, int index, logPPlugin plugin) {
    MolAtom[] atoms = mol.getAtomArray();
    Map<MolAtom, Integer> molToIndexMap = new HashMap<>(atoms.length);
    for (int i = 0; i < atoms.length; i++) {
      molToIndexMap.put(atoms[i], i);
    }

    MolAtom d1 = mol.getAtom(index);
    MolBond[] d1bonds = d1.getBondArray();
    List<Double> neighborhoodLogPs = new ArrayList<>();
    for (MolBond b : d1bonds) {
      MolAtom src, dest;
      int which = 0;
      if (b.getAtom1().equals(d1)) {
        src = b.getAtom1();
        dest = b.getAtom2();
        which = 1;
      } else {
        src = b.getAtom2();
        dest = b.getAtom1();
        which = 2;
      }

      int srci = molToIndexMap.get(src);
      int desti = molToIndexMap.get(dest);
      System.out.format("Atom %d (%d) is requested index (%d), other is %d\n", which, srci, index, desti);
      neighborhoodLogPs.add(plugin.getAtomlogPIncrement(desti));
    }

    System.out.format("Index %d log p is %f, neighborhood log p values are: %s\n",
        index, plugin.getAtomlogPIncrement(index), StringUtils.join(neighborhoodLogPs, ", "));
    return neighborhoodLogPs;
  }

  public static Map<Integer, Integer> exploreNeighborhoodLogPValues(Molecule mol, int index, logPPlugin plugin, int depth) {
    // TODO: this is absurdly wasteful.  Generate this map once per molecule.
    MolAtom[] atoms = mol.getAtomArray();
    Map<MolAtom, Integer> atomToIndexMap = new HashMap<>(atoms.length);
    for (int i = 0; i < atoms.length; i++) {
      atomToIndexMap.put(atoms[i], i);
    }

    Map<Integer, Integer> results = exploreNeighborhoodHelper(mol, index, atomToIndexMap, depth, depth, new HashMap<>());
    System.out.format("Index %d log p is %f, neighborhood log p values are:\n", index, plugin.getAtomlogPIncrement(index));
    for (Integer i : results.keySet()) {
      System.out.format("  %d: %d %f\n", i, results.get(i), plugin.getAtomlogPIncrement(i));
    }
    return results;
  }

  private static Map<Integer, Integer> exploreNeighborhoodHelper(Molecule mol, int index,
                                                                 Map<MolAtom,Integer> atomToIndexMap, int baseDepth,
                                                                 int depth, Map<Integer, Integer> atomsAndDepths) {
    if (!atomsAndDepths.containsKey(index)) {
      atomsAndDepths.put(index, baseDepth - depth);
    }

    if (depth <= 0) {
      return atomsAndDepths;
    }

    MolAtom d1 = mol.getAtom(index);
    MolBond[] d1bonds = d1.getBondArray();
    for (MolBond b : d1bonds) {
      MolAtom dest;
      if (b.getAtom1().equals(d1)) {
        dest = b.getAtom2();
      } else {
        dest = b.getAtom1();
      }

      int desti = atomToIndexMap.get(dest);

      if (!atomsAndDepths.containsKey(desti)) {
        atomsAndDepths = exploreNeighborhoodHelper(mol, desti, atomToIndexMap, baseDepth, depth - 1, atomsAndDepths);
      }
    }
    return atomsAndDepths;
  }
}
