lcmsPlotOutput <- function(id, ...) {
  ns <- NS(id)
  plotOutput(ns("plot"), ...)
}

lcmsPlot <- function(input, output, session, plot.data, plot.parameters) {
  output$plot <- renderPlot({
    drawScatterplot(plot.data(), plot.parameters)
  })
}

lcmsPlotWithNorm <- function(input, output, session, plot.data, plot.parameters, i, max.intensity, normalize) {
  output$plot <- renderPlot({
    plot.data <- plot.data()[[i]]
    if (normalize()) {
      max.intensity <- max.intensity()
      zlim <- c(0, max.intensity)
      clim <- c(0, max.intensity)
      drawScatterplot(plot.data, plot.parameters, zlim, clim)
    } else {
      drawScatterplot(plot.data, plot.parameters)
    }
  })
}
