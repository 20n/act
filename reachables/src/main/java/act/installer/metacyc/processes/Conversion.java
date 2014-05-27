package act.installer.metacyc.processes;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;
import org.biopax.paxtools.model.level3.ConversionDirectionType; // enum, so ok to use

public class Conversion extends BPElement {
  public enum TYPE { BIOCHEMICAL_RXN, TRANSPORT, TRANSPORT_W_BIOCHEMICAL_RXN };
  TYPE type;
  public TYPE getTyp() { return this.type; }
  Set<Resource> left, right;
  Set<Resource> participantStoichiometry;

  ConversionDirectionType dir; // REVERSIBLE, LEFT-TO-RIGHT or RIGHT-TO-LEFT: If a reaction will run in a single direction under all biological contexts then it is considered irreversible and has a direction. Otherwise it is reversible. MOSTLY not set.
  Boolean spontaneous; // MOSTLY not set
  Set<String> ecNumber;
  Set<Resource> deltaG;

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
}
