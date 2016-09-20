# server.R performs the computations behind the scenes
# It collects a list of inputs from ui.R and produces a list of output

# Note that the R libraries "shiny" and "rscala" well as Scala should be installed on the machine.
# To perform these tasks, please run in R:
# install.packages(c("shiny", "rscala", "dplyr", "plot3D", "mzR", "classInt"))
# scalaInstall()

# If package `mzR` fails to install, please follow the steps below:
# source("https://bioconductor.org/biocLite.R") # try http:// if https:// URLs are not supported
# biocLite("mzR")

library(shiny)
library(plot3D)
library(mzR)
library(dplyr)
library(rscala)
library(classInt)

kIntensityThreshold <- 10000
kSSRatio <- 20

# Finally, this assumes that two symlinks have been created and are located in the app directory:
# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
# 20nlogo -> symlink to the 20n logo in the resources directory
k20logoLocation <- "20nlogo"
kFatJarLocation <- "reachables-assembly-0.1.jar"

kPeakDisplaySep <- " - "
kLCMSDataLocation <- "/mnt/data-level1/lcms-ms1/"
kLCMSDataCacheLocation <- "/mnt/data-level1/lcms-ms1-rcache/"

cat("Loading interpreter\n")
sc=scalaInterpreter(kFatJarLocation)
kImportMS1 <- 'import com.act.lcms.MS1'
sc%~%kImportMS1
getIonMzFunctionDef <- 'MS1.computeIonMz(mass, MS1.ionDeltas.filter(i => i.getName.equals(mode)).head)'
getIonMz <- intpDef(sc, 'mass: Double, mode: String', getIonMzFunctionDef)

# alternatives to caching are R.cache 
getAndCachePeaks <- function(filename) {
  shiny::validate(
    need(filename != "", "Filename field cannot be empty")
  )
  filepath <- paste0(kLCMSDataLocation, filename)
  cachename <- gsub(".nc", ".RData", filename)
  cachepath <- paste0(kLCMSDataCacheLocation, cachename)
  tryCatch({
    cat(sprintf("Attempt to load ms1 peaks for plate: %s from cache at %s\n", filename, cachepath))
    full.data <- readRDS(cachepath)
    cat(sprintf("Done loading ms1 peaks for plate: %s from cache at %s\n", filename, cachepath))
    return(full.data)
  }, error = function(e) {
    cat(sprintf("Reading ms1 peaks for plate %s from disk\n", filename))
    msfile <- openMSfile(filepath, backend="netCDF")
    hd <- header(msfile)
    ms1 <- which(hd$msLevel == 1)
    ms1.scans <- peaks(msfile, ms1)
    full.data <- list(hd = hd, ms1.scans = ms1.scans)
    cat(sprintf("Done reading ms1 peaks for plate: %s from disk\n", filename))
    saveRDS(full.data, file = cachepath)
    cat(sprintf("Done saving ms1 peaks for plate: %s in the cache at %s\n", filename, cachepath))
    return(full.data)
  })
}


getScansAndHeader <- function(retention.time.range, full.data) {
  shiny::validate(
    need(length(retention.time.range) == 2, "Rentention time range needs to be a tuple"),
    need(is.numeric(retention.time.range), "Rentention time range needs to be numeric"),
    need(length(full.data$ms1.scans) > 0, "Full data should be of length > 0")
  )
  cat("Getting scans and headers\n")
  min.rt <- retention.time.range[1]
  max.rt <- retention.time.range[2]
  
  # Extract the relevant scans from the full dataset
  header <- full.data$hd
  ms1 <- which(header$msLevel == 1)
  rtsel <- header$retentionTime[ms1] > min.rt & header$retentionTime[ms1] < max.rt # vector of boolean
  scans <- full.data$ms1.scans[rtsel]
  
  # We need to replicate the retention time as many times as the length of each scan
  scan.lengths <- unlist(lapply(scans, nrow))
  retention.time <- rep(header$retentionTime[rtsel], scan.lengths)
  list(retention.time = retention.time, scans = scans)
}

getScopedData <- function(target.mz.value, mz.band.halfwidth, scans.and.header) {
  shiny::validate(
    need(target.mz.value >= 50 && target.mz.value <= 950, "Target mz value should be between 50 and 950"),
    need(mz.band.halfwidth >= 0.00001, "M/Z band halfwidth should be >= 0.00001"),
    need(mz.band.halfwidth <= 1, "Avoid values of M/Z band halfwidth > 1 that can make the server crash")
  )
  cat("Getting data for given mz scope\n")
  min.ionic.mass <- target.mz.value - mz.band.halfwidth
  max.ionic.mass <- target.mz.value + mz.band.halfwidth
  data <- with(scans.and.header, {
    mz <- unlist(lapply(scans, function(x) x[, "mz"]))
    intensity <- unlist(lapply(scans, function(x) x[, "intensity"]))
    data.frame(mz = mz, retention.time = retention.time, intensity = intensity)
  })
  data %>% 
    filter(mz < max.ionic.mass & mz > min.ionic.mass)
}

drawScatterplot <- function(data, title, target.mz.value, mz.band.halfwidth, angle.theta, angle.phi) {
  cat("Plotting...\n")
  with(data, {
    min.ionic.mass <- target.mz.value - mz.band.halfwidth
    max.ionic.mass <- target.mz.value + mz.band.halfwidth
    scatter3D(retention.time, mz, intensity, pch = 16, cex = 1.5, type = "h", main = title,
              colkey = list(side = 1, length = 0.5, width = 0.5, cex.clab = 0.75), expand = 0.5,
              zlab = "Intensity", xlab = "Retention time (sec)", ylab = "m/z (Da)",
              theta = angle.theta, phi = angle.phi, ticktype = "detailed", 
              ylim = c(min.ionic.mass, max.ionic.mass))
  })
}

shinyServer(function(input, output, session) {
  
  # 20n logo rendering
  output$logo <- renderImage({
    # Return a list containing the filename
    list(src = k20logoLocation,
         contentType = "image/png",
         width = "200",
         height = "120",
         alt = "20n Logo")
  }, deleteFile = FALSE)
  
  # URL parameters parsing, only in the simple visualisation mode
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
  
  # Reactive values for loading the full dataset in memory
  # `reactive` is lazy, so this will not be called until the refresh button is pressed
  full.data <- reactive({
    getAndCachePeaks(input$filename)
  })
  
  full.data.1 <- reactive({
    getAndCachePeaks(input$filename1)
  })
  
  full.data.2 <- reactive({
    getAndCachePeaks(input$filename2)
  })
  
  full.data.3 <- reactive({
    getAndCachePeaks(input$filename3)
  })
  
  # Based on the retention time range, select relevant scans reactively
  # Recomputed only when refresh button is pressed
  scans.and.header <- eventReactive(input$load, {
    full.data <- full.data()
    getScansAndHeader(input$retention.time.range, full.data)
  })
  
  scans.and.header.1 <- eventReactive(input$load.multi, {
    full.data <- full.data.1()
    getScansAndHeader(input$retention.time.range.multi, full.data)
  })
  scans.and.header.2 <- eventReactive(input$load.multi, {
    full.data <- full.data.2()
    getScansAndHeader(input$retention.time.range.multi, full.data)
  })
  scans.and.header.3 <- eventReactive(input$load.multi, {
    full.data <- full.data.3()
    getScansAndHeader(input$retention.time.range.multi, full.data)
  })
  
  
  # Scoped data computation
  # Re-evaluated only when the target mass or the mz band halfwidth change
  scoped.data <- reactive({
    scans.header <- scans.and.header()
    target.mz <- target.mz()
    getScopedData(target.mz, input$mz.band.halfwidth, scans.header)
  })
  
  scoped.data.1 <- reactive({
    scans.header <- scans.and.header.1()
    target.mz <- target.mz.multi()
    getScopedData(target.mz, input$mz.band.halfwidth.multi, scans.header)
  })
  scoped.data.2 <- reactive({
    scans.header <- scans.and.header.2()
    target.mz <- target.mz.multi()
    getScopedData(target.mz, input$mz.band.halfwidth.multi, scans.header)
  })
  
  scoped.data.3 <- reactive({
    scans.header <- scans.and.header.3()
    target.mz <- target.mz.multi()
    getScopedData(target.mz, input$mz.band.halfwidth.multi, scans.header)
  })
  
  target.mz <- reactive({
    target.mass <- input$target.monoisotopic.mass
    if (input$mode == "M (use mass as target mz value)") {
      target.mass
    } else {
      getIonMz(target.mass, input$mode)
    }
  })

  target.mz.multi <- reactive({
    target.mass <- input$target.monoisotopic.mass.multi
    if (input$mode.multi == "M (use mass as target mz value)") {
      target.mass
    } else {
      getIonMz(target.mass, input$mode.multi)
    }
  })
    
  # Simple peak detection algorithm.
  detected.peaks <- reactive({
    data <- scoped.data()
    data <- data %>%
      filter(intensity > kIntensityThreshold)
    set.seed(2016)
    fit <- kmeans(data$mz, centers = 2)
    if (fit$betweenss / fit$tot.withinss > kSSRatio) {
      intervals <- classIntervals(data$mz, n = 2, style = "kmeans")  
      mean.mz.break <- intervals$brks[2]
      peak1 <- data %>%
        filter(mz < mean.mz.break) %>%
        top_n(1, intensity)
      peak2 <- data %>%
        filter(mz >= mean.mz.break) %>%
        top_n(1, intensity)
      rbind(peak1, peak2)
    } else {
      peak1 <- data %>%
        top_n(1, intensity)
      peak1
    }
  })
  
  output$plot <- renderPlot({
    scoped.data <- scoped.data()
    target.mz <- target.mz()
    drawScatterplot(scoped.data, target.mz, input$mz.band.halfwidth, input$angle.theta, input$angle.phi)
  })
  
  output$plot1.multi <- renderPlot({
    scoped.data <- scoped.data.1()
    target.mz <- target.mz.multi()
    drawScatterplot(scoped.data, target.mz, input$mz.band.halfwidth.multi, input$angle.theta.multi, input$angle.phi.multi)
  })
  
  output$plot2.multi <- renderPlot({
    scoped.data <- scoped.data.2()
    target.mz <- target.mz.multi()
    drawScatterplot(scoped.data, target.mz, input$mz.band.halfwidth.multi, input$angle.theta.multi, input$angle.phi.multi)
  })
  
  output$plot3.multi <- renderPlot({
    scoped.data <- scoped.data.3()
    target.mz <- target.mz.multi()
    drawScatterplot(scoped.data, target.mz, input$mz.band.halfwidth.multi, input$angle.theta.multi, input$angle.phi.multi)
  })
  
  output$target.mz <- renderText({
    sprintf("Target m/z value (computed from input mass and mode): %s", target.mz())
  })
  
  # https://github.com/rstudio/shiny/issues/743
  output$target.mz.multi <- renderText({
    sprintf("Target m/z value (computed from input mass and mode): %s", target.mz.multi())
  })
  
  output$detected.peaks <- renderTable({
    detected.peaks()
  }, digits = c(0, 6, 2, 0))
  
  config <- reactive({
    inFile <- input$config.file
    shiny::validate(
      need(!is.null(inFile), "Please upload a configuration file.") 
    )
    json.file <- fromJSON(file(input$config.file$datapath))
    layout <- json.file$layout
    platenames <- json.file$plates$filename
    shiny::validate(
      need(layout$nrow * layout$ncol == length(platenames), "Too many or not enough plates for input layout. Please double check the layout."), 
      need(layout$nrow >= 2 && layout$nrow <= 3, "Number of rows in the layout should be in {2, 3}"),
      need(layout$ncol >= 2 && layout$nrow <= 3, "Number of cols in the layout should be in {2, 3}")
    )
    json.file
  })
  
  platenames <- reactive({
    config <- config()
    config$plates$filename
  })
  
  layout <- reactive({
    config <- config()
    config$layout
  })
  
  peaks <- reactive({
    config <- config()
    config$peaks
  })
  
  output$ui.peaks <- renderUI({
    peaks <- peaks()
    labels <- apply(peaks[, c("mz", "retention_time")], 1, function(x) paste0(x, collapse = kPeakDisplaySep))
    selectizeInput("peaks", "Choose a peak to visualize", choices = unname(labels))
  })
  
  output$ui.target.mz <- renderUI({
    selected.peak <- selected.peak()
    numericInput("target.mz.config", label = "Target mz value", value = selected.peak$mz, step = 0.001)
  })
  
  output$ui.retention.time.range <- renderUI({
    selected.peak <- selected.peak()
    rt.min <- selected.peak$retention_time - selected.peak$retention_time_band_halfwidth
    rt.max <- selected.peak$retention_time + selected.peak$retention_time_band_halfwidth
    sprintf("Changing retention time range slider input to %s,%s", rt.min, rt.max)
    sliderInput("retention.time.range.config", value = c(rt.min, rt.max), 
                min = 0, max = 450, label = "Retention time range", step = 1)
  })
  
  output$ui.mz.band.halfwidth <- renderUI({
    selected.peak <- selected.peak()
    numericInput("mz.band.halfwidth.config", label = "Mass charge band halfwidth", 
                 value = selected.peak$mz_band_halfwidth, step = 0.01)
  })
  
  selected.peak <- reactive({
    shiny::validate(
      need(!is.null(input$peaks), "Input peaks are null")
    )
    splits <- unlist(strsplit(input$peaks, kPeakDisplaySep))
    mz.val <- as.numeric(splits[1])
    rt <- as.numeric(splits[2])
    peak <- peaks() %>% dplyr::filter(mz == mz.val, retention_time == rt)
    sprintf("Selected peak was %s", peak)
    peak
  })
  
  full.data.config <- reactive({
    platenames <- platenames()
    lapply(platenames, getAndCachePeaks)
  })
  
  scans.and.headers.config <- reactive({
    shiny::validate(
      need(!is.null(input$retention.time.range.config), "Input retention time was found NULL")
    )
    full.data <- full.data.config()
    lapply(full.data, function(x) getScansAndHeader(input$retention.time.range.config, x))
  })
  
  scoped.data.config <- reactive({
    shiny::validate(
      need(!is.null(input$target.mz.config), "target mz was found NULL"),
      need(!is.null(input$mz.band.halfwidth.config), "mz band was found NULL")
    )
    scans.header <- scans.and.headers.config()
    lapply(scans.header, function(x) getScopedData(input$target.mz.config, input$mz.band.halfwidth.config, x))
  })
  
  output$plot1 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[1]], platenames[1], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plot2 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[2]], platenames[2], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plot3 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[3]], platenames[3], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plot4 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[4]], platenames[4], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plot5 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[5]], platenames[5], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plot6 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[6]], platenames[6], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plot7 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[7]], platenames[7], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plot8 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[8]], platenames[8], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plot9 <- renderPlot({
    scoped.data <- scoped.data.config()
    platenames <- platenames()
    drawScatterplot(scoped.data[[9]], platenames[9], input$target.mz.config, input$mz.band.halfwidth.config, input$angle.theta.config, input$angle.phi.config)
  })
  
  output$plots <- renderUI({
    scoped.data <- scoped.data.config()
    layout <- layout()
    if (layout$ncol == 2) {
      fluidPage(
        fluidRow(
          column(width = 6, plotOutput("plot1")),
          column(width = 6, plotOutput("plot2"))
        ),
        fluidRow(
          column(width = 6, plotOutput("plot3")),
          column(width = 6, plotOutput("plot4"))
        ),
        if (layout$nrow == 3) {
          fluidRow(
            column(width = 6, plotOutput("plot5")),
            column(width = 6, plotOutput("plot6"))
          )
        }        
      )
    } else if (layout$ncol == 3) {
      fluidPage(
        fluidRow(
          column(width = 4, plotOutput("plot1")),
          column(width = 4, plotOutput("plot2")),
          column(width = 4, plotOutput("plot3"))
        ),
        fluidRow(
          column(width = 4, plotOutput("plot4")),
          column(width = 4, plotOutput("plot5")),
          column(width = 4, plotOutput("plot6"))
        ),
        if (layout$nrow == 3) {
          fluidRow(
            column(width = 4, plotOutput("plot7")),
            column(width = 4, plotOutput("plot8")),
            column(width = 4, plotOutput("plot9"))
          )
        }
      )
    }
  })
})
