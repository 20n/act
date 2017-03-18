/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package act.installer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
