package act.installer.swissprot;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import org.json.XML;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import act.server.SQLInterface.MongoDB;

public class SwissProt {
  String sourceDir;
  HashSet<SwissProtEntry> entries;

  public SwissProt(String dir) {
    this.sourceDir = dir;
    this.entries = new HashSet<SwissProtEntry>();
  }

  public void process(int start, int end) {
    List<String> files = getDataFileNames(this.sourceDir);
    files = files.subList(start, end);
    process(files);
  }

  // process only the source file whose names are passed
  public void process(List<String> files) {
    for (String file : files) {
      String fname = this.sourceDir + "/" + file;
      this.entries.addAll(readEntries(fname));
    }
  }

  public void sendToDB(MongoDB db) {
    for (SwissProtEntry e : this.entries)
      e.writeToDB(db);
  }

  private Set<SwissProtEntry> readEntries(String file) {
    Set<SwissProtEntry> extracted = new HashSet<SwissProtEntry>();

    try {
      String line;
      BufferedReader br = new BufferedReader(new FileReader(file));
      StringBuilder sb= new StringBuilder();
      while( ( line = br.readLine() ) != null )
          sb.append( line );
      String xmlstr = sb.toString();
      br.close();

      try {
        JSONObject jo = XML.toJSONObject(xmlstr);
        int pretty_print_indent = 4;

        // jo comes out with structure: jo.uniprot.entry: [ {gene_entries} ]
        JSONArray entries = (JSONArray)((JSONObject)jo.get("uniprot")).get("entry");
        for (int i = 0; i < entries.length(); i++) {
          JSONObject gene_entry = entries.getJSONObject(i);
          extracted.add(new SwissProtEntry(gene_entry));
        }
      } catch (JSONException je) {
        System.out.println(je.toString());
      }
    } catch (IOException e) {
      System.err.println("Err reading: " + file + ". Abort."); System.exit(-1);
    }

    return extracted;
  }

  public static List<String> getDataFileNames(String dir) {
    FilenameFilter subdirfltr = new FilenameFilter() {
      public boolean accept(File dir, String sd) { return new File(dir, sd).isDirectory(); }
    };

    FilenameFilter xmlfltr = new FilenameFilter() {
      public boolean accept(File dir, String nm) { return nm.endsWith(".xml"); }
    };

    List<String> all = new ArrayList<String>();
    for (String subdir : new File(dir).list(subdirfltr)) {
      for (String xmlfile : new File(dir, subdir).list(xmlfltr)) {
        all.add(subdir + "/" + xmlfile);
      }
    }

    return all;
  }
}
