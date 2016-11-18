package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import com.act.biointerpretation.l2expansion.L2InchiCorpus
import com.act.biointerpretation.l2expansion.sparkprojectors.BasicSparkROProjector
import com.mongodb.{DBCollection, DBCursor, DBObject, Mongo}
import org.apache.commons.cli.{CommandLine, Option => CliOption}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

trait ReadFromDatabase extends BasicSparkROProjector {
  abstract val OPTION_READ_DB_NAME: String
  abstract val OPTION_READ_DB_PORT: String
  abstract val OPTION_READ_DB_HOST: String
  abstract val OPTION_READ_DB_COLLECTION: String

  final def getValidInchiCommandLineOptions: List[CliOption.Builder] = {
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

  final def getValidInchis(cli: CommandLine): Stream[Stream[String]] = {
    inchiSourceFromDB(getDbName(cli), getDbPort(cli), getDbHost(cli), getDbCollection(cli))
  }

  private def getDbName(cli: CommandLine): String ={
    cli.getOptionValue(OPTION_READ_DB_NAME)
  }

  private def getDbPort(cli: CommandLine): Int ={
    cli.getOptionValue(OPTION_READ_DB_PORT, "27017").toInt
  }

  private def getDbHost(cli: CommandLine): String ={
    cli.getOptionValue(OPTION_READ_DB_HOST)
  }

  private def getDbCollection(cli: CommandLine): String ={
    cli.getOptionValue(OPTION_READ_DB_COLLECTION)
  }

  private def inchiSourceFromDB(database: String, port: Int, host: String, collection: String): Stream[Stream[String]] = {
    val reachables = getReachablesCollection(database, port, host)(collection)
    val cursor: DBCursor = reachables.find()
    val inchis: ArrayBuffer[String] = ArrayBuffer[String]()

    while (cursor.hasNext) {
      val entry: DBObject = cursor.next
      val inchiString: String = entry.get("InChI").asInstanceOf[String]
      inchis.append(inchiString)
    }

    Stream(new L2InchiCorpus(inchis.toList.asJava).getInchiList.asScala.toStream)
  }

  private def getReachablesCollection(database: String, port: Int, host: String)(collection: String): DBCollection = {
    val mongo = new Mongo(host, port)
    val mongoDB = mongo.getDB(database)
    mongoDB.getCollection(collection)
  }
}
