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

package com.act.biointerpretation.l2expansion;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SerializableReactor;

import java.util.ArrayList;
import java.util.List;

/**
 * This class bundles together the necessary components from which reaction predictions can be made.
 */
public class PredictionSeed {

  private final String projectorName;
  private final List<Molecule> substrates;
  private final SerializableReactor ro;
  private final List<Sar> sars;

  public PredictionSeed(String projectorName, List<Molecule> substrates, SerializableReactor ro, List<Sar> sars) {
    this.projectorName = projectorName;
    this.substrates = substrates;
    this.ro = ro;
    this.sars = sars;
  }

  public String getProjectorName() {
    return projectorName;
  }

  public List<Molecule> getSubstrates() {
    return substrates;
  }

  public SerializableReactor getRo() {
    return ro;
  }

  public List<Sar> getSars() {
    return new ArrayList<Sar>(sars);
  }
}
