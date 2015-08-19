package com.twentyn.patentScorer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyn.patentExtractor.PatentCorpusReader;
import com.twentyn.patentExtractor.PatentDocument;
import com.twentyn.patentExtractor.PatentProcessor;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public class PatentScorer implements PatentProcessor {
  public static final Logger LOGGER = LogManager.getLogger(PatentScorer.class);

  public static void main(String[] args) throws Exception {
    System.out.println("Starting up...");
    System.out.flush();
    Options opts = new Options();
    opts.addOption(Option.builder("i").
        longOpt("input").hasArg().required().desc("Input file or directory to score").build());
    opts.addOption(Option.builder("o").
        longOpt("output").hasArg().required().desc("Output file to which to write score JSON").build());
    opts.addOption(Option.builder("h").longOpt("help").desc("Print this help message and exit").build());
    opts.addOption(Option.builder("v").longOpt("verbose").desc("Print verbose log output").build());

    HelpFormatter helpFormatter = new HelpFormatter();
    CommandLineParser cmdLineParser = new DefaultParser();
    CommandLine cmdLine = null;
    try {
      cmdLine = cmdLineParser.parse(opts, args);
    } catch (ParseException e) {
      System.out.println("Caught exception when parsing command line: " + e.getMessage());
      helpFormatter.printHelp("DocumentIndexer", opts);
      System.exit(1);
    }

    if (cmdLine.hasOption("help")) {
      helpFormatter.printHelp("DocumentIndexer", opts);
      System.exit(0);
    }

    if (cmdLine.hasOption("verbose")) {
      // With help from http://stackoverflow.com/questions/23434252/programmatically-change-log-level-in-log4j2
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      Configuration ctxConfig = ctx.getConfiguration();
      LoggerConfig logConfig = ctxConfig.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
      logConfig.setLevel(Level.DEBUG);;
      ctx.updateLoggers();
      LOGGER.debug("Verbose logging enabled");
    }

    String inputFileOrDir = cmdLine.getOptionValue("input");
    File splitFileOrDir = new File(inputFileOrDir);
    if (!(splitFileOrDir.exists())) {
      LOGGER.error("Unable to find directory at " + inputFileOrDir);
      System.exit(1);
    }

    try (FileWriter writer = new FileWriter(cmdLine.getOptionValue("output"))) {
      PatentScorer scorer = new PatentScorer(PatentModel.getModel(), writer);
      PatentCorpusReader corpusReader = new PatentCorpusReader(scorer, splitFileOrDir);
      corpusReader.readPatentCorpus();
    }
  }

  public static final String LINE_SEPARATOR = System.lineSeparator();

  private PatentModel patentModel;
  private FileWriter outputWriter;
  private ObjectMapper objectMapper;

  public PatentScorer(PatentModel patentModel, FileWriter outputWriter) {
    this.patentModel = patentModel;
    this.outputWriter = outputWriter;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void processPatentText(File patentFile, Reader patentTextReader, int patentTextLength)
      throws IOException, ParserConfigurationException,
      SAXException, TransformerConfigurationException,
      TransformerException, XPathExpressionException {

    System.out.println("Patent text length: " + patentTextLength);
    CharBuffer buff = CharBuffer.allocate(patentTextLength);
    int read = patentTextReader.read(buff);
    System.out.println("Read bytes: " + read);
    patentTextReader.reset();
    String fullContent = new String(buff.array());

    PatentDocument patentDocument = PatentDocument.patentDocumentFromStream(
        new ReaderInputStream(patentTextReader, Charset.forName("utf-8")));
    if (patentDocument == null) {
      LOGGER.info("Found non-patent type document, skipping.");
      return;
    }

    double pr = this.patentModel.ProbabilityOf(fullContent);
    this.outputWriter.write(objectMapper.writeValueAsString(new ClassificationResult(patentDocument.getFileId(), pr)));
    this.outputWriter.write(LINE_SEPARATOR);
    System.out.println("Doc " + patentDocument.getFileId() + " has score " + pr);
  }

  public static class ClassificationResult {
    @JsonProperty("doc_id")
    protected String docId;
    @JsonProperty("score")
    protected Double score;

    protected ClassificationResult() {
    }

    public ClassificationResult(String docId, Double score) {
      this.docId = docId;
      this.score = score;
    }

    public String getDocId() {
      return docId;
    }

    public Double getScore() {
      return score;
    }
  }
}
