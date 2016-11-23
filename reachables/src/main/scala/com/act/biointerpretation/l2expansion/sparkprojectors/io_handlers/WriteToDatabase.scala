package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import act.installer.reachablesexplorer.ReachablesProjectionUpdate
import com.act.biointerpretation.l2expansion.sparkprojectors.utility.ProjectionResult
import com.mongodb.{DBCollection, Mongo}
import org.apache.commons.cli.{CommandLine, Option => CliOption}

trait WriteToDatabase extends BasicProjectorOutput {
  val OPTION_WRITE_DB_NAME: String
  val OPTION_WRITE_DB_PORT: String
  val OPTION_WRITE_DB_HOST: String
  val OPTION_WRITE_DB_COLLECTION: String

  private val DEFAULT_PORT: String = "27017"

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

  final def handleOutput(cli: CommandLine)(results: Stream[ProjectionResult]) = {
    writeToDatabase(getWriteDbName(cli), getWriteDbPort(cli), getWriteDbHost(cli))(getWriteDbCollection(cli))(results)
  }

  private def writeToDatabase(database: String, port: Int, host: String)(collection: String)(results: Stream[ProjectionResult]): Unit = {
    val reachables = getReachablesCollection(database, port, host)(collection)

    results.foreach(projection => {
      val updater = new ReachablesProjectionUpdate(projection)
      updater.updateDatabase(reachables)
    })
  }

  private def getReachablesCollection(database: String, port: Int, host: String)(collection: String): DBCollection = {
    val mongo = new Mongo(host, port)
    val mongoDB = mongo.getDB(database)
    mongoDB.getCollection(collection)
  }

  final protected def getWriteDbName(cli: CommandLine): String = {
    cli.getOptionValue(OPTION_WRITE_DB_NAME)
  }

  final protected def getWriteDbPort(cli: CommandLine): Int = {
    cli.getOptionValue(OPTION_WRITE_DB_PORT, DEFAULT_PORT).toInt
  }

  final protected def getWriteDbHost(cli: CommandLine): String = {
    cli.getOptionValue(OPTION_WRITE_DB_HOST)
  }

  final protected def getWriteDbCollection(cli: CommandLine): String = {
    cli.getOptionValue(OPTION_WRITE_DB_COLLECTION)
  }
}
