package act.installer.pubchem;

import act.shared.Chemical;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This inner class exists as a serializable container for features extracted from PC-Compound documents.  We can go
 * directly to Chemical objects if this intermediate representation turns out to be unnecessary.
 */
public class PubchemEntry implements Serializable {
  private static final long serialVersionUID = -6542683222963930035L;

  // TODO: use a builder for this instead of constructing and mutating.

  @JsonProperty("IUPAC_names")
  private Map<String, String> names = new HashMap<>(5); // There tend to be five name variants per chemical.

  @JsonProperty("pubchem_ids")
  private List<Long> pubchemIds = new ArrayList<>(1); // Hopefully there's only one id.

  @JsonProperty("InChI")
  private String inchi;

  /* Use a list for SMILES rather than a set to maintain order.  We could use a LinkedHashSet instead, but the
   * computational overhead of maintaining a set doesn't seem worth removing a few duplicated SMILES.
   * TODO: check that pubchem doesn't have many dupes. */
  @JsonProperty("SMILES")
  private List<String> smiles = new ArrayList<>(1); // Hopefully there's only one SMILES too!

  // For general use.
  public PubchemEntry(Long pubchemId) {
    this.pubchemIds.add(pubchemId);
  }

  // For deserialization.
  public PubchemEntry(Map<String, String> names, List<Long> pubchemIds, String inchi, List<String> smiles) {
    this.names = names;
    this.pubchemIds = pubchemIds;
    this.inchi = inchi;
    this.smiles = smiles;
  }

  public Map<String, String> getNames() {
    return names;
  }

  public void setNameByType(String type, String value) {
    names.put(type, value);
  }

  public List<Long> getPubchemIds() {
    return pubchemIds;
  }

  public void appendPubchemId(Long pubchemId) {
    pubchemIds.add(pubchemId);
  }

  public String getInchi() {
    return inchi;
  }

  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public List<String> getSmiles() {
    return this.smiles;
  }

  public void appendSmiles(String smiles) {
    this.smiles.add(smiles);
  }

  public Chemical asChemical() {
    Chemical c = new Chemical(this.inchi);
    if (this.smiles.size() > 0) {
      c.setSmiles(this.smiles.get(0)); // Just use the first SMILES we find as the primary.
    }
    c.setPubchem(this.getPubchemIds().get(0)); // Assume we'll have at least one id to start with.
    for (Map.Entry<String, String> entry : names.entrySet()) {
      c.addNames(entry.getKey(), new String[] { entry.getValue() });
    }
    c.putRef(Chemical.REFS.ALT_PUBCHEM,
        new JSONObject().
            put("ids", new JSONArray(pubchemIds.toArray(new Long[pubchemIds.size()]))).
            put("smiles", new JSONArray(smiles.toArray(new String[smiles.size()])))
    );
    return c;
  }
}
