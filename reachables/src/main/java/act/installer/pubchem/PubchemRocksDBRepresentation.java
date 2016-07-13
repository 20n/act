package act.installer.pubchem;

import act.shared.Chemical;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PubchemRocksDBRepresentation implements Serializable {

  private static final long serialVersionUID = 4017492820129501773L;

  private String inchi;
  private String inchiKey;
  private String smiles;
  private List<Long> pubchemIds;
  private Map<String, Set<String>> categoryToSetOfNames;

  public String getInchi() {
    return inchi;
  }

  public String getInchiKey() {
    return inchiKey;
  }

  public String getSmiles() {
    return smiles;
  }

  public List<Long> getPubchemIds() {
    return pubchemIds;
  }

  public Map<String, Set<String>> getCategoryToSetOfNames() {
    return categoryToSetOfNames;
  }

  public void populateNames(Map<String, String[]> pubchemNames) {
    for (Map.Entry<String, String[]> entry : pubchemNames.entrySet()) {
      String category = entry.getKey();
      Set<String> names = categoryToSetOfNames.get(category);
      if (names == null) {
        names = new HashSet<>();
        categoryToSetOfNames.put(category, names);
      }

      if (!names.contains(entry.getValue()[0])) {
        //TODO: change this or explain this...
        names.add(entry.getValue()[0]);
      }
    }
  }

  public PubchemRocksDBRepresentation(Chemical chemical) {
    inchi = chemical.getInChI();
    inchiKey = chemical.getInChIKey();
    smiles = chemical.getSmiles();
    pubchemIds = new ArrayList<>();
    pubchemIds.add(chemical.getPubchemID());
    categoryToSetOfNames = new HashMap<>();
    populateNames(chemical.getPubchemNames());
  }

  public void addPubchemId(Long pubchemId) {
    this.pubchemIds.add(pubchemId);
  }

}
