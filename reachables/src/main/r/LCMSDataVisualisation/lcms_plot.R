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
