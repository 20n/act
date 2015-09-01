package act.installer.metacyc.processes;

import act.installer.metacyc.BPElement;
import act.installer.metacyc.JsonHelper;
import act.installer.metacyc.NXT;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.Resource;
import act.installer.metacyc.annotations.Stoichiometry;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Conversion extends BPElement {
  public enum TYPE { BIOCHEMICAL_RXN, TRANSPORT, TRANSPORT_W_BIOCHEMICAL_RXN, COMPLEX_ASSEMBLY };
  TYPE type;
  public TYPE getTyp() { return this.type; }
  Set<Resource> left, right;
  Set<Resource> participantStoichiometry;

  ConversionDirectionType dir; // REVERSIBLE, LEFT-TO-RIGHT or RIGHT-TO-LEFT: If a reaction will run in a single direction under all biological contexts then it is considered irreversible and has a direction. Otherwise it is reversible. MOSTLY not set.
  Boolean spontaneous; // MOSTLY not set
  Set<String> ecNumber;
  Set<Resource> deltaG;

  public Set<String> getEc() { return this.ecNumber; }
  public Boolean getSpontaneous() { return this.spontaneous; }
  public ConversionDirectionType getDir() { return this.dir; }

  public Conversion(BPElement basics, Set<Resource> l, Set<Resource> r, Set<Resource> stoic, ConversionDirectionType dir, Boolean spont, Set<String> ec, Set<Resource> dG, TYPE t) {
    super(basics);
    this.left = l;
    this.right = r;
    this.participantStoichiometry = stoic;
    this.dir = dir;
    this.spontaneous = spont;
    this.ecNumber = ec;
    this.deltaG = dG;
    this.type = t;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.left) {
      s.addAll(this.left);
    } else if (typ == NXT.right) {
      s.addAll(this.right);
    }
    return s;
  }

  public String getStoichiometry(OrganismComposition src) {
    if (participantStoichiometry == null) 
      return null;

    int pre = "R:http://biocyc.org/biopax/biopax-level3".length();
    String str = "";
    for (BPElement s : src.resolve(participantStoichiometry)) {
      Stoichiometry stoic = (Stoichiometry) s;
      Float coeff = stoic.getCoefficient();
      BPElement participant = src.resolve(stoic.getPhysicalEntity());
      str += (str.length()==0 ? "" : ", ") + coeff + " x " + participant.getStandardName();
      // the participant is either a SmallMolecule, Protein, Complex, Rna
      // JSONObject st = s.expandedJSON(src);
      // str += (str.length()==0 ? "" : " + ") + st.get("c") + " x " + ((String)st.get("on")).substring(pre);
    }
    return str;
  }

  /**
   * Produces a map of participant ids to corresponding Stoichiometry objects, using the specified OrganismComposition
   * to perform Metacyc entity resolution.
   * @param src The OrganismComposition to use when resolving this Conversion's participant stoichiometry entries.
   * @return A map of participant ids to Stoichiometry objects.
   */
  public Map<Resource, Stoichiometry> getRawStoichiometry(OrganismComposition src) {
    if (participantStoichiometry == null) {
      return null;
    }

    // TODO: would it be better to store the raw stoichiometry objects rather than resolving at call time?
    Map<Resource, Stoichiometry> stoichiometries = new HashMap<>(participantStoichiometry.size());
    for (BPElement s : src.resolve(participantStoichiometry)) {
      Stoichiometry stoic = (Stoichiometry) s;
      BPElement participant = src.resolve(stoic.getPhysicalEntity());
      stoichiometries.put(participant.getID(), stoic);
    }
    return stoichiometries;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    if (type != null) o.add("type", type.toString());
    if (dir != null) o.add("dir", dir.toString());
    if (left != null)
      for (BPElement l : src.resolve(left)) 
        o.add("left", l.expandedJSON(src));
    if (right != null)
      for (BPElement r : src.resolve(right)) 
        o.add("right", r.expandedJSON(src));
    if (participantStoichiometry != null) o.add("stoichiometry", getStoichiometry(src));
    if (spontaneous != null) o.add("spontaneous", spontaneous);
    if (ecNumber != null) o.add("ec", ecNumber.toString());
    if (deltaG != null)
      for (BPElement d : src.resolve(deltaG)) 
        o.add("deltaG", d.expandedJSON(src));
    return o.getJSON();
  }
}
