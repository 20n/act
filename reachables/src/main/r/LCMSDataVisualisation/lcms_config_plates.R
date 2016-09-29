lcmsConfigPlatesInput <- function(id, label = "LCMS config plates") {
  # Create a namespace function using the provided id
  ns <- NS(id)
  tagList(
    h3("Input configuration"),
    fileInput(ns("config.file"), label = "Choose a configuration file", accept=c("application/json")),
    p("Example config: '/shared-data/Thomas/lcms_viz/FR_config_file/sample_config.json'"),
    h3("Peak selection"),
    uiOutput(ns("ui.peaks")),
    em("Peak format is {mz-value} - {retention-time} - {rank-factor}"),
    uiOutput(ns("ui.rt.mz.scope")),
    plotParametersInput(ns("plot.parameters")),
    checkboxInput(ns("normalize"), "Normalize values", value = TRUE)
  )
}

lcmsConfigPlatesUI <- function(id) {
  ns <- NS(id)
  uiOutput(ns("plots"))
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