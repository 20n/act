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

package org.twentyn.proteintodna;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import java.util.Set;

public class DNADesign {

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Set<DNAOrgECNum> getDnaDesigns() {
    return dnaDesigns;
  }

  // ToDo: Convert the argument parameter to a Collection if the deserialization is not de-duping entries correctly
  public void setDnaDesigns(Set<DNAOrgECNum> dnaDesigns) {
    this.dnaDesigns = dnaDesigns;
  }

  @ObjectId
  @JsonProperty("_id")
  private String id;

  @JsonProperty("designs")
  private Set<DNAOrgECNum> dnaDesigns;

  public DNADesign(Set<DNAOrgECNum> dnaDesigns) {
    this.dnaDesigns = dnaDesigns;
  }

  private DNADesign() {}
}
