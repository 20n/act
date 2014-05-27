package act.installer.metacyc.annotations;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;

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
}

