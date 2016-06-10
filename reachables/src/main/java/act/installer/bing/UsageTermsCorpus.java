package act.installer.bing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents the Usage Terms corpus, manually curated by Chris and containing keywords to look for in Bing Search
 * results descriptions.
 */

public class UsageTermsCorpus {
  private static final Logger LOGGER = LogManager.getFormatterLogger(UsageTermsCorpus.class);

  private String usageTermsFileName;

  private final Class INSTANCE_CLASS_LOADER = getClass();

  private HashSet<String> usageTerms = new HashSet<>();

  public UsageTermsCorpus(String usageTermsFileName) {
    this.usageTermsFileName = usageTermsFileName;
  }

  public void buildCorpus() throws IOException {
    BufferedReader usageTermsReader = getUsageTermsReader();

    while (usageTermsReader.ready()) {
      // TODO: all usage terms are currently converted to lowercase. Case like "LED" are not well handled.
      String usageTerm = usageTermsReader.readLine().toLowerCase();
      if (!usageTerm.startsWith("\\\\")) {
        usageTerms.add(usageTerm);
      } else {
        LOGGER.info("%s was ignored from the usage terms corpus.", usageTerm);
      }
    }
  }

  private BufferedReader getUsageTermsReader() throws FileNotFoundException {
    File usageTermsFile = new File(INSTANCE_CLASS_LOADER.getResource(usageTermsFileName).getFile());
    FileInputStream usageTermsInputStream = new FileInputStream(usageTermsFile);
    BufferedReader usageTermsReader = new BufferedReader(new InputStreamReader(usageTermsInputStream));
    return usageTermsReader;
  }

  public HashSet<String> getUsageTerms() {
    return usageTerms;
  }
}
