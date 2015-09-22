package com.twentyn.patentTextProcessor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.twentyn.patentExtractor.PatentCorpusReader;
import com.twentyn.patentExtractor.PatentDocument;
import com.twentyn.patentExtractor.PatentProcessor;
import edu.emory.clir.clearnlp.component.utils.NLPUtils;
import edu.emory.clir.clearnlp.tokenization.AbstractTokenizer;
import edu.emory.clir.clearnlp.util.lang.TLanguage;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class WordCountProcessor implements PatentProcessor{

  public static final Logger LOGGER = Logger.getLogger(PatentProcessor.class);

  public static class PatentDocumentWordCount {
    @JsonProperty("patent_file_path")
    String patentFilePath;
    @JsonProperty("word_Count")
    Map<String, Integer> wordCount;

    public PatentDocumentWordCount(String patentFilePath, Map<String, Integer> wordCount) {
      this.patentFilePath = patentFilePath;
      this.wordCount = wordCount;
    }

    public String getPatentFilePath() {
      return patentFilePath;
    }

    public Map<String, Integer> getWordCount() {
      return wordCount;
    }
  }

  ObjectMapper objectMapper = new ObjectMapper();
  {
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }

  private AbstractTokenizer tokenizer;
  private Set<String> stopWords = new HashSet<>();
  public WordCountProcessor() {
    this.tokenizer = NLPUtils.getTokenizer(TLanguage.ENGLISH);
  }

  public void readStopWords(File path) throws IOException{
    try (BufferedReader r = new BufferedReader(new FileReader(path))) {
      String line = null;
      while ((line = r.readLine()) != null) {
        this.stopWords.add(line);
      }
    }
  }

  public static final Pattern EXCLUDE_NUMBERS = Pattern.compile("^[+\\-]?[0-9\\.]+$");

  public void processPatentText(File patentFile, Reader patentTextReader, int patentTextLength)
      throws IOException, ParserConfigurationException, SAXException,
      TransformerConfigurationException, TransformerException, XPathExpressionException {

    PatentDocument doc = PatentDocument.patentDocumentFromXMLStream(new ReaderInputStream(patentTextReader));
    if (doc == null) {
      LOGGER.warn(String.format("Got null patent document object for patent at %s", patentFile.getAbsolutePath()));
      return;
    }
    // Put all the text back together into one big string.  Sigh.
    List<String> textFields = new ArrayList<String>(1 + doc.getClaimsText().size() + doc.getTextContent().size());
    textFields.add(doc.getTitle());
    textFields.addAll(doc.getClaimsText());
    textFields.addAll(doc.getTextContent());
    String joinedText = StringUtils.join(textFields, "\n");

    // TODO: parallelize this.  Because come on, it's the nineties and Map Reduce is almost a thousand years old.
    Map<String, Integer> wordCount = new HashMap<>();
    List<String> words = this.tokenizer.tokenize(joinedText);
    for (String word : words) {
      if (EXCLUDE_NUMBERS.matcher(word).matches()) {
        continue; // Ignore words that are just numbers (with decimals)
      }

      word = word.toLowerCase();
      if (stopWords.contains(word)) {
        continue; // Ignore stop words;
      }

      Integer count = wordCount.get(word);
      if (count == null) {
        count = 1;
      }
      wordCount.put(word, count + 1);
    }

    PatentDocumentWordCount pdwc = new PatentDocumentWordCount(patentFile.getAbsolutePath(), wordCount);
    System.out.println(objectMapper.writeValueAsString(pdwc));
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting up...");
    System.out.flush();
    Options opts = new Options();
    opts.addOption(Option.builder("i").
        longOpt("input").hasArg().required().desc("Input file or directory to score").build());
    opts.addOption(Option.builder("h").longOpt("help").desc("Print this help message and exit").build());
    opts.addOption(Option.builder("v").longOpt("verbose").desc("Print verbose log output").build());


    HelpFormatter helpFormatter = new HelpFormatter();
    CommandLineParser cmdLineParser = new DefaultParser();
    CommandLine cmdLine = null;
    try {
      cmdLine = cmdLineParser.parse(opts, args);
    } catch (ParseException e) {
      System.out.println("Caught exception when parsing command line: " + e.getMessage());
      helpFormatter.printHelp("WordCountProcessor", opts);
      System.exit(1);
    }

    if (cmdLine.hasOption("help")) {
      helpFormatter.printHelp("DocumentIndexer", opts);
      System.exit(0);
    }

    String inputFileOrDir = cmdLine.getOptionValue("input");
    File splitFileOrDir = new File(inputFileOrDir);
    if (!(splitFileOrDir.exists())) {
      LOGGER.error("Unable to find directory at " + inputFileOrDir);
      System.exit(1);
    }

    WordCountProcessor wcp = new WordCountProcessor();
    PatentCorpusReader corpusReader = new PatentCorpusReader(wcp, splitFileOrDir);
    corpusReader.readPatentCorpus();
  }
}
