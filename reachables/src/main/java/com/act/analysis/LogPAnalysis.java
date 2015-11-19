package com.act.analysis;


import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.marvin.plugin.PluginException;
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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.regression.RegressionResults;
import org.apache.commons.math3.stat.regression.SimpleRegression;

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

  // Atom indices for the longest vector between any two atoms in the molecule.
  Integer lvIndex1;
  Integer lvIndex2;
  // Coordinates with lvIndex1 treated as the origin.
  List<DPoint3> normalizedCoordinates;
  Map<Integer, Double> distancesFromLongestVector = new HashMap<>();
  Map<Integer, Double> distancesAlongLongestVector = new HashMap<>();
  Map<Integer, Plane> normalPlanes = new HashMap<>();

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

  public Pair<Integer, Integer> findFarthestContributingAtomPair() {
    Double maxDist = 0.0d;
    Integer di1 = null, di2 = null; // Endpoint atoms of the diameter of the structure.
    for (int i = 0; i < mol.getAtomCount(); i++) {
      if (Double.isNaN(plugin.getAtomlogPIncrement(i))) {
        continue;
      }
      for (int j = 0; j < mol.getAtomCount(); j++) {
        if (i == j) {
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
      }
    }

    this.lvIndex1 = di1;
    this.lvIndex2 = di2;

    this.normalizedCoordinates = resetOriginForCoordinates(di1);

    return Pair.of(di1, di2);
  }

  public Pair<Pair<Integer, Double>, Pair<Integer, Double>> computeMinAndMaxLogPValAtom() {
    return computeMinAndMaxLogPValAtom(null, null);
  }

  public static final Double MIN_AND_MAX_LOG_P_LONGEST_VECTOR_BOOST = 0.00001;
  public Pair<Pair<Integer, Double>, Pair<Integer, Double>> computeMinAndMaxLogPValAtom(
      Integer vIndex1, Integer vIndex2) {
    Double lowestLogP = 0.0, highestLogP = 0.0;
    Integer lowestValAtom = null, highestValAtom = null;
    for (int i = 0; i < mol.getAtomCount(); i++) {
      // Give a little boost to the diameter points in case they have equal logP values to other atoms.
      double diameterBoost = vIndex1 != null && vIndex2 != null && (vIndex1.equals(i)|| vIndex2.equals(i)) ?
          MIN_AND_MAX_LOG_P_LONGEST_VECTOR_BOOST : 0;
      double logPIncrement = plugin.getAtomlogPIncrement(i);
      if (logPIncrement - diameterBoost < lowestLogP) {
        lowestLogP = logPIncrement;
        lowestValAtom = i;
      }
      if (logPIncrement + diameterBoost > highestLogP) {
        highestLogP = logPIncrement;
        highestValAtom = i;
      }
    }
    return Pair.of(Pair.of(lowestValAtom, lowestLogP), Pair.of(highestValAtom, highestLogP));
  }

  public List<DPoint3> resetOriginForCoordinates(Integer newOriginIndex) {
    DPoint3 newOrigin = mol.getAtom(newOriginIndex).getLocation();
    List<DPoint3> coords = new ArrayList<>();
    for (int i = 0; i < mol.getAtomCount(); i++) {
      DPoint3 c = mol.getAtom(i).getLocation();
      c.subtract(newOrigin);
      coords.add(c);
    }
    return coords;
  }

  public static class Plane {
    public double a;
    public double b;
    public double c;
    public double d;

    public Plane(double a, double b, double c, double d) {
      this.a = a;
      this.b = b;
      this.c = c;
      this.d = d;
    }

    public double computeProductForPoint(double x, double y, double z) {
      return a * x + b * y + c * z + d;
    }
  }



  public Pair<Map<Integer, Double>, Map<Integer, Plane>> computeAtomDistanceToLongestVectorAndNormalPlanes() {
    List<DPoint3> coords = this.normalizedCoordinates;
    for (int i = 0; i < mol.getAtomCount(); i++) {
      if (i == lvIndex1 || i == lvIndex2) {
        continue;
      }

      DPoint3 diameter = coords.get(lvIndex2);
      DPoint3 exp = coords.get(i);

      Double dotProduct = diameter.x * exp.x + diameter.y * exp.y + diameter.z * exp.z;
      Double lengthProduct = Math.sqrt(diameter.lengthSquare()) * Math.sqrt(exp.lengthSquare());
      Double cosine = dotProduct / lengthProduct;
      Double sine = Math.sqrt(1 - cosine * cosine);
      Double dist = sine * Math.sqrt(exp.lengthSquare());

      Double vLength = Math.sqrt(exp.lengthSquare());
      Double proj = cosine * vLength;

      distancesFromLongestVector.put(i, dist);
      distancesAlongLongestVector.put(i, cosine * vLength);
      System.out.format("  Point %d, dist from vector is %f, along vector is %f, length is %f, diff is %f\n",
          i, dist, proj, vLength, vLength * vLength - proj * proj - dist * dist);
      normalPlanes.put(i, new Plane(diameter.x, diameter.y, diameter.z, -1d * dotProduct));
    }

    distancesFromLongestVector.put(lvIndex1, 0.0);
    distancesFromLongestVector.put(lvIndex2, 0.0);

    distancesAlongLongestVector.put(lvIndex1, 0.0);
    distancesAlongLongestVector.put(lvIndex2, Math.sqrt(coords.get(lvIndex2).lengthSquare()));

    return Pair.of(distancesFromLongestVector, normalPlanes);
  }

  public Map<Integer, Pair<List<Integer>, List<Integer>>> splitAtomsByNormalPlanes(Map<Integer, Plane> planes) {
    List<DPoint3> coords = resetOriginForCoordinates(lvIndex1);
    Map<Integer, Pair<List<Integer>, List<Integer>>> results = new HashMap<>();

    for (int i = 0; i < mol.getAtomCount(); i++) {
      Plane p = planes.get(i);
      if (p == null) {
        continue;
      }

      List<Integer> negSide = new ArrayList<>();
      List<Integer> posSide = new ArrayList<>();

      System.out.format("-- Plane %d\n", i);
      for (int j = 0; j < mol.getAtomCount(); j++) {
        if (i == j) {
          continue;
        }
        DPoint3 c = coords.get(j);
        double prod = p.computeProductForPoint(c.x, c.y, c.z);
        System.out.format("Product for plane %d at %d: %f\n", i, j, prod);

        // It seems unlikely that an atom would be coplanar to the dividing atom, but who knows.  Throw it in pos if so.
        if (prod < 0.0000d) {
          negSide.add(j);
        } else {
          posSide.add(i);
        }
      }
      results.put(i, Pair.of(negSide, posSide));
    }

    return results;
  }

  public Pair<DPoint3, Double> computeMinimumBoundingBall(List<DPoint3> coords) {
    ArrayPointSet aps = new ArrayPointSet(3, coords.size());
    for (int i = 0; i < coords.size(); i++) {
      DPoint3 c = coords.get(i);
      aps.set(i, 0, c.x);
      aps.set(i, 1, c.y);
      aps.set(i, 2, c.z);
    }

    Miniball mb = new Miniball(aps);
    double[] c = mb.center();
    DPoint3 center = new DPoint3(c[0], c[1], c[2]);
    return Pair.of(center, mb.radius());
  }

  public Pair<DPoint3, Double> computeMinimumBoundingBallForContributingAtoms() {
    MolAtom[] atoms = mol.getAtomArray();
    List<DPoint3> coords = new ArrayList<>(atoms.length);
    for (int i = 0; i < atoms.length; i++) {
      // Ignore atoms that don't contribute to the logP value (i.e. have a NaN LogP value).
      if (Double.isNaN(plugin.getAtomlogPIncrement(i))) {
        continue;
      }
      coords.add(atoms[i].getLocation());
    }
    return computeMinimumBoundingBall(coords);
  }

  public Map<Integer, Integer> exploreNeighborhood(int index, int depth) {
    return exploreNeighborhoodHelper(index, depth, depth, new HashMap<>());
  }

  // Recursively walk the atom's neighborhood.
  private Map<Integer, Integer> exploreNeighborhoodHelper(int index, int baseDepth, int depth,
                                                          Map<Integer, Integer> atomsAndDepths) {
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
        atomsAndDepths = exploreNeighborhoodHelper(desti,baseDepth, depth - 1, atomsAndDepths);
      }
    }
    return atomsAndDepths;
  }

  public Double performRegressionOverLVProjectionOfLogP() {
    SimpleRegression regression = new SimpleRegression();
    for (int i = 0; i < mol.getAtomCount(); i++) {
      Double x = distancesAlongLongestVector.get(i);
      Double y = plugin.getAtomlogPIncrement(i);
      System.out.format("Adding point %d to regression: %f %f\n", i, x, y);
      regression.addData(x, y);
    }
    RegressionResults result = regression.regress();
    System.out.format("Regression r squared: %f, MSE = %f\n", result.getRSquared(), result.getMeanSquareError());
    return regression.getSlope();
  }

  public void renderMolecule(JFrame jFrame, boolean hydrogensShareNeighborsLogP) throws Exception {
    // TODO: use the proper marvin sketch scene to get better rendering control instead of MSpaceEasy.
    MSpaceEasy mspace = new MSpaceEasy(1, 2, true);
    mspace.addCanvas(jFrame.getContentPane());
    mspace.setSize(1200, 600);

    ArrayList<Double> logPVals = new ArrayList<>();
    ArrayList<Double> hValues = new ArrayList<>();
    // Store a list of ids so we can label the
    ArrayList<Integer> ids = new ArrayList<>();
    for (int i = 0; i < mol.getAtomCount(); i++) {
      ids.add(i);
      Double logP = plugin.getAtomlogPIncrement(i);
      logPVals.add(logP);

      /* The surface renderer requires that we specify logP values for all hydrogens, which don't appear to have logP
       * contributions calculated for them, in addition to non-hydrogen atoms.  We fake this by either borrowing the
       * hydrogen's neighbor's logP value, or setting it to 0.0.
       * TODO: figure out what the command-line marvin sketch logP renderer does and do that instead.
       * */
      MolAtom molAtom = mol.getAtom(i);
      for (int j = 0; j < molAtom.getImplicitHcount(); j++) {
        hValues.add(hydrogensShareNeighborsLogP ? logP : 0.0);
      }
    }
    /* Tack the hydrogen's logP contributions on to the list of proper logP values.  The MSC renderer seems to expect
     * the hydrogen's values after the non-hydrogen's values, so appending appears to work fine. */
    logPVals.addAll(hValues);

    MoleculeComponent mc1 = mspace.addMoleculeTo(mol, 0);
    mspace.getEventHandler().createAtomLabels(mc1, ids);

    // Don't draw hydrogens; it makes the drawing too noisy.
    mspace.setProperty("MacroMolecule.Hydrogens", "false");
    MoleculeComponent mc2 = mspace.addMoleculeTo(mol, 1);
    MolecularSurfaceComponent msc = mspace.computeSurface(mc2);
    SurfaceComponent sc = msc.getSurface();

    msc.setPalette(SurfaceColoring.COLOR_MAPPER_BLUE_TO_RED);
    msc.showVolume(true);
    // These parameters were selected via experimentation.
    msc.setSurfacePrecision("High");
    msc.setSurfaceType("van der Waals");
    msc.setDrawProperty("Surface.DrawType", "Dot");
    msc.setDrawProperty("Surface.Quality", "High");
    msc.setAtomPropertyList(logPVals);
    msc.setDrawProperty("Surface.ColorType", "AtomProperty");
    mc1.draw();

    jFrame.pack();
    jFrame.setVisible(true);
  }


  public String getInchi() {
    return inchi;
  }

  public logPPlugin getPlugin() {
    return plugin;
  }

  public Molecule getMol() {
    return mol;
  }

  public Map<MolAtom, Integer> getAtomToIndexMap() {
    return atomToIndexMap;
  }

  public Integer getLvIndex1() {
    return lvIndex1;
  }

  public Integer getLvIndex2() {
    return lvIndex2;
  }

  public List<DPoint3> getNormalizedCoordinates() {
    return normalizedCoordinates;
  }

  public static void main(String[] args) throws Exception {
    LogPAnalysis logPAnalysis = new LogPAnalysis();
    logPAnalysis.init(args[0], args[1]);

    Pair<Integer, Integer> farthestAtoms = logPAnalysis.findFarthestContributingAtomPair();
    System.out.format("Farthest atoms are %d and %d\n", farthestAtoms.getLeft(), farthestAtoms.getRight());
    Pair<Map<Integer, Double> , Map<Integer, Plane>> results =
        logPAnalysis.computeAtomDistanceToLongestVectorAndNormalPlanes();
    Map<Integer, Double> distancesToLongestVector = results.getLeft();
    for (Map.Entry<Integer, Double> e : distancesToLongestVector.entrySet()) {
      System.out.format("  dist to longest vector: %d %f\n", e.getKey(), e.getValue());
    }

    Map<Integer, Plane> cuttingPlanes = results.getRight();
    for (int i = 0; i < logPAnalysis.getMol().getAtomCount(); i++) {
      Plane plane = cuttingPlanes.get(i);
      if (plane == null) {
        continue;
      }
      if (i == logPAnalysis.getLvIndex1() || i == logPAnalysis.getLvIndex2()) {
        continue;
      }

      System.out.format("--Atom %d, plane %f x + %f y + %f z + %f\n", i, plane.a, plane.b, plane.c, plane.d);
      for (int j = 0; j < logPAnalysis.getMol().getAtomCount(); j++) {
        DPoint3 coord = logPAnalysis.getNormalizedCoordinates().get(j);
        Double prod = plane.computeProductForPoint(coord.x, coord.y, coord.z);
        System.out.format("  plane %d coord %d (%f, %f, %f): %f %f\n", i, j, coord.x, coord.y, coord.z,
            prod, logPAnalysis.getPlugin().getAtomlogPIncrement(j));
      }
    }

    Double slope = logPAnalysis.performRegressionOverLVProjectionOfLogP();
    System.out.format("Regression slope: %f\n", slope);

    JFrame jFrame = new JFrame();
    jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    logPAnalysis.renderMolecule(jFrame, true);
  }
}
