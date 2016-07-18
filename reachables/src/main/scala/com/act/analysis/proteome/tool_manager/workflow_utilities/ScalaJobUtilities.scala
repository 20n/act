package com.act.analysis.proteome.tool_manager.workflow_utilities

import scala.collection.mutable.ListBuffer

object ScalaJobUtilities {
  def anyStringToList(any: Any): List[String] = {
    // Map all the values to a list
    val values = ListBuffer[String]()
    try {
      // Try to cast as string
      values.append(any.asInstanceOf[String])
    } catch {
      case e: ClassCastException =>
        // Assume it is a list of strings if unable to be cast as a string
        val contextList: List[String] = any.asInstanceOf[List[String]]
        for (value <- contextList) {
          values.append(value)
        }
    }

    values.toList
  }
}
