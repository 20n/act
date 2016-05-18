package com.act.biointerpretation.mechanisminspection;

import act.server.MongoDB;
import act.server.NoSQLAPI;
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
  public static final String OPTION_HEIGHT = "r";
  public static final String OPTION_WIDTH = "w";

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
        .longOpt("height")
    );
    add(Option.builder(OPTION_WIDTH)
        .argName("width")
        .desc("width of image")
        .longOpt("width")
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

    String[] smilesSubstrates;
    String[] smilesProducts;

    if (includeCofactors) {
      smilesSubstrates = new String[r.getSubstrates().length + r.getSubstrateCofactors().length];
      smilesProducts = new String[r.getProducts().length + r.getProductCofactors().length];
    } else {
      smilesSubstrates = new String[r.getSubstrates().length];
      smilesProducts = new String[r.getProducts().length];
    }

    for (int i = 0; i < r.getSubstrates().length; i++) {
      smilesSubstrates[i] = MolExporter.exportToFormat(
          MolImporter.importMol(db.getChemicalFromChemicalUUID(r.getSubstrates()[i]).getInChI()), "smiles");
    }

    for (int i = 0; i < r.getProducts().length; i++) {
      smilesProducts[i] = MolExporter.exportToFormat(
          MolImporter.importMol(db.getChemicalFromChemicalUUID(r.getProducts()[i]).getInChI()), "smiles");
    }

    if (includeCofactors) {
      for (int i = 0; i < r.getSubstrateCofactors().length; i++) {
        smilesSubstrates[i] = MolExporter.exportToFormat(
            MolImporter.importMol(db.getChemicalFromChemicalUUID(r.getSubstrateCofactors()[i]).getInChI()), "smiles");
      }

      for (int i = 0; i < r.getProductCofactors().length; i++) {
        smilesProducts[i] = MolExporter.exportToFormat(
            MolImporter.importMol(db.getChemicalFromChemicalUUID(r.getProductCofactors()[i]).getInChI()), "smiles");
      }
    }

    smilesReaction.append(StringUtils.join(smilesSubstrates, "."));
    smilesReaction.append(">>");
    smilesReaction.append(StringUtils.join(smilesProducts, "."));

    return smilesReaction.toString();
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

    Integer height = cl.hasOption(OPTION_HEIGHT) ? Integer.parseInt(cl.getOptionValue(OPTION_HEIGHT)) : 1000;
    Integer width = cl.hasOption(OPTION_WIDTH) ? Integer.parseInt(cl.getOptionValue(OPTION_WIDTH)) : 1000;

    NoSQLAPI api = new NoSQLAPI(cl.getOptionValue(OPTION_READ_DB), cl.getOptionValue(OPTION_READ_DB));
    ReactionRenderer renderer = new ReactionRenderer(api.getReadDB());
    renderer.drawAndSaveReaction(Long.parseLong(cl.getOptionValue(OPTION_RXN_ID)), cl.getOptionValue(OPTION_DIR_PATH), false,
        cl.getOptionValue(OPTION_FILE_FORMAT), height, width);
  }
}
