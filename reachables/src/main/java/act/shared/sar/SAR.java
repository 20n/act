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

package act.shared.sar;
import java.util.HashMap;

public class SAR {
  public enum ConstraintPresent { should_have, should_not_have };
  public enum ConstraintContent { substructure };
  public enum ConstraintRequire { soft, hard };

  private HashMap<Object, SARConstraint> constraints;
  public SAR() {
    this.constraints = new HashMap<Object, SARConstraint>();
  }

  public void addConstraint(ConstraintPresent presence, ConstraintContent data_c, ConstraintRequire typ, Object data) {
    this.constraints.put(data, new SARConstraint(presence, data_c, typ));
  }

  public HashMap<Object, SARConstraint> getConstraints() {
    return this.constraints;
  }

  @Override
  public String toString() { return this.constraints.toString(); }
  @Override
  public int hashCode() {
    int hash = 42;
    for (Object o : this.constraints.keySet())
      hash ^= o.hashCode() ^ this.constraints.get(o).hashCode();
    return hash;
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SAR)) return false;
    SAR other = (SAR) o;
    if (this.constraints.size() != other.constraints.size()) return false;
    for (Object oo : this.constraints.keySet())
      if (!other.constraints.containsKey(oo) || other.constraints.get(oo) != this.constraints.get(oo))
        return false;
    return true;
  }
}
