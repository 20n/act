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
import com.act.biointerpretation.sars.CharacterizedGroup;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SerializableReactor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SingleSubstrateSarExpander extends L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SingleSubstrateSarExpander.class);

  final Iterable<CharacterizedGroup> sarGroups;
  final Iterable<Molecule> substrates;

  public SingleSubstrateSarExpander(Iterable<CharacterizedGroup> sarGroups,
                                    Iterable<Molecule> substrates,
                                    PredictionGenerator generator) {
    super(generator);
    this.sarGroups = sarGroups;
    this.substrates = substrates;
  }

  @Override
  public Iterable<PredictionSeed> getPredictionSeeds() {

    List<PredictionSeed> result = new ArrayList<>();

    for (CharacterizedGroup sarGroup : sarGroups) {
      List<Sar> sars = sarGroup.getSars();
      SerializableReactor reactor = sarGroup.getReactor();

      for (Molecule mol : substrates) {
        List<Molecule> singleSubstrateContainer = Arrays.asList(mol);

        result.add(new PredictionSeed(sarGroup.getGroupName(), singleSubstrateContainer, reactor, sars));
      }

    }
    return result;
  }
}
