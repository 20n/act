package act.installer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

/*
 * This is used by systems that look at data on the web, pull it,
 * and cache it locally. They then also install it into the db
 *
 * E.g., patents, and chemspider vendors.
*/

public class WebData {

  protected Set<String> readChemicalsFromFile(String file) throws IOException {
    Set<String> list = new HashSet<String>();
    BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(file))));
    String inchi;
    while ((inchi = br.readLine()) != null) {
      list.add(inchi);
    }
    return list;
  }

  protected int status_total = 0, status_pulled = 0, status_w_vendors = 0, status_wo_vendors = 0;
  protected void logStatusToConsole(int num_vendors) {

    // update counts

    status_pulled++;
    if (num_vendors > 0)
      status_w_vendors++;
    else
      status_wo_vendors++;

    // report counts to screen
    String whiteline = "                                                                    \r";
    System.out.format(whiteline);
    System.out.format("%f\t%d (retrieved) / %d (total)\t\t%d (have vendors)\t%d (no vendors)\r",
      100*((float)status_pulled/status_total),
      status_pulled,
      status_total,
      status_w_vendors,
      status_wo_vendors);

  }
}
