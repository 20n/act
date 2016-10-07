package com.act.biointerpretation;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.Reaction;
import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.cofactorremoval.CofactorRemover;
import com.act.biointerpretation.desalting.ReactionDesalter;
import com.act.biointerpretation.mechanisminspection.MechanisticValidator;
import com.act.biointerpretation.reactionmerging.ReactionMerger;
import com.act.biointerpretation.sequencemerging.SequenceMerger;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.act.utils.CLIUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * This class extracts all product chemicals from reactions in an installer DB that contain proteins belonging to a
 * class of user-specified organisms.  Cofactors are included in the products extracted by this class.  The
 * type of organism to extract is defined by an organism name prefix: any reaction that contains a protein that
 * references an organism whose name begins with the specified prefix is considered for extraction.
 *
 * Why would we want to extract just the products of reactions?  Doing so allows us to produce a superset of all
 * L2 molecules that we might see in the metabolome of an organism like humans or yeast.  While we may not be able to
 * explicitly declare that all of the extracted molecules are bio-reachable, their characterization in relation to a
 * host organism gives us some evidence that we might see them in an LCMS scan.
 */
public class ProductExtractor {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ProductExtractor.class);

  private static final String OPTION_ORGANISM_PREFIX = "r";
  private static final String OPTION_OUTPUT_FILE = "o";
  private static final String OPTION_DB_NAME = "n";
  private static final String DEFAULT_DB_HOST = "localhost";
  private static final Integer DEFAULT_DB_PORT = 27017;

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_ORGANISM_PREFIX)
        .argName("organism prefix")
        .desc("Organism prefix to use when filtering reactions")
        .hasArg()
        .required()
        .longOpt("organism")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("output file")
        .desc("The file to which to write product InChIs (default is stdout)")
        .hasArg()
        .longOpt("output")
    );
    add(Option.builder(OPTION_DB_NAME)
        .argName("DB name")
        .desc("The name of the DB from which to extract products")
        .hasArg().required()
        .longOpt("db-name")
    );
  }};

  private static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "Extracts all products for reactions belonging ",
      "to organisms whose names match a given prefix",
  }, "");

  public static void main(String[] args) throws Exception {
    CLIUtil cliUtil = new CLIUtil(ProductExtractor.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    String orgPrefix = cl.getOptionValue(OPTION_ORGANISM_PREFIX);
    LOGGER.info("Using organism prefix %s", orgPrefix);

    MongoDB db = new MongoDB(DEFAULT_DB_HOST, DEFAULT_DB_PORT, cl.getOptionValue(OPTION_DB_NAME));

    Map<Long, String> validOrganisms = new TreeMap<>();
    DBIterator orgIter = db.getDbIteratorOverOrgs();
    Organism o = null;
    while ((o = db.getNextOrganism(orgIter)) != null) {
      if (!o.getName().isEmpty() && o.getName().startsWith(orgPrefix)) {
        validOrganisms.put(o.getUUID(), o.getName());
      }
    }

    LOGGER.info("Found %d valid organisms", validOrganisms.size());

    Set<Long> productIds = new TreeSet<>(); // Use something with implicit ordering we can traverse in order.
    DBIterator reactionIterator = db.getIteratorOverReactions(true);
    Reaction r;
    while ((r = db.getNextReaction(reactionIterator)) != null) {
      Set<JSONObject> proteins = r.getProteinData();
      boolean valid = false;
      for (JSONObject j : proteins) {
        if (j.has("organism") && validOrganisms.containsKey(j.getLong("organism"))) {
          valid = true;
          break;
        } else if (j.has("organisms")) {
          JSONArray organisms = j.getJSONArray("organisms");
          for (int i = 0; i < organisms.length(); i++) {
            if (validOrganisms.containsKey(organisms.getLong(i))) {
              valid = true;
              break;
            }
          }
        }
      }

      if (valid) {
        for (Long id : r.getProducts()) {
          productIds.add(id);
        }
        for (Long id : r.getProductCofactors()) {
          productIds.add(id);
        }
      }
    }

    LOGGER.info("Found %d valid product ids for '%s'", productIds.size(), orgPrefix);
    PrintWriter writer = cl.hasOption(OPTION_OUTPUT_FILE) ?
        new PrintWriter(new FileWriter(cl.getOptionValue(OPTION_OUTPUT_FILE))) :
        new PrintWriter(System.out);

    for (Long id : productIds) {
      Chemical c = db.getChemicalFromChemicalUUID(id);
      String inchi = c.getInChI();
      if (inchi.startsWith("InChI=") && !inchi.startsWith("InChI=/FAKE")) {
        writer.println(inchi);
      }
    }

    if (cl.hasOption(OPTION_OUTPUT_FILE)) {
      writer.close();
    }
    LOGGER.info("Done.");
  }
}
