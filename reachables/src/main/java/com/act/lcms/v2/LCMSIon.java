package com.act.lcms.v2;

import com.act.lcms.MS1;

public class LCMSIon implements Ion {
  private Double mz;
  private MS1.MetlinIonMass ionType;

  public LCMSIon(Double mz, MS1.MetlinIonMass ionType) {
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
