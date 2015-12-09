package com.act.analysis.surfactant;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.marvin.space.MSpaceEasy;
import chemaxon.marvin.space.MolecularSurfaceComponent;
import chemaxon.marvin.space.MoleculeComponent;
import chemaxon.marvin.space.SurfaceColoring;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.lcms.db.io.parser.TSVParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.io.Console;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogPLabeler {
  public static final String OPTION_LICENSE_FILE = "l";
  public static final String OPTION_INPUT_FILE = "i";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_INCHI_SOURCE = "s";
  public static final String OPTION_INCHI_SOURCE_JOIN_FIELD = "j";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class is a CLI tool for fast labeling of molecules based on logP surfaces.  It displays unadorned ",
      "logP surface visualizations of the molecule, and accepts a label value (1, 0, or other for '?') that is ",
      "written to an output file along with the name, id, and InChI of the molecule.  The labeling process can be ",
      "safely quit at any time; running it again with the same parameters will cause it to pick up where it left off."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_LICENSE_FILE)
            .argName("path")
            .desc("The Chemaxon license file to load")
            .hasArg().required()
            .longOpt("license")
    );
    add(Option.builder(OPTION_INPUT_FILE)
            .argName("input file")
            .desc("An input TSV of chemicals to analyze")
            .hasArg().required()
            .longOpt("input-file")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
            .argName("output file")
            .desc("An output TSV in which to write features")
            .hasArg().required()
            .longOpt("output-file")
    );
    add(Option.builder(OPTION_INCHI_SOURCE)
            .argName("inchi/name source")
            .desc("A source of inchi and name data for an input file of features")
            .hasArg()
            .longOpt("inchi-source")
    );
    add(Option.builder(OPTION_INCHI_SOURCE_JOIN_FIELD)
            .argName("join field")
            .desc("The field by which to look up inchi/name data in an inchi source file")
            .hasArg()
            .longOpt("join-field")
    );
  }};

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    File inputFile = new File(cl.getOptionValue(OPTION_INPUT_FILE));
    if (!inputFile.isFile()) {
      System.err.format("No input file at: %s\n", inputFile.getAbsolutePath());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    File outputFile = new File(cl.getOptionValue(OPTION_OUTPUT_FILE));
    if (outputFile.exists()) {
      System.err.format("WARNING: output file at %s already exists\n", outputFile.getAbsolutePath());
    }

    /* Sometimes the InChIs might not appear in the input file (like in regression results).  Instead a corpus of
     * names and InChIs can be specified in a separate file and looked up as molecules are read/visualized.  The join
     * field is the key on which the InChI for a given row in the input file is found. */
    File inchiSourceFile = null;
    if (cl.hasOption(OPTION_INCHI_SOURCE)) {
      inchiSourceFile = new File(cl.getOptionValue(OPTION_INCHI_SOURCE));
      boolean err = false;
      if (!inchiSourceFile.isFile()) {
        System.err.format("No inchi source file at: %s\n", inchiSourceFile.getAbsolutePath());
        err = true;
      }
      if (!cl.hasOption(OPTION_INCHI_SOURCE_JOIN_FIELD)) {
        System.err.format("Must specify a join field when using an inchi source file.\n");
        err = true;
      }
      if (err) {
        HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        System.exit(1);
      }
    }

    LogPLabeler logPLabeler = new LogPLabeler();
    logPLabeler.runAnalysis(cl.getOptionValue(OPTION_LICENSE_FILE), inputFile, outputFile,
        inchiSourceFile, cl.getOptionValue(OPTION_INCHI_SOURCE_JOIN_FIELD));
  }

  public void runAnalysis(String licensePath, File inputFile, File outputFile,
                          File inchiSourceFile, String inchiJoinField) throws Exception {
    LicenseManager.setLicenseFile(licensePath);
    // If a separate InChI source was specified, build a map from the join key to that source's per-molecule data.
    Map<String, Map<String, String>> inchiSource = null;
    if (inchiSourceFile != null) {
      TSVParser inchiSourceParser = new TSVParser();
      inchiSourceParser.parse(inchiSourceFile);
      List<Map<String, String>> inchiSourceData = inchiSourceParser.getResults();

      inchiSource = new HashMap<>(inchiSourceData.size());
      int i = 0;
      for (Map<String, String> row : inchiSourceData) {
        i++;
        if (!row.containsKey(inchiJoinField)) {
          throw new RuntimeException(String.format("Missing inchi join field %s on row %d of inchi source file %s",
              inchiJoinField, i, inchiSourceFile.getAbsolutePath()));
        }
        inchiSource.put(row.get(inchiJoinField), row);
      }
      System.out.format("Loaded %d inchi source entries from %s\n",
          inchiSource.size(), inchiSourceFile.getAbsolutePath());
    }

    // Create the visualization environment.
    JFrame jFrame = new JFrame();
    jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    MSpaceEasy mspace = new MSpaceEasy(1, 2, true);
    mspace.addCanvas(jFrame.getContentPane());
    mspace.setSize(1200, 600);
    jFrame.pack();
    jFrame.setVisible(true);

    /* Read all the input chemicals at once.  We don't expect this file to be too big.  Do this before touching the
     * (possibly non-empty) results file in case there's a problem with the input. */
    TSVParser inputChemsParser = new TSVParser();
    inputChemsParser.parse(inputFile);
    List<Map<String, String>> chemicals = inputChemsParser.getResults();

    /* Read in any old results to find where we left off, and write them back to the (truncated) results file again.
     * TODO: add append mode to TSVWriter.  This re-writing stuff is kinda silly. */
    List<Map<String, String>> oldResults = new ArrayList<>();
    if (outputFile.exists()) {
      TSVParser oldResultsParser = new TSVParser();
      oldResultsParser.parse(outputFile);
      oldResults.addAll(oldResultsParser.getResults());
    }

    TSVWriter<String, String> resultsWriter = new TSVWriter<>(Arrays.asList("name", "id", "inchi", "label"));
    resultsWriter.open(outputFile);
    Set<String> knownInchis = new HashSet<>();
    for (Map<String, String> row  : oldResults) {
      knownInchis.add(row.get("inchi"));
      resultsWriter.append(row);
    }
    resultsWriter.flush();

    int molNumber = 0;
    try {
      Console c = System.console();
      while (molNumber < chemicals.size()) {
        // Copy the source data; we're gonna add some fields to it, and a clean copy might come in handy.
        Map<String, String> r = new HashMap<>(chemicals.get(molNumber));

        // Lookup the InChI source data by this row's join field if a source is defined.
        if (inchiSource != null) {
          Map<String, String> sourceData = inchiSource.get(chemicals.get(molNumber).get(inchiJoinField));
          r.put("inchi", sourceData.get("inchi"));
          r.put("name", sourceData.get("name"));
        }

        String inchi = r.get("inchi");
        // If we've already seen this InChI, skip it and move to the next molecule.  Also de-duplicates a little bit.
        if (knownInchis.contains(inchi)) {
          System.out.format("Skpping known molecule %d\n", molNumber);
          molNumber++;
          continue;
        }
        knownInchis.add(inchi);

        /* Remove all existing molecules from the visualizer, draw the new one, and then reset/refresh to ensure both
         * panels are displayed (sometimes one of the visualizations is corrupted w/o user input due to some strange
         * mspace issue). */
        mspace.removeAllComponents();
        drawMolecule(mspace, MolImporter.importMol(inchi));
        jFrame.pack();
        mspace.resetAll();
        mspace.refresh();

        // Very liberally ask for a label.  TODO: figure out how to accept key input on the jFrame (tried+failed once).
        System.out.format("%s (%s)\n", r.get("name"), inchiJoinField == null ? "" : r.get(inchiJoinField));
        System.out.format("Input (?/1/0):\n");
        String line = c.readLine();
        String label = "?";
        switch (line) {
          case "1" :
            label = "1";
            break;
          case "0" :
            label = "0";
            break;
        }
        r.put("label", label);
        resultsWriter.append(r);
        // Flush every time to ensure a clean exit when the user gets tired of labeling.  It happens.
        resultsWriter.flush();

        molNumber++;
      }
    } finally {
      resultsWriter.close();
    }
    System.out.format("Done.\n");
  }

  public void drawMolecule(MSpaceEasy mspace, Molecule mol) throws Exception {
    /* The cleaner assigns 3d coordiantes.  Warning: this can take a long time for complex molecules, and the cleaner
     * may be a system-wide shared resouce (not sure why, but observed behavior indicates that as a possibility). */
    Cleaner.clean(mol, 3);
    // Compute logP like we do in the analysis class.  TODO: move this to a shared util class.
    logPPlugin plugin = new logPPlugin();
    plugin.standardize(mol);
    plugin.setlogPMethod(LogPMethod.CONSENSUS);
    plugin.setUserTypes("logPTrue,logPMicro,logPNonionic"); // These arguments were chosen via experimentation.
    plugin.setMolecule(mol);
    plugin.run();

    ArrayList<Double> logPVals = new ArrayList<>();
    ArrayList<Double> hValues = new ArrayList<>();
    // Store a list of ids so we can label the
    ArrayList<Integer> ids = new ArrayList<>();
    ArrayList<String> logPLabels = new ArrayList<>();
    MolAtom[] atoms = mol.getAtomArray();
    double minLogP  = 0.0, maxLogP = 0.0;
    for (int i = 0; i < mol.getAtomCount(); i++) {
      ids.add(i);
      Double logP = plugin.getAtomlogPIncrement(i);
      logPVals.add(logP);
      logPLabels.add(String.format("%.4f", logP));

      /* The surface renderer requires that we specify logP values for all hydrogens, which don't appear to have logP
       * contributions calculated for them, in addition to non-hydrogen atoms.  We fake this by either borrowing the
       * hydrogen's neighbor's logP value, or setting it to 0.0.
       * TODO: figure out what the command-line marvin sketch logP renderer does and do that instead.
       * */
      MolAtom molAtom = mol.getAtom(i);
      for (int j = 0; j < molAtom.getImplicitHcount(); j++) {
        /* Note: the logPPlugin's deprecated getAtomlogPHIncrement method just uses the non-H neighbor's logP, as here.
         * msketch seems to do something different, but it's unclear what that is. */
        hValues.add(logP);
      }

      minLogP = Math.min(minLogP, logP);
      maxLogP = Math.max(maxLogP, logP);
    }
    /* Tack the hydrogen's logP contributions on to the list of proper logP values.  The MSC renderer seems to expect
     * the hydrogen's values after the non-hydrogen's values, so appending appears to work fine. */
    logPVals.addAll(hValues);

    // Render the plain (number-labeled) molecule on the left.
    MoleculeComponent mc1 = mspace.addMoleculeTo(mol, 0);
    mspace.getEventHandler().createAtomLabels(mc1, ids);

    // Don't draw hydrogens; it makes the drawing too noisy.
    mspace.setProperty("MacroMolecule.Hydrogens", "false");
    // Render the logP shell on the right.
    MoleculeComponent mc2 = mspace.addMoleculeTo(mol, 1);
    mspace.getEventHandler().createAtomLabels(mc2, logPLabels);
    MolecularSurfaceComponent msc = mspace.computeSurface(mc2);

    msc.setPalette(SurfaceColoring.COLOR_MAPPER_BLUE_TO_RED);
    /* Note: the positive/negative color criteria can be set explicitly here (or at the end?), but the API doesn't seem
     * to work as expected (the colors get washed out, strangely).  Just let the surface coloring do its thing. */
    msc.showVolume(true);
    // These parameters were selected via experimentation.
    msc.setSurfacePrecision("High");
    msc.setSurfaceType("van der Waals");
    // Dots let us see the underlying molecular structure more easily.
    msc.setDrawProperty("Surface.DrawType", "Dot");
    msc.setDrawProperty("Surface.Quality", "High");
    msc.setAtomPropertyList(logPVals);
    msc.setDrawProperty("Surface.ColorType", "AtomProperty");
  }
}
