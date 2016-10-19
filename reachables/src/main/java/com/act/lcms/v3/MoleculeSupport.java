package com.act.lcms.v3;

import java.util.List;

public interface MoleculeSupport {
  Metabolite getMetabolite();
  List<Ion> getSupportedVariants();
  List<DetectedPeak> getDetectedPeaks(Ion ion);
}
