package act.installer.bing;

import java.util.HashSet;
import java.util.Set;

public class NamesOfMolecule {

  private String inchi;
  private Set<String> brendaNames = new HashSet<>();
  private Set<String> metacycNames = new HashSet<>();
  private Set<String> drugbankNames = new HashSet<>();
  private Set<String> drugbankBrands = new HashSet<>();
  private Set<String> chebiNames = new HashSet<>();
  private String wikipediaName = null;
  private String iupacName = null;
  private String inchiKey = null;

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

  public Set<String> getDrugbankBrands() {
    return drugbankBrands;
  }

  public Set<String> getChebiNames() {
    return chebiNames;
  }

  public String getWikipediaName() {
    return wikipediaName;
  }

  public String getIupacName() {
    return iupacName;
  }

  public String getInchiKey() {
    return inchiKey;
  }

  public Set<String> getPrimaryNames() {
    Set<String> primaryNames = getBrendaNames();
    primaryNames.addAll(getMetacycNames());
    primaryNames.addAll(getDrugbankNames());
    primaryNames.addAll(getDrugbankBrands());
    primaryNames.addAll(getChebiNames());
    if (wikipediaName != null) {
      primaryNames.add(wikipediaName);
    }
    return primaryNames;
  }

  public Set<String> getAlternateName() {
    Set<String> altNames = new HashSet<>();
    altNames.add(getIupacName());
    altNames.add(getInchiKey());
    return altNames;
  }

  public Set<String> getAllNames() {
    Set<String> allNames = getPrimaryNames();
    allNames.addAll(getAlternateName());
    return allNames;
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

  public void setDrugbankBrands(Set<String> drugbankBrands) {
    this.drugbankBrands = drugbankBrands;
  }

  public void setChebiNames(Set<String> chebiNames) {
    this.chebiNames = chebiNames;
  }

  public void setWikipediaName(String wikipediaName) {
    this.wikipediaName = wikipediaName;
  }

  public void setIupacName(String iupacName) {
    this.iupacName = iupacName;
  }

  public void setInchiKey(String inchiKey) {
    this.inchiKey = inchiKey;
  }
}
