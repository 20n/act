package com.act.analysis.proteome.tool_manager.workflow_utilities

import scala.collection.mutable.ListBuffer

object ScalaJobUtilities {
  def AnyStringToList(any: Any): List[String] = {
    // Map all the ROs to a list which can then be queried against
    val values = ListBuffer[String]()
    try {
      // Try to cast as string
      values.append(any.asInstanceOf[String])
    } catch {
      case e: ClassCastException =>
        // Assume it is a list of strings
        val contextList: List[String] = any.asInstanceOf[List[String]]
        for (value <- contextList) {
          values.append(value)
        }
    }

    values.toList
  }
}
