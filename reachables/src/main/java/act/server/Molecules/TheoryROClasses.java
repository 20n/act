package act.server.Molecules;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.ggasoftware.indigo.Indigo;

import act.server.Logger;
import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;

public class TheoryROClasses {
  HashMap<TheoryROs, List<Reaction>> TheoryROs;
  HashMap<TheoryROs, TheoryROs> Leaders;
  HashMap<TheoryROs, List<Boolean>> Dirs;

  BufferedWriter hierarchyLogFile;
  MongoDB DB;

  public TheoryROClasses(MongoDB db) {
    this.DB = db;
    this.TheoryROs = new HashMap<TheoryROs, List<Reaction>>();
    this.Dirs = new HashMap<TheoryROs, List<Boolean>>();
    this.Leaders = new HashMap<TheoryROs, TheoryROs>();
    this.hierarchyLogFile = null; // set later.
  }

  public void SetHierarchyLogF(BufferedWriter w) {
    this.hierarchyLogFile = w;
  }

  public void add(TheoryROs tro, Reaction r, boolean knownGoodRxn, boolean addToDB) {
    Logger.println(10, "TheoryROs accumulated by their hashes; means clustered by the most specific RO; i.e., ERO currently.");

    if (!this.TheoryROs.containsKey(tro)){
      logNewRO(tro, r);
      this.TheoryROs.put(tro, new ArrayList<Reaction>());
      this.Dirs.put(tro, new ArrayList<Boolean>());
      // this might appear like an Identity mapping, but since
      // cros are invariant under reversal, this map allows us
      // to lookup the representatives for a CRO.
      this.Leaders.put(tro, tro);
    }
    if (addToDB)
      this.DB.submitToActOperatorDB(tro, r, knownGoodRxn);
    this.TheoryROs.get(tro).add(r);
    // cros are considered equal under reversal.
    // but we track the original direction reaction "r"
    // went by comparing against the representativeCRO
    Boolean dir = tro.CRO().direction(this.Leaders.get(tro).CRO()); // if CRO is in one direction, ERO is in the same dir, so just lookup CRO's dir.
    this.Dirs.get(tro).add(dir);
    logHierarchy(r, tro);
  }

  private void logNewRO(TheoryROs tro, Reaction r) {
    // report to the user that a new class was added...
    System.err.println("Adding new RO class: " + tro);

    // now output the CRO/ERO rendering too...
    int id = r.getUUID();
    new File("operators/").mkdir(); // create the dir if it doesn't exist.
    tro.CRO().render("operators/" + id + "-cro.png", "CRO leader");
    tro.ERO().render("operators/" + id + "-ero.png", "ERO leader");
  }

  public void logHierarchy(Reaction r, TheoryROs me) {
    if (this.hierarchyLogFile == null)
      return;

    try {
      TheoryROs parentRO = this.Leaders.get(me);
      String hier = r.getUUID() + "\t" + r.getECNum() + "\t" + parentRO.CRO() + "\t" + parentRO.ERO() + "\n";
      this.hierarchyLogFile.write(hier);
      this.hierarchyLogFile.flush();
    } catch (IOException e) {
      System.err.println("Could not write to TheoryRO hierarchy to the logging file, the reaction: " + r);
    }
  }

  public Set<TheoryROs> getROs() {
    return this.TheoryROs.keySet();
  }
  public List<Reaction> getRORxnSet(TheoryROs tro) {
    return this.TheoryROs.get(tro);
  }
  public List<Boolean> getRODirSet(TheoryROs tro) {
    return this.Dirs.get(tro);
  }

}