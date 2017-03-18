##########################################################################
#                                                                        #
#  This file is part of the 20n/act project.                             #
#  20n/act enables DNA prediction for synthetic biology/bioengineering.  #
#  Copyright (C) 2017 20n Labs, Inc.                                     #
#                                                                        #
#  Please direct all queries to act@20n.com.                             #
#                                                                        #
#  This program is free software: you can redistribute it and/or modify  #
#  it under the terms of the GNU General Public License as published by  #
#  the Free Software Foundation, either version 3 of the License, or     #
#  (at your option) any later version.                                   #
#                                                                        #
#  This program is distributed in the hope that it will be useful,       #
#  but WITHOUT ANY WARRANTY; without even the implied warranty of        #
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         #
#  GNU General Public License for more details.                          #
#                                                                        #
#  You should have received a copy of the GNU General Public License     #
#  along with this program.  If not, see <http://www.gnu.org/licenses/>. #
#                                                                        #
##########################################################################

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
