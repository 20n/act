package com.act.biointerpretation.l2expansion.sparkprojectors.utility

// Basic storage class for serializing and deserializing projection results
case class ProjectionResult(substrates: List[String], ros: String, products: List[String]) extends Serializable
