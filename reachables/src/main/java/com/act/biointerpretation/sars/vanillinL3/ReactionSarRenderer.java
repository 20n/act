package com.act.biointerpretation.sars.vanillinL3;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.SeqDBReactionGrouper;
import com.act.jobs.FileChecker;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ReactionSarRenderer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionSarRenderer.class);

  private static final String OPTION_DB = "d";
  private static final String OPTION_INPUT_PATH = "i";
  private static final String OPTION_OUTPUT_DIR = "o";
  private static final String OPTION_HELP = "h";

  public static final String HELP_MESSAGE =
      "This class takes in a list of reaction IDs, finds the associated sequences in the DB, and then finds all" +
          "reactions catalyzed by those sequences. It then creates one directory for each input reaction, within " +
          "which it creates on directory per sequence. In each sequence directory, it renders all reactions " +
          "catalyezd by that sequence. The idea of this is to see whether or not the input reactions are amenable " +
          "to L3 validation. If many reactions with similar substrates and the same RO are seen for a given reaction, " +
          "that reaction may be a good candidate for L3 validation by SAR generation.";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB)
        .argName("db name")
        .desc("The name of the mongo DB to use.")
        .hasArg()
        .longOpt("db-name")
        .required(true)
    );
    add(Option.builder(OPTION_INPUT_PATH)
        .argName("input path")
        .hasArg()
        .desc("The file path to the input reactions, with one reaction ID per line.")
        .longOpt("input-path")
        .required(true)
    );
    add(Option.builder(OPTION_OUTPUT_DIR)
        .argName("output directory")
        .desc("The absolute path to the directory to which to write the reaction iamges.")
        .hasArg()
        .longOpt("output-file-path")
        .required(true)
    );
    add(Option.builder(OPTION_HELP)
        .argName("help")
        .desc("Prints this help message.")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private static final String LOCAL_HOST = "localhost";
  private static final Integer MONGO_PORT = 27017;

  private static final ReactionRenderer RENDERER = new ReactionRenderer();

  public static void main(String[] args) throws Exception {
    // Build command line parser.
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      LOGGER.error("Argument parsing failed: %s", e.getMessage());
      HELP_FORMATTER.printHelp(SeqDBReactionGrouper.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    // Print help.
    if (cl.hasOption(OPTION_HELP)) {
      HELP_FORMATTER.printHelp(SeqDBReactionGrouper.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    MongoDB db = new MongoDB(LOCAL_HOST, MONGO_PORT, cl.getOptionValue(OPTION_DB));

    File inputFile = new File(cl.getOptionValue(OPTION_INPUT_PATH));
    FileChecker.verifyInputFile(inputFile);
    File outputDir = new File(cl.getOptionValue(OPTION_OUTPUT_DIR));
    FileChecker.verifyOrCreateDirectory(outputDir);

    List<Long> rxnIds = getReactions(inputFile);


    for (Long baseRxnId : rxnIds) {

      if (db.getReactionFromUUID(baseRxnId) == null) {
        LOGGER.info("Invalid reaction id %d", baseRxnId);
        continue;
      }
      File rxnDir = new File(outputDir, String.format("REACTION_%d", baseRxnId));
      FileChecker.verifyOrCreateDirectory(rxnDir);

      Reaction reaction = db.getReactionFromUUID(baseRxnId);

      Set<String> distinctSeqs = new HashSet<>();

      boolean seqsFound = false;

      Loop: for (Long seqId : reaction.getSeqs()) {
        Seq seq = db.getSeqFromID(seqId);
        if (distinctSeqs.contains(seq.getSequence())) {
          continue Loop;
        } else {
          distinctSeqs.add(seq.getSequence());
        }

        Set<Long> siblingReactions = new HashSet<>();
        List<Seq> identicalSeqs = db.getAllSeqsFromSequence(seq.getSequence());
        LOGGER.info("%d seqs identical to seq %d", identicalSeqs.size(), seq.getUUID());

        for (Seq identicalSeq : identicalSeqs) {
          siblingReactions.addAll(identicalSeq.getReactionsCatalyzed());
        }


        if (siblingReactions.size() > 10) {
          LOGGER.info("Found seq %d with %d reactions. Only drawing first 10", seq.getUUID(), siblingReactions.size());
          File seqDir = new File(rxnDir, String.format("SEQ_%d", seqId));
          FileChecker.verifyOrCreateDirectory(seqDir);

          drawReactions(siblingReactions, 10, seqDir, db);
          seqsFound = true;
        } else if (siblingReactions.size() > 4) {
          LOGGER.info("Found seq %d with %d reactions. Drawing all.", seq.getUUID(), siblingReactions.size());
          File seqDir = new File(rxnDir, String.format("SEQ_%d", seqId));
          FileChecker.verifyOrCreateDirectory(seqDir);

          drawReactions(siblingReactions, seqDir, db);
          seqsFound = true;
        } else {
          LOGGER.info("Seq %d has only %d reactions. Ignoring this seq.", seq.getUUID(), siblingReactions.size());
        }
      }

      if (seqsFound) {
        RENDERER.drawReaction(db, baseRxnId, rxnDir, false);
      } else {
        rxnDir.delete();
      }
    }
  }

  /**
   * Draws reactions with ids given in directory given, as stored in DB given.
   */
  public static void drawReactions(Set<Long> reactions, File directory, MongoDB db) throws IOException {
    drawReactions(reactions, reactions.size(), directory, db);
  }

  /**
   * Only draws the first numToDraw reactions
   */
  public static void drawReactions(Set<Long> reactions, int numToDraw, File directory, MongoDB db) throws IOException {
    int i = 0;
    for (Long siblingReactionId : reactions) {
      if (i > 9) {
        break;
      }
      RENDERER.drawReaction(db, siblingReactionId, directory, false);
      i++;
    }
  }

  public static List<Long> getReactions(File inputFile) throws IOException {
    List<Long> result = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        result.add(Long.parseLong(line));
      }
    }
    return result;
  }

  /**
   * Writes the SAR map to file!
   */
  public static void writeSarMapToFile(Map<Long, List<OneSubstrateSubstructureSar>> sarMap, File outputFile) {
    throw new NotImplementedException();
  }

  /**
   * Computes the substrate molecules for the reaction.
   */
  public static List<Molecule> getSubstrateMolecules(Reaction reaction, MongoDB db) throws MolFormatException {
    List<Molecule> result = new ArrayList<>();
    for (Long substrateId : reaction.getSubstrates()) {
      result.add(MoleculeImporter.importMolecule(db.getChemicalFromChemicalUUID(substrateId)));
    }
    return result;
  }

  /**
   * Gets the reactions associated with a given seq.
   */
  public static List<Reaction> getReactions(Long seqId, MongoDB db) {
    Seq seq = db.getSeqFromID(seqId);
    Set<Long> reactions = db.getSeqFromID(seqId).getReactionsCatalyzed();
    return reactions.stream().map(db::getReactionFromUUID).collect(Collectors.toList());
  }
}
