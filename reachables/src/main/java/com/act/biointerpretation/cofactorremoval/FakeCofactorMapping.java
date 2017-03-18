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

package com.act.biointerpretation.cofactorremoval;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FakeCofactorMapping {

  @JsonProperty("fake_cofactor_name")
  private String fake_cofactor_name;

  @JsonProperty("cofactor_name")
  private String cofactor_name;

  @JsonProperty("rank")
  private Integer rank;

  public FakeCofactorMapping() {}

  public String getFake_cofactor_name() {
    return fake_cofactor_name;
  }

  public void setFake_cofactor_name(String fake_cofactor_name) {
    this.fake_cofactor_name = fake_cofactor_name;
  }

  public String getCofactor_name() {
    return cofactor_name;
  }

  public void setCofactor_name(String cofactor_name) {
    this.cofactor_name = cofactor_name;
  }

  public Integer getRank() {
    return rank;
  }

  public void setRank(Integer rank) {
    this.rank = rank;
  }
}
