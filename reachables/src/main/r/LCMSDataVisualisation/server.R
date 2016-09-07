library(shiny)
library(plot3D)
library(mzR)

kHydrogenMass <- 1.007276

shinyServer(function(input, output, session) {
 
  output$logo <- renderImage({
    # Return a list containing the filename
    list(src = "../../resources/20n.png",
         contentType = 'image/png',
         width = "200",
         height = "120",
         alt = "20n Logo")
  }, deleteFile = FALSE)


  # Reactive value, loading all the scans in memory.
  # Recomputed only when filename changes
  full.data <- reactive({
    filepath <- paste0('/mnt/data-level1/lcms-ms1/', input$filename)
    msfile <- openMSfile(filepath, backend="netCDF")
    hd <- header(msfile)
    ms1 <- which(hd$msLevel == 1)
    ms1.scans <- peaks(msfile, ms1) # list of num matrix
    list(hd = hd, ms1.scans = ms1.scans)
  })

  # Based on the retention time range, select relevant scans reactively
  # Recomputed only when retention time range changes
  scans.and.header <- reactive({
    min.rt <- input$retention.time.range[1]
    max.rt <- input$retention.time.range[2]

    full.data <- full.data()

    # Extract the relevant scans from the full dataset
    header <- full.data$hd
    ms1 <- which(header$msLevel == 1)
    rtsel <- header$retentionTime[ms1] > min.rt & header$retentionTime[ms1] < max.rt # vector of boolean
    scans <- full.data$ms1.scans[rtsel]

    # We need to replicate the retention time as many times as the length of each scan
    scan.lengths <- unlist(lapply(scans, function(x) length(x[, 1])))
    retention.time <- rep(header$retentionTime[rtsel], scan.lengths)
    list(retention.time = retention.time, scans = scans)
  })

  # We compute the data in long format
  # Recomputed only when the target mass or the mz band halfwidth changes
  data.long <- reactive({

    # We need to transform the monoisotopic mass to the ionic mass for matching with LCMS m/z values
    target.ionic.mass <- input$target.monoisotopic.mass + kHydrogenMass
    min.ionic.mass <- target.ionic.mass - input$mz.band.halfwidth
    max.ionic.mass <- target.ionic.mass + input$mz.band.halfwidth
    
    data <- scans.and.header()
    
    scans <- data$scans
    retention.time <- data$retention.time
    
    mz <- unlist(lapply(scans, function(x) x[, 1]))
    int <- unlist(lapply(scans, function(x) x[, 2]))

    mzsel <- mz < max.ionic.mass & mz > min.ionic.mass
    retention.time <- retention.time[mzsel]
    mz <- mz[mzsel]
    intensity <- int[mzsel]
    list(retention.time = retention.time, mz = mz, intensity = intensity)
  })
    
  output$plot <- renderPlot({
    data <- data.long()
    if (input$top.value) {
      intensity <- data$intensity + max(data$intensity) / 4
    } else {
      intensity <- data$intensity
    }
    monoisotopic.masses <- data$mz - kHydrogenMass
    scatter3D(data$retention.time, monoisotopic.masses, data$intensity, pch = 16, cex = 1.5, type = "h",
              colkey = list(side = 1, length = 0.5, width = 0.5, cex.clab = 0.75), expand = 0.5,
              zlab = "Intensity", xlab = "Retention time", ylab = "m/z (Monoisotopic mass)",
              theta = input$angle.theta, phi = input$angle.phi, ticktype = "detailed", zlim = c(0, max(intensity)))
    if (input$top.value) {
      # Compute index of max intensity point
      ind.max <- head(sort(data$intensity, decreasing = TRUE, index.return = TRUE)$ix, 1)
      # Display additional layer with top peak label
      text3D(data$rt[ind.max], mz[ind.max], int[ind.max], expand = 0.5, 
             theta = input$angle.theta, phi = input$angle.phi, labels = round(mz[ind.max], 6), add = TRUE)
    }
  })
})
