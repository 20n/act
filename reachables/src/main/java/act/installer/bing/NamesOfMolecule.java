/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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

  public Set<String> getAllNames() {
    Set<String> allNames = getBrendaNames();
    allNames.addAll(getMetacycNames());
    allNames.addAll(getDrugbankNames());
    allNames.addAll(getDrugbankBrands());
    allNames.addAll(getChebiNames());
    if (wikipediaName != null) {
      allNames.add(wikipediaName);
    }
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
}
