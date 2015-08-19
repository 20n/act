package com.twentyn.patentScorer;

import com.twentyn.patentExtractor.PatentDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatentModel {

  private Map<String, Integer> model;
  private Double modelNormalizationParam;

  private final String _RootDir = "FTO_training";
  private final String _NegDataSet = _RootDir + "/bioneg";
  private final String _PosDataSet = _RootDir + "/biopos";
  private final String _ChemNegDataSet = _RootDir + "/chemneg";
  private final String _ChemPosDataSet = _RootDir + "/chempos";

  private static PatentModel instance = null;
  public static PatentModel getModel() {
    if (instance == null) {
      instance = new PatentModel();
    }
    return instance;
  }

  private PatentModel() {
    initModel();

    dumpValidationAgainstTrainingData();
  }

  public double ProbabilityOf(String text) {
    return NormalizeScoreToProbability(ScoreText(text));
  }

  public double ProbabilityOf(PatentDocument patentDocument) {
    StringBuilder builder = new StringBuilder();
    for (String text : patentDocument.getClaimsText()) {
      builder.append(text).append("\n");
    }

    for (String text : patentDocument.getTextContent()) {
      builder.append(text).append("\n");
    }

    builder.append(patentDocument.getClaimsText());

    return ProbabilityOf(builder.toString());
  }

  private double NormalizeScoreToProbability(int score) {
    // normalization function is 1-e(-B x score)
    // where B is calculated optimally from the dataset
    return 1 - Math.exp(-this.modelNormalizationParam * score);
  }

  private int ScoreText(String text) {
    int out = 0;
    Set<String> extract = extractTokens(text);
    for(String str : this.model.keySet()) {
      if (extract.contains(str)) {
        out += this.model.get(str);
      }
    }
    return out;
  }

  private void initModel() {
    // check that there are training files in the positive, negative datasets
    if (!Utils.filesPresentIn(_PosDataSet) || !Utils.filesPresentIn(_NegDataSet)) {
      System.err.println("First time initialization. Downloading training set.");
      DownloadTrainingDataSets();
    }

    Map<String, Integer> pattern = calculatePattern(_NegDataSet, _PosDataSet);
    this.model = pattern;

    Double normParam = calculateNormalizationParam(_NegDataSet, _PosDataSet);
    this.modelNormalizationParam = normParam;

    System.err.println("FTO: Pattern size = " + pattern.size());
    System.err.println("FTO: 1-exp(-Bx) norm. B = " + normParam);
  }

  private void DownloadTrainingDataSets() {
    // download text for biosynthesis and chemosynthesis datasets
    CreateBiosynthesisDataSet();
    CreateChemosynthesisDataSet();
  }

  private void dumpValidationAgainstTrainingData() {
    try {
      // dump all scores and probabilities for training negatives
      File dir = new File(_NegDataSet);
      for(File fily : dir.listFiles()) {
        String text = Utils.readFile(fily.getAbsolutePath());
        dumpScoreProbability("-", fily.getName(), text);
      }

      // dump all scores and probabilities for training positives
      dir = new File(_PosDataSet);
      for(File fily : dir.listFiles()) {
        String text = Utils.readFile(fily.getAbsolutePath());
        dumpScoreProbability("+", fily.getName(), text);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void dumpScoreProbability(String posOrNeg, String name, String text) {
    double probability = ProbabilityOf(text);
    System.err.println(posOrNeg + "\t" + name + "\t" + probability);
  }

  private final int CUTOFF = 5;
  private Map<String, Integer> calculatePattern(String negDir, String posDir) {
    Map<String, Integer> negs = readFolderAndHashOut(negDir);
    Map<String, Integer> poss = readFolderAndHashOut(posDir);
    Map<String, Integer> pattern = new HashMap<>();

    for(String str : negs.keySet()) {
      Integer negvalue = negs.get(str);
      if(poss.containsKey(str)) {
        Integer posvalue = poss.get(str);
        Integer newval = posvalue-negvalue;
        if (newval > CUTOFF) {
          pattern.put(str, newval);
        }
      }
    }
    return pattern;
  }

  private Double calculateNormalizationParam(String negDir, String posDir) {
    Set<Integer> negs = scoreFolder(negDir);
    Set<Integer> poss = scoreFolder(posDir);

    Double Lp = average(poss), Hn = average(negs);
    if (Lp < Hn) {
      System.err.println("FTO: Error. Centroid of +ves < -ves. Bad training data.");
      System.err.println("FTO: This means that on average the +ve patents score.");
      System.err.println("FTO: less than the -ve patents; but higher scores are");
      System.err.println("FTO: supposed to mean more +ve. Abort!");
      System.exit(-1);
    }

    // fit a 1-e(-B * x) curve to the positive and negative dataset
    // where B is a positive real, which is learnt by maximizing
    // the distance between the average of the negatives Hn and
    // the average of the positives Lp.
    // Maximization occurs where
    //      d/dB( e(-Hn * B) - e(-Lp * B) ) = 0
    //      i.e., Hn * e(-Hn * B) = Lp * e(-Lp * B) - solve for B
    //      Or Log(Lp/Hn) = B(Lp - Hn)
    //      Or B = Log(Lp/Hn)/(Lp - Hn)
    Double B = Math.log(Lp/Hn) / (Lp - Hn);

    return B;
  }

  private Double average(Set<Integer> S) {
    Double avg = 0.0;
    int sz = S.size();
    for (Integer i : S) avg += (double)i/(double)sz;
    return avg;
  }

  private Set<Integer> scoreFolder(String path) {
    try {
      Set<Integer> out = new HashSet<>();
      File dir = new File(path);
      for(File afile : dir.listFiles()) {
        String text = Utils.readFile(afile.getAbsolutePath());
        out.add(ScoreText(text));
      }
      return out;
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
      return null;
    }
  }

  private Map<String, Integer> readFolderAndHashOut(String path) {
    try {
      Map<String, Integer> out = new HashMap<>();
      File dir = new File(path);
      for(File afile : dir.listFiles()) {
        String text = Utils.readFile(afile.getAbsolutePath());
        Set<String> extract = extractTokens(text);
        for(String str : extract) {
          Integer value = 0;
          if(out.containsKey(str)) {
            value = out.get(str);
          }
          value++;
          out.put(str, value);
        }
      }
      return out;
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
      return null;
    }
  }

  private Set<String> extractTokens(String text) {
    Set<String> out = new HashSet<>();
    String patternString = "[0-9a-zA-Z]+";

    Pattern patt = Pattern.compile(patternString);

    Matcher matcher = patt.matcher(text);
    boolean matches = matcher.matches();

    int count = 0;
    while(matcher.find()) {
      count++;
      out.add((text.substring(matcher.start(), matcher.end())).toLowerCase());
    }
    return out;
  }

  private void CreateBiosynthesisDataSet() {
    File training = new File(_RootDir);
    if(!training.exists()) {
      training.mkdir();
    }

    // Create a list of patent urls talking about biosynthesis
    List<String> positives = new ArrayList<>();
    positives.add("WO2012016177A2"); //Amyris farnesene
    positives.add("WO2013192543A2"); //Phytogene styrene
    positives.add("EP2438178A2"); //Genomatica BDO
    positives.add("US6194185");  //Wash U limonene
    positives.add("US8828693");  //isopropanol
    positives.add("EP1799828B1"); //phloroglucinol
    positives.add("CA2112374C"); //yeast xylitol
    positives.add("WO2013071112A1"); //yeast xylose
    positives.add("WO2014066892A1"); //Dupont isoprene
    positives.add("EP2252691B1"); //santalene Firmenich
    positives.add("US7374920"); //
    positives.add("US20120107893"); //Stephanopoulus very broad claim about something with indole and coli specifically
    positives.add("US8889381"); //A host cell, comprising a nucleic acid molecule encoding a cis-abienol synthase
    positives.add("US7238514"); //
    positives.add("US20130302861"); //mitochondrial targeting
    positives.add("US8062878"); //levopimaradiene synthase
    positives.add("US5994114"); //taxadiene synthase

    List<String> negatives = new ArrayList<>();
    negatives.add("US5274029"); //
    negatives.add("US3284393"); //
    negatives.add("US7141615"); //
    negatives.add("US3632822"); //
    negatives.add("US3787335"); //
    negatives.add("US8017658"); //
    negatives.add("WO2012173477A1"); //
    negatives.add("CN103275146A"); //
    negatives.add("CN103113443A"); //
    negatives.add("CN103755556A"); //
    negatives.add("US20130143826"); //
    negatives.add("WO2014078168A1"); //
    negatives.add("US20130005581"); //
    negatives.add("US20140303361"); //
    negatives.add("CN103193799A"); //
    negatives.add("CN103467567A"); //
    negatives.add("WO2002044197A2"); //
    negatives.add("US20140058063"); //
    negatives.add("EP2729123A2"); //
    negatives.add("US8470822"); //
    negatives.add("WO2014031646A3"); //
    negatives.add("CN102558143B"); //
    negatives.add("WO2000026174A2"); //
    negatives.add("US20110250626"); //coatings incorporating bioactive enzymes

    // NOT IN CHRIS' DATASET THAT HE SENT OVER....
    // negatives.add("US20130189677"); //terpenoid transporters

    negatives.add("US20090238811"); //Enzymatic antimicrobial and antifouling coatings
    negatives.add("US8846351"); //degrading cellulose
    negatives.add("US20100248334"); //Biological active coating components
    negatives.add("US20130338330"); //chemical synthesis
    negatives.add("US20130331342"); //hair/scalp care compositions
    negatives.add("CA2595380A1"); //Stabilized liquid polypeptide formulations


    File afile = new File(_PosDataSet);
    if(!afile.exists()) {
      afile.mkdir();
    }
    for(String id : positives) {
      try {
        String text = Utils.GetPatentText(id);
        Utils.writeFile(text, _PosDataSet + "/" + id + ".txt");
      } catch(Exception err) {
        err.printStackTrace();
      }
    }

    afile = new File(_NegDataSet);
    if(!afile.exists()) {
      afile.mkdir();
    }
    for(String id : negatives) {
      try {
        String text = Utils.GetPatentText(id);
        Utils.writeFile(text, _NegDataSet + "/" + id+".txt");
      } catch(Exception err) {
        err.printStackTrace();
      }
    }
  }

  private void CreateChemosynthesisDataSet() {
    File training = new File(_RootDir);
    if(!training.exists()) {
      training.mkdir();
    }

    // Create a list of patent urls talking about biosynthesis
    List<String> positives = new ArrayList<>();
    positives.add("US2623897"); //Galllic acid esters
    positives.add("WO2008065527A2"); //
    positives.add("US7045654"); //
    positives.add("US4788331"); //
    positives.add("EP0771782A1"); //
    positives.add("US2606186"); //
    positives.add("US1836568"); //
    positives.add("US2886438"); //
    positives.add("US6399810"); //
    positives.add("US2945068"); //
    positives.add("US2155856"); //

    List<String> negatives = new ArrayList<>();
    negatives.add("US6180666"); //use
    negatives.add("EP1159007A1"); //use
    negatives.add("EP2753336A1"); //use
    negatives.add("US3792014"); //use
    negatives.add("WO2011138345A2");
    negatives.add("US20100034762");
    negatives.add("WO2012131348A1");
    negatives.add("US6669964");
    negatives.add("WO2009084020A2");
    negatives.add("US2211485");
    negatives.add("US5223179");
    negatives.add("US20060286061");
    negatives.add("US5756446");
    negatives.add("EP2595599A1");
    negatives.add("US4368056");
    negatives.add("EP2582775A1");
    negatives.add("US4379168");
    negatives.add("US4915707");
    negatives.add("US6200625");
    negatives.add("USRE36982");
    negatives.add("US4818250");
    negatives.add("CA2118071C");
    negatives.add("US6194185");
    negatives.add("US5849680");
    negatives.add("WO1999021891A1");
    negatives.add("CA2492498C");
    negatives.add("US6342535");
    negatives.add("US5344776");
    negatives.add("US7622269");
    negatives.add("US20020058075");
    negatives.add("US20040204497");
    negatives.add("WO2014151732A1");
    negatives.add("EP2502621A1");
    negatives.add("US5427798");
    negatives.add("US6312716");
    negatives.add("WO1999038502A1");
    negatives.add("US6462237");
    negatives.add("EP2316456A1");
    negatives.add("WO1999038503A1");
    negatives.add("US4820522");
    negatives.add("EP2649993A1");
    negatives.add("US20110124718");
    negatives.add("US8518438");
    negatives.add("US20070237816");
    negatives.add("US8658631");
    negatives.add("US8609684");
    negatives.add("US20080293804");

    File afile = new File(_ChemPosDataSet);
    if(!afile.exists()) {
      afile.mkdir();
    }
    for(String id : positives) {
      try {
        String text = Utils.GetPatentText(id);
        Utils.writeFile(text, _ChemPosDataSet + "/" + id + ".txt");
      } catch(Exception err) {
        err.printStackTrace();
      }
    }

    afile = new File(_ChemNegDataSet);
    if(!afile.exists()) {
      afile.mkdir();
    }
    for(String id : negatives) {
      try {
        String text = Utils.GetPatentText(id);
        Utils.writeFile(text, _ChemNegDataSet + "/" + id + ".txt");

        // We use the negatives in this training set to also serve as
        // training for the bio dataset; in addition to the chem dataset
        // This is because the bioalgorithm has already been seeded with
        // the positives, and could do with more negatives
        // FTO_Utils.writeFile(text, _NegDataSet + "/" + id + ".txt");

      } catch(Exception err) {
        err.printStackTrace();
      }
    }
  }
}

