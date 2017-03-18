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

package com.act.biointerpretation.sars;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a group of reactions that has been deemed likely explicable by the same SAR.
 * Importantly a ReactionGroup has not yet been characterized by a particular SAR.
 */
public class ReactionGroup {

  @JsonProperty("name")
  private String name;

  @JsonProperty("db_name")
  private String dbName;

  @JsonProperty("reaction_ids")
  private Set<Long> reactionIds;

  /**
   * For JSON.
   */
  private ReactionGroup() {
  }

  public ReactionGroup(String name, String dbName) {
    this.name = name;
    this.dbName = dbName;
    reactionIds = new HashSet<>();
  }

  public void addReactionId(Long id) {
    reactionIds.add(id);
  }

  public Collection<Long> getReactionIds() {
    return new ArrayList(reactionIds);
  }

  public String getName() {
    return name;
  }

  public String getDbName() {
    return dbName;
  }
}
