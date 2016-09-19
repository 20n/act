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

kChartLabelSizeFactor <- 1
kIntensityThreshold <- 10000
kSSRatio <- 20

# Finally, this assumes that two symlinks have been created and are located in the app directory:
# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
# 20nlogo -> symlink to the 20n logo in the resources directory
k20logoLocation <- "20nlogo"
kFatJarLocation <- "reachables-assembly-0.1.jar"

kLCMSDataLocation <- "/mnt/data-level1/lcms-ms1/"
print("Loading interpreter")
sc=scalaInterpreter(kFatJarLocation)
kImportMS1 <- 'import com.act.lcms.MS1'
sc%~%kImportMS1
getIonMzFunctionDef <- 'MS1.computeIonMz(mass, MS1.ionDeltas.filter(i => i.getName.equals(mode)).head)'
getIonMz <- intpDef(sc, 'mass: Double, mode: String', getIonMzFunctionDef)

getFullData <- function(filename) {
  validate(
    need(filename != "", "Filename field cannot be empty")
  )
  cat(paste("Getting full data from file:", filename))
  filepath <- paste0(kLCMSDataLocation, filename)
  msfile <- openMSfile(filepath, backend = "netCDF")
  hd <- header(msfile)
  ms1 <- which(hd$msLevel == 1)
  ms1.scans <- peaks(msfile, ms1) # list of num matrix
  list(hd = hd, ms1.scans = ms1.scans)
}

getScansAndHeader <- function(retention.time.range, full.data) {
  validate(
    need(length(retention.time.range) == 2, "Rentention time range needs to be a tuple"),
    need(is.numeric(retention.time.range), "Rentention time range needs to be numeric"),
    need(length(full.data$ms1.scans) > 0, "Full data should be of length > 0")
  )
  cat("Getting scans and headers")
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

getData <- function(target.mz.value, mz.band.halfwidth, scans.and.header) {
  validate(
    need(target.mz.value >= 50 && target.mz.value <= 950, "Target mz value should be between 50 and 950"),
    need(mz.band.halfwidth >= 0.00001, "M/Z band halfwidth should be >= 0.00001")
  )
  cat("Getting data for given mz scope")
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

plotRawData <- function(data, target.mz.value, mz.band.halfwidth, angle.theta, angle.phi) {
  cat("Plotting...")
  with(data, {
    min.ionic.mass <- target.mz.value - mz.band.halfwidth
    max.ionic.mass <- target.mz.value + mz.band.halfwidth
    scatter3D(retention.time, mz, intensity, pch = 16, cex = 1.5, type = "h",
              colkey = list(side = 1, length = 0.5, width = 0.5, cex.clab = 0.75), expand = 0.5,
              cex.lab = kChartLabelSizeFactor, cex.axis = kChartLabelSizeFactor,
              cex.main = kChartLabelSizeFactor, cex.sub = kChartLabelSizeFactor,
              zlab = "Intensity", xlab = "Retention time (sec)", ylab = "m/z (Da)",
              theta = angle.theta, phi = angle.phi, ticktype = "detailed", 
              ylim = c(min.ionic.mass, max.ionic.mass))
  })
}

shinyServer(function(input, output, session) {
  output$logo <- renderImage({
    # Return a list containing the filename
    list(src = k20logoLocation,
         contentType = "image/png",
         width = "200",
         height = "120",
         alt = "20n Logo")
  }, deleteFile = FALSE)
  
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
  
  # Reactive value, loading all the scans in memory.
  # Recomputed only when filename changes
  full.data.simple <- reactive({
    getFullData(input$filename)
  })
  
  full.data.simple.1 <- eventReactive(input$load.multi.1, {
    getFullData(input$filename1)
  })
  
  full.data.simple.2 <- eventReactive(input$load.multi.2, {
    getFullData(input$filename2)
  })
  
  full.data.simple.3 <- eventReactive(input$load.multi.3, {
    getFullData(input$filename3)
  })
  
  # Based on the retention time range, select relevant scans reactively
  # Recomputed only when retention time range changes
  scans.and.header <- eventReactive(input$load.simple, {
    full.data.simple <- full.data.simple()
    getScansAndHeader(input$retention.time.range, full.data.simple)
  })
  
  scans.and.header.1 <- eventReactive(input$load.multi.time, {
    full.data.simple <- full.data.simple.1()
    getScansAndHeader(input$retention.time.range.multi, full.data.simple)
  })
  scans.and.header.2 <- eventReactive(input$load.multi.time, {
    full.data.simple <- full.data.simple.2()
    getScansAndHeader(input$retention.time.range.multi, full.data.simple)
  })
  scans.and.header.3 <- eventReactive(input$load.multi.time, {
    full.data.simple <- full.data.simple.3()
    getScansAndHeader(input$retention.time.range.multi, full.data.simple)
  })
  
  
  # We compute the data in long format
  # Recomputed only when the target mass or the mz band halfwidth changes
  data.simple <- reactive({
    scans.header <- scans.and.header()
    target.mz <- target.mz()
    getData(target.mz, input$mz.band.halfwidth, scans.header)
  })
  
  data.1 <- reactive({
    scans.header <- scans.and.header.1()
    target.mz <- target.mz.multi()
    getData(target.mz, input$mz.band.halfwidth.multi, scans.header)
  })
  data.2 <- reactive({
    scans.header <- scans.and.header.2()
    target.mz <- target.mz.multi()
    getData(target.mz, input$mz.band.halfwidth.multi, scans.header)
  })
  
  data.3 <- reactive({
    scans.header <- scans.and.header.3()
    target.mz <- target.mz.multi()
    getData(target.mz, input$mz.band.halfwidth.multi, scans.header)
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
    
  detected.peaks <- reactive({
    data <- data.simple()
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
    data <- data.simple()
    target.mz <- target.mz()
    plotRawData(data, target.mz, input$mz.band.halfwidth, input$angle.theta, input$angle.phi)
  })
  
  output$plot1 <- renderPlot({
    data <- data.1()
    target.mz <- target.mz.multi()
    plotRawData(data, target.mz, input$mz.band.halfwidth.multi, input$angle.theta.multi, input$angle.phi.multi)
  })
  
  output$plot2 <- renderPlot({
    data <- data.2()
    target.mz <- target.mz.multi()
    plotRawData(data, target.mz, input$mz.band.halfwidth.multi, input$angle.theta.multi, input$angle.phi.multi)
  })
  
  output$plot3 <- renderPlot({
    data <- data.3()
    target.mz <- target.mz.multi()
    plotRawData(data, target.mz, input$mz.band.halfwidth.multi, input$angle.theta.multi, input$angle.phi.multi)
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
})
