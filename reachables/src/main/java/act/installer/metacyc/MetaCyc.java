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

  public MetaCyc(String dirWithL3Files) {
    this.organismModels = new HashMap<String, OrganismComposition>();
    this.sourceDir = dirWithL3Files;
  }

  // processes num files in source directory (num = -1 for all)
  public void process(int num) {
    if (num > 50) 
      warnAboutMem(num);

    if (num > 0) 
      process(0, num); // process only num files
    else
      process(getOWLs(this.sourceDir)); // proces all files
  }

  public void process(int start, int end) {
    if (end-start > 50) warnAboutMem(end-start);
    List<String> files = getOWLs(this.sourceDir);
    files = files.subList(start, end); // only process a sublist from [start, end)
    process(files);
  }

  private void warnAboutMem(int num_asked) {
    System.out.println("You asked to process more than 50 files: " + num_asked);
    System.out.println("You can process about 50 files in 2GB of runtime memory");
    System.out.println("But to truly process all of metacyc's 3528 level3 files, we need");
    System.out.println("to do 71 file chunks (each of 50 files); and probably in parallel");
  }

  // process only the source file whose names are passed
  public void process(List<String> files) {


    FileInputStream f = null;
    for (String file : files) {
      System.out.println("Processing: " + file);
      
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
    FilenameFilter subdirfltr = new FilenameFilter() {
      public boolean accept(File dir, String sd) { return new File(dir, sd).isDirectory(); }
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

