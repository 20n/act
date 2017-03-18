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

package com.act.biointerpretation.desalting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DesaltingRO {

  @JsonProperty("description")
  private String description;

  @JsonProperty("reaction")
  private String reaction;

  @JsonProperty("test_cases")
  private List<ROTestCase> testCases;

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getReaction() {
    return reaction;
  }

  public void setReaction(String reaction) {
    this.reaction = reaction;
  }

  public List<ROTestCase> getTestCases() {
    return testCases;
  }

  public void setTestCases(List<ROTestCase> test_cases) {
    this.testCases = test_cases;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DesaltingRO that = (DesaltingRO) o;

    if (description != null ? !description.equals(that.description) : that.description != null) return false;
    if (reaction != null ? !reaction.equals(that.reaction) : that.reaction != null) return false;
    return testCases != null ? testCases.equals(that.testCases) : that.testCases == null;

  }

  @Override
  public int hashCode() {
    int result = description != null ? description.hashCode() : 0;
    result = 31 * result + (reaction != null ? reaction.hashCode() : 0);
    result = 31 * result + (testCases != null ? testCases.hashCode() : 0);
    return result;
  }
}
