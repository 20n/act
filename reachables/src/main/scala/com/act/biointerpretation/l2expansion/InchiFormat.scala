package com.act.biointerpretation.l2expansion

import com.act.biointerpretation.l2expansion.SparkSingleSubstrateROProjector.InchiResult
import spray.json

object InchiFormat extends json.DefaultJsonProtocol {
  implicit val inchiResultFormat = jsonFormat3(InchiResult)
}
