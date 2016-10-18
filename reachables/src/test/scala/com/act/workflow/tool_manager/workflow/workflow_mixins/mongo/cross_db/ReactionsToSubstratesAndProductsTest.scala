package com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.cross_db

import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.scalatest._
import org.scalatest.concurrent.{ThreadSignaler, TimeLimitedTests}
import org.scalatest.time.SpanSugar._

class ReactionsToSubstratesAndProductsTest extends FlatSpec with Matchers with TimeLimitedTests with BeforeAndAfterEach{
  override val defaultTestSignaler = ThreadSignaler
  val timeLimit = 15 seconds


}
