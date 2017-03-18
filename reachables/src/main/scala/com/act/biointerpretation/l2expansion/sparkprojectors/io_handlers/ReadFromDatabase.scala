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

package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import com.mongodb.{DBCollection, DBCursor, DBObject, Mongo}
import org.apache.commons.cli.{CommandLine, Option => CliOption}

import scala.collection.mutable.ArrayBuffer

trait ReadFromDatabase extends BasicProjectorInput {
  val OPTION_READ_DB_NAME: String
  val OPTION_READ_DB_PORT: String
  val OPTION_READ_DB_HOST: String
  val OPTION_READ_DB_COLLECTION: String

  private val DEFAULT_PORT: String = "27017"

  final def getInputCommandLineOptions: List[CliOption.Builder] = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_READ_DB_NAME).
        required(true).
        hasArg().
        longOpt("read-db-name").
        desc("The name of the MongoDB to use."),

      CliOption.builder(OPTION_READ_DB_PORT).
        hasArg().
        longOpt("read-db-port").
        desc("Which port to use when reading from MongoDB"),

      CliOption.builder(OPTION_READ_DB_HOST).
        required(true).
        hasArg().
        longOpt("read-db-host").
        desc("The host of the MongoDB to use."),

      CliOption.builder(OPTION_READ_DB_COLLECTION).
        required(true).
        hasArg().
        longOpt("read-db-collection").
        desc("Which collection in the MongoDB to read from.  Required to have an `InChI` field.")
    )

    options
  }

  final def getInputMolecules(cli: CommandLine): Stream[Stream[String]] = {
    inchiSourceFromDB(getReadDbName(cli), getReadDbPort(cli), getReadDbHost(cli), getReadDbCollection(cli))
  }

  final protected def getReadDbName(cli: CommandLine): String = {
    cli.getOptionValue(OPTION_READ_DB_NAME)
  }

  final protected def getReadDbPort(cli: CommandLine): Int = {
    cli.getOptionValue(OPTION_READ_DB_PORT, DEFAULT_PORT).toInt
  }

  final protected def getReadDbHost(cli: CommandLine): String = {
    cli.getOptionValue(OPTION_READ_DB_HOST)
  }

  final protected def getReadDbCollection(cli: CommandLine): String = {
    cli.getOptionValue(OPTION_READ_DB_COLLECTION)
  }

  private def inchiSourceFromDB(database: String, port: Int, host: String, collection: String): Stream[Stream[String]] = {
    val reachables = getReachablesCollection(database, port, host)(collection)
    val cursor: DBCursor = reachables.find()
    val inchis: ArrayBuffer[String] = ArrayBuffer[String]()

    while (cursor.hasNext) {
      val entry: DBObject = cursor.next
      val rawString: Option[AnyRef] = Option(entry.get("InChI"))

      if (rawString.isDefined) {
        inchis.append(rawString.get.asInstanceOf[String])
      }
    }

    // List of combinations of InChIs
    combinationList(Stream(inchis.toStream))
  }

  private def getReachablesCollection(database: String, port: Int, host: String)(collection: String): DBCollection = {
    val mongo = new Mongo(host, port)
    val mongoDB = mongo.getDB(database)
    mongoDB.getCollection(collection)
  }
}
