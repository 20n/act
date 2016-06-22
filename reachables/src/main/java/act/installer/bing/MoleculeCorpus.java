package act.installer.bing;

import com.act.utils.TSVParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

  public void buildCorpusFromTSVFile(String moleculeTSVFileName, String inchiHeader) throws IOException {
    TSVParser parser = new TSVParser();
    parser.parse(new File(moleculeTSVFileName));
    List<Map<String, String>> results = parser.getResults();
    if (!results.get(0).keySet().contains(inchiHeader)) {
      System.err.format("InChI header (%s) was not found in input file. Please run \"head -1 %s\" to confirm.",
          inchiHeader, moleculeTSVFileName);
    }
    for (Map<String, String> result : results) {
      molecules.add(result.get(inchiHeader));
    }
  }
}
