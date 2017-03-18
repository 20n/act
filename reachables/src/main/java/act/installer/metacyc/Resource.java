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

package act.installer.metacyc;

public class Resource {
  String id; // this id is without the # in front of it

  public Resource(String id) { this.id = id; }

  @Override
  public String toString() {
    return "R:" + this.id;
  }

  private String mostSeenPrefix = "http://biocyc.org/biopax/biopax-level3";
  private int mostSeenPrefixLen = mostSeenPrefix.length();
  public String getLocal() {
    if (this.id.startsWith(mostSeenPrefix))
      return this.id.substring(mostSeenPrefixLen);
    else
      return this.id; // unexpected global uri prefix. return the entire string
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Resource)) return false;
    return this.id.equals(((Resource)o).id);
  }

  @Override
  public int hashCode() {
    return this.id.hashCode();
  }
}
