package com.act.workflow.tool_manager.jobs.management

object LoggingVerbosity extends Enumeration {
  type LoggingVerbosity = Value
  // No logging
  val Off = 0
  // Only essential messages to indicate run is complete, etc.
  val Low = 1
  // Return values
  val Medium_Low = 2
  // Summary values
  val Medium = 3
  // Status Changes that are not redundant
  val Medium_High = 4
  // Everything
  val High = 5
}
