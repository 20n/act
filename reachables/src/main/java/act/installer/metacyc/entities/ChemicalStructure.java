package act.installer.metacyc.entities;

import act.installer.metacyc.BPElement;
import org.biopax.paxtools.model.level3.StructureFormatType;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;

public class ChemicalStructure extends BPElement {
  StructureFormatType format; // Enum.{CML, InChI, SMILES}
  String structureData; // xml data corresponding to the structure's CML
                        // has &lt; &gt; &quot; escapes in it but otherwise
                        // valid cml that obabel -icml mol.cml -osmiles
                        // converts to smiles. but they do contains "R"
                        // sometimes, that obabel converts to "*" in the
                        // smiles output. But converting to inchi for those
                        // fails. So we have to be careful about those.
  // In metacyc the structures are in CML format. So we compute inchis and smiles
  // using openbabel, but as a postprocessing operation.
  String smiles;
  // As of 20150909, ChemicalStructure entries may represent their structure with InChIs directly.
  String inchi;

  public String getStructure() { return this.structureData; }
  public StructureFormatType getOriginalFormat() { return this.format; }
  public String getSMILES() { return this.smiles; }
  public String getInChI() { return this.inchi; }

  public ChemicalStructure(BPElement basics, StructureFormatType format, String structureData) {
    super(basics);
    this.format = format;
    this.structureData = structureData;
    handleStructureData();
  }

  static final String pre = "<string title=\"smiles\">";
  static final int prelen = pre.length();
  static final String post = "</string>";
  public void handleStructureData() {
    switch (this.format) {
      case SMILES:
        this.smiles  = this.structureData; // already in SMILES format
        break;

      case CML:
        int start = this.structureData.indexOf(pre) + prelen;
        int end = this.structureData.indexOf(post);
        if (!(start == -1 || end == -1)) {
          this.smiles = this.structureData.substring(start, end);
        } else {
          System.err.println("ERROR: Received CML without SMILES");
        }
        break;
      case InChI:
        if (this.structureData.startsWith("InChI=")) {
          this.inchi = this.structureData;
        } else {
          System.err.println("ERROR: Found badly formed InChI chemical structure: " + this.structureData);
        }
        break;
      default:
        System.err.println("WARNING: Received unknown chemical structure format: " + format);
        break;
    }
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("smiles", smiles);
    return o.getJSON();
  }

}

