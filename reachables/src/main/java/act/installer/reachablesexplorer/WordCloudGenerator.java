package act.installer.reachablesexplorer;


import act.server.DBIterator;
import act.server.MongoDB;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.jobs.FileChecker;
import com.act.utils.CLIUtil;
import com.act.utils.ProcessRunner;
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.ChemicalKeywords;
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.MongoKeywords;
import com.mongodb.BasicDBObject;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class WordCloudGenerator {

  /**
   * This class allow the generation of wordclouds, using R, for any inchi having a Bing reference.
   * It requires an R script, that takes an InChI as argument and writes a word cloud to a file
   */

  private static final String RSCRIPT_EXE_PATH = "/usr/bin/Rscript"; // TODO: find this using `env` instead.
  private static final String RSCRIPT_LOCATION = "src/main/r/RWordCloudGenerator.R";
  private static final Logger LOGGER = LogManager.getFormatterLogger(WordCloudGenerator.class);
  private static final String PNG_EXTENSION = ".png";
  private static final long CHILD_PROCESS_TIMEOUT_IN_SECONDS = 60; // Thomas thinks this is plenty of time for a cloud.

  private static final String OPTION_DB_HOST = "H";
  private static final String OPTION_DB_PORT = "p";
  private static final String OPTION_INSTALLER_SOURCE_DB = "i";
  private static final String OPTION_RENDERING_CACHE = "e";
  private static final String OPTION_INPUT_INCHIS = "l";
  private static final String OPTION_RSCRIPT_EXE_PATH = "r";

  private static final String DEFAULT_ASSETS_LOCATION = "data/reachables-explorer-rendering-cache";

  // Default host. If running on a laptop, please set a SSH bridge to access speakeasy
  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_PORT = "27017";
  private static final String DEFAULT_CHEMICALS_DATABASE = "SHOULD_COME_FROM_CMDLINE"; // "jarvis_2016-12-09";


  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class allows WordCloud generation as a separate process from the Loader"
  }, " ");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB_HOST)
        .argName("DB host")
        .desc(String.format("The database host to which to connect (default: %s)", DEFAULT_HOST))
        .hasArg()
        .longOpt("db-host")
    );
    add(Option.builder(OPTION_DB_PORT)
        .argName("DB port")
        .desc(String.format(
            "The port on which to connect to the database (default: %s)",
            DEFAULT_PORT))
        .hasArg()
        .longOpt("db-port")
    );
    add(Option.builder(OPTION_INSTALLER_SOURCE_DB)
        .argName("DB name")
        .desc(String.format(
            "The name of the database from which to fetch chemicals and reactions (default: %s)",
            DEFAULT_CHEMICALS_DATABASE))
        .hasArg()
        .longOpt("source-db-name")
    );
    add(Option.builder(OPTION_RENDERING_CACHE)
        .argName("path to cache")
        .desc(String.format(
            "A directory in which to cache rendered images for reachables documents (default: %s)",
            DEFAULT_ASSETS_LOCATION))
        .hasArg()
        .longOpt("cache-dir")
    );
    add(Option.builder(OPTION_INPUT_INCHIS)
        .argName("path to inchis list")
        .desc("A list of input inchis for which to compute word clouds")
        .hasArg()
        .required()
        .longOpt("inchis-path")
    );
    add(Option.builder(OPTION_RSCRIPT_EXE_PATH)
        .argName("rscript exe path")
        .desc(String.format(
            "The path to the Rscript exe for running R scripts. Default is %s. Can be determined by running \"which Rscript\"",
            RSCRIPT_EXE_PATH))
        .hasArg()
        .required()
        .longOpt("r-location")
    );
  }};

  private File rScript;
  private String rScriptExePath;

  private String host;
  private Integer port;
  private MongoDB bingDb;
  private Set<String> inchisSet;
  private File assetLocation;

  public WordCloudGenerator(String host, Integer port, String database, String assetLocation, String rScriptExePath) {
    this.host = host;
    this.port = port;
    this.bingDb = new MongoDB(host, port, database);
    this.inchisSet = getBingInchis();
    this.assetLocation = new File(assetLocation);
    this.rScript = new File(RSCRIPT_LOCATION);
    this.rScriptExePath = rScriptExePath;
    try {
      FileChecker.verifyInputFile(this.rScript);
    } catch (IOException e) {
      String msg = String.format("Failed to locate R script at %s", this.rScript.getAbsolutePath());
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
    if (!this.assetLocation.exists() || !this.assetLocation.isDirectory()) {
      String msg = String.format("Failed to locate asset location directory at %s", this.assetLocation.getAbsolutePath());
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  public WordCloudGenerator(String host, Integer port, String database, String assetLocation) {
    this(host, port, database, assetLocation, RSCRIPT_EXE_PATH);
  }

  public WordCloudGenerator(String host, Integer port, String database) {
    this(host, port, database, DEFAULT_ASSETS_LOCATION, RSCRIPT_EXE_PATH);
  }

  public Set<String> getBingInchis() {

    BasicDBObject query = new BasicDBObject("xref.BING.metadata.usage_terms.0", new BasicDBObject(MongoKeywords.EXISTS$.MODULE$.value(), true));
    BasicDBObject keys = new BasicDBObject(ChemicalKeywords.INCHI$.MODULE$.value(), true);

    DBIterator ite = bingDb.getIteratorOverChemicals(query, keys);
    Set<String> bingSet = new HashSet<>();
    while (ite.hasNext()) {
      BasicDBObject o = (BasicDBObject) ite.next();
      String inchi = o.getString(ChemicalKeywords.INCHI$.MODULE$.value());
      if (inchi != null) {
        bingSet.add(inchi);
      }
    }
    return bingSet;
  }

  public File getWordcloudFile(String inchi) {
    String md5 = DigestUtils.md5Hex(inchi);
    String postfix = new StringBuilder("-").append(md5).append(PNG_EXTENSION).toString();

    String wordcloudFilename = String.join("", "wordcloud", postfix);

    return Paths.get(this.assetLocation.getPath(), wordcloudFilename).toFile();
  }

  public File generateWordCloud(String inchi) {

    // TODO: improve wordcloud generation. Currently, each instance open a mongo connection on the R side.
    // By doing data manipulation in Java and utilizing Rengine, we could make this much better
    // Wordclouds could be generated ahead of time this way, using the inchi coprus

    File wordcloud = getWordcloudFile(inchi);

    if (!Files.exists(wordcloud.toPath()) && inchisSet.contains(inchi)) {
      try {
        ProcessRunner.runProcess(
            rScriptExePath,
            // TODO: remove hardcoded database from R script
            Arrays.asList(rScript.getAbsolutePath(), inchi, wordcloud.getAbsolutePath(), host, port.toString()),
            CHILD_PROCESS_TIMEOUT_IN_SECONDS);
        FileChecker.verifyInputFile(wordcloud);
      } catch (IOException e) {
        LOGGER.error("Unable to generate wordcloud for %s at location %s", inchi, wordcloud.toPath().toString());
        return null;
      } catch (InterruptedException e) {
        LOGGER.error("Child process was interrupted: %s", e.getMessage());
        return null;
      }
    }
    return wordcloud;
  }


  public static void main(String[] args) {

    CLIUtil cliUtil = new CLIUtil(Loader.class, HELP_MESSAGE, OPTION_BUILDERS);
    CommandLine cl = cliUtil.parseCommandLine(args);

    // TODO add possibility to run wordcloud generation as a post processing step, from a loaded reachables database
    File inchisFile = new File(cl.getOptionValue(OPTION_INPUT_INCHIS));
    L2InchiCorpus inchiCorpus = new L2InchiCorpus();
    try {
      inchiCorpus.loadCorpus(inchisFile);
    } catch (IOException e) {
      cliUtil.failWithMessage("Could not load inchi corpus from input file %s", inchisFile.getAbsolutePath());
    }
    WordCloudGenerator wordCloudGenerator = new WordCloudGenerator(
        cl.getOptionValue(OPTION_DB_HOST, DEFAULT_HOST),
        Integer.parseInt(cl.getOptionValue(OPTION_DB_PORT, DEFAULT_PORT)),
        cl.getOptionValue(OPTION_INSTALLER_SOURCE_DB, DEFAULT_CHEMICALS_DATABASE),
        cl.getOptionValue(OPTION_RENDERING_CACHE, DEFAULT_ASSETS_LOCATION),
        cl.getOptionValue(OPTION_RSCRIPT_EXE_PATH, RSCRIPT_EXE_PATH)
    );
    inchiCorpus.getInchiList().forEach(wordCloudGenerator::generateWordCloud);
  }
}
