package act.installer.bing;

import java.util.HashSet;

public class MoleculeNames {

  private String inchi;
  private HashSet<String> brendaNames = new HashSet<>();
  private HashSet<String> metacycNames = new HashSet<>();
  private HashSet<String> drugbankNames = new HashSet<>();
  private HashSet<String> chebiNames = new HashSet<>();

  public MoleculeNames(String inchi) {
    this.inchi = inchi;
  }

  public String getInchi() {
    return inchi;
  }

  public HashSet<String> getBrendaNames() {
    return brendaNames;
  }

  public HashSet<String> getMetacycNames() {
    return metacycNames;
  }

  public HashSet<String> getDrugbankNames() {
    return drugbankNames;
  }

  public HashSet<String> getChebiNames() {
    return chebiNames;
  }

  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public void setBrendaNames(HashSet<String> brendaNames) {
    this.brendaNames = brendaNames;
  }

  public void setMetacycNames(HashSet<String> metacycNames) {
    this.metacycNames = metacycNames;
  }

  public void setDrugbankNames(HashSet<String> drugbankNames) {
    this.drugbankNames = drugbankNames;
  }

  public void setChebiNames(HashSet<String> chebiNames) {
    this.chebiNames = chebiNames;
  }
}
