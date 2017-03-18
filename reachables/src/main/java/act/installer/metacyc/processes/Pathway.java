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
import java.util.List;
import java.util.Set;

public class Pathway extends BPElement {
  Set<Resource> order;  // the order in which the components appear, a set of PathwayStep
                        // resources. These resources have a getNextStep() function that
                        // returns a Set<PathwayStep> allowing one to traverse an acyclic g
  Set<Resource> components; // catalysis or biochemicalreaction

  public Pathway(BPElement basics, Set<Resource> order, Set<Resource> components) {
    super(basics);
    this.order = order;
    this.components = components;
  }
}

