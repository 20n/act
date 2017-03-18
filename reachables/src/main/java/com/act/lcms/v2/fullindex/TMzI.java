/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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
