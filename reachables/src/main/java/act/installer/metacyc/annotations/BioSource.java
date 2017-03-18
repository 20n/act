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

package act.installer.metacyc.annotations;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;

public class BioSource extends BPElement {
  // contains only a xref and a name (both from BPElement)

  // e.g.,  {
  //          ID: BioSource32423,
  //          xref: #UnificationXref32424,
  //          name: "Escherichia coli MS 124-1"
  //        }
  // the xref points to a unification ref:
  //        { id: "679205", db: "NCBI Taxonomy" }
  // which is great because we can then lookup the full lineage:
  // cellular organisms; Bacteria; Proteobacteria;
  //  Gammaproteobacteria; Enterobacteriales;
  //  Enterobacteriaceae; Escherichia; Escherichia coli
  // from http://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?mode=Info&id=679205

  public BioSource(BPElement basics) {
    super(basics);
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    if (name != null)
      o.add("name", this.name);
    return o.getJSON();
  }
}

