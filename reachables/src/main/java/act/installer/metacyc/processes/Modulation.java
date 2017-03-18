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

package act.installer.metacyc.processes;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.biopax.paxtools.model.level3.ControlType;
import java.util.Set;
import org.json.JSONObject;

public class Modulation extends BPElement {
  ControlType controlType; // e.g., INHIBITION, fullset: Enum.values(ControlType)
  Set<Resource> controller;
  Set<Resource> controlled;

  public Modulation(BPElement basics, ControlType ctrlt, Set<Resource> controller, Set<Resource> controlled) {
    super(basics);
    this.controlType = ctrlt;
    this.controller = controller;
    this.controlled = controlled;
  }

  public JSONObject json(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("type", controlType.toString());
    o.add("controller", src.resolve(controller));
    o.add("controlled", src.resolve(controlled));
    return o.getJSON();
  }
}

