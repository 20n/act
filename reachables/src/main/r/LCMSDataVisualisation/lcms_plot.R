lcmsPlotOutput <- function(id, ...) {
  ns <- NS(id)
  plotOutput(ns("plot"), ...)
}

lcmsPlot <- function(input, output, session, plot.data, plot.parameters) {
  output$plot <- renderPlot({
    plot.data <- plot.data()
    max.int <- max(plot.data$peaks$intensity)
    drawScatterplot(plot.data, plot.parameters, max.int)
  })
}

lcmsPlotWithNorm <- function(input, output, session, plot.data, plot.parameters, i, max.int, normalize) {
  output$plot <- renderPlot({
    plot.data <- plot.data()[[i]]
    if (normalize()) {
      max.int <- max.int()
    } else {
      max.int <- max(plot.data$peaks$intensity)
    }
    drawScatterplot(plot.data, plot.parameters, max.int)
  })
}
