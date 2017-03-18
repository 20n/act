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

package com.act.reachables;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EnvCond {
  private Set<Long> c_ids;
  public EnvCond(List<Long> s) {
    this.c_ids = new HashSet<Long>();
    this.c_ids.addAll(s);
  }
  public Set<Long> speculatedChems() { return this.c_ids; }
  @Override
  public int hashCode() {
    long h = 42;
    for (Long l : this.c_ids) h = h ^ l;
    return (int)h;
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof EnvCond)) return false;
    EnvCond s = (EnvCond)o;
    return this.c_ids.containsAll(s.c_ids) && s.c_ids.containsAll(this.c_ids);
  }
  @Override
  public String toString() {
    return this.c_ids.toString();
  }
  public String toReadableString(int sz) {
    return toString();
  }
}
