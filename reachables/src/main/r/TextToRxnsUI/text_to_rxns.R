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
