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

# MZ scope module

kModes <- c("M (use mass as target mz value)", "M+H", "M-H", "M+Na", "M+Li", "M+H-H2O")
kDefaultMzValue <- 463.184234
kDefaultMzBandwidthValue <- 0.01

mzScopeInput <- function(id) {
  ns <- NS(id)
  tagList(
    h3("M/Z scope"),
    selectInput(ns("mode"), label = "m/z mode", choices = kModes, selected = "M+H"),
    numericInput(ns("target.monoisotopic.mass"), label = "Target monoisotopic mass", value = kDefaultMzValue, step = 0.001),
    numericInput(ns("mz.band.halfwidth"), label = "Mass charge band halfwidth", value = kDefaultMzBandwidthValue, step = 0.01)
  )
}

mzScope <- function(input, output, session) {
  return(input)
}
