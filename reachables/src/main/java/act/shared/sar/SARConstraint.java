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

public class SARConstraint {
  SARConstraint(SAR.ConstraintPresent p, SAR.ConstraintContent c, SAR.ConstraintRequire r) {
    this.presence = p;
    this.contents = c;
    this.requires = r;
  }
  public SAR.ConstraintPresent presence;
  public SAR.ConstraintContent contents;
  public SAR.ConstraintRequire requires;

  @Override
  public String toString() { return this.presence + "/" + this.contents + "/" + this.requires; }
  @Override
  public int hashCode() { return this.presence.hashCode() ^ this.contents.hashCode() ^ this.requires.hashCode(); }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SARConstraint)) return false;
    SARConstraint other = (SARConstraint) o;
    return other.presence == this.presence && other.contents == this.contents && other.requires == this.requires;
  }
}


