# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
kFatJarLocation <- "reachables-assembly-0.1.jar"

loginfo("Loading Scala interpreter from fat jar at %s.", kFatJarLocation)
kScalaInterpreter=scalaInterpreter(kFatJarLocation)
loginfo("Done loading Scala interpreter.")

extractFrom <- {
  kScalaInterpreter%~%'import act.shared.TextToRxns'
  extractor <- 'TextToRxns.getRxnsFromString(textStr)'
  intpDef(kScalaInterpreter, 'textStr: String', extractor)
}
