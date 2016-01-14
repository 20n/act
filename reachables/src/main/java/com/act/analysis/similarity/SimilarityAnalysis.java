package com.act.analysis.similarity;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.marvin.alignment.AlignmentException;
import chemaxon.marvin.alignment.AlignmentMolecule;
import chemaxon.marvin.alignment.AlignmentMoleculeFactory;
import chemaxon.marvin.alignment.AlignmentProperties;
import chemaxon.marvin.alignment.PairwiseAlignment;
import chemaxon.marvin.alignment.PairwiseSimilarity3D;
import chemaxon.struc.Molecule;
import com.act.analysis.surfactant.TSVWriter;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimilarityAnalysis {
  public static final String OPTION_LICENSE_FILE = "l";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_QUERY_FILE = "r";
  public static final String OPTION_TARGET_FILE = "i";
  public static final String OPTION_QUERY_INCHI = "q";
  public static final String OPTION_TARGET_INCHI = "t";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This (now defunct) similarity analysis computes the alignment and 3D similarity between one or more pairs of ",
      "molecules using Chemaxon's PairwiseComparison APIs.  These calculations are, however, incredibly slow in ",
      "practice: running similarity analysis with one query over the whole reachables set is expected to take weeks ",
      "if not longer.  Use the `similarity.scala` Spark job instead, which can execute the same kind of search over ",
      "a cluster of machines.\n\n",
      "Note that if you're looking for a very fast approximate similarity search, you should check out ",
      "SaccharideSearch and FattyAcidSearch.  These use structural methods that are much faster than 3D alignment."
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
    add(Option.builder(OPTION_OUTPUT_FILE)
            .argName("output file")
            .desc("An output TSV in which to write features")
            .hasArg().required()
            .longOpt("output-file")
    );
    add(Option.builder(OPTION_QUERY_FILE)
            .argName("query file")
            .desc("A file of query InChIs to use in similarity comparison")
            .hasArg()
            .longOpt("query-file")
    );
    add(Option.builder(OPTION_TARGET_FILE)
            .argName("target file")
            .desc("A file of target inchis to use in similarity comparison (ideally, |queries| < |targets|)")
            .hasArg()
            .longOpt("target-file")
    );
    add(Option.builder(OPTION_QUERY_INCHI)
            .argName("inchi")
            .desc("A single query InChI to use in structural similarity analysis")
            .hasArg()
            .longOpt("query")
    );
    add(Option.builder(OPTION_TARGET_INCHI)
        .argName("inchi")
        .desc("A single target InChI to use in structural similarity analysis")
        .hasArg()
        .longOpt("target")
    );
  }};

  public static Molecule findLargestFragment(Molecule[] fragments) {
    Molecule largest = null;
    int maxAtomCount = 0;
    for (Molecule mol : fragments) {
      if (largest == null || mol.getAtomCount() > maxAtomCount) {
        largest = mol;
        maxAtomCount = mol.getAtomCount();
      }
    }
    return largest;
  }

  public static Map<String, String> doubleMapToStringMap(Map<String, Double> m) {
    Map<String, String> r = new HashMap<>(m.size());
    for (Map.Entry<String, Double> entry : m.entrySet()) {
      r.put(entry.getKey(), String.format("%.6f", entry.getValue()));
    }
    return r;
  }

  public static class SimilarityOperator {
    public static final AlignmentMoleculeFactory ALIGNMENT_MOLECULE_FACTORY = new AlignmentMoleculeFactory();

    private String name;
    private String inchi;
    private Molecule queryFragment;
    private PairwiseAlignment alignment;
    private PairwiseSimilarity3D similarity3D;

    private String alignmentScoreHeader;
    private String alignmentTMHeader;
    private String sim3DScoreHeader;
    private String sim3DTMHeader;

    public SimilarityOperator(String name, String inchi) {
      this.name = name;
      this.inchi = inchi;

      alignmentScoreHeader = String.format("%s alignment score", name);
      alignmentTMHeader = String.format("%s alignment tanimoto", name);
      sim3DScoreHeader = String.format("%s sim-3d score", name);
      sim3DTMHeader = String.format("%s sim-3d tanimoto", name);
    }

    public void init() throws AlignmentException, MolFormatException {
      Molecule queryMol = MolImporter.importMol(inchi);
      Cleaner.clean(queryMol, 3);
      queryFragment = findLargestFragment(queryMol.convertToFrags());
      AlignmentMolecule am = ALIGNMENT_MOLECULE_FACTORY.create(
          queryFragment, AlignmentProperties.DegreeOfFreedomType.TRANSLATE_ROTATE);
      alignment = new PairwiseAlignment();
      alignment.setQuery(am);
      similarity3D = new PairwiseSimilarity3D();
      similarity3D.setQuery(queryFragment);
    }

    public Map<String, Double> calculateSimilarity(AlignmentMolecule targetFragment) throws AlignmentException {
      Map<String, Double> results = new HashMap<>();
      results.put(alignmentScoreHeader, alignment.similarity(targetFragment));
      results.put(alignmentTMHeader, alignment.getShapeTanimoto());
      results.put(sim3DScoreHeader, similarity3D.similarity(targetFragment));
      results.put(sim3DTMHeader, similarity3D.getShapeTanimoto());
      return results;
    }

    public List<String> getResultFields() {
      return Arrays.asList(alignmentScoreHeader, alignmentTMHeader, sim3DScoreHeader, sim3DTMHeader);
    }

    public String getName() {
      return name;
    }

    public String getInchi() {
      return inchi;
    }

    public Molecule getQueryFragment() {
      return queryFragment;
    }
  }

  public static SimilarityOperator makeSimilarityOperators(String name, String inchi)
      throws AlignmentException, MolFormatException {
    return new SimilarityOperator(name, inchi);
  }

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

    LicenseManager.setLicenseFile(cl.getOptionValue(OPTION_LICENSE_FILE));

    if (cl.hasOption(OPTION_TARGET_INCHI) && cl.hasOption(OPTION_TARGET_FILE)) {
      System.err.format("Specify only one of -%s or -%s\n", OPTION_TARGET_INCHI, OPTION_TARGET_FILE);
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    List<SimilarityOperator> querySimilarityOperators = new ArrayList<>();
    List<String> header = new ArrayList<>();
    header.add("name");
    header.add("id");
    header.add("inchi");

    if (cl.hasOption(OPTION_QUERY_INCHI) && !cl.hasOption(OPTION_QUERY_FILE)) {
      SimilarityOperator so = makeSimilarityOperators("from inchi", cl.getOptionValue(OPTION_QUERY_INCHI));
      so.init();
      querySimilarityOperators.add(so);
      header.addAll(so.getResultFields());
    } else if (cl.hasOption(OPTION_QUERY_FILE) && !cl.hasOption(OPTION_QUERY_INCHI)) {
      TSVParser parser = new TSVParser();
      parser.parse(new File(cl.getOptionValue(OPTION_QUERY_FILE)));
      for (Map<String, String> row : parser.getResults()) {
        System.out.format("Compiling query for %s, %s\n", row.get("name"), row.get("inchi"));
        SimilarityOperator so = makeSimilarityOperators(row.get("name"), row.get("inchi"));
        so.init();
        querySimilarityOperators.add(so);
        header.addAll(so.getResultFields());
      }
    } else {
      System.err.format("Specify exactly one of -%s or -%s\n", OPTION_QUERY_INCHI, OPTION_QUERY_FILE);
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    List<Map<String, String>> targetChemicals = null;
    if (cl.hasOption(OPTION_TARGET_INCHI) && !cl.hasOption(OPTION_TARGET_FILE)) {
      String inchi = cl.getOptionValue(OPTION_TARGET_INCHI);
      targetChemicals = Collections.singletonList(
          new HashMap<String, String>() {{
            put("name", "direct-input");
            put("id", null);
            put("inchi", inchi);
          }}
      );
    } else if (cl.hasOption(OPTION_TARGET_FILE) && !cl.hasOption(OPTION_TARGET_INCHI)) {
      TSVParser parser = new TSVParser();
      parser.parse(new File(cl.getOptionValue(OPTION_TARGET_FILE)));
      targetChemicals = parser.getResults();
    } else {
      System.err.format("Specify exactly one of -%s or -%s\n", OPTION_TARGET_INCHI, OPTION_TARGET_FILE);
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    AlignmentMoleculeFactory alignmentMoleculeFactory = new AlignmentMoleculeFactory();

    // TODO: add symmetric computations for target as query and each query as target.
    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(cl.getOptionValue(OPTION_OUTPUT_FILE)));
    try {
      for (Map<String, String> row : targetChemicals) {
        Molecule targetMol = MolImporter.importMol(row.get("inchi"));
        Cleaner.clean(targetMol, 3); // This will assign 3D atom coordinates to the MolAtoms in targetMol.
        Molecule targetFragment = findLargestFragment(targetMol.convertToFrags());
        AlignmentMolecule am = alignmentMoleculeFactory.create(
            targetFragment, AlignmentProperties.DegreeOfFreedomType.TRANSLATE_ROTATE_FLEXIBLE);
        Map<String, String> outputRow = new HashMap<>(row);
        System.out.format("Processing target %s\n", row.get("name"));
        for (SimilarityOperator so : querySimilarityOperators) {
          System.out.format("  running query %s\n", so.getName());
          Map<String, Double> results = so.calculateSimilarity(am);
          outputRow.putAll(doubleMapToStringMap(results));
        }
        writer.append(outputRow);
        writer.flush();
      }
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
    System.out.format("Done\n");
  }
}
