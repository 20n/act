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

package com.act.biointerpretation.rsmiles.chemicals

import java.io.Serializable

object JsonInformationTypes {

  @SerialVersionUID(9508372526223L)
  case class ChemicalInformation(chemicalId: Int, chemicalAsString: String) extends Serializable {
    def getChemicalId: Int = chemicalId

    def getString: String = chemicalAsString
  }

  case class AbstractChemicalInfo(chemicalId: Int, dbSmiles: String, asSubstrate: String, asProduct: String) extends Serializable {

    def getAsSubstrate: String = asSubstrate

    def getAsProduct: String = asProduct

  }

  case class ReactionInformation(reactionId: Int, substrates: List[ChemicalInformation], products: List[ChemicalInformation]) {
    def getReactionId: Int = reactionId

    def getSubstrates: List[ChemicalInformation] = substrates

    def getProducts: List[ChemicalInformation] = products
  }

}
