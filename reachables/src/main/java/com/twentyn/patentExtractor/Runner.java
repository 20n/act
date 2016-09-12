package com.twentyn.patentExtractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.cam.ch.wwmm.chemicaltagger.ChemistryPOSTagger;
import uk.ac.cam.ch.wwmm.chemicaltagger.Rule;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * This class applies the PatentDocument parser and PatentDocumentFeatures feature extractor to a patent document or
 * documents.
 * TODO: convert this to read raw ZIPs and output some compressed corpus of results.
 */
public class Runner {
  public static final Logger LOGGER = LogManager.getLogger(Runner.class);

  public static void main(String args[]) throws Exception {
    System.out.println("Runner starting up.");
    System.out.flush();

    if (args.length == 0) {
      LOGGER.error("Must specify a directory to search.");
      System.exit(1);
    }

    List<File> toProcess = null;

    File splitFileOrDir = new File(args[0]);
    if (!(splitFileOrDir.exists())) {
      LOGGER.error("Unable to find directory at " + args[0]);
      System.exit(1);
    }

    if (splitFileOrDir.isDirectory()) {
      final Pattern filenamePattern = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9\\.]+(\\.gz)?$");
      final Pattern resultPattern = Pattern.compile("\\.out$");
      FileFilter filter = new FileFilter() {
        public boolean accept(File pathname) {
          return pathname.isFile() &&
              filenamePattern.matcher(pathname.getName()).matches() &&
              !resultPattern.matcher(pathname.getName()).find();
        }
      };
      toProcess = Arrays.asList(splitFileOrDir.listFiles(filter));
    } else {
      toProcess = Collections.singletonList(splitFileOrDir);
    }

    Collections.sort(toProcess, new Comparator<File>() {
      @Override
      public int compare(File o1, File o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    LOGGER.info("Setting up ChemistryPOSTagger");

    /* Important note: ChemistryPOSTagger is *not* thread safe.  As a singleton that configures itself on startup,
     * it doesn't seem feasible to use it safely in a multi-threaded environment.  This class is necessarily
     * serial--any parallelism will need to be implemented at the process level. */
    final ChemistryPOSTagger posTagger = ChemistryPOSTagger.getDefaultInstance();
    List<Rule> rules = posTagger.getRegexTagger().getRules();
    /* Add chemtagger rules for E. coli and S. cerevisiae, as we want to look for biosynthesis-related documents that
     * reference them specifically.  TODO: add more organisms and other potentially interesting patterns. */
    rules.add(new Rule("NN-ORGANISM", "e\\. +coli", true));
    rules.add(new Rule("NN-ORGANISM", "s\\. +cerevisiae", true));
    posTagger.getRegexTagger().setRules(rules);

    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    LOGGER.info("Processing " + toProcess.size() + " lcms.");

    final AtomicInteger processed = new AtomicInteger(0);
    final int toProcessSize = toProcess.size();
    for (final File splitFile : toProcess) {
      int localProcessed;
      synchronized (processed) {
        localProcessed = processed.incrementAndGet();
      }

      File outputFile = new File(splitFile.getParent(), splitFile.getName().concat(".out"));
      LOGGER.info("Processing file: " + splitFile.getAbsolutePath() +
          " -> " + outputFile.getAbsolutePath() + " (" + localProcessed + "/" + toProcessSize + ")");

      if (outputFile.exists()) {
        LOGGER.info("!! Output exists for " + outputFile.getAbsolutePath() + ", skipping.");
        continue;
      }

      try {
        PatentDocument pdoc = PatentDocument.patentDocumentFromXMLFile(splitFile);
        if (pdoc == null) {
          LOGGER.warn("No patent doc produced from " + splitFile + ", skipping.");
          continue;
        }
        PatentDocumentFeatures pdf = PatentDocumentFeatures.extractPatentDocumentFeatures(posTagger, pdoc);

        OutputStream os = new GZIPOutputStream(new FileOutputStream(outputFile));
        mapper.writeValue(os, pdf);
        os.flush();
        os.close();
      } catch (Exception e) { // NB: this is a Very Bad Thing, but we can't throw from here.
        LOGGER.error("Caught exception when processing " + splitFile, e);
      }
    }

    LOGGER.info("Done.");
  }
}
