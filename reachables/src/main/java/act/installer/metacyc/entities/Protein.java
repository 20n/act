package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import act.installer.metacyc.NXT;
import org.json.JSONObject;
import java.util.Set;
import java.util.HashSet;

public class Protein extends BPElement {

  Resource proteinRef; // entityReference field pointing to a ProteinRNARef

  // the reason for a protein pointing to an model.level3.EntityReference is because the same wild type sequence (which will be in the entityReference) can be pointed to by DNA RNA or Protein
  // More from http://www.biopax.org/m2site/paxtools-4.2.1/apidocs/org/biopax/paxtools/model/level3/EntityReference.html
  // Definition: An entity reference is a grouping of several physical entities across different contexts and molecular states, that share common physical properties and often named and treated as a single entity with multiple states by biologists.
  // Rationale: Many protein, small molecule and gene databases share this point of view, and such a grouping is an important prerequisite for interoperability with those databases. Biologists would often group different pools of molecules in different contexts under the same name. For example cytoplasmic and extracellular calcium have different effects on the cell's behavior, but they are still called calcium. For DNA, RNA and Proteins the grouping is defined based on a wildtype sequence, for small molecules it is defined by the chemical structure.

  public Protein(BPElement basics, Resource r) {
    super(basics);
    this.proteinRef = r;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.ref) {
      s.add(this.proteinRef);
    }
    return s;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("name", super.standardName); // from BPElement
    if (proteinRef != null)
      o.add("ref", src.resolve(proteinRef).expandedJSON(src));
    return o.getJSON();
  }
}
