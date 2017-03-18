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

package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

public class SequenceData implements Comparable<SequenceData> {
  @JsonIgnore
  static final String ORGANISM_FIELD_NAME = "organism_name";
  @JsonIgnore
  static final String SEQUENCE_FIELD_NAME = "sequence";

  @ObjectId
  @JsonProperty("_id")
  private String id;

  @JsonProperty("organism_name")
  private String organismName;

  @JsonProperty("sequence")
  private String sequence;

  private SequenceData() {

  }

  public SequenceData(String organismName, String sequence) {
    this.organismName = organismName;
    this.sequence = sequence;
  }

  public String getId() {
    return id;
  }

  private void setId(String id) {
    this.id = id;
  }

  public String getOrganismName() {
    return organismName;
  }

  private void setOrganismName(String organismName) {
    this.organismName = organismName;
  }

  public String getSequence() {
    return sequence;
  }

  private void setSequence(String sequence) {
    this.sequence = sequence;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SequenceData that = (SequenceData) o;

    if (!organismName.equals(that.organismName)) return false;
    return sequence.equals(that.sequence);

  }

  @Override
  public int hashCode() {
    int result = organismName.hashCode();
    result = 31 * result + sequence.hashCode();
    return result;
  }

  @Override
  public int compareTo(SequenceData o) {
    int result = getOrganismName().compareTo(o.getOrganismName());
    return result != 0 ? result : getSequence().compareTo(o.getSequence());
  }
}
