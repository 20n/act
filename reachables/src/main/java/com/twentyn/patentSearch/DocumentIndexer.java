package com.twentyn.patentSearch;

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
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * This class reads concatenated USPTO XML patent documents (as distributed in Google's patent full-text corpus) and
 * indexes their contents with minimal normalization.  Lucene is used for indexing; the output of this class's main
 * method is a Lucene index of the contents of any specified patent files.  A single file or directory of files can be
 * supplied as input.  If a directory is specified, this class will read and index any .zip files in that directory,
 * assuming these to be compressed USPTO dumps.
 */
public class DocumentIndexer implements PatentProcessor {
  public static final Logger LOGGER = LogManager.getLogger(DocumentIndexer.class);

  public static void main(String[] args) throws Exception {
    System.out.println("Starting up...");
    System.out.flush();
    Options opts = new Options();
    opts.addOption(Option.builder("i").
        longOpt("input").hasArg().required().desc("Input file or directory to index").build());
    opts.addOption(Option.builder("x").
        longOpt("index").hasArg().required().desc("Path to index file to generate").build());
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
      logConfig.setLevel(Level.DEBUG);

      ctx.updateLoggers();
      LOGGER.debug("Verbose logging enabled");
    }

    LOGGER.info("Opening index at " + cmdLine.getOptionValue("index"));
    Directory indexDir = FSDirectory.open(new File(cmdLine.getOptionValue("index")).toPath());

    /* The standard analyzer is too aggressive with chemical entities (it strips structural annotations, for one
     * thing), and the whitespace analyzer doesn't do any case normalization or stop word elimination.  This custom
     * analyzer appears to treat chemical entities better than the standard analyzer without admitting too much
     * cruft to the index. */
    Analyzer analyzer = CustomAnalyzer.builder().
        withTokenizer("whitespace").
        addTokenFilter("lowercase").
        addTokenFilter("stop").
        build();

    IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
    writerConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    writerConfig.setRAMBufferSizeMB(1 << 10);
    IndexWriter indexWriter = new IndexWriter(indexDir, writerConfig);

    String inputFileOrDir = cmdLine.getOptionValue("input");
    File splitFileOrDir = new File(inputFileOrDir);
    if (!(splitFileOrDir.exists())) {
      LOGGER.error("Unable to find directory at " + inputFileOrDir);
      System.exit(1);
    }

    DocumentIndexer indexer = new DocumentIndexer(indexWriter);
    PatentCorpusReader corpusReader = new PatentCorpusReader(indexer, splitFileOrDir);
    corpusReader.readPatentCorpus();
    indexer.commitAndClose();
  }

  IndexWriter indexWriter;

  public DocumentIndexer(IndexWriter indexWriter) {
    this.indexWriter = indexWriter;
  }

  @Override
  public void processPatentText(File patentFile, Reader patentTextReader, int patentTextLength)
      throws IOException, ParserConfigurationException,
      SAXException, TransformerConfigurationException,
      TransformerException, XPathExpressionException {
    PatentDocument patentDocument = PatentDocument.patentDocumentFromXMLStream(
        new ReaderInputStream(patentTextReader, Charset.forName("utf-8")));
    if (patentDocument == null) {
      LOGGER.info("Found non-patent type document, skipping.");
      return;
    }
    Document doc = patentDocToLuceneDoc(patentFile, patentDocument);
    LOGGER.debug("Adding document: " + doc.get("id"));
    this.indexWriter.addDocument(doc);
    this.indexWriter.commit();
  }

  public Document patentDocToLuceneDoc(File path, PatentDocument patentDoc) {
    // With help from https://lucene.apache.org/core/5_2_0/demo/src-html/org/apache/lucene/demo/IndexFiles.html
    Document doc = new Document();
    doc.add(new StringField("file_name", path.getName(), Field.Store.YES));
    doc.add(new StringField("id", patentDoc.getFileId(), Field.Store.YES));
    doc.add(new StringField("grant_date", patentDoc.getGrantDate(), Field.Store.YES));
    doc.add(new StringField("main_classification", patentDoc.getMainClassification(), Field.Store.YES));
    doc.add(new TextField("title", patentDoc.getTitle(), Field.Store.YES));
    doc.add(new TextField("claims", StringUtils.join("\n", patentDoc.getClaimsText()), Field.Store.NO));
    doc.add(new TextField("description", StringUtils.join("\n", patentDoc.getTextContent()), Field.Store.NO));

    // TODO: verify that these are searchable as expected.
    for (String cls : patentDoc.getFurtherClassifications()) {
      doc.add(new SortedSetDocValuesField("further_classification", new BytesRef(cls)));
    }
    for (String cls : patentDoc.getSearchedClassifications()) {
      doc.add(new SortedSetDocValuesField("searched_classification", new BytesRef(cls)));
    }

    return doc;
  }

  public void commitAndClose() throws IOException {
    if (!indexWriter.isOpen()) {
      return;
    }
    indexWriter.commit();
    indexWriter.close();
  }
}
