package act.installer.pubchem;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import act.installer.PubmedParser;
import act.server.MongoDB;
import act.server.PubmedEntry;

public class PubmedDBCreator {
  PubmedParser parser;
  private MongoDB db;

  public PubmedDBCreator(String pubmedDir, int start, int end, String mongohost, int mongodbPort, String mongodatabase) {
    this.parser = new PubmedParser(pubmedDir, start, end);
    this.db = new MongoDB(mongohost, mongodbPort, mongodatabase);
  }

  public void addPubmedEntries() {
    BufferedWriter log;
    try {
      log = new BufferedWriter(new FileWriter("pmids.txt"));

      PubmedEntry entry;
      int count = 0;
      while ((entry = (PubmedEntry)parser.getNext())!=null) {

        List<String> xPath = new ArrayList<String>();
        xPath.add("MedlineCitation"); xPath.add("PMID");
        int pmid = Integer.parseInt(entry.getXPathString(xPath));

        // System.out.format("\n------ Pubmed Entry [%d] ----- \n%s\n", pmid, entry);
        System.out.format("\n %d ---- Pubmed Entry [%d]", count++, pmid);
        log.write(count + "\t" + pmid + "\n");
        db.submitToPubmedDB(entry);
      }

      log.close();
    } catch (IOException e) {
      System.err.println("Could not write to log file.");
    }
  }

}
