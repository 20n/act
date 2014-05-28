package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import org.biopax.paxtools.model.level3.StructureFormatType;
import java.util.List;
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

  public String getStructure() { return this.structureData; }
  public StructureFormatType getOriginalFormat() { return this.format; }
  public String getSMILES() { return this.smiles; }

  public ChemicalStructure(BPElement basics, StructureFormatType format, String structureData) {
    super(basics);
    this.format = format;
    this.structureData = structureData;
    this.smiles = extractSMILESFromCML();
  }

  static final String pre = "<string title=\"smiles\">";
  static final int prelen = pre.length();
  static final String post = "</string>";
  public String extractSMILESFromCML() {
    if (format == StructureFormatType.SMILES)
      return this.structureData; // already in SMILES format

    if (format == StructureFormatType.CML) {
      int start = this.structureData.indexOf(pre) + prelen;
      int end = this.structureData.indexOf(post);
      if (start == -1 || end == -1)
        return null;
      String smi = this.structureData.substring(start, end);
      return smi;
    }

    System.out.println("Received non-CML format: " + format);
    // the only other option is StructureFormatType.INCHI
    // we right now do not convert it back to smiles
    return null;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("smiles", smiles);
    return o.getJSON();
  }

}

