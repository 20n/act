package com.act.lcms.v3;


import com.act.lcms.MS1;

public interface Ion {
  Isotope getIsotope();
  Double getIsotopicMass();
  Double getMzValue();
  MS1.MetlinIonMass getIonType();
}
