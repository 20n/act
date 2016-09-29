library(logging)

kFatJarLocation <- "reachables-assembly-0.1.jar"

loginfo("Loading Scala interpreter from fat jar at %s.", kFatJarLocation)
sc=scalaInterpreter(kFatJarLocation)
loginfo("Done loading Scala interpreter.")
kImportMS1 <- 'import com.act.lcms.MS1'
sc%~%kImportMS1
getIonMzFunctionDef <- 'MS1.computeIonMz(mass, MS1.ionDeltas.filter(i => i.getName.equals(mode)).head)'
getIonMz <- intpDef(sc, 'mass: Double, mode: String', getIonMzFunctionDef)

plotParameters <- function(input, output, session) {
  return(input)
}

mzScope <- function(input, output, session) {
  return(input)
}

lcmsPlotOutput <- function(id, ...) {
  ns <- NS(id)
  plotOutput(ns("plot"), ...)
}

lcmsSinglePlateData <- function(input, output, session, platename, retention.time.range, target.mz, mz.band.halfwidth, load) {
  
  plate <- reactive({
    getAndCachePlate(platename())
  })
  
  scans <- eventReactive(load(), {
    retention.time.range <- retention.time.range()
    plate <- plate()
    shiny::validate(
      need(!is.null(retention.time.range), "Retention time range is missing.")
    )
    getScans(plate, retention.time.range)
  })
  
  peaks <- reactive({
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    scans <- scans()
    getPeaksInScope(scans, target.mz, mz.band.halfwidth)
  })
  
  return(peaks)
}

lcmsPlatesData <- function(input, output, session, platenames, retention.time.range, target.mz, mz.band.halfwidth) {
  
  plates <- reactive({
    lapply(platenames(), getAndCachePlate)
  })
  
  scans <- reactive({
    retention.time.range <- retention.time.range()
    plates <- plates()
    shiny::validate(
      need(!is.null(retention.time.range()), "Retention time range is missing.")
    )
    lapply(plates, function(x) getScans(retention.time.range, x))
  })
  
  peaks <- reactive({
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    scans <- scans()
    lapply(scans, function(x) getPeaksInScope(x, target.mz, mz.band.halfwidth))
  })
  
  return(peaks)
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



# Module server function
lcmsSinglePlate <- function(input, output, session) {
  
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
  
  plot.data <- callModule(lcmsSinglePlateData, "plate", reactive(input$filename), 
                          reactive(input$retention.time.range), target.mz, 
                          reactive(mz.scope$mz.band.halfwidth), reactive(input$load))
  
  plot.parameters <- callModule(plotParameters, "plot.parameters")
  callModule(lcmsPlot, "plot", plot.data, plot.parameters)
  
  output$target.mz <- renderText({
    sprintf("Target m/z value (computed from input mass and mode): %s", target.mz())
  })
  
  output$detected.peaks <- renderTable({
    plot.data <- plot.data()
    detectPeaks(plot.data$peaks)
  }, digits = c(0, 6, 2, 0))
  
  #TODO (test this)
  observe({
    query <- parseQueryString(session$clientData$url_search)
    filename <- query[['filename']]
    mode <- query[['mode']]
    target.mz <- query[['target.mz']]
    mz.band <- query[['mz.band']]
    rt.min <- query[['rt.min']]
    rt.max <- query[['rt.max']]
    if (!is.null(filename)) {
      updateTextInput(session, "filename", value = filename)
    }
    if (!is.null(mode)) {
      updateSelectInput(session, "mode", selected = mode)
    }
    if (!is.null(target.mz)) {
      target.mz <- as.double(target.mz)
      updateNumericInput(session, "target.monoisotopic.mass", value = target.mz)
    }
    if (!is.null(mz.band)) {
      mz.band <- as.double(mz.band)
      updateNumericInput(session, "mz.band.halfwidth", value = mz.band)
    }
    if (!is.null(rt.min) && !is.null(rt.max)) {
      rt.min <- as.double(rt.min)
      rt.max <- as.double(rt.max)
      updateSliderInput(session, "retention.time.range", value = c(rt.min, rt.max))      
    }
  })
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

lcmsConfigPlates <- function(input, output, session, plot.parameters) {
  
  ns <- session$ns
  
  config <- reactive({
    inFile <- input$config.file
    getAndValidateConfigFile(inFile)
  })
  
  platenames <- reactive(config()$plates$filename)
  layout <- reactive(config()$layout)
  peaks <- reactive({
    config <- config()
    config$peaks
  })
  target.mz <- reactive(input$target.mz)
  mz.band.halfwidth <- reactive(input$mz.band.halfwidth)
  normalize <- reactive(input$normalize)
  retention.time.range <- reactive(input$retention.time.range)
  
  output$ui.peaks <- renderUI({
    peaks <- peaks() %>% 
      mutate_each(funs(round(.,2)), mz, rt) %>%
      mutate(rank_metric_signif = signif(rank_metric, 3)) %>%
      arrange(desc(rank_metric_signif))
    labels <- apply(peaks[, c("mz", "rt", "rank_metric_signif")], 1, function(x) paste0(x, collapse = kPeakDisplaySep))
    selectizeInput(ns("peaks"), "Choose a peak to visualize", choices = unname(labels), options = list(maxOptions = 30000))
  })
  
  selected.peak <- reactive({
    shiny::validate(
      need(!is.null(input$peaks), "Waiting for peak selection...")
    )
    splits <- unlist(strsplit(input$peaks, kPeakDisplaySep))
    mz.val <- as.numeric(splits[1])
    rt.val <- as.numeric(splits[2])
    peak <- peaks() %>% dplyr::filter(round(mz, 2) == mz.val, round(rt, 2) == rt.val)
    shiny::validate(
      need(nrow(peak) == 1, "Less or more than one peak")
    )
    peak
  })
  
  output$ui.rt.mz.scope <- renderUI({
    selected.peak <- selected.peak()
    rt.min <- selected.peak$rt - selected.peak$rt_band
    rt.max <- selected.peak$rt + selected.peak$rt_band
    tagList(
      sliderInput(ns("retention.time.range"), value = c(rt.min, rt.max), 
                  min = 0, max = 450, label = "Retention time range", step = 1),
      numericInput(ns("target.mz"), label = "Target mz value", value = selected.peak$mz, step = 0.001),
      numericInput(ns("mz.band.halfwidth"), label = "Mass charge band halfwidth", 
                   value = selected.peak$mz_band, step = 0.01)
    )
  })
  
  plot.data <- callModule(lcmsPlatesData, "plates", platenames, retention.time.range, target.mz, mz.band.halfwidth)
  
  max.int <- reactive({
    plot.data <- plot.data()
    max(unlist(lapply(plot.data, function(x) max(x$peaks$intensity))))
  })
  
  plot.parameters <- callModule(plotParameters, "plot.parameters")
  
  callModule(lcmsPlotWithNorm, "plot1", plot.data, plot.parameters, 1, max.int, normalize)
  callModule(lcmsPlotWithNorm, "plot2", plot.data, plot.parameters, 2, max.int, normalize)
  callModule(lcmsPlotWithNorm, "plot3", plot.data, plot.parameters, 3, max.int, normalize)
  callModule(lcmsPlotWithNorm, "plot4", plot.data, plot.parameters, 4, max.int, normalize)
  callModule(lcmsPlotWithNorm, "plot5", plot.data, plot.parameters, 5, max.int, normalize)
  callModule(lcmsPlotWithNorm, "plot6", plot.data, plot.parameters, 6, max.int, normalize)
  callModule(lcmsPlotWithNorm, "plot7", plot.data, plot.parameters, 7, max.int, normalize)
  callModule(lcmsPlotWithNorm, "plot8", plot.data, plot.parameters, 8, max.int, normalize)
  callModule(lcmsPlotWithNorm, "plot9", plot.data, plot.parameters, 9, max.int, normalize)
  
  output$plots <- renderUI({
    layout <- layout()
    if (layout$ncol == 2) {
      fluidPage(
        fluidRow(
          column(width = 6, lcmsPlotOutput(ns("plot1"))),
          column(width = 6, lcmsPlotOutput(ns("plot2")))
        ),
        fluidRow(
          column(width = 6, lcmsPlotOutput(ns("plot3"))),
          column(width = 6, lcmsPlotOutput(ns("plot4")))
        ),
        if (layout$nrow == 3) {
          fluidRow(
            column(width = 6, lcmsPlotOutput(ns("plot5"))),
            column(width = 6, lcmsPlotOutput(ns("plot6")))
          )
        }        
      )
    } else if (layout$ncol == 3) {
      fluidPage(
        fluidRow(
          column(width = 4, lcmsPlotOutput(ns("plot1"))),
          column(width = 4, lcmsPlotOutput(ns("plot2"))),
          column(width = 4, lcmsPlotOutput(ns("plot3")))
        ),
        fluidRow(
          column(width = 4, lcmsPlotOutput(ns("plot4"))),
          column(width = 4, lcmsPlotOutput(ns("plot5"))),
          column(width = 4, lcmsPlotOutput(ns("plot6")))
        ),
        if (layout$nrow == 3) {
          fluidRow(
            column(width = 4, lcmsPlotOutput(ns("plot7"))),
            column(width = 4, lcmsPlotOutput(ns("plot8"))),
            column(width = 4, lcmsPlotOutput(ns("plot9")))
          )
        }
      )
    }
  })
}


