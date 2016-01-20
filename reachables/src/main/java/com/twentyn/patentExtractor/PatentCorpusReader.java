package com.twentyn.patentExtractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PatentCorpusReader {
  public static final Logger LOGGER = LogManager.getLogger(PatentCorpusReader.class);
  public static final String DOCUMENT_DELIMITER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
  public static final String LINE_SEPARATOR = System.lineSeparator();

  private PatentProcessor processor;
  private File inputFileOrDir;

  public PatentCorpusReader(PatentProcessor processor, File inputFileOrDir) {
    this.processor = processor;
    this.inputFileOrDir = inputFileOrDir;
  }

  public int readPatentCorpus()
      throws IOException, ParserConfigurationException,
      SAXException, TransformerConfigurationException,
      TransformerException, XPathExpressionException {
    if (!(inputFileOrDir.exists())) {
      LOGGER.error("Unable to find directory at " + inputFileOrDir);
      return 0;
    }

    List<File> toProcess = null;
    if (inputFileOrDir.isDirectory()) {
      // Note: this regex is supposed to handle multiple levels of .'s, as might be produced by the `split` command.
      final Pattern filenamePattern = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9\\.]+$");
      final Pattern zipFilePattern = Pattern.compile("\\.zip$");
      FileFilter filter = new FileFilter() {
        public boolean accept(File pathname) {
          return pathname.isFile() &&
              filenamePattern.matcher(pathname.getName()).matches() &&
              zipFilePattern.matcher(pathname.getName()).find();
        }
      };
      toProcess = Arrays.asList(inputFileOrDir.listFiles(filter));
      Collections.sort(toProcess, new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });
    } else {
      toProcess = Collections.singletonList(inputFileOrDir);
    }
    LOGGER.info("Processing " + toProcess.size() + " files");

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

    for (File currentFile : toProcess) {
      LOGGER.info("Processing file " + currentFile.getAbsolutePath());
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
          BufferedReader reader = new BufferedReader(new InputStreamReader(is));
          splitDocsAndClose(currentFile, reader);
        }
      } else {
        LOGGER.info("Processing file: " + currentFile);
        BufferedReader reader = new BufferedReader(new FileReader(currentFile));
        splitDocsAndClose(currentFile, reader);
      }
    }
    return toProcess.size();
  }

  /**
   * Given a file path (mostly for debugging) and a reader, read in a concatenated patent corpus, split the docs based
   * on a known delimiter, and call this.processor.processPatentText on each document.
   *
   * @param path The patent corpus file being read (mostly for debugging)
   * @param reader A reader for that file (which might be slurping in a compressed stream).
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   * @throws TransformerConfigurationException
   * @throws TransformerException
   * @throws XPathExpressionException
   */
  private void splitDocsAndClose(File path, BufferedReader reader)
      throws IOException, ParserConfigurationException,
      SAXException, TransformerConfigurationException,
      TransformerException, XPathExpressionException {
    LOGGER.debug("Input file reader is ready: " + reader.ready());

    StringBuilder stringBuilder = new StringBuilder();
    String line = null;
    int processed = 0;
    // TODO: Is there still no better way to do accomplish this w/ v7?
    while ((line = reader.readLine()) != null) {
      if (line.equals(DOCUMENT_DELIMITER) && stringBuilder.length() > 0) {
        String content = stringBuilder.toString();
        this.processor.processPatentText(path, new StringReader(content), content.length());
        stringBuilder = new StringBuilder(line).append(LINE_SEPARATOR);
        processed++;
        if ((processed % 100) == 0) {
          LOGGER.info("Processed " + processed + " documents");
        }
      } else {
        stringBuilder.append(line).append(LINE_SEPARATOR);
      }
    }
    if (stringBuilder.length() > 0) {
      String content = stringBuilder.toString();
      processor.processPatentText(path, new StringReader(content), content.length());
      processed++;
    }
    LOGGER.info("Found " + processed + " documents in " + path.getName());
  }
}
