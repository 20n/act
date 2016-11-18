# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
kFatJarLocation <- "reachables-assembly-0.1.jar"

loginfo("Loading Scala interpreter from fat jar at %s.", kFatJarLocation)
kScalaInterpreter=scalaInterpreter(kFatJarLocation, heap.maximum="2096M")
loginfo("Done loading Scala interpreter.")

extractFrom <- {
  kScalaInterpreter%~%'import act.shared.TextToRxns'
  extractor <- 'val rxns = TextToRxns.getRxnsFromString(textStr)'
  intpDef(kScalaInterpreter, 'textStr: String', extractor)
}

saveMoleculeStructure <- {
  # Documentation for `saveMoleculeStructure`
  # Render a molecule's structure and saves a .png to file.
  #
  # Args:
  #   inchiString: input inchi string 
  #   file: absolute file path for saving the structure image file
  kScalaInterpreter%~%'import com.act.analysis.chemicals.molecules.MoleculeImporter'
  kScalaInterpreter%~%'import com.act.biointerpretation.mechanisminspection.ReactionRenderer'
  kScalaInterpreter%~%'import java.io.File'
  defineReactionRenderer <- 'val reactionRenderer: ReactionRenderer = new ReactionRenderer'
  kScalaInterpreter%~%defineReactionRenderer
  getSaveMolStructFunctionDef <- 'reactionRenderer.drawMolecule(MoleculeImporter.importMolecule(inchiString), new File(file))'
  intpDef(kScalaInterpreter, 'inchiString: String, file: String', getSaveMolStructFunctionDef) 
}
