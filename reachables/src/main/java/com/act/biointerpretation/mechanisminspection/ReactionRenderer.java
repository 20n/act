package com.act.biointerpretation.mechanisminspection;

import act.server.MongoDB;
import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
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

  private MongoDB db;

  public ReactionRenderer(MongoDB db) {
    this.db = db;
  }

  public void drawAndSaveReaction(Long reactionId, String dirPath, boolean includeCofactors, String format, Integer height, Integer width) throws IOException {
    Reaction reaction = this.db.getReactionFromUUID(reactionId);
    RxnMolecule renderedReactionMolecule = new RxnMolecule();

    for (Long sub : reaction.getSubstrates()) {
      renderedReactionMolecule.addComponent(
          MolImporter.importMol(this.db.getChemicalFromChemicalUUID(sub).getInChI()), RxnMolecule.REACTANTS);
    }

    if (includeCofactors) {
      for (Long sub : reaction.getSubstrateCofactors()) {
        renderedReactionMolecule.addComponent(
            MolImporter.importMol(this.db.getChemicalFromChemicalUUID(sub).getInChI()), RxnMolecule.REACTANTS);
      }
    }

    for (Long prod : reaction.getProducts()) {
      renderedReactionMolecule.addComponent(
          MolImporter.importMol(this.db.getChemicalFromChemicalUUID(prod).getInChI()), RxnMolecule.PRODUCTS);
    }

    if (includeCofactors) {
      for (Long prod : reaction.getProductCofactors()) {
        renderedReactionMolecule.addComponent(
            MolImporter.importMol(this.db.getChemicalFromChemicalUUID(prod).getInChI()), RxnMolecule.PRODUCTS);
      }
    }

    // Calculate coordinates with a 2D coordinate system.
    Cleaner.clean(renderedReactionMolecule, 2, null);

    // Change the reaction arrow type.
    renderedReactionMolecule.setReactionArrowType(RxnMolecule.REGULAR_SINGLE);

    String formatAndSize = format + StringUtils.join(new String[] {":w", width.toString(), ",", "h", height.toString()});
    byte[] graphics = MolExporter.exportToBinFormat(renderedReactionMolecule, formatAndSize);

    String filePath = StringUtils.join(new String[] {dirPath, reactionId.toString(), ".", format});
    try (FileOutputStream fos = new FileOutputStream(new File(filePath))) {
      fos.write(graphics);
    }
  }

  public String renderReactionInSmilesNotation(Long reactionId, boolean includeCofactors) throws IOException {
    Reaction r = this.db.getReactionFromUUID(reactionId);
    StringBuilder smilesReaction = new StringBuilder();

    List<String> smilesSubstrates = new ArrayList<>();
    List<String> smilesProducts = new ArrayList<>();

    for (Long id : r.getSubstrates()) {
      smilesSubstrates.add(returnSmilesNotationOfChemical(db.getChemicalFromChemicalUUID(id)));
    }

    for (Long id : r.getProducts()) {
      smilesProducts.add(returnSmilesNotationOfChemical(db.getChemicalFromChemicalUUID(id)));
    }

    if (includeCofactors) {
      for (Long id : r.getSubstrateCofactors()) {
        smilesSubstrates.add(returnSmilesNotationOfChemical(db.getChemicalFromChemicalUUID(id)));
      }

      for (Long id : r.getProductCofactors()) {
        smilesProducts.add(returnSmilesNotationOfChemical(db.getChemicalFromChemicalUUID(id)));
      }
    }

    smilesReaction.append(StringUtils.join(smilesSubstrates, "."));
    smilesReaction.append(">>");
    smilesReaction.append(StringUtils.join(smilesProducts, "."));

    return smilesReaction.toString();
  }

  private String returnSmilesNotationOfChemical(Chemical chemical) throws IOException {
    // If the chemical does not have a smiles notation, convert it's inchi to smiles.
    return chemical.getSmiles() == null ? MolExporter.exportToFormat(
        MolImporter.importMol(chemical.getInChI()), "smiles") : chemical.getSmiles();
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
    ReactionRenderer renderer = new ReactionRenderer(api.getReadDB());
    renderer.drawAndSaveReaction(reactionId, cl.getOptionValue(OPTION_DIR_PATH), representCofactors,
        cl.getOptionValue(OPTION_FILE_FORMAT), height, width);
    LOGGER.info(renderer.renderReactionInSmilesNotation(reactionId, representCofactors));
  }
}
