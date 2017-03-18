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

# Plotting modules

# Module output function
lcmsPlotOutput <- function(id, ...) {
  ns <- NS(id)
  plotOutput(ns("plot"), ...)
}

# Module server function
lcmsPlot <- function(input, output, session, plot.data, plot.parameters) {
  output$plot <- renderPlot({
    plot.data <- plot.data()
    logdebug("Drawing non-normalized scatterplot of %d peaks from %s with plot parameters: (%d, %d)", nrow(plot.data$peaks), 
             plot.data$filename, plot.parameters$angle.theta, plot.parameters$angle.phi)
    drawScatterplot(plot.data, plot.parameters)
  })
}

# Module server function
lcmsPlotWithNorm <- function(input, output, session, plot.data, plot.parameters, i, max.intensity, normalize) {
  output$plot <- renderPlot({
    plot.data <- plot.data()[[i]]
    if (normalize()) {
      max.intensity <- max.intensity()
      zlim <- c(0, max.intensity)
      clim <- c(0, max.intensity)
      logdebug("Drawing normalized scatterplot of %d peaks from %s with plot parameters: (%d, %d)", nrow(plot.data$peaks), 
               plot.data$filename, plot.parameters$angle.theta, plot.parameters$angle.phi)
      drawScatterplot(plot.data, plot.parameters, zlim = zlim, clim = clim)
    } else {
      logdebug("Drawing non-normalized scatterplot of %d peaks from %s with plot parameters: (%d, %d)", nrow(plot.data$peaks), 
               plot.data$filename, plot.parameters$angle.theta, plot.parameters$angle.phi)
      drawScatterplot(plot.data, plot.parameters)
    }
  })
}
