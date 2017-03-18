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

public class Stoichiometry extends BPElement {
  Float stoichiometricCoefficient;
  Resource physicalEntity; // Protein|Complex|Rna|SmallMolecule

  public Resource getPhysicalEntity() { return this.physicalEntity; }
  public Float getCoefficient() { return this.stoichiometricCoefficient; }

  public Stoichiometry(BPElement basics, Resource phyEnt, Float coeff) {
    super(basics);
    this.stoichiometricCoefficient = coeff;
    this.physicalEntity = phyEnt;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    if (stoichiometricCoefficient != null) o.add("c", stoichiometricCoefficient);
    if (physicalEntity != null) o.add("on", physicalEntity.toString()); // do not resolve the physical entity as this is always supposed to be a reference to the entity closely in the datastructures.
    return o.getJSON();
  }
}

