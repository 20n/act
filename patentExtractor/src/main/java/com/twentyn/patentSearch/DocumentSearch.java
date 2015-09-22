package com.twentyn.patentSearch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * This class implements a naive phrase searcher over a specified Lucene index.  It can also dump the contents of a
 * Lucene index (documents or terms for a given field) for diagnostic purposes.
 */
public class DocumentSearch {
  public static final Logger LOGGER = LogManager.getLogger(DocumentSearch.class);

  public static void main(String[] args) throws Exception {
    System.out.println("Starting up...");
    System.out.flush();
    Options opts = new Options();
    opts.addOption(Option.builder("x").
        longOpt("index").hasArg().required().desc("Path to index file to read").build());
    opts.addOption(Option.builder("h").longOpt("help").desc("Print this help message and exit").build());
    opts.addOption(Option.builder("v").longOpt("verbose").desc("Print verbose log output").build());

    opts.addOption(Option.builder("f").
        longOpt("field").hasArg().desc("The indexed field to search").build());
    opts.addOption(Option.builder("q").
        longOpt("query").hasArg().desc("The query to use when searching").build());
    opts.addOption(Option.builder("l").
        longOpt("list-file").hasArg().desc("A file containing a list of queries to run in sequence").build());
    opts.addOption(Option.builder("e").
        longOpt("enumerate").desc("Enumerate the documents in the index").build());
    opts.addOption(Option.builder("d").
        longOpt("dump").hasArg().desc("Dump terms in the document index for a specified field").build());
    opts.addOption(Option.builder("o").
        longOpt("output").hasArg().desc("Write results JSON to this file.").build());
    opts.addOption(Option.builder("n").
        longOpt("inchi-field").hasArg().
        desc("The index of the InChI field if an input TSV is specified.").build());
    opts.addOption(Option.builder("s").
        longOpt("synonym-field").hasArg().
        desc("The index of the chemical synonym field if an input TSV is specified.").build());

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

    if (!(cmdLine.hasOption("enumerate") || cmdLine.hasOption("dump") ||
        (cmdLine.hasOption("field") && (cmdLine.hasOption("query") || cmdLine.hasOption("list-file"))))) {
      System.out.println("Must specify one of 'enumerate', 'dump', or 'field' + {'query', 'list-file'}");
      helpFormatter.printHelp("DocumentIndexer", opts);
      System.exit(1);
    }

    if (cmdLine.hasOption("verbose")) {
      // With help from http://stackoverflow.com/questions/23434252/programmatically-change-log-level-in-log4j2
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      Configuration ctxConfig = ctx.getConfiguration();
      LoggerConfig logConfig = ctxConfig.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
      logConfig.setLevel(Level.DEBUG);
      ;
      ctx.updateLoggers();
      LOGGER.debug("Verbose logging enabled");
    }

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

    LOGGER.info("Opening index at " + cmdLine.getOptionValue("index"));

    try (
        Directory indexDir = FSDirectory.open(new File(cmdLine.getOptionValue("index")).toPath());
        IndexReader indexReader = DirectoryReader.open(indexDir);
    ) {
      if (cmdLine.hasOption("enumerate")) {
        // Enumerate all documents in the index.
        // With help from
        // http://stackoverflow.com/questions/2311845/is-it-possible-to-iterate-through-documents-stored-in-lucene-index
        for (int i = 0; i < indexReader.maxDoc(); i++) {
          Document doc = indexReader.document(i);
          LOGGER.info("Doc " + i + ":");
          LOGGER.info(doc);
        }
      } else if (cmdLine.hasOption("dump")) {
        // Dump indexed terms for a specific field.
        // With help from http://stackoverflow.com/questions/11148036/find-list-of-terms-indexed-by-lucene
        Terms terms = SlowCompositeReaderWrapper.wrap(indexReader).terms(cmdLine.getOptionValue("dump"));
        LOGGER.info("Has positions: " + terms.hasPositions());
        LOGGER.info("Has offsets:   " + terms.hasOffsets());
        LOGGER.info("Has freqs:     " + terms.hasFreqs());
        LOGGER.info("Stats:         " + terms.getStats());
        LOGGER.info(terms);
        TermsEnum termsEnum = terms.iterator();
        BytesRef br = null;
        while ((br = termsEnum.next()) != null) {
          LOGGER.info("  " + br.utf8ToString());
        }

      } else {
        IndexSearcher searcher = new IndexSearcher(indexReader);
        String field = cmdLine.getOptionValue("field");

        List<Pair<String, String>> queries = null;
        if (cmdLine.hasOption("query")) {
          queries = Collections.singletonList(Pair.of("", cmdLine.getOptionValue("query")));
        } else if (cmdLine.hasOption("list-file")) {
          if (!(cmdLine.hasOption("inchi-field") && cmdLine.hasOption("synonym-field"))) {
            LOGGER.error("Must specify both inchi-field and synonym-field when using list-file.");
            System.exit(1);
          }
          Integer inchiField = Integer.parseInt(cmdLine.getOptionValue("inchi-field"));
          Integer synonymField = Integer.parseInt(cmdLine.getOptionValue("synonym-field"));

          queries = new LinkedList<>();
          BufferedReader r = new BufferedReader(new FileReader(cmdLine.getOptionValue("list-file")));
          String line;
          while ((line = r.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
              // TODO: use a proper TSV reader; this is intentionally terrible as is.
              String[] fields = line.split("\t");
              queries.add(Pair.of(fields[inchiField].replace("\"", ""), fields[synonymField]));
            }
          }
          r.close();
        }

        if (queries == null || queries.size() == 0) {
          LOGGER.error("Found no queries to run.");
          return;
        }

        List<SearchResult> searchResults = new ArrayList<>(queries.size());
        for (Pair<String, String> queryPair : queries) {
          String inchi = queryPair.getLeft();
          String rawQueryString = queryPair.getRight();
                    /* The Lucene query parser interprets the kind of structural annotations we see in chemical entities
                     * as query directives, which is not what we want at all.  Phrase queries seem to work adequately
                     * with the analyzer we're currently using. */
          String queryString = rawQueryString.trim().toLowerCase();
          String[] parts = queryString.split("\\s+");
          PhraseQuery query = new PhraseQuery();
          for (String p : parts) {
            query.add(new Term(field, p));
          }
          LOGGER.info("Running query: " + query.toString());

          BooleanQuery bq = new BooleanQuery();
          bq.add(query, BooleanClause.Occur.MUST);
          bq.add(new TermQuery(new Term(field, "yeast")), BooleanClause.Occur.SHOULD);
          bq.add(new TermQuery(new Term(field, "ferment")), BooleanClause.Occur.SHOULD);
          bq.add(new TermQuery(new Term(field, "fermentation")), BooleanClause.Occur.SHOULD);
          bq.add(new TermQuery(new Term(field, "fermentive")), BooleanClause.Occur.SHOULD);
          bq.add(new TermQuery(new Term(field, "saccharomyces")), BooleanClause.Occur.SHOULD);

          LOGGER.info("  Full query: " + bq.toString());

          TopDocs topDocs = searcher.search(bq, 100);
          ScoreDoc[] scoreDocs = topDocs.scoreDocs;
          if (scoreDocs.length == 0) {
            LOGGER.info("Search returned no results.");
          }
          List<ResultDocument> results = new ArrayList<>(scoreDocs.length);
          for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            Document doc = indexReader.document(scoreDoc.doc);
            LOGGER.info("Doc " + i + ": " + scoreDoc.doc + ", score " + scoreDoc.score + ": " +
                doc.get("id") + ", " + doc.get("title"));
            results.add(
                new ResultDocument(scoreDoc.doc, scoreDoc.score, doc.get("title"), doc.get("id"), null));
          }
          LOGGER.info("----- Done with query " + query.toString());
          // TODO: reduce memory usage when not writing results to an output file.
          searchResults.add(new SearchResult(inchi, rawQueryString, bq, results));
        }

        if (cmdLine.hasOption("output")) {
          try (
              FileWriter writer = new FileWriter(cmdLine.getOptionValue("output"));
          ) {
            writer.write(objectMapper.writeValueAsString(searchResults));
          }
        }
      }
    }
  }

  public static class SearchResult {
    @JsonView(DocumentSearch.class)
    @JsonProperty("inchi")
    protected String inchi;
    @JsonProperty("synonym")
    protected String synonym;
    @JsonProperty("query")
    protected String queryString;
    @JsonProperty("results")
    protected List<ResultDocument> results;

    protected SearchResult() {
    }

    public SearchResult(String inchi, String synonym, Query query, List<ResultDocument> results) {
      this.inchi = inchi;
      this.synonym = synonym;
      this.queryString = query.toString();
      this.results = results;
    }

    public String getInchi() {
      return inchi;
    }

    public String getSynonym() {
      return synonym;
    }

    public String getQueryString() {
      return queryString;
    }

    public List<ResultDocument> getResults() {
      return results;
    }
  }

  public static class ResultDocument {
    @JsonProperty("index_id")
    protected Integer index;
    @JsonProperty("score")
    protected Float score;
    @JsonProperty("title")
    protected String title;
    @JsonProperty("doc_id")
    protected String docId;
    @JsonProperty("classifier_score")
    protected Double classifierScore;

    protected ResultDocument() {
    }

    public ResultDocument(Integer indexId, Float score, String title, String docId, Double classifierScore) {
      this.index = indexId;
      this.score = score;
      this.title = title;
      this.docId = docId;
      this.classifierScore = classifierScore;
    }

    public Integer getIndex() {
      return index;
    }

    public Float getScore() {
      return score;
    }

    public String getTitle() {
      return title;
    }

    public String getDocId() {
      return docId;
    }

    public Double getClassifierScore() {
      return classifierScore;
    }

    public void setClassifierScore(Double classifierScore) {
      this.classifierScore = classifierScore;
    }
  }
}
