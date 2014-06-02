package act.installer.metacyc;

import act.server.SQLInterface.MongoDB;

import java.io.FileInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import act.shared.Chemical;

public class MetaCyc {
  // map from location of biopax L3 file to the corresponding parsed organism model
  HashMap<String, OrganismComposition> organismModels;
  String sourceDir;

  // if onlyTier12 is set, then only the 38 main files are processed
  // we identify them as not having names that contain one of ("hmpcyc", "wgscyc", more than three successive digits)
  // See http://biocyc.org/biocyc-pgdb-list.shtml and the descriptions of Tier1 and Tier2
  // Outside of these 38, there are 3487 Tier3 files that have not received manual
  // curation and are just the dump output of their PathLogic program.
  boolean onlyTier12; 

  public MetaCyc(String dirWithL3Files) {
    this.organismModels = new HashMap<String, OrganismComposition>();
    this.sourceDir = dirWithL3Files;
    this.onlyTier12 = true; // by default only process the Tier1,2 files
  }

  public MetaCyc(String dirWithL3Files, boolean onlyTier12) {
    this.organismModels = new HashMap<String, OrganismComposition>();
    this.sourceDir = dirWithL3Files;
    this.onlyTier12 = onlyTier12;
  }

  // processes num files in source directory (num = -1 for all)
  public void process(int num) {
    if (num > 15) 
      warnAboutMem(num);

    if (num > 0) 
      process(0, num); // process only num files
    else
      process(getOWLs(this.sourceDir, this.onlyTier12)); // process all files
  }

  public void process(int start, int end) {
    if (end-start > 50) warnAboutMem(end-start);
    List<String> files = getOWLs(this.sourceDir, this.onlyTier12);
    files = files.subList(start, end); // only process a sublist from [start, end)
    process(files);
  }

  private void warnAboutMem(int num_asked) {
    System.out.println("You asked to process more than 15 files: " + num_asked);
    System.out.println("You can process about 10 files in 4GB of runtime memory");
  }

  // process only the source file whose names are passed
  public void process(List<String> files) {


    FileInputStream f = null;
    for (String file : files) {
      System.out.println("Processing: " + file);
      if (file.endsWith("leishcyc/biopax-level3.owl")) {
        System.out.println("Friendly reminder: Did you patch this leishcyc file with the diff in src/main/resources/leishcyc.biopax-level3.owl.diff to take care of the bad data in the original? If you are running over the plain downloaded file, then this will crash.");
      }
      
      try {
        f = new FileInputStream(this.sourceDir + "/" + file);
      } catch (FileNotFoundException e) {
        System.err.println("Could not find: " + file + ". Abort."); System.exit(-1);
      }

      OrganismComposition o = new OrganismComposition();
      new BioPaxFile(o).initFrom(f);
      this.organismModels.put(file, o);

      try {
        f.close();
      } catch (IOException e) {
        System.err.println("Could not close: " + file);
      }

    }
  }

  public OrganismComposition get(String file) {
    return this.organismModels.get(file);
  }

  public static List<String> getOWLs(String dir) {
    return getOWLs(dir, true); // by default only get Tier1, 2 files.
  }

  public static List<String> getOWLs(String dir, boolean onlyTier12) {
    FilenameFilter subdirfltr = new FilenameFilter() {
      public boolean accept(File dir, String sd) { 
        if (!new File(dir, sd).isDirectory())
          return false;
        if (onlyTier12) {
          // additional checks if only looking for tier1,2 files
          // Tier1,2 are the important ones because they are the
          // only ones that have received manual curation: http://biocyc.org/biocyc-pgdb-list.shtml
          // It is a Tier1,2 file if its name does not contain one of 
          // ("hmpcyc", "wgscyc", more than three successive digits)
          if (sd.contains("hmpcyc") || sd.contains("wgscyc"))
            return false;
          if (sd.matches("^.*[0-9][0-9][0-9].*$"))
            return false;
        }
        return true;
      }
    };

    FilenameFilter owlfltr = new FilenameFilter() {
      public boolean accept(File dir, String nm) { return nm.endsWith("level3.owl"); }
    };

    List<String> allL3 = new ArrayList<String>();
    for (String subdir : new File(dir).list(subdirfltr)) {
      for (String owlfile : new File(dir, subdir).list(owlfltr)) {
        allL3.add(subdir + "/" + owlfile);
      }
    }

    return allL3;
  }

  public void sendToDB(MongoDB db) {
    Chemical.REFS originDB = Chemical.REFS.METACYC;
    for (String oid : this.organismModels.keySet()) {
      OrganismCompositionMongoWriter owriter = new OrganismCompositionMongoWriter(db, this.organismModels.get(oid), oid, originDB);
      owriter.write();
    }
  }

}

