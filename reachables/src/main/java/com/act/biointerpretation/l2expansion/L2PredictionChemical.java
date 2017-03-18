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

package com.act.biointerpretation.l2expansion;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one substrate or product prediction in an L2Prediction
 */
public class L2PredictionChemical implements Serializable {
  private static final long serialVersionUID = -5587683103497903872L;

  public static final String NO_NAME = "no_name";
  public static final Long NO_ID = new Long(-1);

  @JsonProperty("inchi")
  private String inchi;

  @JsonProperty("id")
  private Long id;

  @JsonProperty("name")
  private String name;

  // Necessary for JSON
  private L2PredictionChemical() {
  }

  public L2PredictionChemical(L2PredictionChemical template) {
    this.inchi = template.inchi;
    this.id = template.id;
    this.name = template.name;
  }

  public L2PredictionChemical(String inchi) {
    this.inchi = inchi;
    this.id = NO_ID;
    this.name = NO_NAME;
  }

  public L2PredictionChemical(String inchi, Long id, String name) {
    this.inchi = inchi;
    this.id = id;
    this.name = name;
  }

  public static List<L2PredictionChemical> getPredictionChemicals(List<String> inchis) {
    List<L2PredictionChemical> results = new ArrayList<>();
    for (String inchi : inchis) {
      results.add(new L2PredictionChemical(inchi));
    }
    return results;
  }

  public String getInchi() {
    return inchi;
  }

  public boolean hasId() {
    return !id.equals(NO_ID);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public boolean hasName() {
    return !name.equals(NO_NAME);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
