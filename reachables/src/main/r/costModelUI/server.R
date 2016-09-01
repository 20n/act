library(shiny)
library(rscala)

getData <- function(input, sc) {
  titer = input$titer
  price = input$market.price
  location <- input$location
  command <- sprintf('bing.ExploreRange.getOutcomeVsYieldTable(%s, %s, "CMOS", "%s")', titer, price, location)
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
  
  plot(xValues, yValues, type = "l", col="blue",lwd=3, ylab = yLabel, xlab = xLabel, 
       cex.lab=1.3, cex.axis=1.3, cex.main=1.3, cex.sub=1.3, xlim = xLim, main = yLabel)
  rect(0, rect.y.bottom, 30, rect.y.top, density = 3, col = "red")
}

shinyServer(function(input, output, session) {
  
  sc=scalaInterpreter("/Users/tom/act/reachables/target/scala-2.10/reachables-assembly-0.1.jar")
  sc%~%'import act.installer.bing'
  
  output$logo <- renderImage({
    # Return a list containing the filename
    list(src = "20n.png",
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