# server.R performs the computations behind the scenes
# It collects a list of inputs from ui.R and produces a list of output

# Note that the R libraries "shiny" and "rscala" well as Scala should be installed on the machine.
# To perform these tasks, please run in R:
# install.packages(c("shiny", "rscala"))
# scalaInstall()

# Finally, this assumes that a fat jar of the reachables project has been created with the command `sbt assembly`
# and is located at
kFatJarLocation <- "../../../../target/scala-2.10/reachables-assembly-0.1.jar"

# Constants
kImportBingPackageCommand <- 'import act.installer.bing'
kScalaCommand <- 'bing.ExploreRange.getOutcomeVsYieldTable(%s, %s, "CMOS", "%s")'

k20nLogoLocation <- "../../resources/20n.png"
kChartLabelSizeFactor <- 1.3

library(shiny)
library(rscala)

getData <- function(input, sc) {
  titer = input$titer
  price = input$market.price
  location <- input$location
  command <- sprintf(kScalaCommand, titer, price, location)
  out <- sc%~%command
  con <- textConnection(out)
  on.exit(close(con))
  table <- read.table(con, header = TRUE)
  table
}

getBreakEvenPoint <- function(data) {
  which.min(abs(data$ROIPercent))
}

plotGraph <- function(input, data, output) {
  d <- data
  i <- getBreakEvenPoint(d)

  switch(output,
         ROI = {
           yValues <- d$ROIPercent
           yLabel <- "ROI (%)"
           rect.y.top <- 0
           rect.y.bottom <- -100000
         },
         NPV = {
           yValues <- d$NPV
           yLabel <- "NPV ($$M)"
           rect.y.top <- 0
           rect.y.bottom <- -10000
         },
         Yield = {
           yValues <- d$Yield
           yLabel <- "Yield (g/L)"
           rect.y.top <- d$Yield[i]
           rect.y.bottom <- -100
         },
         COGS = {
           yValues <- d$COGS
           yLabel <- "COGS ($$/T)"
           rect.y.bottom <- input$market.price
           rect.y.top <- 1000000
         })
  switch(input$x.axis,
         InvestmentUSD = {
           xValues <- d$InvestM
           xLabel <- "Investment ($$M)"
           xLim <- input$investment.usd.max.min
         },
         InvestmentYears = {
           xValues <- d$InvestY
           xLabel <- "Investment (Years)"
           xLim <- input$investment.years.max.min
        })
  # Line plot for the chart
  plot(xValues, yValues, type="l", col="blue",lwd=3, ylab=yLabel, xlab=xLabel, xlim=xLim, main=yLabel,
       cex.lab=kChartLabelSizeFactor, cex.axis=kChartLabelSizeFactor,
       cex.main=kChartLabelSizeFactor, cex.sub=kChartLabelSizeFactor)
  # Breakeven boundaries
  rect(0, rect.y.bottom, 30, rect.y.top, density = 3, col = "red")
}

shinyServer(function(input, output, session) {
  
  sc=scalaInterpreter(kFatJarLocation)
  sc%~%kImportBingPackageCommand
  
  output$logo <- renderImage({
    list(src = k20nLogoLocation,
         contentType = 'image/png',
         width = "200",
         height = "120",
         alt = "20n Logo")
  }, deleteFile = FALSE)
  
  data <- reactive({
    getData(input, sc)
  })
  
  output$plot1 <- renderPlot({
    plotGraph(input, data(), "ROI")
  })
  
  output$plot2 <- renderPlot({
    plotGraph(input, data(), "NPV")
  })
  
  output$plot3 <- renderPlot({
    plotGraph(input, data(), "Yield")
  })
  
  output$plot4 <- renderPlot({
    plotGraph(input, data(), "COGS")
  })
})