package com.act.biointerpretation.networkanalysis;

import com.act.lcms.MS1;
import com.act.lcms.v2.Ion;
import com.act.lcms.v2.Isotope;
import org.apache.commons.lang.NotImplementedException;

/**
 * Created by gil on 10/26/16.
 */
public class MzIon implements Ion {

  private final double mz;
  private final MS1.MetlinIonMass ionType;

  public MzIon(double mz, MS1.MetlinIonMass ionType) {
    this.mz = mz;
    this.ionType = ionType;
  }

  @Override
  public Isotope getIsotope() {
    throw new NotImplementedException();
  }

  @Override
  public Double getMzValue() {
    return mz;
  }

  @Override
  public MS1.MetlinIonMass getIonType() {
    return ionType;
  }
}
