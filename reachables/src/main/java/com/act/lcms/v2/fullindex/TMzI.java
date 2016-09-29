package com.act.lcms.v2.fullindex;

import java.nio.ByteBuffer;

/**
 * (time, mass/charge, intensity) triples.
 *
 * The three axes (XYZ) from MS1 are stored as binary triples in our index, so that we can recover the individual
 * readings from the LCMS instrument in any m/z and time ranges.
 *
 * This could be called XYZ, but T(ime, )Mz(, and) I(ntensity) makes more sense to me.
 */
public class TMzI {
  /* Note: we are cheating here.  We usually throw around Doubles for time and intensity.  To save (a whole bunch of)
   * of bytes, we pare down our time intensity values to floats, knowing they were actually floats to begin with
   * in the NetCDF file but were promoted to doubles to be compatible with the LCMS parser API.  */
  public static final int BYTES = Float.BYTES + Double.BYTES + Float.BYTES;

  // TODO: we might get better compression out of using an index for time.
  private float time;
  private double mz;
  private float intensity;

  public TMzI(float time, double mz, float intensity) {
    this.time = time;
    this.mz = mz;
    this.intensity = intensity;
  }

  public float getTime() {
    return time;
  }

  public double getMz() {
    return mz;
  }

  public float getIntensity() {
    return intensity;
  }

  public void writeToByteBuffer(ByteBuffer buffer) {
    buffer.putFloat(time);
    buffer.putDouble(mz);
    buffer.putFloat(intensity);
  }

  // Write the fields without bothering to create an object.
  static void writeToByteBuffer(ByteBuffer buffer, float time, double mz, float intensity) {
    buffer.putFloat(time);
    buffer.putDouble(mz);
    buffer.putFloat(intensity);
  }

  static TMzI readNextFromByteBuffer(ByteBuffer buffer) {
    float time = buffer.getFloat();
    double mz = buffer.getDouble();
    float intensity = buffer.getFloat();
    return new TMzI(time, mz, intensity);
  }
}
