# LCMS visualisation configuration-based module

# Module Input function
lcmsConfigTracesInput <- function(id, label = "LCMS config traces") {
  # Create a namespace function using the provided id
  ns <- NS(id)
  tagList(
    h3("Input configuration"),
    fileInput(ns("config.file"), label = "Choose a configuration file"),
    p("Sample config: /shared-data/Thomas/lcms_viz/FR_config_file/sample_config.json"),
    h3("Peak selection"),
    uiOutput(ns("ui.peaks")),
    em("Peak format is {mz-value} - {retention-time} - {rank-factor} - {molecular-mass (optional)}"),
    checkboxInput(ns("has.mol.mass"), "Expect multiple m/z values per peak (-M option)", value = FALSE),
    uiOutput(ns("ui.rt.mz.scope")),
    plotParametersInput(ns("plot.parameters")),
    checkboxInput(ns("normalize"), "Normalize values", value = TRUE),
    actionButton(ns("wait"), "Michael's button (press if you're bored)")
  )
}

# Module UI function
lcmsConfigTracesUI <- function(id) {
  ns <- NS(id)
  fluidPage(
    fluidRow(
      uiOutput(ns("structures"))
    ),
    fluidRow(
      uiOutput(ns("formulae"))
    ),
    fluidRow(
      h4("3D scatterplots"),
      uiOutput(ns("plots"))    
    )
  )
}

# Module server function
lcmsConfigTraces <- function(input, output, session) {
  
  ns <- session$ns
  
  # reactive values - re-evaluation is triggered upon input change
  config <- reactive(getAndValidateConfigFile(input$config.file))
  scan.filenames <- reactive(config()$scanfiles$filename)
  layout <- reactive(config()$layout)
  peaks <- reactive(config()$peaks)
  target.mz <- reactive(input$target.mz)
  mz.band.halfwidth <- reactive(input$mz.band.halfwidth)
  normalize <- reactive(input$normalize)
  retention.time.range <- reactive(input$retention.time.range)
  has.mol.mass <- reactive(input$has.mol.mass)
  max.int <- reactive({
    max(unlist(lapply(plot.data(), function(x) max(x$peaks$intensity))))
  })
  
  # render peak selection UI
  output$ui.peaks <- renderUI({
    # round variables and order parsed peaks according to `rank_metric_signif`
    peaks <- peaks() %>% 
      mutate_each(funs(round(.,2)), mz, rt) %>%
      # rank_metric_signif is simply rank_metric with 3 significant digits
      mutate(rank_metric_signif = signif(rank_metric, 3)) %>%
      arrange(desc(rank_metric_signif))
    # add molecular mass in peak definition if user said so (checkbox)
    if (has.mol.mass()) {
      peaks <- peaks %>%
        mutate_each(funs(round(.,2)), moleculeMass)
      labels <- apply(peaks[, c("mz", "rt", "rank_metric_signif", "moleculeMass")], 1, function(x) paste0(x, collapse = kPeakDisplaySep))
    } else {
      labels <- apply(peaks[, c("mz", "rt", "rank_metric_signif")], 1, function(x) paste0(x, collapse = kPeakDisplaySep))
    }
    selectizeInput(ns("peaks"), "Choose a peak to visualize", choices = unname(labels), 
                   # maxOptions is the number of peaks to show in the drop-down menu
                   options = list(maxOptions = 30000))
  })
  
  selected.peak <- reactive({
    shiny::validate(
      need(!is.null(input$peaks), "Waiting for peak selection...")
    )
    splits <- unlist(strsplit(input$peaks, kPeakDisplaySep))
    mz.val <- as.numeric(splits[1])
    rt.val <- as.numeric(splits[2])
    # add molecular mass in peak definition if user said so (checkbox)
    if (has.mol.mass()) {
      mol.mass <- as.numeric(splits[4])
      peak <- peaks() %>% 
        dplyr::filter(round(mz, 2) == mz.val, round(rt, 2) == rt.val, round(moleculeMass, 2) == mol.mass)  
    } else {
      peak <- peaks() %>% 
        dplyr::filter(round(mz, 2) == mz.val, round(rt, 2) == rt.val)
    }
    # check that only one peak was selected for display. Not sure which to display otherwise
    shiny::validate(
      need(nrow(peak) == 1, "Less or more than one peak. Try using the 'expect multiple mz values' checkbox!")
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
    matching.inchis.code <- selected.peak()$matching_inchis
    # get the  the matching inchis for this hashcode
    matching.inchis <- with(config()$matching_inchi_hashes, {
      unlist(vals[code == matching.inchis.code]) 
    })
    shiny::validate(
      need(length(matching.inchis) > 0, "No matching molecule for this peak...")
    )
    matching.inchis
  })
  
  matching.formulae <- reactive({
    matching.formulae.code <- selected.peak()$matching_formulae
    # get the  the matching inchis for this hashcode
    print(config()$matching_formulae_hashes)
    matching.formulae <- with(config()$matching_formulae_hashes, {
      unlist(vals[code == matching.formulae.code]) 
    })
    shiny::validate(
      need(length(matching.formulae) > 0, "No matching formulae for this peak...")
    )
    matching.formulae
  })
  
  output$structures <- renderUI({
    matching.inchis <- matching.inchis()
    n <- length(matching.inchis)
    for (i in 1:n) {
      # we need to call `local` since we don't know when the call will be made
      # `local` evaluates an expression in a local environment
      local({
        my_i <- i
        callModule(moleculeRenderer, paste0("plot", my_i), reactive(matching.inchis[my_i]), "200px")
      })
    }
    # get a list of rendered molecule
    molecule_output_list <- lapply(1:n, function(i) {
      plotname <- paste0("plot", i)
      chemSpiderUrl <- sprintf("http://www.chemspider.com/Search.aspx?q=%s", matching.inchis[i][1])
      # CSS tag `display:inline-block` allows to display all structures on one line
      div(style="display:inline-block", 
          tags$a(moleculeRendererUI(ns(plotname)), href = chemSpiderUrl, target="_blank"))
    })
    # wrap these in a tagList()
    uiStructures <- do.call(tagList, molecule_output_list)
    # wrap in div tag, with some cool CSS tags to fix height and allow overflowing on the x axis
    tagList(
      h4("Matching molecules"),
      em("Please scroll horizontally to display all"),
      div(style="height: 200px; overflow-x: auto; white-space: nowrap", uiStructures)  
    )
  })
  
  output$formulae <- renderUI({
    tagList(
      h4("Matching formulae"),
      p(paste(matching.formulae(), collapse = " - "))
    )
  })
  
  plot.data <- callModule(lcmsTracesPeaks, "traces", scan.filenames, retention.time.range, target.mz, mz.band.halfwidth)
  plot.parameters <- callModule(plotParameters, "plot.parameters")

  output$plots <- renderUI({
    
    n <- length(scan.filenames())
    
    # call modules for plot rendering
    for (i in 1:n) {
      # we need to call `local` since we don't know when the call will be made
      # `local` evaluates an expression in a local environment
      local({
        my_i <- i
        callModule(lcmsPlotWithNorm, paste0("plot", my_i), plot.data, plot.parameters, my_i, max.int, normalize)
      })
    }  
    
    layout <- layout()
    # max column width is 12
    colWidth <- 12 / layout$ncol
    plot_output_list <- lapply(1:n, function(i) {
      plotname <- paste0("plot", i)
      column(width = colWidth, lcmsPlotOutput(ns(plotname)))
    })
    # split into a list of indexes for each line
    # example: split(1:5, ceiling(1:5 / 2)) returns a list(c(1,2), c(3,4), 5)
    #          split(1:5, ceiling(1:5 / 3)) returns a list(c(1,2,3), c(4,5))
    plot.indexes <- split(1:n, ceiling(1:n /layout$ncol))
    
    do.call(fluidPage, 
            lapply(1:length(plot.indexes), 
                   function(x) do.call(fluidRow, plot_output_list[plot.indexes[[x]]])))
  })
}
