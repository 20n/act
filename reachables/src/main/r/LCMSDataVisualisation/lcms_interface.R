# This file contains function definition that rely on 20n-specific libraries.
# If you do not have access to these libraries, you will need to implement these for the app to fully work!

# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
kFatJarLocation <- "reachables-assembly-0.1.jar"

loginfo("Loading Scala interpreter from fat jar at %s.", kFatJarLocation)
kScalaInterpreter=scalaInterpreter(kFatJarLocation)
loginfo("Done loading Scala interpreter.")

saveMoleculeStructure <- {
  # Documentation for `saveMoleculeStructure`
  # Render a molecule's structure and saves a .png to file.
  #
  # Args:
  #   inchiString: input inchi string 
  #   file: absolute file path for saving the structure image file
  
  # We use the scala interpreter to save the molecule structure.
  # Warning: this will break if you don't have access to 20n.com internal libraries
  # Alternatives include R package like: ChemmineR
  kScalaInterpreter%~%'import com.act.analysis.chemicals.molecules.MoleculeImporter'
  kScalaInterpreter%~%'import com.act.biointerpretation.mechanisminspection.ReactionRenderer'
  kScalaInterpreter%~%'import java.io.File'
  defineReactionRenderer <- 'val reactionRenderer: ReactionRenderer = new ReactionRenderer'
  kScalaInterpreter%~%defineReactionRenderer
  getSaveMolStructFunctionDef <- 'reactionRenderer.drawMolecule(MoleculeImporter.importMolecule(inchiString), new File(file))'
  intpDef(kScalaInterpreter, 'inchiString: String, file: String', getSaveMolStructFunctionDef) 
}

getIonMz <- {
  # Documentation for `getIonMz`
  # Compute the m/z value for a molecular mass and ion mode
  #
  # Args:
  #   mass: Molecular mass
  #   mode: Ion for the m/z computation. One of com.act.lcms.MS1.ionDeltas
  #
  # Returns:
  #   A Double representing the Ion m/z value
  
  # We use the scala interpreter to compute the ion m/z value.
  # Warning: this will break if you don't have access to 20n.com internal libraries
  
  # Alternatives include R package like: OrgMassSpecR
  kScalaInterpreter%~%'import com.act.lcms.MS1'
  getIonMzFunctionDef <- 'MS1.computeIonMz(mass, MS1.ionDeltas.filter(i => i.getName.equals(mode)).head)'
  intpDef(kScalaInterpreter, 'mass: Double, mode: String', getIonMzFunctionDef) 
}