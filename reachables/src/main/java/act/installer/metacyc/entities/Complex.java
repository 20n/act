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
import act.installer.metacyc.NXT;
import java.util.Set;
import java.util.HashSet;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;

public class Complex extends BPElement {
  Set<Resource> componentStoichiometry;
  Set<Resource> components;

  public Complex(BPElement basics, Set<Resource> stoi, Set<Resource> comp) {
    super(basics);
    this.componentStoichiometry = stoi;
    this.components = comp;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.components) {
      s.addAll(this.components);
    }
    return s;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("name", super.standardName); // from BPElement
    if (components != null)
      for (BPElement c : src.resolve(components))
        o.add("component", c.expandedJSON(src));
    if (componentStoichiometry != null) {
      int pre = "R:http://biocyc.org/biopax/biopax-level3".length();
      String str = "";
      for (BPElement s : src.resolve(componentStoichiometry)) {
        JSONObject st = s.expandedJSON(src);
        str += (str.length()==0 ? "" : " + ") + st.get("c") + " x " + ((String)st.get("on")).substring(pre);
      }
      o.add("multiplicity", str);
    }
    return o.getJSON();
  }
}


