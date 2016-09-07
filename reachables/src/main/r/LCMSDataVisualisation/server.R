library(shiny)
library(plot3D)
library(mzR)
library(dplyr)

kHMass <- 1.007276
kNaMass <- 22.989771
kLiMass <- 7.016004
kKMass <- 38.963707
kOMass <- 15.994915

kChartLabelSizeFactor <- 1.3
kLabelFactor <- 1.2

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
    scan.lengths <- unlist(lapply(scans, nrow))
    retention.time <- rep(header$retentionTime[rtsel], scan.lengths)
    list(retention.time = retention.time, scans = scans)
  })
  
  # We compute the data in long format
  # Recomputed only when the target mass or the mz band halfwidth changes
  data.long <- reactive({
    
    # We need to transform the monoisotopic mass to the ionic mass for matching with LCMS m/z values
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
    switch(input$mode,
           "M" = target.mass,
           "M+H" = target.mass + kHMass,
           "M+Na" = target.mass + kNaMass,
           "M+Li" = target.mass + kLiMass,
           "M+H+H2O" = target.mass + 3 * kHMass + kOMass)
  })
  
  output$plot <- renderPlot({
    data <- data.long()
    with(data, {
      # Label factor, used to plot labels a little above points
      zlim.up <- max(intensity) * kLabelFactor
      scatter3D(retention.time, mz, intensity, pch = 16, cex = 1.5, type = "h",
                colkey = list(side = 1, length = 0.5, width = 0.5, cex.clab = 0.75), expand = 0.5,
                cex.lab=kChartLabelSizeFactor, cex.axis=kChartLabelSizeFactor,
                cex.main=kChartLabelSizeFactor, cex.sub=kChartLabelSizeFactor,
                zlab = "Intensity", xlab = "Retention time", ylab = "m/z (Monoisotopic mass)",
                theta = input$angle.theta, phi = input$angle.phi, ticktype = "detailed", zlim = c(0, zlim.up))
      top.points <- data %>% top_n(1, intensity)
      
      if (input$top.value) {
        # Display additional layer with top peak label
        with(top.points, text3D(retention.time, mz, intensity * kLabelFactor, add = TRUE, labels = round(monoisotopic.masses, 6)))
      }
    })
  })

  output$target.mz <- renderText({
    paste0("Corresponding m/z value is ", target.mz())
  })
})
