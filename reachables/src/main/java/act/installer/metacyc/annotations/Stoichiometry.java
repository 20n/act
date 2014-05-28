package act.installer.metacyc.annotations;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;

public class Stoichiometry extends BPElement {
  Float stoichiometricCoefficient;
  Resource physicalEntity; // Protein|Complex|Rna|SmallMolecule

  public Resource getPhysicalEntity() { return this.physicalEntity; }
  public Float getCoefficient() { return this.stoichiometricCoefficient; }

  public Stoichiometry(BPElement basics, Resource phyEnt, Float coeff) {
    super(basics);
    this.stoichiometricCoefficient = coeff;
    this.physicalEntity = phyEnt;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    if (stoichiometricCoefficient != null) o.add("c", stoichiometricCoefficient);
    if (physicalEntity != null) o.add("on", physicalEntity.toString()); // do not resolve the physical entity as this is always supposed to be a reference to the entity closely in the datastructures.
    return o.getJSON();
  }
}

