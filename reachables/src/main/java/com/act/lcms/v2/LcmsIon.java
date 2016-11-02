package com.act.lcms.v2;

import com.act.lcms.MS1;

public class LcmsIon implements Ion {
  private Double mz;
  private MS1.MetlinIonMass ionType;

  public LcmsIon(Double mz, MS1.MetlinIonMass ionType) {
    this.mz = mz;
    this.ionType = ionType;
  }

  public Double getMzValue() {
    return mz;
  }

  public MS1.MetlinIonMass getIonType() {
    return ionType;
  }
}
