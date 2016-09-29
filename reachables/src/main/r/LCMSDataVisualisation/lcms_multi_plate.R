lcmsMultiPlateInput <- function(id, label = "LCMS multi plate") {
  # Create a namespace function using the provided id
  ns <- NS(id)
  tagList(
    h3("Scans selection"),
    textInput(ns("filename1"), label = "Filename - Plate 1", value = "Plate_jaffna3_A1_0815201601.nc"),
    textInput(ns("filename2"), label = "Filename - Plate 2", value = "Plate_jaffna3_B1_0815201601.nc"),
    textInput(ns("filename3"), label = "Filename - Plate 3", value = "Plate_jaffna3_C1_0815201601.nc"),
    sliderInput(ns("retention.time.range"), label = "Retention Time range",
                min = 0, max = 450, value = c(130, 160), step = 5),
    actionButton(ns("load"), "Refresh scans!", icon("magic"), width = "100%", 
                 style="color: #fff; background-color: #337ab7; border-color: #2e6da4"),
    mzScopeInput(ns("mz.scope")),
    plotParametersInput(ns("plot.parameters"))
  )
}

lcmsMultiPlateUI <- function(id) {
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
lcmsMultiPlate <- function(input, output, session, plot.parameters, mz.scope) {
  
  
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
  
  plot.data1 <- callModule(lcmsSinglePlateData, "plate1", reactive(input$filename1), 
                           reactive(input$retention.time.range), target.mz, 
                           reactive(mz.scope$mz.band.halfwidth), reactive(input$load))
  plot.data2 <- callModule(lcmsSinglePlateData, "plate2", reactive(input$filename2), 
                           reactive(input$retention.time.range), target.mz, 
                           reactive(mz.scope$mz.band.halfwidth), reactive(input$load))
  plot.data3 <- callModule(lcmsSinglePlateData, "plate3", reactive(input$filename3), 
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