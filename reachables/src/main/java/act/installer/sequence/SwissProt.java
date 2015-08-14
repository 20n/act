package act.installer.sequence;

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
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import act.shared.Seq;

import act.server.SQLInterface.MongoDB;

public class SwissProt {
  String sourceDir;
  HashSet<SequenceEntry> entries;

  public SwissProt(String dir) {
    this.sourceDir = dir;
    this.entries = new HashSet<SequenceEntry>();
  }

  public void process(int start, int end, MongoDB db) {
    List<String> files = getDataFileNames(this.sourceDir);
    files = files.subList(start, end);
    process(files, db);
  }

  // process only the source file whose names are passed
  public void process(List<String> files, MongoDB db) {
    for (String file : files) {
      String fname = this.sourceDir + "/" + file;
      readEntries(fname, db);
    }
  }

  public void sendToDB(MongoDB db) {
    for (SequenceEntry e : this.entries)
      e.writeToDB(db, Seq.AccDB.swissprot);
  }

  private void readEntries(String file, MongoDB db) {
    try {
      SwissProtEntry.parsePossiblyMany(new File(file), db);
    } catch (IOException e) {
      System.err.println("Err reading: " + file + ". Abort."); System.exit(-1);
    }

    return;
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
