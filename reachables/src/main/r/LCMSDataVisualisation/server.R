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

kChartLabelSizeFactor <- 1.3
kLabelFactor <- 1.2
kIntensityThreshold <- 10000
kSSRatio <- 20

# Finally, this assumes that two symlinks have been created and are located in the app directory:
# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
# 20nlogo -> symlink to the 20n logo in the resources directory
k20logoLocation <- "20nlogo"
kFatJarLocation <- "reachables-assembly-0.1.jar"

kLCMSDataLocation <- "/mnt/data-level1/lcms-ms1/"

sc=scalaInterpreter(kFatJarLocation)
kImportMS1 <- 'import com.act.lcms.MS1'
sc%~%kImportMS1
getIonMzFunctionDef <- 'MS1.computeIonMz(mass, MS1.ionDeltas.filter(i => i.getName.equals(mode)).head)'
getIonMz <- intpDef(sc, 'mass: Double, mode: String', getIonMzFunctionDef)

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
    target.mz <- as.double(query[['target.mz']])
    mz.band <- as.double(query[['mz.band']])
    rt.min <- as.double(query[['rt.min']])
    rt.max <- as.double(query[['rt.max']])
    if (!is.null(filename)) {
      updateTextInput(session, "filename", value = filename)
    }
    if (!is.null(mode)) {
      updateSelectInput(session, "mode", selected = mode)
    }
    if (!is.null(target.mz)) {
      updateNumericInput(session, "target.monoisotopic.mass", value = target.mz)
    }
    if (!is.null(mz.band)) {
      updateNumericInput(session, "mz.band.halfwidth", value = mz.band)
    }
    if (!is.null(rt.min) && !is.null(rt.max)) {
      updateSliderInput(session, "retention.time.range", value = c(rt.min, rt.max))      
    }
  })
  
  # Reactive value, loading all the scans in memory.
  # Recomputed only when filename changes
  full.data <- reactive({
    filepath <- paste0(kLCMSDataLocation, input$filename)
    msfile <- openMSfile(filepath, backend = "netCDF")
    hd <- header(msfile)
    ms1 <- which(hd$msLevel == 1)
    ms1.scans <- peaks(msfile, ms1) # list of num matrix
    list(hd = hd, ms1.scans = ms1.scans)
  })
  
  # Based on the retention time range, select relevant scans reactively
  # Recomputed only when retention time range changes
  scans.and.header <- eventReactive(input$load, {
    min.rt <- input$retention.time.range[1]
    max.rt <- input$retention.time.range[2]
    
    full.data <- full.data()
    
    # Extract the relevant scans from the full dataset
    header <- full.data$hd
    ms1 <- which(header$msLevel == 1)
    rtsel <- header$retentionTime[ms1] > min.rt & header$retentionTime[ms1] < max.rt # vector of boolean
    scans <- full.data$ms1.scans[rtsel]
    
    # We need to replicate the retention time as many times as the length of each scan
    scan.lengths <- unlist(lapply(scans, nrow))
    retention.time <- rep(header$retentionTime[rtsel], scan.lengths)
    list(retention.time = retention.time, scans = scans)
  })
  
  # We compute the data in long format
  # Recomputed only when the target mass or the mz band halfwidth changes
  data.long <- reactive({
    
    target.mz.value <- target.mz()
    min.ionic.mass <- target.mz.value - input$mz.band.halfwidth
    max.ionic.mass <- target.mz.value + input$mz.band.halfwidth
    scans.header <- scans.and.header()
    data <- with(scans.header, {
      mz <- unlist(lapply(scans, function(x) x[, "mz"]))
      intensity <- unlist(lapply(scans, function(x) x[, "intensity"]))
      data.frame(mz = mz, retention.time = retention.time, intensity = intensity)
    })
    
    data %>% 
      filter(mz < max.ionic.mass & mz > min.ionic.mass)
  })
  
  target.mz <- reactive({
    target.mass <- input$target.monoisotopic.mass
    if (input$mode == "M (use mass as target mz value)") {
      target.mass
    } else {
      getIonMz(target.mass, input$mode)
    }
  })
  
  detected.peaks <- reactive({
    data <- data.long()
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
    data <- data.long()
    with(data, {
      target.mz.value <- target.mz()
      min.ionic.mass <- target.mz.value - input$mz.band.halfwidth
      max.ionic.mass <- target.mz.value + input$mz.band.halfwidth
      scatter3D(retention.time, mz, intensity, pch = 16, cex = 1.5, type = "h",
                colkey = list(side = 1, length = 0.5, width = 0.5, cex.clab = 0.75), expand = 0.5,
                cex.lab = kChartLabelSizeFactor, cex.axis = kChartLabelSizeFactor,
                cex.main = kChartLabelSizeFactor, cex.sub = kChartLabelSizeFactor,
                zlab = "Intensity", xlab = "Retention time (sec)", ylab = "m/z (Da)",
                theta = input$angle.theta, phi = input$angle.phi, ticktype = "detailed", 
                ylim = c(min.ionic.mass, max.ionic.mass))
    })
  })
  output$target.mz <- renderText({
    sprintf("Target m/z value (computed from input mass and mode): %s", target.mz())
  })
  output$detected.peaks <- renderTable({
    detected.peaks()
  }, digits = c(0, 6, 2, 0))
})
