package act.installer.bing;

import java.util.HashSet;
import java.util.Set;

public class NamesOfMolecule {

  private String inchi;
  private Set<String> brendaNames = new HashSet<>();
  private Set<String> metacycNames = new HashSet<>();
  private Set<String> drugbankNames = new HashSet<>();
  private Set<String> chebiNames = new HashSet<>();

  public NamesOfMolecule(String inchi) {
    this.inchi = inchi;
  }

  public String getInchi() {
    return inchi;
  }

  public Set<String> getBrendaNames() {
    return brendaNames;
  }

  public Set<String> getMetacycNames() {
    return metacycNames;
  }

  public Set<String> getDrugbankNames() {
    return drugbankNames;
  }

  public Set<String> getChebiNames() {
    return chebiNames;
  }

  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public void setBrendaNames(Set<String> brendaNames) {
    this.brendaNames = brendaNames;
  }

  public void setMetacycNames(Set<String> metacycNames) {
    this.metacycNames = metacycNames;
  }

  public void setDrugbankNames(Set<String> drugbankNames) {
    this.drugbankNames = drugbankNames;
  }

  public void setChebiNames(Set<String> chebiNames) {
    this.chebiNames = chebiNames;
  }
}
