package com.act.analysis.logp;

import chemaxon.marvin.calculations.logPPlugin;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AtomSplit {
  Set<Integer> leftIndices;
  Set<Integer> rightIndices;

  double leftSum = 0.0, rightSum = 0.0;
  double weightedLeftSum = 0.0, weightedRightSum = 0.0;
  int leftPosCount = 0, leftNegCount = 0;
  int rightPosCount = 0, rightNegCount = 0;
  double leftMin = 0.0, leftMax = 0.0;
  double rightMin = 0.0, rightMax = 0.0;

  protected AtomSplit() { }

  public AtomSplit(AtomSplit toCopy) {
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

    AtomSplit that = (AtomSplit) o;

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

  public static Pair<AtomSplit, AtomSplit> computePlaneSplitsForIntersectingAtom(
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

    AtomSplit ps = new AtomSplit();
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
    AtomSplit leftVariant = ps;
    AtomSplit rightVariant = new AtomSplit(ps);
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
