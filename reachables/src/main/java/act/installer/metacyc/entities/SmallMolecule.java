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

package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.entities.SmallMoleculeRef;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.NXT;
import act.installer.metacyc.JsonHelper;
import java.util.Set;
import java.util.HashSet;
import org.json.JSONObject;

public class SmallMolecule extends BPElement {
  Resource smRef; // entityReference field pointing to a SmallMoleculeRef
  Resource cellularLocation;

  public Resource getSMRef() { return this.smRef; }
  public Resource getCellularLocation() { return this.cellularLocation; }

  public SmallMolecule(BPElement basics, Resource smRef, Resource loc) {
    super(basics);
    this.smRef = smRef;
    this.cellularLocation = loc;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.ref) {
      s.add(this.smRef);
    }
    return s;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("ref", src.resolve(smRef).expandedJSON(src));
    if (cellularLocation != null)
      o.add("loc", src.resolve(cellularLocation).expandedJSON(src));
    return o.getJSON();
  }

}

