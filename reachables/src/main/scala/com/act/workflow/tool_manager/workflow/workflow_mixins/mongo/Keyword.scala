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

package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo

sealed case class Keyword(keyword: String) {
  override def toString: String = value

  def value: String = keyword
}

object MongoKeywords {
  object ID extends Keyword("_id")

  // General Use
  object WHERE extends Keyword("$where")
  object EXISTS extends Keyword("$exists")
  object NOT extends Keyword("$not")
  object OR extends Keyword("$or")
  object AND extends Keyword("$and")
  object IN extends Keyword("$in")
  object REGEX extends Keyword("$regex")
  object NOT_EQUAL extends Keyword("$ne")

  object OPTIONS extends Keyword("$options")
  object LENGTH extends Keyword("length")

  // Aggregation
  object MATCH extends Keyword("$match")
  object UNWIND extends Keyword("$unwind")
  object GROUP extends Keyword("$group")
  object PUSH extends Keyword("$push")
}

object SequenceKeywords {
  object ECNUM extends Keyword("ecnum")
  object SEQ extends Keyword("seq")
  object METADATA extends Keyword("metadata")
  object NAME extends Keyword("name")
  object ID extends Keyword("_id")
  object RXN_REFS extends Keyword("rxn_refs")
  object ORGANISM_NAME extends Keyword("org")
}

object ReactionKeywords {
  object ECNUM extends Keyword("ecnum")
  object ID extends Keyword("_id")
  object MECHANISTIC_VALIDATOR extends Keyword("mechanistic_validator_result")
  object PROTEINS extends Keyword("proteins")
  object KM extends Keyword("km")
  object VALUE extends Keyword("val")
  object SUBSTRATES extends Keyword("substrates")
  object PRODUCTS extends Keyword("products")
  object ENZ_SUMMARY extends Keyword("enz_summary")
  object PUBCHEM extends Keyword("pubchem")
  object ORGANISM extends Keyword("organism")
  object COEFFICIENT extends Keyword("coefficient")
}

object ChemicalKeywords {
  object ID extends Keyword("_id")
  object INCHI extends Keyword("InChI")
  object SMILES extends Keyword("SMILES")
}
