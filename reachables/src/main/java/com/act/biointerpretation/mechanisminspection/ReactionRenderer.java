package com.act.biointerpretation.mechanisminspection;

import act.server.MongoDB;
import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ReactionRenderer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionRenderer.class);

  public static final String OPTION_READ_DB = "d";
  public static final String OPTION_RXN_IDS = "r";
  public static final String OPTION_DIR_PATH = "f";
  public static final String OPTION_FILE_FORMAT = "e";
  public static final String OPTION_HEIGHT = "y";
  public static final String OPTION_WIDTH = "x";
  public static final String OPTION_COFACTOR = "c";

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This class renders representations of a reaction."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_READ_DB)
        .argName("read db name")
        .desc("The name of the read DB to use")
        .hasArg().required()
        .longOpt("db")
    );
    add(Option.builder(OPTION_RXN_IDS)
        .argName("id")
        .desc("The id of the reaction to validate")
        .hasArgs()
        .valueSeparator(',')
        .required()
        .longOpt("id")
    );
    add(Option.builder(OPTION_DIR_PATH)
        .argName("dir path")
        .desc("The dir path where the image will be rendered")
        .hasArg().required()
        .longOpt("dir path")
    );
    // The list of file formats supported are here: https://marvin-demo.chemaxon.com/marvin/help/formats/formats.html
    add(Option.builder(OPTION_FILE_FORMAT)
        .argName("file format")
        .desc("The file format for the image")
        .hasArg().required()
        .longOpt("file format")
    );
    add(Option.builder(OPTION_HEIGHT)
        .argName("height")
        .desc("height of image")
        .hasArg()
        .longOpt("height")
    );
    add(Option.builder(OPTION_WIDTH)
        .argName("width")
        .desc("width of image")
        .hasArg()
        .longOpt("width")
    );
    add(Option.builder(OPTION_COFACTOR)
        .argName("cofactor")
        .desc("true if cofactors need to be rendered, false otherwise")
        .hasArg()
        .longOpt("cofactor")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final String DEFAULT_FORMAT = "png";
  private static final Integer DEFAULT_WIDTH = 1000;
  private static final Integer DEFAULT_HEIGHT = 1000;
  private static final String XENON_INCHI = "InChI=1S/Xe";
  private static final String SMARTS_FORMAT = "smarts";

  String format;
  Integer width;
  Integer height;

  public ReactionRenderer() {
    this.format = DEFAULT_FORMAT;
    this.width = DEFAULT_WIDTH;
    this.height = DEFAULT_HEIGHT;
  }

  public ReactionRenderer(String format, Integer width, Integer height) {
    this.format = format;
    this.width = width;
    this.height = height;
  }

  public void drawReaction(MongoDB db, Long reactionId, String dirPath, boolean includeCofactors) throws IOException {

    RxnMolecule renderedReactionMolecule = getRxnMolecule(db, reactionId, includeCofactors);

    String fileName = StringUtils.join(new String[] {reactionId.toString(), ".", format});
    drawRxnMolecule(renderedReactionMolecule, new File(dirPath, fileName));
  }

  public String getSmartsForReaction(MongoDB db, Long reactionId, boolean includeCofactors) throws IOException {
    RxnMolecule rxnMolecule = getRxnMolecule(db, reactionId, includeCofactors);
    return getSmartsNotation(rxnMolecule);
  }


  public RxnMolecule getRxnMolecule(MongoDB db, Long reactionId, boolean includeCofactors) {
    RxnMolecule renderedReactionMolecule = new RxnMolecule();

    List<Long> substrateIds = getSubstrates(db, reactionId, includeCofactors);
    List<Long> productIds = getProducts(db, reactionId, includeCofactors);

    for (Long sub : substrateIds) {
      Chemical chemical = db.getChemicalFromChemicalUUID(sub);
      Molecule mol = importMoleculeOrXenon(chemical);
      renderedReactionMolecule.addComponent(mol, RxnMolecule.REACTANTS);
    }

    for (Long prod : productIds) {
      Chemical chemical = db.getChemicalFromChemicalUUID(prod);
      Molecule mol = importMoleculeOrXenon(chemical);
      renderedReactionMolecule.addComponent(mol, RxnMolecule.PRODUCTS);
    }

    return renderedReactionMolecule;
  }

  private List<Long> getSubstrates(MongoDB db, Long reactionId, boolean includeCofactors) {
    Reaction r = db.getReactionFromUUID(reactionId);

    List<Long> substrates = new ArrayList<>();

    for (Long id : r.getSubstrates()) {
      substrates.add(id);
    }

    if (includeCofactors) {
      for (Long id : r.getSubstrateCofactors()) {
        substrates.add(id);
      }
    }

    return substrates;
  }

  private List<Long> getProducts(MongoDB db, Long reactionId, boolean includeCofactors) {
    Reaction r = db.getReactionFromUUID(reactionId);

    List<Long> products = new ArrayList<>();

    for (Long id : r.getProducts()) {
      products.add(id);
    }

    if (includeCofactors) {
      for (Long id : r.getProductCofactors()) {
        products.add(id);
      }
    }

    return products;
  }

  public void drawRxnMolecule(RxnMolecule molecule, File imageFile) throws IOException {
    // Change the reaction arrow type.
    molecule.setReactionArrowType(RxnMolecule.REGULAR_SINGLE);

    drawMolecule(molecule, imageFile);
  }

  public void drawMolecule(Molecule molecule, File imageFile)
      throws IOException {
    // Calculate coordinates with a 2D coordinate system.
    Cleaner.clean(molecule, 2, null);

    byte[] graphics = MolExporter.exportToBinFormat(molecule, getFormatAndSizeString());

    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
      fos.write(graphics);
    }
  }

  public String getSmartsNotation(Molecule mol) throws IOException {
    return MolExporter.exportToFormat(mol, SMARTS_FORMAT);
  }

  /**
   * Imports the molecule if possible, or else returns a Xenon atom as a placeholder for rendering.
   *
   * @throws MolFormatException
   */
  private Molecule importMoleculeOrXenon(Chemical chemical) {
    try {
      return chemical.importAsMolecule();
    } catch (MolFormatException e) {
      LOGGER.warn("No molecule returned for chemical %d. Replacing with Xenon.", chemical.getUuid());
      try {
        return MolImporter.importMol(XENON_INCHI);
      } catch (MolFormatException f) {
        LOGGER.error("Could not import xenon inchi; something is very wrong.");
        throw new RuntimeException(f);
      }
    }
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public Integer getWidth() {
    return width;
  }

  public void setWidth(Integer width) {
    this.width = width;
  }

  public Integer getHeight() {
    return height;
  }

  public void setHeight(Integer height) {
    this.height = height;
  }

  private String getFormatAndSizeString() {
    return format + StringUtils.join(":w", width.toString(), ",", "h", height.toString());
  }


  public static void main(String[] args) throws IOException {
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
      HELP_FORMATTER.printHelp(ReactionRenderer.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ReactionRenderer.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    Integer height = Integer.parseInt(cl.getOptionValue(OPTION_HEIGHT, "1000"));
    Integer width = Integer.parseInt(cl.getOptionValue(OPTION_WIDTH, "1000"));
    Boolean representCofactors = cl.hasOption(OPTION_COFACTOR) && Boolean.parseBoolean(cl.getOptionValue(OPTION_COFACTOR));

    NoSQLAPI api = new NoSQLAPI(cl.getOptionValue(OPTION_READ_DB), cl.getOptionValue(OPTION_READ_DB));

    for (String val : cl.getOptionValues(OPTION_RXN_IDS)) {
      Long reactionId = Long.parseLong(val);
      ReactionRenderer renderer = new ReactionRenderer(cl.getOptionValue(OPTION_FILE_FORMAT), width, height);
      renderer.drawReaction(api.getReadDB(), reactionId, cl.getOptionValue(OPTION_DIR_PATH), representCofactors);
      LOGGER.info(renderer.getSmartsForReaction(api.getReadDB(), reactionId, representCofactors));
    }
  }
}
