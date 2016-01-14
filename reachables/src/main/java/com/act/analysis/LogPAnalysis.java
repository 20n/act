package com.act.analysis;


import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.MajorMicrospeciesPlugin;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.regression.RegressionResults;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogPAnalysis {
  String inchi;
  logPPlugin plugin = new logPPlugin();
  MajorMicrospeciesPlugin microspeciesPlugin = new MajorMicrospeciesPlugin();

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
      throws LicenseProcessingException, MolFormatException, PluginException, IOException {
    this.inchi = inchi;
    LicenseManager.setLicenseFile(licenseFilePath);
    Molecule importMol = MolImporter.importMol(this.inchi);
    Cleaner.clean(importMol, 3); // This will assign 3D atom coordinates to the MolAtoms in this.mol.
    plugin.standardize(importMol);

    microspeciesPlugin.setpH(1.5);
    microspeciesPlugin.setMolecule(importMol);
    microspeciesPlugin.run();
    Molecule phMol = microspeciesPlugin.getMajorMicrospecies();
    System.out.format("PH adjusted mol: %s\n", MolExporter.exportToFormat(phMol, "inchi"));

    plugin.setlogPMethod(LogPMethod.CONSENSUS);

    // TODO: do we need to explicitly specify ion concentration?
    plugin.setUserTypes("logPTrue,logPMicro,logPNonionic"); // These arguments were chosen via experimentation.

    plugin.setMolecule(phMol);
    plugin.run();
    this.mol = plugin.getResultMolecule();

    System.out.format("Post-plugin mol: %s\n", MolExporter.exportToFormat(this.mol, "inchi"));

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

  public Double computeDistance(Integer a1, Integer a2) {
    return this.normalizedCoordinates.get(a1).distance(this.normalizedCoordinates.get(a2));
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

  public Map<Integer, Pair<List<Integer>, List<Integer>>> splitAtomsByNormalPlanes() {
    List<DPoint3> coords = resetOriginForCoordinates(lvIndex1);
    Map<Integer, Pair<List<Integer>, List<Integer>>> results = new HashMap<>();

    for (int i = 0; i < mol.getAtomCount(); i++) {
      Plane p = normalPlanes.get(i);
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
          posSide.add(j);
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

  public Pair<Double, Double> performRegressionOverXYPairs(List<Pair<Double, Double>> vals) {
    SimpleRegression regression = new SimpleRegression(true);
    for (Pair<Double, Double> v : vals) {
      System.out.format("Adding regression point: %f, %f\n", v.getLeft(), v.getRight());
      regression.addData(v.getLeft(), v.getRight());
    }
    RegressionResults result = regression.regress();
    System.out.format("Regression r squared: %f, MSE = %f\n", result.getRSquared(), result.getMeanSquareError());
    return Pair.of(regression.getSlope(), regression.getIntercept());
  }

  public static class PlaneSplit {
    Set<Integer> leftIndices;
    Set<Integer> rightIndices;

    double leftSum = 0.0, rightSum = 0.0;
    double weightedLeftSum = 0.0, weightedRightSum = 0.0;
    int leftPosCount = 0, leftNegCount = 0;
    int rightPosCount = 0, rightNegCount = 0;
    double leftMin = 0.0, leftMax = 0.0;
    double rightMin = 0.0, rightMax = 0.0;

    protected PlaneSplit() { }

    public PlaneSplit(PlaneSplit toCopy) {
      this.leftIndices = new HashSet<>(toCopy.leftIndices);
      this.rightIndices = new HashSet<>(toCopy.rightIndices);
      this.leftSum = toCopy.leftSum;
      this.rightSum = toCopy.rightSum;
      this.weightedLeftSum = toCopy.weightedLeftSum;
      this.weightedRightSum = toCopy.weightedRightSum;
      this.leftPosCount = toCopy.leftPosCount;
      this.leftNegCount = toCopy.leftNegCount;
      this.rightPosCount = toCopy.rightPosCount;
      this.rightNegCount = toCopy.rightNegCount;
      this.leftMin = toCopy.leftMin;
      this.leftMax = toCopy.leftMax;
      this.rightMin = toCopy.rightMin;
      this.rightMax = toCopy.rightMax;
      assert(this.equals(toCopy));
    }

    public Set<Integer> getLeftIndices() {
      return leftIndices;
    }

    public Set<Integer> getRightIndices() {
      return rightIndices;
    }

    public double getLeftSum() {
      return leftSum;
    }

    public double getRightSum() {
      return rightSum;
    }

    public double getWeightedLeftSum() {
      return weightedLeftSum;
    }

    public double getWeightedRightSum() {
      return weightedRightSum;
    }

    public int getLeftPosCount() {
      return leftPosCount;
    }

    public int getLeftNegCount() {
      return leftNegCount;
    }

    public int getRightPosCount() {
      return rightPosCount;
    }

    public int getRightNegCount() {
      return rightNegCount;
    }

    public double getLeftMin() {
      return leftMin;
    }

    public double getLeftMax() {
      return leftMax;
    }

    public double getRightMin() {
      return rightMin;
    }

    public double getRightMax() {
      return rightMax;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PlaneSplit that = (PlaneSplit) o;

      if (Double.compare(that.leftSum, leftSum) != 0) return false;
      if (Double.compare(that.rightSum, rightSum) != 0) return false;
      if (Double.compare(that.weightedLeftSum, weightedLeftSum) != 0) return false;
      if (Double.compare(that.weightedRightSum, weightedRightSum) != 0) return false;
      if (leftPosCount != that.leftPosCount) return false;
      if (leftNegCount != that.leftNegCount) return false;
      if (rightPosCount != that.rightPosCount) return false;
      if (rightNegCount != that.rightNegCount) return false;
      if (Double.compare(that.leftMin, leftMin) != 0) return false;
      if (Double.compare(that.leftMax, leftMax) != 0) return false;
      if (Double.compare(that.rightMin, rightMin) != 0) return false;
      if (Double.compare(that.rightMax, rightMax) != 0) return false;
      if (leftIndices != null ? !leftIndices.equals(that.leftIndices) : that.leftIndices != null) return false;
      return !(rightIndices != null ? !rightIndices.equals(that.rightIndices) : that.rightIndices != null);

    }

    @Override
    public int hashCode() {
      int result;
      long temp;
      result = leftIndices != null ? leftIndices.hashCode() : 0;
      result = 31 * result + (rightIndices != null ? rightIndices.hashCode() : 0);
      temp = Double.doubleToLongBits(leftSum);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      temp = Double.doubleToLongBits(rightSum);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      temp = Double.doubleToLongBits(weightedLeftSum);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      temp = Double.doubleToLongBits(weightedRightSum);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      result = 31 * result + leftPosCount;
      result = 31 * result + leftNegCount;
      result = 31 * result + rightPosCount;
      result = 31 * result + rightNegCount;
      temp = Double.doubleToLongBits(leftMin);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      temp = Double.doubleToLongBits(leftMax);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      temp = Double.doubleToLongBits(rightMin);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      temp = Double.doubleToLongBits(rightMax);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      return result;
    }

    public static Pair<PlaneSplit, PlaneSplit> computePlaneSplitsForIntersectingAtom(
        Collection<Integer> leftAtoms, Collection<Integer> rightAtoms,
        Integer planeIntersectingAtom, logPPlugin plugin,
        Map<Integer, Integer> surfaceComponentCounts) {

      /* Compute variants of the split with the current atom on either side of its plane.
       *
       * This isn't the most clever way to compute the optimal planes, since adjacent split points will end up creating
       * one redundant plane with each computation (if a and b are adjacent, a to the left of the border and b to the
       * right create the same split sets).  However, it's easy to reason about the correctness of this approach, so
       * we'll just live with the inefficiency for now.
       *
       * TODO: extend this to handle an arbitrary set of co-planar points that define the split, and apply the same
       * technique to all triples of atoms.
       * */

      PlaneSplit ps = new PlaneSplit();
      ps.leftIndices = new HashSet<>(leftAtoms);
      ps.rightIndices = new HashSet<>(rightAtoms);

      for (Integer a : leftAtoms) {
        if (planeIntersectingAtom.equals(a)) {
          // Skip the split point, though it could contribute to the "best" solution.
          continue;
        }
        Double logP = plugin.getAtomlogPIncrement(a);
        Double weightedLogP = logP * surfaceComponentCounts.get(a).doubleValue();
        ps.leftSum += logP;
        ps.weightedLeftSum += weightedLogP;

        if (logP < ps.leftMin) {
          ps.leftMin = logP;
        }
        if (logP > ps.leftMax) {
          ps.leftMax = logP;
        }

        if (logP >= 0.000) {
          ps.leftPosCount++;
        } else {
          ps.leftNegCount++;
        }
        System.out.format("  L %d: %f, %f\n", a, logP, weightedLogP);
      }
      for (Integer a : rightAtoms) {
        if (planeIntersectingAtom.equals(a)) {
          continue;
        }
        Double logP = plugin.getAtomlogPIncrement(a);
        Double weightedLogP = logP * surfaceComponentCounts.get(a);
        ps.rightSum += logP;
        ps.weightedRightSum += weightedLogP;

        if (logP < ps.rightMin) {
          ps.rightMin = logP;
        }
        if (logP > ps.rightMax) {
          ps.rightMax = logP;
        }

        if (logP >= 0.000) {
          ps.rightPosCount++;
        } else {
          ps.rightNegCount++;
        }
        System.out.format("  R %d: %f, %f\n", a, logP, weightedLogP);
      }

      // Create variants with this point added to left and right sides of split plane.
      PlaneSplit leftVariant = ps;
      PlaneSplit rightVariant = new PlaneSplit(ps);
      Double logP = plugin.getAtomlogPIncrement(planeIntersectingAtom);
      Double weightedLogP = logP * surfaceComponentCounts.get(planeIntersectingAtom).doubleValue();

      leftVariant.leftIndices.add(planeIntersectingAtom);
      leftVariant.leftSum += logP;
      leftVariant.weightedLeftSum += weightedLogP;
      if (logP >= 0.000) {
        leftVariant.leftPosCount++;
      } else {
        leftVariant.leftNegCount++;
      }
      if (logP < leftVariant.leftMin) {
        leftVariant.leftMin = logP;
      }
      if (logP > leftVariant.leftMax) {
        leftVariant.leftMax = logP;
      }


      rightVariant.rightIndices.add(planeIntersectingAtom);
      rightVariant.rightSum += logP;
      rightVariant.weightedRightSum += weightedLogP;
      if (logP >= 0.000) {
        rightVariant.rightPosCount++;
      } else {
        rightVariant.rightNegCount++;
      }
      if (logP < rightVariant.rightMin) {
        rightVariant.rightMin = logP;
      }
      if (logP > rightVariant.rightMax) {
        rightVariant.rightMax = logP;
      }

      return Pair.of(leftVariant, rightVariant);
    }
  }

  public Pair<PlaneSplit, Map<String, Double>> findBestPlaneSplitFeatures(List<PlaneSplit> planeSplits) {
    double bestWeightedLogPDiff = 0.0;
    PlaneSplit bestPlaneSplit = null;
    Map<String, Double> features = null;
    for (PlaneSplit ps : planeSplits) {
      double absLogPDiff = Math.abs(ps.getLeftSum() - ps.getRightSum());
      double absLogPSignDiff = ps.getLeftSum() * ps.getRightSum() < 0.000 ? 1.0 : 0.0;
      double absLogPMinMaxDiff = Math.max(
          ps.getLeftMax()  - ps.getRightMin(),
          ps.getRightMax() - ps.getLeftMin());
      double weightedLogPDiff = Math.abs(ps.getWeightedLeftSum() - ps.getWeightedRightSum());
      double weightedLogPSignDiff = ps.getWeightedLeftSum() * ps.getWeightedRightSum() < 0.000 ? 1.0 : 0.0;
      int leftSize = ps.getLeftIndices().size();
      int rightSize = ps.getRightIndices().size();
      double lrSetSizeDiffRatio = Math.abs(Integer.valueOf(leftSize - rightSize).doubleValue() /
          Integer.valueOf(leftSize + rightSize).doubleValue());
      double sizeWeightedLeftSum = ps.getLeftSum() / Integer.valueOf(Math.max(leftSize, 1)).doubleValue();
      double sizeWeightedRightSum = ps.getRightSum() / Integer.valueOf(Math.max(rightSize, 1)).doubleValue();
      double sizeWeightedLeftWeightedSum = ps.getWeightedLeftSum() / Integer.valueOf(Math.max(leftSize, 1)).doubleValue();
      double sizeWeightedRightWeightedSum = ps.getWeightedRightSum() / Integer.valueOf(Math.max(rightSize, 1)).doubleValue();
      double lrPosNegCountRatio1 = Integer.valueOf(ps.getLeftNegCount()).doubleValue() /
          Integer.valueOf(Math.max(ps.getRightPosCount(), 1)).doubleValue();
      double lrPosNegCountRatio2 = Integer.valueOf(ps.getRightNegCount()).doubleValue() /
          Integer.valueOf(Math.max(ps.getLeftPosCount(), 1)).doubleValue();
      System.out.format("Plane split %s / %s:\n",
          StringUtils.join(ps.getLeftIndices(), ","), StringUtils.join(ps.getRightIndices(), ","));
      System.out.format("  abs/weighted diff: %f   %f\n", absLogPDiff, weightedLogPDiff);
      System.out.format("  set size ratio: %f\n", lrSetSizeDiffRatio);
      System.out.format("  size weighted sums: %f %f / %f %f\n",
          sizeWeightedLeftSum, sizeWeightedLeftWeightedSum, sizeWeightedRightSum, sizeWeightedRightWeightedSum);
      System.out.format("  LR +/- ratios: %f %f\n", lrPosNegCountRatio1, lrPosNegCountRatio2);
      System.out.format("  Mean logP: %f %f\n",
          ps.getLeftSum() / Integer.valueOf(Math.max(leftSize, 1)).doubleValue(),
          ps.getRightSum() / Integer.valueOf(Math.max(rightSize, 1)).doubleValue());
      if (weightedLogPDiff > bestWeightedLogPDiff) {
        bestWeightedLogPDiff = weightedLogPDiff;
        bestPlaneSplit = ps;

        features = new HashMap<String, Double>(){{
          put("ps_left_mean_logP", ps.getLeftSum() / Integer.valueOf(Math.max(leftSize, 1)).doubleValue());
          put("ps_right_mean_logP", ps.getRightSum() / Integer.valueOf(Math.max(rightSize, 1)).doubleValue());
          put("ps_lr_size_diff_ratio", lrSetSizeDiffRatio);
          put("ps_lr_pos_neg_ratio_1", lrPosNegCountRatio1);
          put("ps_lr_pos_neg_ratio_2", lrPosNegCountRatio2);
          put("ps_abs_logP_diff", absLogPDiff);
          put("ps_abs_logP_signs_differ", absLogPSignDiff);
          put("ps_sw_logP_signs_differ", weightedLogPSignDiff);
          put("ps_max_abs_diff", absLogPMinMaxDiff);
          // TODO: add surface-contribution-based metrics as well.
        }};
      }
    }
    return Pair.of(bestPlaneSplit, features);
  }

  public Map<String, Double> computeSurfaceFeatures(JFrame jFrame, boolean hydrogensShareNeighborsLogP) throws Exception {
    // TODO: use the proper marvin sketch scene to get better rendering control instead of MSpaceEasy.
    MSpaceEasy mspace = new MSpaceEasy(1, 2, true);
    mspace.addCanvas(jFrame.getContentPane());
    mspace.setSize(1200, 600);

    ArrayList<Double> logPVals = new ArrayList<>();
    ArrayList<Double> hValues = new ArrayList<>();
    // Store a list of ids so we can label the
    ArrayList<Integer> ids = new ArrayList<>();
    MolAtom[] atoms = mol.getAtomArray();
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

    // Compute the planes before rendering to avoid the addition of implicit hydrogens in the calculation.
    // TODO: re-strip hydrogens after rendering to avoid these weird issues in general.
    Map<Integer, Pair<List<Integer>, List<Integer>>> splitPlanes = splitAtomsByNormalPlanes();

    System.out.format("mol count before adding to mspace: %d\n", mol.getAtomCount());
    MoleculeComponent mc1 = mspace.addMoleculeTo(mol, 0);
    System.out.format("mol count after adding to mspace but before adding labels: %d\n", mol.getAtomCount());
    mspace.getEventHandler().createAtomLabels(mc1, ids);
    System.out.format("mol count after adding to mspace and labels: %d\n", mol.getAtomCount());

    // Don't draw hydrogens; it makes the drawing too noisy.
    mspace.setProperty("MacroMolecule.Hydrogens", "false");
    MoleculeComponent mc2 = mspace.addMoleculeTo(mol, 1);
    System.out.format("mol count before computing surface: %d\n", mol.getAtomCount());
    MolecularSurfaceComponent msc = mspace.computeSurface(mc2);
    System.out.format("mol count after computing surface: %d\n", mol.getAtomCount());
    SurfaceComponent sc = msc.getSurface();

    // Note: if we call mol.getAtomArray() here, it will contain all the implicit hydrogens.
    Map<Integer, Integer> surfaceComponentCounts = new HashMap<>();
    for (int i = 0; i < atoms.length; i++) {
      surfaceComponentCounts.put(i, 0);
    }
    for (int i = 0; i < sc.getVertexCount(); i++) {
      DPoint3 c = new DPoint3(sc.getVertexX(i), sc.getVertexY(i), sc.getVertexZ(i));
      Double closestDist = null;
      Integer closestAtom = null;
      for (int j = 0; j < atoms.length; j++) {
        double dist = c.distance(atoms[j].getLocation());
        if (closestDist == null || closestDist > dist) {
          closestDist = dist;
          closestAtom = j;
        }
      }
      surfaceComponentCounts.put(closestAtom, surfaceComponentCounts.get(closestAtom) + 1);
    }

    Map<String, Double> features = new HashMap<>();

    List<Pair<Double, Double>> weightedVals = new ArrayList<>();
    for (int i = 0; i < atoms.length; i++) {
      Integer count = surfaceComponentCounts.get(i);
      Double logP = plugin.getAtomlogPIncrement(i);
      System.out.format("Closest surface component counts for %d (%s): %d * %f = %f\n",
          i, atoms[i].getSymbol(), count, logP, count.doubleValue() * logP);
      Double x = distancesAlongLongestVector.get(i);
      Double y = count.doubleValue() * logP;
      // Ditch non-contributing atoms.
      if (y < -0.001 || y > 0.001) {
        weightedVals.add(Pair.of(x, y));
      }
    }
    Collections.sort(weightedVals);

    Pair<Double, Double> slopeIntercept = performRegressionOverXYPairs(weightedVals);
    double valAtFarthestPoint =
        distancesAlongLongestVector.get(lvIndex2) * slopeIntercept.getLeft() + slopeIntercept.getRight();
    System.out.format("Weighted slope/intercept: %f %f\n", slopeIntercept.getLeft(), slopeIntercept.getRight());
    System.out.format("Val at farthest point: %f %f\n",
        distancesAlongLongestVector.get(lvIndex2), valAtFarthestPoint);

    features.put("reg_w_slope", slopeIntercept.getLeft());
    features.put("reg_w_intercept", slopeIntercept.getRight());
    features.put("reg_val_at_farthest_point", valAtFarthestPoint);
    /* Multiply the intercept with the value at the largest point to see if there's a sign change.  If so, we'll
     * get a negative number and know the regression line crosses the axis. */
    features.put("reg_crosses_x_axis", valAtFarthestPoint * slopeIntercept.getRight() < 0.000 ? 1.0 : 0.0);

    List<PlaneSplit> allSplitPlanes = new ArrayList<>();
    for (int i = 0; i < atoms.length; i++) {
      if (!splitPlanes.containsKey(i)) {
        continue;
      }
      Pair<List<Integer>, List<Integer>> splitAtoms = splitPlanes.get(i);
      List<Integer> leftAtoms = splitAtoms.getLeft();
      List<Integer> rightAtoms = splitAtoms.getRight();
      Pair<PlaneSplit, PlaneSplit> splitVariants = PlaneSplit.computePlaneSplitsForIntersectingAtom(
          leftAtoms, rightAtoms, i, plugin, surfaceComponentCounts
      );

      PlaneSplit l = splitVariants.getLeft();
      PlaneSplit r = splitVariants.getRight();
      System.out.format("results for %d in Left:  L %f %f %d %d  R %f %f %d %d\n", i,
          l.leftSum, l.weightedLeftSum, l.leftPosCount, l.leftNegCount,
          l.rightSum, l.weightedRightSum, l.rightPosCount, l.rightNegCount);
      System.out.format("results for %d in Right: L %f %f %d %d  R %f %f %d %d\n", i,
          r.leftSum, r.weightedLeftSum, r.leftPosCount, r.leftNegCount,
          r.rightSum, r.weightedRightSum, r.rightPosCount, r.rightNegCount);
      allSplitPlanes.add(l);
      allSplitPlanes.add(r);
    }
    Pair<PlaneSplit, Map<String, Double>> bestPsRes = findBestPlaneSplitFeatures(allSplitPlanes);
    features.putAll(bestPsRes.getRight());

    msc.setPalette(SurfaceColoring.COLOR_MAPPER_BLUE_TO_RED);
    msc.showVolume(true);
    // These parameters were selected via experimentation.
    msc.setSurfacePrecision("High");
    msc.setSurfaceType("van der Waals");
    msc.setDrawProperty("Surface.DrawType", "Dot");
    msc.setDrawProperty("Surface.Quality", "High");
    msc.setAtomPropertyList(logPVals);
    msc.setDrawProperty("Surface.ColorType", "AtomProperty");

    //jFrame.pack();
    //jFrame.setVisible(true);
    return features;
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

  public MajorMicrospeciesPlugin getMicrospeciesPlugin() {
    return microspeciesPlugin;
  }

  public Map<Integer, Double> getDistancesFromLongestVector() {
    return distancesFromLongestVector;
  }

  public Map<Integer, Double> getDistancesAlongLongestVector() {
    return distancesAlongLongestVector;
  }

  public Map<Integer, Plane> getNormalPlanes() {
    return normalPlanes;
  }

  // TODO: add neighborhood exploration features around min/max logP values and farthest molecules.
  // TODO: add greedy high/low logP neighborhood picking, compute bounding balls, and calc intersection (spherical cap)

  public static void performAnalysis(String licensePath, String inchi) throws Exception {
    LogPAnalysis logPAnalysis = new LogPAnalysis();
    logPAnalysis.init(licensePath, inchi);

    Pair<Integer, Integer> farthestAtoms = logPAnalysis.findFarthestContributingAtomPair();
    System.out.format("Farthest atoms are %d and %d\n", farthestAtoms.getLeft(), farthestAtoms.getRight());
    Double longestVectorLength = logPAnalysis.computeDistance(farthestAtoms.getLeft(), farthestAtoms.getRight());
    Pair<Map<Integer, Double> , Map<Integer, Plane>> results =
        logPAnalysis.computeAtomDistanceToLongestVectorAndNormalPlanes();
    double maxDistToLongestVector = 0.0;
    Map<Integer, Double> distancesToLongestVector = results.getLeft();
    for (Map.Entry<Integer, Double> e : distancesToLongestVector.entrySet()) {
      maxDistToLongestVector = Math.max(maxDistToLongestVector, e.getValue());
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
    Map<String, Double> features = logPAnalysis.computeSurfaceFeatures(jFrame, true);

    features.put("geo_lv_fd_ratio", maxDistToLongestVector / longestVectorLength);

    List<String> sortedFeatures = new ArrayList<String>(features.keySet());
    Collections.sort(sortedFeatures);
    System.out.format("features:\n");
    for (String f : sortedFeatures) {
      System.out.format("  %s = %f\n", f, features.get(f));
    }
  }

  public static void main (String[] args) throws Exception {
    performAnalysis(args[0], args[1]);
  }
}
