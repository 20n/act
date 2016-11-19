# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
kFatJarLocation <- "reachables-assembly-0.1.jar"

loginfo("Loading Scala interpreter from fat jar at %s.", kFatJarLocation)
sc=scalaInterpreter(kFatJarLocation, heap.maximum="2096M")
loginfo("Done loading Scala interpreter.")

extractFrom <- {
  sc%~%'import act.shared.TextToRxns'
  extractor <- 'TextToRxns.getRxnsFromStringUI(textStr)'
  intpDef(sc, 'textStr: String', extractor)
}

saveMoleculeStructure <- {
  # Documentation for `saveMoleculeStructure`
  # Render a molecule's structure and saves a .png to file.
  #
  # Args:
  #   inchiString: input inchi string 
  #   file: absolute file path for saving the structure image file
  sc%~%'import com.act.analysis.chemicals.molecules.MoleculeImporter'
  sc%~%'import com.act.biointerpretation.mechanisminspection.ReactionRenderer'
  sc%~%'import java.io.File'
  defineReactionRenderer <- 'val reactionRenderer: ReactionRenderer = new ReactionRenderer'
  sc%~%defineReactionRenderer
  getSaveMolStructFunctionDef <- 'reactionRenderer.drawMolecule(MoleculeImporter.importMolecule(inchiString), new File(file))'
  intpDef(sc, 'inchiString: String, file: String', getSaveMolStructFunctionDef) 
}
