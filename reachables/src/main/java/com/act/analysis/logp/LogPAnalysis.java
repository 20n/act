package com.act.analysis.logp;


import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.marvin.calculations.HlbPlugin;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.MajorMicrospeciesPlugin;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.marvin.calculations.pKaPlugin;
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
import com.chemaxon.calculations.solubility.SolubilityCalculator;
import com.chemaxon.calculations.solubility.SolubilityResult;
import com.chemaxon.calculations.solubility.SolubilityUnit;
import com.dreizak.miniball.highdim.Miniball;
import com.dreizak.miniball.model.ArrayPointSet;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.regression.RegressionResults;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  // Atoms with max/min logP values.
  Integer maxLogPIndex;
  Integer minLogPIndex;

  public enum FEATURES {
    // Whole-molecule features
    LOGP_TRUE,

    //TODO:
    //LOGD_7_4,
    //LOGD_2_5,
    //LOGD_RATIO,

    // Plane split features
    PS_LEFT_MEAN_LOGP,
    PS_RIGHT_MEAN_LOGP,
    PS_LR_SIZE_DIFF_RATIO,
    PS_LR_POS_NEG_RATIO_1, // Left neg / right pos
    PS_LR_POS_NEG_RATIO_2, // Right neg / left pos
    PS_ABS_LOGP_DIFF,
    PS_ABS_LOGP_SIGNS_DIFFER,
    PS_WEIGHTED_LOGP_DIFF,
    PS_WEIGHTED_LOGP_SIGNS_DIFFER,
    PS_MAX_ABS_DIFF, // This should be equivalent to the old split metric from the DARPA report (I hope).
    PS_LEFT_POS_NEG_RATIO,
    PS_RIGHT_POS_NEG_RATIO,

    // Regression features
    REG_WEIGHTED_SLOPE,
    REG_WEIGHTED_INTERCEPT,
    REG_VAL_AT_FARTHEST_POINT,
    REG_CROSSES_X_AXIS,
    REG_ABS_SLOPE,

    // Geometric features,
    GEO_LV_FD_RATIO,

    // Extreme neighborhood features
    NBH_MAX_AND_MIN_TOGETHER,
    NBH_MAX_IN_V1,
    NBH_MAX_IN_V2,
    NBH_MIN_IN_V1,
    NBH_MIN_IN_V2,
    NBH_MAX_N_MEAN,
    NBH_MIN_N_MEAN,
    NBH_MAX_POS_RATIO,
    NBH_MIN_NEG_RATIO,

    // Solubility features
    SOL_MG_ML_25,
    SOL_MG_ML_30,
    SOL_MG_ML_35,

    // pKa features
    PKA_ACID_1, PKA_ACID_1_IDX,
    PKA_ACID_2, PKA_ACID_2_IDX,
    PKA_ACID_3, PKA_ACID_3_IDX,
    PKA_BASE_1, PKA_BASE_1_IDX,
    PKA_BASE_2, PKA_BASE_2_IDX,
    PKA_BASE_3, PKA_BASE_3_IDX,

    // HBL features
    HLB_VAL,
  }

  public LogPAnalysis() { }

  /**
   * Imports a molecule and runs essential calculations (like logP).
   * @param inchi The InChI of a molecule to be imported.
   * @throws MolFormatException
   * @throws PluginException
   * @throws IOException
   */
  public void init(String inchi)
      throws MolFormatException, PluginException, IOException {
    this.inchi = inchi;
    Molecule importMol = MolImporter.importMol(this.inchi);
    Cleaner.clean(importMol, 3); // This will assign 3D atom coordinates to the MolAtoms in this.mol.
    plugin.standardize(importMol);

    // Note: this doesn't seem to have any effect, but we'll try anyway for our current use case.
    microspeciesPlugin.setpH(1.5);
    microspeciesPlugin.setMolecule(importMol);
    microspeciesPlugin.run();
    Molecule phMol = microspeciesPlugin.getMajorMicrospecies();

    plugin.setlogPMethod(LogPMethod.CONSENSUS);

    // TODO: do we need to explicitly specify ion concentration?
    plugin.setUserTypes("logPTrue,logPMicro,logPNonionic"); // These arguments were chosen via experimentation.

    plugin.setMolecule(phMol);
    plugin.run();
    this.mol = plugin.getResultMolecule();

    // The logP values exposed by the plugin are only accessible by index; make an object -> id map for easier lookup.
    MolAtom[] molAtoms = mol.getAtomArray();
    for (int i = 0; i < molAtoms.length; i++) {
      atomToIndexMap.put(molAtoms[i], i);
    }
  }

  /**
   * Finds the pair of most distant atoms that contribute to the molecule's logP value.
   * @return A pair of atom indices for the two most distant atoms in the molecule.
   */
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

  /**
   * Compute the distance between two atoms in the molecule being analyzed.
   * @param a1 The index of one atom.
   * @param a2 The index of the other atom.
   * @return A distance (units not specified) between the two atoms in the molecule's coordinate space.
   */
  public Double computeDistance(Integer a1, Integer a2) {
    return this.normalizedCoordinates.get(a1).distance(this.normalizedCoordinates.get(a2));
  }

  /**
   * Recenters all atomic coordinates around a new origin.
   * @param newOriginIndex The atom index to use as a new origin.
   * @return A list of coordinates for all atoms using the specified atom as the origin.
   */
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

  /**
   * Computes an atom's projection onto `lv` and the `lv`-normal plane that intersects that projection, where `lv` is
   * the vector between the pair of most distant atoms in the molecule.
   *
   * @return Maps of atomic indices to distances from `lv` and to an `lv`-normal plane that intersects that molecule.
   */
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
      distancesAlongLongestVector.put(i, proj);
      normalPlanes.put(i, new Plane(diameter.x, diameter.y, diameter.z, -1d * dotProduct));
    }

    distancesFromLongestVector.put(lvIndex1, 0.0);
    distancesFromLongestVector.put(lvIndex2, 0.0);

    distancesAlongLongestVector.put(lvIndex1, 0.0);
    distancesAlongLongestVector.put(lvIndex2, Math.sqrt(coords.get(lvIndex2).lengthSquare()));

    return Pair.of(distancesFromLongestVector, normalPlanes);
  }

  /**
   * Computes sets of atoms on either side of each `lv`-normal plane defined by each atom.
   * @return A map of atom index to lists of atoms on each side of the atom-incident `lv`-normal plane.
   */
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

      for (int j = 0; j < mol.getAtomCount(); j++) {
        if (i == j) {
          continue;
        }
        DPoint3 c = coords.get(j);
        double prod = p.computeProductForPoint(c.x, c.y, c.z);
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

  /**
   * Computes the minimum bounding ball around a list of coordinates.
   * @param coords A list of coordinates whose minimum bounding ball to compute.
   * @return A center and radius of the minimum bounding ball for the specified list of points.
   */
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

  /**
   * Contribute the minimum bounding ball for all atoms that contribute the the molecule's logP value.
   * @return A center and raidus for the minimum bounding ball around logP-contributing atoms.
   */
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

  /**
   * Explore the neighborhood within `depths` steps of the atom with the specified atomic index, returning a map of
   * neighboring atomic indices to their step-wise distance from the specified origin atom.
   *
   * @param index The index of the atom whose neighborhood to explore.
   * @param depth The maximum number of steps to take away from the origin atom.
   * @return A map of atomic index to step-wise distance from the specified origin atom.
   */
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

  public static final Double MIN_AND_MAX_LOG_P_LONGEST_VECTOR_BOOST = 0.00001;
  /**
   * Walk bonds from the lv endpoints and min/max logP atoms, computing stats about their makeup.
   *
   * @return A map of features to numeric values for extreme-neighborhood type attributes (NBH_*).
   */
  public Map<FEATURES, Double> exploreExtremeNeighborhoods() {
    Integer vMax = null, vMin = null;
    double lpMax = 0.0, lpMin = 0.0;
    for (int i = 0; i < mol.getAtomCount(); i++) {
      double lp = plugin.getAtomlogPIncrement(i);
      if (i == lvIndex1 || i == lvIndex2) {
        // Boost the most distant points by a little bit to break ties.
        lp = lp > 0.0 ? lp + MIN_AND_MAX_LOG_P_LONGEST_VECTOR_BOOST : lp - MIN_AND_MAX_LOG_P_LONGEST_VECTOR_BOOST;
      }
      if (vMax == null || lp > lpMax) {
        vMax = i;
        lpMax = lp;
      }

      if (vMin == null || lp < lpMin) {
        vMin = i;
        lpMin = lp;
      }
    }

    maxLogPIndex = vMax;
    minLogPIndex = vMin;

    Map<Integer, Integer> maxNeighborhood = exploreNeighborhood(vMax, 2);
    Map<Integer, Integer> minNeighborhood = exploreNeighborhood(vMin, 2);

    Map<Integer, Integer> v1Neighborhood = exploreNeighborhood(lvIndex1, 2);
    Map<Integer, Integer> v2Neighborhood = exploreNeighborhood(lvIndex2, 2);

    boolean maxAndMinInSimilarNeighborhood = maxNeighborhood.containsKey(vMin);
    boolean maxInV1N = v1Neighborhood.containsKey(vMax);
    boolean maxInV2N = v2Neighborhood.containsKey(vMax);
    boolean minInV1N = v1Neighborhood.containsKey(vMin);
    boolean minInV2N = v2Neighborhood.containsKey(vMin);

    // These odd *_ accumulators are because the vars used in the put() calls for the return value need to be final.
    double maxNSum_ = 0.0;
    int maxNWithPosSign_ = 0;
    for (Integer i : maxNeighborhood.keySet()) {
      double logp = plugin.getAtomlogPIncrement(i);
      maxNSum_ += logp;
      if (logp >= 0.0) {
        maxNWithPosSign_++;
      }
    }
    double maxNSum = maxNSum_;
    double maxNWithPosSign = Integer.valueOf(maxNWithPosSign_).doubleValue();

    double minNSum_ = 0.0;
    int minNWithNegSign_ = 0;
    for (Integer i : minNeighborhood.keySet()) {
      double logp = plugin.getAtomlogPIncrement(i);
      minNSum_ += logp;
      if (logp <= 0.0) {
        minNWithNegSign_++;
      }
    }
    double minNSum = minNSum_;
    double minNWithNegSign = Integer.valueOf(minNWithNegSign_).doubleValue();

    return new HashMap<FEATURES, Double>() {{
      put(FEATURES.NBH_MAX_AND_MIN_TOGETHER, maxAndMinInSimilarNeighborhood ? 1.0 : 0);
      put(FEATURES.NBH_MAX_IN_V1, maxInV1N ? 1.0 : 0); // Boolean -> float makes this friendly to downstream analysis.
      put(FEATURES.NBH_MAX_IN_V2, maxInV2N ? 1.0 : 0);
      put(FEATURES.NBH_MIN_IN_V1, minInV1N ? 1.0 : 0);
      put(FEATURES.NBH_MIN_IN_V2, minInV2N ? 1.0 : 0);
      put(FEATURES.NBH_MAX_N_MEAN, maxNSum / Integer.valueOf(maxNeighborhood.size()).doubleValue());
      put(FEATURES.NBH_MIN_N_MEAN, minNSum / Integer.valueOf(maxNeighborhood.size()).doubleValue());
      put(FEATURES.NBH_MAX_POS_RATIO, maxNWithPosSign / Integer.valueOf(maxNeighborhood.size()).doubleValue());
      put(FEATURES.NBH_MIN_NEG_RATIO, minNWithNegSign / Integer.valueOf(minNeighborhood.size()).doubleValue());
    }};
  }

  /**
   * Perform linear regression over atoms' projection onto `lv` using their logP contributions as y-axis values.
   *
   * @return The slope of the regression line computed over the `lv`-projection.
   */
  public Double performRegressionOverLVProjectionOfLogP() {
    SimpleRegression regression = new SimpleRegression();
    for (int i = 0; i < mol.getAtomCount(); i++) {
      Double x = distancesAlongLongestVector.get(i);
      Double y = plugin.getAtomlogPIncrement(i);
      regression.addData(x, y);
    }
    regression.regress();
    return regression.getSlope();
  }

  /**
   * Perform linear regression over a list of X/Y coordinates
   * @param coords A set of coordinates over which to perform linear regression.
   * @return The slope and intercept of the regression line.
   */
  public Pair<Double, Double> performRegressionOverXYPairs(List<Pair<Double, Double>> coords) {
    SimpleRegression regression = new SimpleRegression(true);
    for (Pair<Double, Double> c : coords) {
      regression.addData(c.getLeft(), c.getRight());
    }
    // Note: the regress() call can raise an exception for small molecules.  We should probably handle that gracefully.
    RegressionResults result = regression.regress();
    return Pair.of(regression.getSlope(), regression.getIntercept());
  }

  /**
   * Computes plane-split (PS_*_) features for a list of AtomSplit objects, and returns the one that best separates
   * positivie and negative logP-contributing atoms.
   * @param atomSplits A list of atom splits for which to compute features.
   * @return A pair of the best AtomSplit and its features.
   */
  public Pair<AtomSplit, Map<FEATURES, Double>> findBestPlaneSplitFeatures(List<AtomSplit> atomSplits) {
    double bestWeightedLogPDiff = 0.0;
    AtomSplit bestAtomSplit = null;
    Map<FEATURES, Double> features = null;
    // Compute a bunch of metrics for every split, and take the one that best partitions the weighted logP delta.
    for (AtomSplit ps : atomSplits) {
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
      double sizeWeightedLeftWeightedSum =
          ps.getWeightedLeftSum() / Integer.valueOf(Math.max(leftSize, 1)).doubleValue();
      double sizeWeightedRightWeightedSum =
          ps.getWeightedRightSum() / Integer.valueOf(Math.max(rightSize, 1)).doubleValue();
      double lrPosNegCountRatio1 = Integer.valueOf(ps.getLeftNegCount()).doubleValue() /
          Integer.valueOf(Math.max(ps.getRightPosCount(), 1)).doubleValue();
      double lrPosNegCountRatio2 = Integer.valueOf(ps.getRightNegCount()).doubleValue() /
          Integer.valueOf(Math.max(ps.getLeftPosCount(), 1)).doubleValue();
      double leftPosNegRatio = Integer.valueOf(Math.min(ps.getLeftNegCount(), ps.getLeftPosCount())).doubleValue() /
          Integer.valueOf(Math.max(ps.getLeftNegCount(), ps.getLeftPosCount())).doubleValue();
      double rightPosNegRatio = Integer.valueOf(Math.min(ps.getRightNegCount(), ps.getRightPosCount())).doubleValue() /
          Integer.valueOf(Math.max(ps.getRightNegCount(), ps.getRightPosCount())).doubleValue();

      if (weightedLogPDiff > bestWeightedLogPDiff) {
        bestWeightedLogPDiff = weightedLogPDiff;
        bestAtomSplit = ps;

        // Store the features while they're computed; seems like it'd be more expensive to recompute than store.
        features = new HashMap<FEATURES, Double>(){{
          put(FEATURES.PS_LEFT_MEAN_LOGP, ps.getLeftSum() / Integer.valueOf(Math.max(leftSize, 1)).doubleValue());
          put(FEATURES.PS_RIGHT_MEAN_LOGP, ps.getRightSum() / Integer.valueOf(Math.max(rightSize, 1)).doubleValue());
          put(FEATURES.PS_LR_SIZE_DIFF_RATIO, lrSetSizeDiffRatio);
          put(FEATURES.PS_LR_POS_NEG_RATIO_1, lrPosNegCountRatio1);
          put(FEATURES.PS_LR_POS_NEG_RATIO_2, lrPosNegCountRatio2);
          put(FEATURES.PS_ABS_LOGP_DIFF, absLogPDiff);
          put(FEATURES.PS_ABS_LOGP_SIGNS_DIFFER, absLogPSignDiff);
          put(FEATURES.PS_WEIGHTED_LOGP_DIFF, weightedLogPDiff);
          put(FEATURES.PS_WEIGHTED_LOGP_SIGNS_DIFFER, weightedLogPSignDiff);
          put(FEATURES.PS_MAX_ABS_DIFF, absLogPMinMaxDiff);
          put(FEATURES.PS_LEFT_POS_NEG_RATIO, leftPosNegRatio);
          put(FEATURES.PS_RIGHT_POS_NEG_RATIO, rightPosNegRatio);
          // TODO: add surface-contribution-based metrics as well.
        }};
      }
    }
    return Pair.of(bestAtomSplit, features);
  }

  /**
   * Compute features related to the logP-labeled molecular surface computed by MarvinSpace.
   * @param jFrame A jFrame to use when running MarvinSpace (seems strange but is requred).
   * @param hydrogensShareNeighborsLogP Set to true if hydrogen atoms should share their neighbor's logP value.
   * @return A map of features related to and depending on the computed molecular surface.
   * @throws Exception
   */
  public Map<FEATURES, Double> computeSurfaceFeatures(JFrame jFrame, boolean hydrogensShareNeighborsLogP)
      throws Exception {
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
        // Note: the logPPlugin's deprecated getAtomlogPHIncrement method just uses the non-H neighbor's logP, as here.
        // msketch seems to do something different, but it's unclear what that is.
        hValues.add(hydrogensShareNeighborsLogP ? logP : 0.0);
      }
    }
    /* Tack the hydrogen's logP contributions on to the list of proper logP values.  The MSC renderer seems to expect
     * the hydrogen's values after the non-hydrogen's values, so appending appears to work fine. */
    logPVals.addAll(hValues);

    // Compute the planes before rendering to avoid the addition of implicit hydrogens in the calculation.
    // TODO: re-strip hydrogens after rendering to avoid these weird issues in general.
    Map<Integer, Pair<List<Integer>, List<Integer>>> splitPlanes = splitAtomsByNormalPlanes();

    MoleculeComponent mc1 = mspace.addMoleculeTo(mol, 0);
    mspace.getEventHandler().createAtomLabels(mc1, ids);

    // Don't draw hydrogens; it makes the drawing too noisy.
    mspace.setProperty("MacroMolecule.Hydrogens", "false");
    MoleculeComponent mc2 = mspace.addMoleculeTo(mol, 1);
    MolecularSurfaceComponent msc = mspace.computeSurface(mc2);
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

    // Build a line of (proj(p, lv), logP) pairs.
    List<Pair<Double, Double>> weightedVals = new ArrayList<>();
    for (int i = 0; i < atoms.length; i++) {
      Integer count = surfaceComponentCounts.get(i);
      Double logP = plugin.getAtomlogPIncrement(i);
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

    Map<FEATURES, Double> features = new HashMap<>();
    features.put(FEATURES.REG_WEIGHTED_SLOPE, slopeIntercept.getLeft());
    features.put(FEATURES.REG_WEIGHTED_INTERCEPT, slopeIntercept.getRight());
    features.put(FEATURES.REG_VAL_AT_FARTHEST_POINT, valAtFarthestPoint);
    /* Multiply the intercept with the value at the largest point to see if there's a sign change.  If so, we'll
     * get a negative number and know the regression line crosses the axis. */
    features.put(FEATURES.REG_CROSSES_X_AXIS, valAtFarthestPoint * slopeIntercept.getRight() < 0.000 ? 1.0 : 0.0);

    // Flatten the list of split planes and find the "best" one (i.e. the one that maximizes the weighted logP delta).
    List<AtomSplit> allSplitPlanes = new ArrayList<>();
    for (int i = 0; i < atoms.length; i++) {
      if (!splitPlanes.containsKey(i)) {
        continue;
      }
      Pair<List<Integer>, List<Integer>> splitAtoms = splitPlanes.get(i);
      List<Integer> leftAtoms = splitAtoms.getLeft();
      List<Integer> rightAtoms = splitAtoms.getRight();
      Pair<AtomSplit, AtomSplit> splitVariants = AtomSplit.computePlaneSplitsForIntersectingAtom(
          leftAtoms, rightAtoms, i, plugin, surfaceComponentCounts
      );

      AtomSplit l = splitVariants.getLeft();
      AtomSplit r = splitVariants.getRight();
      allSplitPlanes.add(l);
      allSplitPlanes.add(r);
    }
    Pair<AtomSplit, Map<FEATURES, Double>> bestPsRes = findBestPlaneSplitFeatures(allSplitPlanes);
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

    // Don't display here--leave that to the owner of the JFrame.
    return features;
  }

  public static final double[] SOLUBILITY_PHS = new double[] {2.5, 3.0, 3.5};
  /**
   * Calculate whole-molecule fatures used in post-processing and filtering.
   * @return A map of whole-molecule features.
   * @throws Exception
   */
  public Map<FEATURES, Double> calculateAdditionalFilteringFeatures() throws Exception {
    SolubilityCalculator sc = new SolubilityCalculator();
    SolubilityResult[] solubility = sc.calculatePhDependentSolubility(mol, SOLUBILITY_PHS);

    HlbPlugin hlb = HlbPlugin.Builder.createNew();
    hlb.setMolecule(mol);
    hlb.run();
    double hlbVal = hlb.getHlbValue();

    pKaPlugin pka = new pKaPlugin();
    // From the documentation.  Not sure what these knobs do...
    pka.setBasicpKaLowerLimit(-5.0);
    pka.setAcidicpKaUpperLimit(25.0);
    pka.setpHLower(2.5); // for ms distr
    pka.setpHUpper(3.5); // for ms distr
    pka.setpHStep(0.5);  // for ms distr
    pka.setMolecule(mol);
    pka.run();

    double[] pkaAcidVals = new double[3];
    int[] pkaAcidIndices = new int[3];

    double[] pkaBasicVals = new double[3];
    int[] pkaBasicIndices = new int[3];

    // Also not sure these are the values we're interested in.
    pka.getMacropKaValues(pKaPlugin.ACIDIC, pkaAcidVals, pkaAcidIndices);
    pka.getMacropKaValues(pKaPlugin.BASIC, pkaBasicVals, pkaBasicIndices);

    // TODO: compute carbon chain length.
    return new HashMap<FEATURES, Double>() {{
      put(FEATURES.SOL_MG_ML_25, solubility[0].getSolubility(SolubilityUnit.MGPERML));
      put(FEATURES.SOL_MG_ML_30, solubility[1].getSolubility(SolubilityUnit.MGPERML));
      put(FEATURES.SOL_MG_ML_35, solubility[2].getSolubility(SolubilityUnit.MGPERML));

      put(FEATURES.PKA_ACID_1, pkaAcidVals[0]);
      put(FEATURES.PKA_ACID_1_IDX, Integer.valueOf(pkaAcidIndices[0]).doubleValue());
      put(FEATURES.PKA_ACID_2, pkaAcidVals[1]);
      put(FEATURES.PKA_ACID_2_IDX, Integer.valueOf(pkaAcidIndices[1]).doubleValue());
      put(FEATURES.PKA_ACID_3, pkaAcidVals[2]);
      put(FEATURES.PKA_ACID_3_IDX, Integer.valueOf(pkaAcidIndices[2]).doubleValue());

      put(FEATURES.PKA_BASE_1, pkaBasicVals[0]);
      put(FEATURES.PKA_BASE_1_IDX, Integer.valueOf(pkaBasicIndices[0]).doubleValue());
      put(FEATURES.PKA_BASE_2, pkaBasicVals[1]);
      put(FEATURES.PKA_BASE_2_IDX, Integer.valueOf(pkaBasicIndices[1]).doubleValue());
      put(FEATURES.PKA_BASE_3, pkaBasicVals[2]);
      put(FEATURES.PKA_BASE_3_IDX, Integer.valueOf(pkaBasicIndices[2]).doubleValue());

      put(FEATURES.HLB_VAL, hlbVal);
    }};
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

  // TODO: add greedy high/low logP neighborhood picking, compute bounding balls, and calc intersection (spherical cap).
  // TODO: restructure this class to make the analysis steps more modular (now they're coupled to surface computation).
  /**
   * Perform all analysis for a molecule, returning a map of all available features.
   * @param inchi The molecule to analyze.
   * @param display True if the molecule should be displayed; set to false for non-interactive analysis.
   * @return A map of all features for this molecule.
   * @throws Exception
   */
  public static Map<FEATURES, Double> performAnalysis(String inchi, boolean display) throws Exception {
    LogPAnalysis logPAnalysis = new LogPAnalysis();
    logPAnalysis.init(inchi);

    // Start with simple structural analyses.
    Pair<Integer, Integer> farthestAtoms = logPAnalysis.findFarthestContributingAtomPair();
    Double longestVectorLength = logPAnalysis.computeDistance(farthestAtoms.getLeft(), farthestAtoms.getRight());

    // Then compute the atom distances to the longest vector (lv) and produce lv-normal planes at each atom.
    Pair<Map<Integer, Double> , Map<Integer, Plane>> results =
        logPAnalysis.computeAtomDistanceToLongestVectorAndNormalPlanes();
    // Find the max distance so we can calculate the maxDist/|lv| ratio, or "skinny" factor.
    double maxDistToLongestVector = 0.0;
    Map<Integer, Double> distancesToLongestVector = results.getLeft();
    for (Map.Entry<Integer, Double> e : distancesToLongestVector.entrySet()) {
      maxDistToLongestVector = Math.max(maxDistToLongestVector, e.getValue());
    }

    // A map of the molecule features we'll eventually output.
    Map<FEATURES, Double> features = new HashMap<>();

    // Explore the lv endpoint and min/max logP atom neighborhoods, and merge those features into the complete map.
    Map<FEATURES, Double> neighborhoodFeatures = logPAnalysis.exploreExtremeNeighborhoods();
    features.putAll(neighborhoodFeatures);

    /* Perform regression analysis on the projection of the molecules onto lv, where their y-axis is their logP value.
     * Higher |slope| may mean more extreme logP differences at the ends. */
    Double slope = logPAnalysis.performRegressionOverLVProjectionOfLogP();

    /* Compute the logP surface of the molecule (seems to require a JFrame?), and collect those features.  We consider
     * the number of closest surface components to each atom so we can guess at how much interior atoms actually
     * contribute to the molecule's solubility. */
    JFrame jFrame = new JFrame();
    jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    Map<FEATURES, Double> surfaceFeatures = logPAnalysis.computeSurfaceFeatures(jFrame, true);
    features.putAll(surfaceFeatures);

    features.put(FEATURES.LOGP_TRUE, logPAnalysis.plugin.getlogPTrue()); // Save absolute logP since we calculated it.
    features.put(FEATURES.GEO_LV_FD_RATIO, maxDistToLongestVector / longestVectorLength);
    features.put(FEATURES.REG_ABS_SLOPE, slope);

    Map<FEATURES, Double> additionalFeatures = logPAnalysis.calculateAdditionalFilteringFeatures();
    features.putAll(additionalFeatures);

    List<FEATURES> sortedFeatures = new ArrayList<>(features.keySet());
    Collections.sort(sortedFeatures);

    // Print these for easier progress tracking.
    System.out.format("features:\n");
    for (FEATURES f : sortedFeatures) {
      System.out.format("  %s = %f\n", f, features.get(f));
    }

    if (display) {
      jFrame.pack();
      jFrame.setVisible(true);
    }

    return features;
  }
}
