package com.twentyn.patentScorer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.twentyn.patentSearch.DocumentSearch;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ScoreMerger {
  public static final Logger LOGGER = LogManager.getLogger(ScoreMerger.class);

  public static void main(String[] args) throws Exception {
    System.out.println("Starting up...");
    System.out.flush();
    Options opts = new Options();
    opts.addOption(Option.builder("h").longOpt("help").desc("Print this help message and exit").build());

    opts.addOption(Option.builder("r").
        longOpt("results").required().hasArg().desc("A directory of search results to read").build());
    opts.addOption(Option.builder("s").
        longOpt("scores").required().hasArg().desc("A directory of patent classification scores to read").build());
    opts.addOption(Option.builder("o").
        longOpt("output").required().hasArg().desc("The output file where results will be written.").build());

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
    File scoresDirectory = new File(cmdLine.getOptionValue("scores"));
    if (cmdLine.getOptionValue("scores") == null || !scoresDirectory.isDirectory()) {
      LOGGER.error("Not a directory of score lcms: " + cmdLine.getOptionValue("scores"));
    }

    File resultsDirectory = new File(cmdLine.getOptionValue("results"));
    if (cmdLine.getOptionValue("results") == null || !resultsDirectory.isDirectory()) {
      LOGGER.error("Not a directory of results lcms: " + cmdLine.getOptionValue("results"));
    }

    FileWriter outputWriter = new FileWriter(cmdLine.getOptionValue("output"));

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

    FilenameFilter jsonFilter = new FilenameFilter() {
      public final Pattern JSON_PATTERN = Pattern.compile("\\.json$");

      public boolean accept(File dir, String name) {
        return JSON_PATTERN.matcher(name).find();
      }
    };

    Map<String, PatentScorer.ClassificationResult> scores = new HashMap<>();
    LOGGER.info("Reading scores from directory at " + scoresDirectory.getAbsolutePath());
    for (File scoreFile : scoresDirectory.listFiles(jsonFilter)) {
      BufferedReader reader = new BufferedReader(new FileReader(scoreFile));
      int count = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        PatentScorer.ClassificationResult res = objectMapper.readValue(line, PatentScorer.ClassificationResult.class);
        scores.put(res.docId, res);
        count++;
      }
      LOGGER.info("Read " + count + " scores from " + scoreFile.getAbsolutePath());
    }

    Map<String, List<DocumentSearch.SearchResult>> synonymsToResults = new HashMap<>();
    Map<String, List<DocumentSearch.SearchResult>> inchisToResults = new HashMap<>();
    LOGGER.info("Reading results from directory at " + resultsDirectory);
    // With help from http://stackoverflow.com/questions/6846244/jackson-and-generic-type-reference.
    JavaType resultsType =
        objectMapper.getTypeFactory().constructCollectionType(List.class, DocumentSearch.SearchResult.class);

    List<File> resultsFiles = Arrays.asList(resultsDirectory.listFiles(jsonFilter));
    Collections.sort(resultsFiles, new Comparator<File>() {
      @Override
      public int compare(File o1, File o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (File resultsFile : resultsFiles) {
      BufferedReader reader = new BufferedReader(new FileReader(resultsFile));
      CharBuffer buffer = CharBuffer.allocate(Long.valueOf(resultsFile.length()).intValue());
      int bytesRead = reader.read(buffer);
      LOGGER.info("Read " + bytesRead + " bytes from " + resultsFile.getName() +
          " (length is " + resultsFile.length() + ")");
      List<DocumentSearch.SearchResult> results =
          objectMapper.readValue(new CharArrayReader(buffer.array()), resultsType);

      LOGGER.info("Read " + results.size() + " results from " + resultsFile.getAbsolutePath());

      int count = 0;
      for (DocumentSearch.SearchResult sres : results) {
        for (DocumentSearch.ResultDocument resDoc : sres.getResults()) {
          String docId = resDoc.getDocId();
          PatentScorer.ClassificationResult classificationResult = scores.get(docId);
          if (classificationResult == null) {
            LOGGER.warn("No classification result found for " + docId);
          } else {
            resDoc.setClassifierScore(classificationResult.getScore());
          }
        }
        if (!synonymsToResults.containsKey(sres.getSynonym())) {
          synonymsToResults.put(sres.getSynonym(), new ArrayList<DocumentSearch.SearchResult>());
        }
        synonymsToResults.get(sres.getSynonym()).add(sres);
        count++;
        if (count % 1000 == 0) {
          LOGGER.info("Processed " + count + " search result documents");
        }
      }
    }

    Comparator<DocumentSearch.ResultDocument> resultDocumentComparator =
        new Comparator<DocumentSearch.ResultDocument>() {
          @Override
          public int compare(DocumentSearch.ResultDocument o1, DocumentSearch.ResultDocument o2) {
            int cmp = o2.getClassifierScore().compareTo(o1.getClassifierScore());
            if (cmp != 0) {
              return cmp;
            }
            cmp = o2.getScore().compareTo(o1.getScore());
            return cmp;
          }
        };

    for (Map.Entry<String, List<DocumentSearch.SearchResult>> entry : synonymsToResults.entrySet()) {
      DocumentSearch.SearchResult newSearchRes = null;
      // Merge all result documents into a single search result.
      for (DocumentSearch.SearchResult sr : entry.getValue()) {
        if (newSearchRes == null) {
          newSearchRes = sr;
        } else {
          newSearchRes.getResults().addAll(sr.getResults());
        }
      }
      if (newSearchRes == null || newSearchRes.getResults() == null) {
        LOGGER.error("Search results for " + entry.getKey() + " are null.");
        continue;
      }
      Collections.sort(newSearchRes.getResults(), resultDocumentComparator);
      if (!inchisToResults.containsKey(newSearchRes.getInchi())) {
        inchisToResults.put(newSearchRes.getInchi(), new ArrayList<DocumentSearch.SearchResult>());
      }
      inchisToResults.get(newSearchRes.getInchi()).add(newSearchRes);
    }

    List<String> sortedKeys = new ArrayList<String>(inchisToResults.keySet());
    Collections.sort(sortedKeys);
    List<GroupedInchiResults> orderedResults = new ArrayList<>(sortedKeys.size());
    Comparator<DocumentSearch.SearchResult> synonymSorter = new Comparator<DocumentSearch.SearchResult>() {
      @Override
      public int compare(DocumentSearch.SearchResult o1, DocumentSearch.SearchResult o2) {
        return o1.getSynonym().compareTo(o2.getSynonym());
      }
    };
    for (String inchi : sortedKeys) {
      List<DocumentSearch.SearchResult> res = inchisToResults.get(inchi);
      Collections.sort(res, synonymSorter);
      orderedResults.add(new GroupedInchiResults(inchi, res));
    }

    objectMapper.writerWithView(Object.class).writeValue(outputWriter, orderedResults);
    outputWriter.close();
  }

  public static class GroupedInchiResults {
    @JsonProperty("inchi")
    protected String inchi;
    @JsonProperty("search_results")
    protected List<DocumentSearch.SearchResult> results;

    protected GroupedInchiResults() {
    }

    public GroupedInchiResults(String inchi, List<DocumentSearch.SearchResult> results) {
      this.inchi = inchi;
      this.results = results;
    }

    public String getInchi() {
      return inchi;
    }

    public List<DocumentSearch.SearchResult> getResults() {
      return results;
    }
  }
}
