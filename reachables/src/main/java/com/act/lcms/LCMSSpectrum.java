package com.act.lcms;

import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.List;

/**
 * A class representing a particular time point in an LCMS scan.  Contains mass/charge and intensity data for a
 * specific time.
 *
 * This object should be constructed from data parsed from mzXML or NetCDF lcms produced by an LCM apparatus.
 */
public class LCMSSpectrum implements Serializable {
  private static final long serialVersionUID = -1329555801774532940L;

  private Integer index;
  private Double timeVal;
  private String timeUnit;
  private List<Pair<Double, Double>> intensities;
  private Double basePeakMZ;
  private Double basePeakIntensity;
  private Integer function;
  private Integer scan;
  private Double totalIntensity;

  public LCMSSpectrum(Integer index, Double timeVal, String timeUnit, List<Pair<Double, Double>> intensities,
                      Double basePeakMZ, Double basePeakIntensity,
                      Integer function, Integer scan, Double totalIntensity) {
    this.index = index;
    this.timeVal = timeVal;
    this.timeUnit = timeUnit;
    this.intensities = intensities;
    this.basePeakMZ = basePeakMZ;
    this.basePeakIntensity = basePeakIntensity;
    this.function = function;
    this.scan = scan;
    this.totalIntensity = totalIntensity;
  }

  /**
   * Gets the index of this time point in its input file.
   *
   * Note: This is not always guaranteed to be the index of this time point in the scan, as the input data file may
   * have several kinds of spectra in the same array.
   * @return The index of this spectrum in the input file (not the same as the index in the scan!).
   */
  public Integer getIndex() {
    return index;
  }

  /**
   * Get the time value when this scan was done.  It is uncertain whether this represents the start or end of the scan,
   * but that is expected to be consistent for all spectra sourced from the same file.
   * @return A numeric representation of the time of this scan, expressed in units available from
   * {@link #getTimeUnit()}.
   */
  public Double getTimeVal() {
    return timeVal;
  }

  /**
   * Gets the units in which the time value is expressed.  This is assumed to be consistent for all spectra from a
   * particular data file.
   * @return A string representing the units in which the time value is expressed.
   */
  public String getTimeUnit() {
    return timeUnit;
  }

  /**
   * Gets a list of {mass/charge, intensity} pairs expressed as floats.  Mass/charge values are not assumed to be
   * uniform across spectra, nor are the differences between them uniform even within a spectra.  Their minimum and
   * maximum, however, tend to remain consistent within a particular input file (and probably for all scans done one a
   * particular instrument.  Intensity values do not necessary have a known upper bound.
   * @return A list of {mass/charge, intensity} pairs for this scan.
   */
  public List<Pair<Double, Double>> getIntensities() {
    return intensities;
  }

  /**
   * (Optional) Certain LCMS data formats define a field that characterizes the mass/charge with the highest intensity
   * value at a given time point.  This is known as the "base peak", and is removed from the {mass/charge, intensity}
   * pair list when it is available.  The availability of this should be consistent across all spectra from a particular
   * input file.
   *
   * Note that this has been found to be available (and the corressponding value missing from the spectrum) in mzXML
   * lcms, but not available in NetCDF lcms.
   *
   * @return The mass/charge of maximal intensity if one is available; null otherwise.
   */
  public Double getBasePeakMZ() {
    return basePeakMZ;
  }

  /**
   * The maximum intensity of any scanned mass/charge at this point in time.  See the note for {@link #getBasePeakMZ()}
   * for more information on the availability of this value.
   * @return The maximum available intensity at this time point, corresponding to the {@link #getBasePeakMZ()} value.
   */
  public Double getBasePeakIntensity() {
    return basePeakIntensity;
  }

  /**
   * (Optional) Get the function type for this time point.  Some LCMS devices include multiple kinds of scan data in
   * their output, only some of which is relevant to us.
   *
   * If this value is null, then assume that the spectrum is a standard LCMS scan.
   *
   * Note that the Waters instrument used by ECL uses '2' to designate standard LCMS scan data.
   *
   * @return An integer representing the kind of scan data represented by this spectrum, if available; null otherwise.
   */
  public Integer getFunction() {
    return function;
  }

  /**
   * An integer representing the index/ordinal of this time point in the scan of which it was part.  This may be the
   * same as {@link #getIndex()} if the input file contains only standard mass/charge+intensity data; if there are
   * other kinds of scan data avilable, this will represent the offset of this time point in its respective scan type.
   *
   * Note that mzXML lcms will usually have this defined and distinct from {@link #getIndex()}, whereas those values
   * will be the same in NetCDF lcms.
   * @return An integer representing this time points offset within its particular scan (see {@link #getFunction()}.
   */
  public Integer getScan() {
    return scan;
  }

  /**
   * (Optional) Returns the total intensity at this time point if available.
   *
   * Note that this will probably not be available in mzXML lcms, but should be available in NetCDF lcms.
   * @return The total intensity at this time point.
   */
  public Double getTotalIntensity() {
    return totalIntensity;
  }
}
