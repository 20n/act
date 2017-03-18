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

# LCMS visualisation module for multi trace data

# Module input function
lcmsMultiTracesInput <- function(id, label = "LCMS multi traces") {
  # Create a namespace function using the provided id
  ns <- NS(id)
  tagList(
    h3("Scans selection"),
    textInput(ns("filename1"), label = "Filename - Scan 1", value = "Plate_jaffna3_A1_0815201601.nc"),
    textInput(ns("filename2"), label = "Filename - Scan 2", value = "Plate_jaffna3_B1_0815201601.nc"),
    textInput(ns("filename3"), label = "Filename - Scan 3", value = "Plate_jaffna3_C1_0815201601.nc"),
    sliderInput(ns("retention.time.range"), label = "Retention Time range",
                min = 0, max = 450, value = c(130, 160), step = 5),
    actionButton(ns("load"), "Refresh scans!", icon("magic"), width = "100%", 
                 style="color: #fff; background-color: #337ab7; border-color: #2e6da4"),
    mzScopeInput(ns("mz.scope")),
    plotParametersInput(ns("plot.parameters"))
  )
}

# Module UI function
lcmsMultiTracesUI <- function(id) {
  ns <- NS(id)
  tagList(
    h4("Target m/z value"),
    textOutput(ns("target.mz")),
    h4("3D scatterplot of the raw data"),
    lcmsPlotOutput(ns("plot1"), height = "450px"),
    lcmsPlotOutput(ns("plot2"), height = "450px"),
    lcmsPlotOutput(ns("plot3"), height = "450px")  
  )
}

# Module server function
lcmsMultiTraces <- function(input, output, session, plot.parameters, mz.scope) {
  
  
  mz.scope <- callModule(mzScope, "mz.scope")
  
  target.mz <- reactive({
    shiny::validate(
      need(length(mz.scope) == 3, "m/z scope input was not as expected")
    )
    target.mass <- mz.scope$target.monoisotopic.mass
    if (mz.scope$mode == "M (use mass as target mz value)") {
      target.mass
    } else {
      getIonMz(target.mass, mz.scope$mode)
    }
  })
  
  plot.data1 <- callModule(lcmsSingleTracePeaks, "trace1", reactive(input$filename1), 
                           reactive(input$retention.time.range), target.mz, 
                           reactive(mz.scope$mz.band.halfwidth), reactive(input$load))
  plot.data2 <- callModule(lcmsSingleTracePeaks, "trace2", reactive(input$filename2), 
                           reactive(input$retention.time.range), target.mz, 
                           reactive(mz.scope$mz.band.halfwidth), reactive(input$load))
  plot.data3 <- callModule(lcmsSingleTracePeaks, "trace3", reactive(input$filename3), 
                           reactive(input$retention.time.range), target.mz, 
                           reactive(mz.scope$mz.band.halfwidth), reactive(input$load))
  
  plot.parameters <- callModule(plotParameters, "plot.parameters")
  callModule(lcmsPlot, "plot1", plot.data1, plot.parameters)
  callModule(lcmsPlot, "plot2", plot.data2, plot.parameters)
  callModule(lcmsPlot, "plot3", plot.data3, plot.parameters)
  
  output$target.mz <- renderText({
    sprintf("Target m/z value (computed from input mass and mode): %s", target.mz())
  })
}
