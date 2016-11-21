# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
kFatJarLocation <- "reachables-assembly-0.1.jar"

loginfo("Loading Scala interpreter from fat jar at %s.", kFatJarLocation)
sc=scalaInterpreter(kFatJarLocation, heap.maximum="2096M")
loginfo("Done loading Scala interpreter.")

extractFromPlainText <- {
  sc%~%'import act.shared.TextToRxns'
  extractor <- 'TextToRxns.getRxnsFromStringUI(textStr)'
  intpDef(sc, 'textStr: String', extractor)
}

extractFromURL <- {
  sc%~%'import act.shared.TextToRxns'
  url_extractor <- 'TextToRxns.getRxnsFromURLUI(uri)'
  intpDef(sc, 'uri: String', url_extractor)
}

extractFromPDF <- {
  sc%~%'import act.shared.TextToRxns'
  pdf_extractor <- 'TextToRxns.getRxnsFromPDFUI(fileLoc)'
  intpDef(sc, 'fileLoc: String', pdf_extractor)
}
