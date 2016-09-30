lcmsConfigPlatesInput <- function(id, label = "LCMS config plates") {
  # Create a namespace function using the provided id
  ns <- NS(id)
  tagList(
    h3("Input configuration"),
    fileInput(ns("config.file"), label = "Choose a configuration file"),
    p("Sample config: /shared-data/Thomas/lcms_viz/FR_config_file/sample_config.json"),
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
  fluidPage(
    fluidRow(
      h4("Matching molecules"),
      em("Please scroll to display all"),
      uiOutput(ns("structures"))
    ),
    fluidRow(
      h4("3D scatterplots"),
      uiOutput(ns("plots"))    
    )
  )
}

lcmsConfigPlates <- function(input, output, session) {
  
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
      need(nrow(peak) == 1, "Less or more than one peak. `mz` values have to be unique accross peaks!")
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
  
  
  matching.inchis <- reactive({
    selected.peak <- selected.peak()
    matching.inchis.code <- selected.peak$matching_inchis
    config <- config()
    matching.inchis <- with(config$matching_inchi_hashes, {
      unlist(vals[code == matching.inchis.code]) 
    })
    shiny::validate(
      need(length(matching.inchis) > 0, "No matching molecule for this peak...")
    )
    matching.inchis
  })
  
  output$structures <- renderUI({
    matching.inchis <- matching.inchis()
    n <- length(matching.inchis)
    for (i in 1:n) {
      local({
        my_i <- i
        callModule(moleculeRenderer, paste0("plot", my_i), reactive(matching.inchis[my_i]), "200px")
      })
    }
    plot_output_list <- lapply(1:n, function(i) {
      plotname <- paste0("plot", i)
      div(style="display:inline-block", moleculeRendererUI(ns(plotname)))
    })
    uiStructures <- do.call(tagList, plot_output_list)
    div(style="height: 200px; overflow-x: auto; white-space: nowrap", uiStructures)    
  })
  
  
  plot.data <- callModule(lcmsPlatesData, "plates", platenames, retention.time.range, target.mz, mz.band.halfwidth)
  
  max.int <- reactive({
    plot.data <- plot.data()
    max(unlist(lapply(plot.data, function(x) max(x$peaks$intensity))))
  })
  
  plot.parameters <- callModule(plotParameters, "plot.parameters")
  
  observe({
    for (i in 1:length(platenames())) {
      local({
        my_i <- i
        callModule(lcmsPlotWithNorm, paste0("plot", my_i), plot.data, plot.parameters, my_i, max.int, normalize)
      })
    }  
  })
  
  output$plots <- renderUI({
    layout <- layout()
    n <- layout$nrow * layout$ncol
    colWidth <- 12 / layout$ncol

    plot_output_list <- lapply(1:n, function(i) {
      plotname <- paste0("plot", i)
      column(width = colWidth, lcmsPlotOutput(ns(plotname)))
    })
    plot.indexes <- split(1:n, ceiling(1:n /layout$nrow))
    
    fluidPage(
      do.call(fluidRow, plot_output_list[plot.indexes[[1]]]),
      do.call(fluidRow, plot_output_list[plot.indexes[[2]]]),
      if (layout$nrow == 3) {
        do.call(fluidRow, plot_output_list[plot.indexes[[3]]])
      }        
    )
  })
}
