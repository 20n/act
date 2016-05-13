package act.installer.wikipedia;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;

public class WikipediaChemical {
  private static String XML_DUMP_FILENAME = "/Users/tom/Documents/enwiki-latest-pages-articles1.xml-p000000010p000030302";
  private static Integer MAX_COUNT = 1000;

  public static boolean ValidateInchi(String inchi, String title) {
    Molecule molecule = null;
    try {
      molecule = MolImporter.importMol(inchi);
    } catch (MolFormatException e) {
      System.out.println("InChI for molecule " + title + " could not be parsed by Chemaxon");
      e.printStackTrace();
    } finally {
      if (molecule != null) {
        return true;
      }
    }
    return false;
  }


  public static void main(final String[] args) throws IOException {
    int counter = 0;
    Pattern titlePattern = Pattern.compile(".*<title>([\\p{Alnum}\\p{Space}]+)</title>.*");
    Pattern inchiPattern = Pattern.compile(".*(?i)([Std]?InChI[0-9]?=[^J][0-9BCOHNSOPrIFla+\\-\\(\\)/,pqbtmsih]{6,}).*");
    String title = "dummy title";
    try (BufferedReader br = new BufferedReader(new FileReader(XML_DUMP_FILENAME))) {
      String line;
      while ((line = br.readLine()) != null) {
        Matcher titleMatcher = titlePattern.matcher(line);

        if (titleMatcher.matches()) {
          title = titleMatcher.group(1);
        }

        Matcher inchiMatcher = inchiPattern.matcher(line);
        if (inchiMatcher.matches()) {
          String inchi = inchiMatcher.group(1);
          if (ValidateInchi(inchi, title)) {
            boolean stdinchi = false;
            if (inchi.startsWith("InChI=1S")) {
              stdinchi = true;
            }
            System.out.println(title + " : " + inchi + " isStandard:" + stdinchi);
            counter++;
            if (counter == MAX_COUNT) {
              return;
            }
          }
        }
      }
    }
  }


}
