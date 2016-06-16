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
import com.act.biointerpretation.desalting.ReactionDesalter;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
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
  public static final String OPTION_RXN_ID = "r";
  public static final String OPTION_DIR_PATH = "f";
  public static final String OPTION_FILE_FORMAT = "e";
  public static final String OPTION_HEIGHT = "y";
  public static final String OPTION_WIDTH = "x";
  public static final String OPTION_COFACTOR = "c";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class renders representations of a reaction."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_READ_DB)
        .argName("read db name")
        .desc("The name of the read DB to use")
        .hasArg().required()
        .longOpt("db")
    );
    add(Option.builder(OPTION_RXN_ID)
        .argName("id")
        .desc("The id of the reaction to validate")
        .hasArg().required()
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

  public void drawReaction(MongoDB db, Long reactionId, String filePath, boolean includeCofactors) throws IOException {

    RxnMolecule renderedReactionMolecule = getRxnMolecule(db, reactionId, includeCofactors);

    // Calculate coordinates with a 2D coordinate system.
    Cleaner.clean(renderedReactionMolecule, 2, null);

    // Change the reaction arrow type.
    renderedReactionMolecule.setReactionArrowType(RxnMolecule.REGULAR_SINGLE);

    String fullPath = StringUtils.join(new String[]{filePath, reactionId.toString(), ".", format});

    drawMolecule(renderedReactionMolecule, new File(fullPath));
  }

  public RxnMolecule getRxnMolecule(MongoDB db, Long reactionId, boolean includeCofactors) throws MolFormatException {
    Reaction reaction = db.getReactionFromUUID(reactionId);
    RxnMolecule renderedReactionMolecule = new RxnMolecule();

    List<Long> substrateIds = getSubstrates(db, reactionId, includeCofactors);
    List<Long> productIds = getProducts(db, reactionId, includeCofactors);

    for (Long sub : substrateIds) {
      renderedReactionMolecule.addComponent(
          MolImporter.importMol(db.getChemicalFromChemicalUUID(sub).getInChI()), RxnMolecule.REACTANTS);
    }

    for (Long prod : productIds) {
      renderedReactionMolecule.addComponent(
          MolImporter.importMol(db.getChemicalFromChemicalUUID(prod).getInChI()), RxnMolecule.PRODUCTS);
    }

    return renderedReactionMolecule;
  }

  public String renderReactionInSmilesNotation(MongoDB db, Long reactionId, boolean includeCofactors) throws IOException {
    Reaction r = db.getReactionFromUUID(reactionId);

    List<Long> substrateIds = getSubstrates(db, reactionId, includeCofactors);
    List<Long> productIds = getProducts(db, reactionId, includeCofactors);

    StringBuilder smilesReaction = new StringBuilder();

    List<String> smilesSubstrates = new ArrayList<>();
    List<String> smilesProducts = new ArrayList<>();

    for (Long id : substrateIds) {
      smilesSubstrates.add(returnSmilesNotationOfChemical(db.getChemicalFromChemicalUUID(id)));
    }

    for (Long id : productIds) {
      smilesProducts.add(returnSmilesNotationOfChemical(db.getChemicalFromChemicalUUID(id)));
    }

    smilesReaction.append(StringUtils.join(smilesSubstrates, "."));
    smilesReaction.append(">>");
    smilesReaction.append(StringUtils.join(smilesProducts, "."));

    return smilesReaction.toString();
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

  private String returnSmilesNotationOfChemical(Chemical chemical) throws IOException {
    // If the chemical does not have a smiles notation, convert it's inchi to smiles.
    return chemical.getSmiles() == null ? MolExporter.exportToFormat(
        MolImporter.importMol(chemical.getInChI()), "smiles") : chemical.getSmiles();
  }


  public void drawMolecule(Molecule molecule, File imageFile)
      throws IOException {

    byte[] graphics = MolExporter.exportToBinFormat(molecule, getFormatAndSizeString());

    try (FileOutputStream fos = new FileOutputStream(imageFile)) {
      fos.write(graphics);
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
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ReactionDesalter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    Integer height = Integer.parseInt(cl.getOptionValue(OPTION_HEIGHT, "1000"));
    Integer width = Integer.parseInt(cl.getOptionValue(OPTION_WIDTH, "1000"));
    Boolean representCofactors = cl.hasOption(OPTION_COFACTOR) && Boolean.parseBoolean(cl.getOptionValue(OPTION_COFACTOR));

    NoSQLAPI api = new NoSQLAPI(cl.getOptionValue(OPTION_READ_DB), cl.getOptionValue(OPTION_READ_DB));

    Long reactionId = Long.parseLong(cl.getOptionValue(OPTION_RXN_ID));
    ReactionRenderer renderer = new ReactionRenderer(cl.getOptionValue(OPTION_FILE_FORMAT), width, height);
    renderer.drawReaction(api.getReadDB(), reactionId, cl.getOptionValue(OPTION_DIR_PATH), representCofactors);
    LOGGER.info(renderer.renderReactionInSmilesNotation(api.getReadDB(), reactionId, representCofactors));
  }
}
