package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;

public class RNA extends BPElement {
  Resource rnaRef; // entityReference field pointing to a ProteinRNARef
  Resource cellularLocation; // cellularLocationVocab that is a act.installer.metacyc.annotations.Term

  public RNA(BPElement basics, Resource r, Resource localization) {
    super(basics);
    this.rnaRef = r;
    this.cellularLocation = localization;
  }
}
