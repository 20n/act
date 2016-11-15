package com.act.biointerpretation.networkanalysis;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.lcms.MS1;
import com.act.lcms.v2.Ion;
import com.act.lcms.v2.IonCalculator;
import com.act.lcms.v2.LcmsIonCalculator;
import com.act.lcms.v2.Metabolite;
import com.act.lcms.v2.PeakSpectrum;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EndogenousAnalysis {

  static Set<String> ions = new HashSet<String>() {{
    addAll(Arrays.asList("M+H","M+Na","M+H-H2O"));
  }};

  public static void main(String[] args) throws IOException {
    String tsvFileName = args[0];
    String inchiFileName = args[1];

    File tsvFile = new File((tsvFileName));
    File inchiFile = new File(inchiFileName);

    PeakSpectrum spectrum = LcmsTSVParser.parseControlTSV(tsvFile);
    System.out.printf("Parsed %d peaks.\n", spectrum.getAllPeaks().size());
    L2InchiCorpus inchis = new L2InchiCorpus();
    inchis.loadCorpus(inchiFile);
    System.out.printf("Parsed %d inchis\n", inchis.getInchiList().size());

    IonCalculator ionCalculator = new LcmsIonCalculator();

    int molHits = 0;
    for (String inchi : inchis.getInchiList()) {
      Metabolite metabolite = new InchiMetabolite(inchi);
      List<Ion> metaboliteIons = ionCalculator.getSelectedIons(metabolite, ions, MS1.IonMode.POS);
      if (metaboliteIons.stream().flatMap(ion -> spectrum.getPeaksByMZ(ion.getMzValue(), 1.0).stream()).findAny().isPresent()) {
        molHits++;
      }
    }

    System.out.printf("%d out of %d molecules hit LCMS peaks", molHits, inchis.getInchiList().size());
  }
}
