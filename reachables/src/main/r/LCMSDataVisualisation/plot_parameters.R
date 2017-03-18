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

# Plot parameters module

plotParametersInput <- function(id) {
  ns <- NS(id)
  tagList(
    h3("Plot parameters"),
    sliderInput(ns("angle.theta"), label = "Azimuthal Angle (left <-> right)", 
                min = 0, max = 90, value = 0, step = 5),
    sliderInput(ns("angle.phi"), label = "Colatitude Angle (down <-> up)",
                min = 0, max = 90, value = 0, step = 5)
  )
}

plotParameters <- function(input, output, session) {
  return(input)
}
