package act.installer.bing;

import com.act.utils.TSVParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a Molecule corpus.
 */

public class MoleculeCorpus {

  private Set<String> molecules = new HashSet<>();

  public Set<String> getMolecules() {
    return molecules;
  }

  public MoleculeCorpus() {}

  public void buildCorpusFromRawInchis(String moleculeFileName) throws IOException {
    File usageTermsFile = new File(moleculeFileName);
    FileInputStream usageTermsInputStream = new FileInputStream(usageTermsFile);
    BufferedReader usageTermsReader = new BufferedReader(new InputStreamReader(usageTermsInputStream));

    while (usageTermsReader.ready()) {
      String usageTerm = usageTermsReader.readLine();
      molecules.add(usageTerm);
    }
  }

  public void buildCorpusFromTSVFile(String moleculeTSVFileName) throws IOException {
    TSVParser parser = new TSVParser();
    parser.parse(new File(moleculeTSVFileName));
    List<Map<String, String>> results = parser.getResults();
    Set<String> headers = results.get(0).keySet();
    String inchiHeader = getInchiHeader(headers);
    if (inchiHeader == null) {
      System.err.format("InChI header was not found in input file. " +
          "Please run \"head -1 %s\" to confirm that an InChI column is present. " +
          "The InChI header needs to be \"inchi\" and is detected in a case-insensitive way.",
          moleculeTSVFileName);
      System.exit(1);
    }
    for (Map<String, String> result : results) {
      molecules.add(result.get(inchiHeader));
    }
  }

  private String getInchiHeader(Set<String> headers) {
    Pattern inchiPattern = Pattern.compile("inchi", Pattern.CASE_INSENSITIVE);
    String inchiHeader = null;
    for (String header : headers) {
      Matcher matcher = inchiPattern.matcher(header);
      if (matcher.matches()) {
        inchiHeader = header;
      }
    }
    return inchiHeader;
  }
}
