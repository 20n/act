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
import java.util.Set;
import org.biopax.paxtools.model.level3.StepDirection;

public class BiochemicalPathwayStep extends BPElement {
  StepDirection direction; // LEFT-TO-RIGHT
  Resource conversion; // a Transport | BiochemicalReaction | both
  Set<Resource> process;  // a Catalysis
                          // while process.size() will usually be 1
                          // sometimes there are multiple proteins
                          // controlling a reaction, and so for the
                          // single conversion that is this step, there
                          // might be many options for catalysis

  Set<Resource> nextSteps;  // a ref to another BiochemicalPathwayStep:
                            // allows the specification of a branchout in
                            // the top to bottom graph, multiple nextSteps
                            // mean that they are alternative steps that
                            // can be followed. A single nextStep means
                            // in the pathway, if a subsequent rxn happens
                            // that is the one that happens.

  public Resource getConversion() { return this.conversion; }
  public Set<Resource> getProcess() { return this.process; }

  public StepDirection getDirection() {
    return this.direction;
  }

  public BiochemicalPathwayStep(BPElement basics, StepDirection dir, Resource conv, Set<Resource> process, Set<Resource> next) {
    super(basics);
    this.direction = dir;
    this.conversion = conv;
    this.process = process;
    this.nextSteps = next;
  }
}


