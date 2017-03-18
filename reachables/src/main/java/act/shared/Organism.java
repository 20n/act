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

package act.shared;

import java.io.Serializable;

public class Organism implements Serializable {
  private static final long serialVersionUID = 42L;
  Organism() { /* default constructor for serialization */ }

  private Long uuid, parent, ncbiID;
  private String name, rank;

  @Deprecated
  public Organism(long uuid, long ncbiID, String name) {
    this.uuid = uuid;
    this.ncbiID = ncbiID;
    this.name = name;
  }

  public Organism(long uuid, String name) {
    this.uuid = uuid;
    this.ncbiID = -1L;
    this.name = name;
  }

  public void setParent(Long parent) { this.parent = parent; }
  public void setRank(String rank) { this.rank= rank; }

  public Long getUUID() { return uuid; }
  public Long getParent() { return parent; }
  public Long getNCBIid() { return ncbiID; }
  public String getName() { return name; }
  public String getRank() { return rank; }

  @Override
  public String toString() {
    return name + "[id:" + uuid + ", parent: " + parent +
        ", rank: " + rank + ", ncbi: " + ncbiID + "]";
  }
}
