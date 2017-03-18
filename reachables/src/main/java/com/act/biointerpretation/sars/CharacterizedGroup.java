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

import java.io.Serializable;
import java.util.List;

/**
 * Represents a group of sequences and reactions characterized by the same SAR.
 */
public class CharacterizedGroup implements Serializable {

  private static final long serialVersionUID = -4807145693001996472L;
  @JsonProperty("reaction_group_name")
  private String groupName;

  @JsonProperty("sars")
  private List<Sar> sars;

  @JsonProperty("reactor")
  private SerializableReactor reactor;

  /**
   * Needed for JSON.
   */
  private CharacterizedGroup() {
  }

  public CharacterizedGroup(String groupName, List<Sar> sars, SerializableReactor reactor) {
    this.groupName = groupName;
    this.sars = sars;
    this.reactor = reactor;
  }

  public List<Sar> getSars() {
    return sars;
  }

  public String getGroupName() {
    return groupName;
  }

  public SerializableReactor getReactor() {
    return reactor;
  }

  private void setReactor(SerializableReactor reactor) {
    this.reactor = reactor;
  }
}
