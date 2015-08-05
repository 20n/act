package com.twentyn.patentSearch;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
            logConfig.setLevel(Level.DEBUG);;
            ctx.updateLoggers();
            LOGGER.debug("Verbose logging enabled");
        }


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

                List<String> queries = null;
                if (cmdLine.hasOption("query")) {
                    queries = Collections.singletonList(cmdLine.getOptionValue("query"));
                } else if (cmdLine.hasOption("list-file")) {
                    queries = new LinkedList<>();
                    BufferedReader r = new BufferedReader(new FileReader(cmdLine.getOptionValue("list-file")));
                    String line = null;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            queries.add(line);
                        }
                    }
                    r.close();
                }

                if (queries == null || queries.size() == 0) {
                    LOGGER.error("Found no queries to run.");
                }

                for (String rawQueryString : queries) {
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

                    TopDocs topDocs = searcher.search(query, 100);
                    ScoreDoc[] scoreDocs = topDocs.scoreDocs;
                    if (scoreDocs.length == 0) {
                        LOGGER.info("Search returned no results.");
                    }
                    for (int i = 0; i < scoreDocs.length; i++) {
                        ScoreDoc scoreDoc = scoreDocs[i];
                        Document doc = indexReader.document(scoreDoc.doc);
                        LOGGER.info("Doc " + i + ": " + scoreDoc.doc + ", score " + scoreDoc.score + ": " +
                                doc.get("id") + ", " + doc.get("title"));
                    }
                    LOGGER.info("----- Done with query " + query.toString());
                }
            }
        }
    }
}
