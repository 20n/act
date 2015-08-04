package com.twentyn.patentSearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.twentyn.patentExtractor.PatentDocument;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This class reads concatenated USPTO XML patent documents (as distributed in Google's patent full-text corpus) and
 * indexes their contents with minimal normalization.  Lucene is used for indexing; the output of this class's main
 * method is a Lucene index of the contents of any specified patent files.  A single file or directory of files can be
 * supplied as input.  If a directory is specified, this class will read and index any .zip files in that directory,
 * assuming these to be compressed USPTO dumps.
 */
public class DocumentIndexer {
    public static final Logger LOGGER = LogManager.getLogger(DocumentIndexer.class);
    public static final String DOCUMENT_DELIMITER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    public static final Character LINE_SEPARATOR = Character.LINE_SEPARATOR;

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
            logConfig.setLevel(Level.DEBUG);;
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

        List<File> toProcess = null;

        String inputFileOrDir = cmdLine.getOptionValue("input");
        File splitFileOrDir = new File(inputFileOrDir);
        if (!(splitFileOrDir.exists())) {
            LOGGER.error("Unable to find directory at " + inputFileOrDir);
            System.exit(1);
        }

        if (splitFileOrDir.isDirectory()) {
            final Pattern filenamePattern = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9\\.]+$");
            final Pattern zipFilePattern = Pattern.compile("\\.zip$");
            FileFilter filter = new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.isFile() &&
                            filenamePattern.matcher(pathname.getName()).matches() &&
                            zipFilePattern.matcher(pathname.getName()).find();
                }
            };
            toProcess = Arrays.asList(splitFileOrDir.listFiles(filter));
            Collections.sort(toProcess, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
        } else {
            toProcess = Collections.singletonList(splitFileOrDir);
        }
        LOGGER.info("Processing " + toProcess.size() + " files");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        for (File currentFile : toProcess) {
            LOGGER.info("*** Processing file " + currentFile.getAbsolutePath());
            if (currentFile.getName().endsWith(".zip")) {
                LOGGER.debug("Zip compression detected.");
                // With help from
                // http://stackoverflow.com/questions/15667125/read-content-from-files-which-are-inside-zip-file
                ZipFile zipFile = new ZipFile(currentFile);
                Enumeration<? extends ZipEntry> entries = zipFile.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    InputStream is = zipFile.getInputStream(entry);
                    LOGGER.debug("Zip input stream is available: " + is.available());
                    indexAndClose(indexWriter, currentFile.getName(), is);
                }
            } else {
                LOGGER.info("Processing file: " + currentFile);
                InputStream is = new FileInputStream(currentFile);
                LOGGER.debug("Input stream available: " + is.available());
                indexAndClose(indexWriter, currentFile.getName(), is);
            }
        }
        indexWriter.close();
        indexDir.close();
    }

    public static void indexAndClose(IndexWriter idxw, String fileName, InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        LOGGER.debug("Input file reader is ready: " + reader.ready());

        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        int processed = 0;
        // TODO: Is there still no better way to do accomplish this w/ v7?
        while ((line = reader.readLine()) != null) {
            if (line.equals(DOCUMENT_DELIMITER)) {
                processDocument(idxw, fileName, stringBuilder);
                stringBuilder = new StringBuilder(line).append(LINE_SEPARATOR);
                processed++;
                if ((processed % 100) == 0) {
                    LOGGER.info("Processed " + processed + " documents");
                }
            } else {
                stringBuilder.append(line).append(LINE_SEPARATOR);
            }
        }
        processDocument(idxw, fileName, stringBuilder);
        LOGGER.info("Found" + processed + " documents in " + fileName);
        reader.close();
    }

    public static void processDocument(IndexWriter idxw, String fileName, StringBuilder stringBuilder)
            throws Exception {
        if (stringBuilder.length() == 0) {
            return;
        }
        String documentText = stringBuilder.toString();
        LOGGER.debug("Found complete XML document with length " + documentText.length());
        PatentDocument patentDocument = PatentDocument.patentDocumentFromString(documentText);
        if (patentDocument == null) {
            LOGGER.info("Found non-patent type document, skipping.");
            return;
        }
        Document doc = patentDocToLuceneDoc(fileName, patentDocument);
        LOGGER.debug("Adding document: " + doc.get("id"));
        idxw.addDocument(doc);
        idxw.commit();
    }

    public static Document patentDocToLuceneDoc(String fileName, PatentDocument patentDoc) throws Exception {
        // With help from https://lucene.apache.org/core/5_2_0/demo/src-html/org/apache/lucene/demo/IndexFiles.html
        Document doc = new Document();
        doc.add(new StringField("file_name", fileName, Field.Store.YES));
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
}
