package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import act.installer.reachablesexplorer.ReachablesProjectionUpdate
import com.act.biointerpretation.l2expansion.sparkprojectors.{BasicSparkROProjector, ProjectionResult}
import com.mongodb.{DBCollection, Mongo}
import org.apache.commons.cli.{CommandLine, Option => CliOption}

trait WriteToDatabase extends BasicSparkROProjector {
  abstract val OPTION_WRITE_DB_NAME: String
  abstract val OPTION_WRITE_DB_PORT: String
  abstract val OPTION_WRITE_DB_HOST: String
  abstract val OPTION_WRITE_DB_COLLECTION: String

  final def getTerminationCommandLineOptions: List[CliOption.Builder] = {
    val options = List[CliOption.Builder](
      CliOption.builder(OPTION_WRITE_DB_NAME).
        required(true).
        hasArg().
        longOpt("write-db-name").
        desc("The name of the MongoDB to use."),

      CliOption.builder(OPTION_WRITE_DB_PORT).
        hasArg().
        longOpt("write-db-port").
        desc("Which port to use when writing from MongoDB"),

      CliOption.builder(OPTION_WRITE_DB_HOST).
        required(true).
        hasArg().
        longOpt("write-db-host").
        desc("The host of the MongoDB to use."),

      CliOption.builder(OPTION_WRITE_DB_COLLECTION).
        required(true).
        hasArg().
        longOpt("write-db-collection").
        desc("Which collection in the MongoDB to write from.  Required to have an `InChI` field.")
    )

    options
  }

  final def handleTermination(cli: CommandLine)(results: Iterator[ProjectionResult]) = {
    writeToDatabase(getDbName(cli), getDbPort(cli), getDbHost(cli))(getDbCollection(cli))(results)
  }

  private def writeToDatabase(database: String, port: Int, host: String)(collection: String)(results: Iterator[ProjectionResult]): Unit = {
    val reachables = getReachablesCollection(database, port, host)(collection)

    results.foreach(projection => {
      val updater = new ReachablesProjectionUpdate(projection)
      updater.updateReachables(reachables)
    })
  }

  private def getReachablesCollection(database: String, port: Int, host: String)(collection: String): DBCollection = {
    val mongo = new Mongo(host, port)
    val mongoDB = mongo.getDB(database)
    mongoDB.getCollection(collection)
  }

  private def getDbName(cli: CommandLine): String ={
    cli.getOptionValue(OPTION_WRITE_DB_NAME)
  }

  private def getDbPort(cli: CommandLine): Int ={
    cli.getOptionValue(OPTION_WRITE_DB_PORT, "27017").toInt
  }

  private def getDbHost(cli: CommandLine): String ={
    cli.getOptionValue(OPTION_WRITE_DB_HOST)
  }

  private def getDbCollection(cli: CommandLine): String ={
    cli.getOptionValue(OPTION_WRITE_DB_COLLECTION)
  }
}
