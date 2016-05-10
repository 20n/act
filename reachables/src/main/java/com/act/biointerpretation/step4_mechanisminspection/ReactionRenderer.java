package com.act.biointerpretation.step4_mechanisminspection;

import act.server.MongoDB;
import act.server.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.step2_desalting.ReactionDesalter;
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
  public static final String OPTION_FILE_PATH = "f";
  public static final String OPTION_FILE_FORMAT = "e";
  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class renders an image of a given reaction id and file path."
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
    add(Option.builder(OPTION_FILE_PATH)
        .argName("file path")
        .desc("The file path where the image will be rendered")
        .hasArg().required()
        .longOpt("file path")
    );
    add(Option.builder(OPTION_FILE_FORMAT)
        .argName("file format")
        .desc("The file format for the image")
        .hasArg().required()
        .longOpt("file format")
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

  public void drawAndSaveReaction(Long reactionId, String filePath, String format) throws IOException {
    Reaction reaction = this.db.getReactionFromUUID(reactionId);
    RxnMolecule renderedReactionMolecule = new RxnMolecule();

    for (Long sub : reaction.getSubstrates()) {
      renderedReactionMolecule.addComponent(
          MolImporter.importMol(this.db.getChemicalFromChemicalUUID(sub).getInChI()), RxnMolecule.REACTANTS);
    }

    for (Long prod : reaction.getProducts()) {
      renderedReactionMolecule.addComponent(
          MolImporter.importMol(this.db.getChemicalFromChemicalUUID(prod).getInChI()), RxnMolecule.PRODUCTS);
    }

    // Calculate coordinates with a 2D coordinate system.
    Cleaner.clean(renderedReactionMolecule, 2, null);

    // Change the reaction arrow type.
    renderedReactionMolecule.setReactionArrowType(RxnMolecule.REGULAR_SINGLE);

    String formatAndSize = format + ":w600,h600";
    byte[] graphics = MolExporter.exportToBinFormat(renderedReactionMolecule, formatAndSize);
    FileOutputStream fos = new FileOutputStream(new File(filePath));
    fos.write(graphics);
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

    NoSQLAPI api = new NoSQLAPI(cl.getOptionValue(OPTION_READ_DB), cl.getOptionValue(OPTION_READ_DB));
    ReactionRenderer renderer = new ReactionRenderer(api.getReadDB());
    renderer.drawAndSaveReaction(Long.parseLong(cl.getOptionValue(OPTION_RXN_ID)), cl.getOptionValue(OPTION_FILE_PATH),
        cl.getOptionValue(OPTION_FILE_FORMAT));
  }
}
