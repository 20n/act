package act.installer.bing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents the Usage Terms corpus, manually curated by Chris and containing keywords to look for in Bing Search
 * results descriptions.
 */

public class UsageTermsCorpus {
  private static final Logger LOGGER = LogManager.getFormatterLogger(UsageTermsCorpus.class);
  private final Class INSTANCE_CLASS_LOADER = getClass();

  private String usageTermsFileName;
  private Set<String> usageTerms = new HashSet<>();

  public Set<String> getUsageTerms() {
    return usageTerms;
  }

  public UsageTermsCorpus(String usageTermsFileName) {
    this.usageTermsFileName = usageTermsFileName;
  }

  public void buildCorpus() throws IOException {
    BufferedReader usageTermsReader = getUsageTermsReader();

    while (usageTermsReader.ready()) {
      // TODO: all usage terms are currently converted to lowercase. Case like "LED" are not well handled.
      String usageTerm = usageTermsReader.readLine().toLowerCase();
      if (usageTerm.startsWith("\\\\s")) {
        usageTerms.add(usageTerm.replace("\\\\s", " "));
        LOGGER.debug("Usage term \"%s\" was added to the usage terms corpus as \"%s\"",
            usageTerm,
            usageTerm.replace("\\\\s", " "));
        continue;
      }
      usageTerms.add(usageTerm);
    }
  }

  private BufferedReader getUsageTermsReader() throws FileNotFoundException {
    InputStream usageTermsFile = INSTANCE_CLASS_LOADER.getResourceAsStream(usageTermsFileName);
    BufferedReader usageTermsReader = new BufferedReader(new InputStreamReader(usageTermsFile));
    return usageTermsReader;
  }
}
