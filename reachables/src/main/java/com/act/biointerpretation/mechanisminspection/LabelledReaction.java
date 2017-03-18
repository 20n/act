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

package com.act.biointerpretation.mechanisminspection;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

public class LabelledReaction {

  @JsonProperty("ecnum")
  private String ecnum;

  @JsonProperty("notes")
  private String notes;

  @JsonProperty("easy_desc")
  private String easyDesc;

  @JsonProperty("products")
  private Set<String> products;

  @JsonProperty("substrates")
  private Set<String> substrates;

  public String getEcnum() {
    return ecnum;
  }

  public void setEcnum(String ecnum) {
    this.ecnum = ecnum;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getEasy_desc() {
    return easyDesc;
  }

  public void setEasy_desc(String easyDesc) {
    this.easyDesc = easyDesc;
  }

  public Set<String> getProducts() {
    return products;
  }

  public void setProducts(Set<String> products) {
    this.products = products;
  }

  public Set<String> getSubstrates() {
    return substrates;
  }

  public void setSubstrates(Set<String> substrates) {
    this.substrates = substrates;
  }
}
