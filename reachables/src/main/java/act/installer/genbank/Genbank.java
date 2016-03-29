package act.installer.genbank;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import org.json.XML;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import act.shared.Seq;

import act.installer.sequence.SequenceEntry;

import act.server.MongoDB;

public class Genbank {
  String sourceDir;
  HashSet<SequenceEntry> entries;

  public Genbank(String dir) {
    this.sourceDir = dir;
    this.entries = new HashSet<SequenceEntry>();
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
    for (SequenceEntry e : this.entries)
      e.writeToDB(db, Seq.AccDB.swissprot);
  }

  private Set<SequenceEntry> readEntries(String file) {
    Set<SequenceEntry> extracted = new HashSet<SequenceEntry>();

    try {
      extracted.addAll(GenbankEntry.parsePossiblyMany(file));
    } catch (Exception e) {
      System.err.println("Err reading: " + file + ". Abort."); System.exit(-1);
    }

    return extracted;
  }

  public static List<String> getDataFileNames(String dir) {
    FilenameFilter subdirfltr = new FilenameFilter() {
      public boolean accept(File dir, String sd) { return new File(dir, sd).isDirectory(); }
    };

    FilenameFilter seqfltr = new FilenameFilter() {
      public boolean accept(File dir, String nm) { return nm.endsWith(".seq"); }
    };

    List<String> all = new ArrayList<String>();
    for (String subdir : new File(dir).list(subdirfltr)) {
      for (String seqfile : new File(dir, subdir).list(seqfltr)) {
        all.add(subdir + "/" + seqfile);
      }
    }

    return all;
  }
}
