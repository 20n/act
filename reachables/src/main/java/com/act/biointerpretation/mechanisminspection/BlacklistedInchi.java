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

public class BlacklistedInchi {

  @JsonProperty("wrong_inchi")
  private String wrong_inchi;

  @JsonProperty("correct_inchi")
  private String correct_inchi;

  public String getWrong_inchi() {
    return wrong_inchi;
  }

  public BlacklistedInchi() {}

  public void setWrong_inchi(String wrong_inchi) {
    this.wrong_inchi = wrong_inchi;
  }

  public String getCorrect_inchi() {
    return correct_inchi;
  }

  public void setCorrect_inchi(String correct_inchi) {
    this.correct_inchi = correct_inchi;
  }
}
