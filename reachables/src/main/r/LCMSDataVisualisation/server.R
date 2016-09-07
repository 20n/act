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
  
  full.data <- reactive({
    filepath <- paste0('/mnt/data-level1/lcms-ms1/', input$filename)
    ms <- openMSfile(filepath, backend="netCDF")
    hd <- header(ms)
    ms1 <- which(hd$msLevel == 1)
    ms1.scans <- peaks(ms, ms1) # list of num matrix
    list(hd = hd, ms1.scans = ms1.scans)
  })
  
  scans.and.header <- reactive({
    min.rt <- input$rt.range[1]
    max.rt <- input$rt.range[2]

    full.data <- full.data()
        
    hd <- full.data$hd
    ms1 <- which(hd$msLevel == 1)
    rtsel <- hd$retentionTime[ms1] > min.rt & hd$retentionTime[ms1] < max.rt # vector of boolean
    scans <- full.data$ms1.scans[rtsel]
    howmany <- unlist(lapply(scans, function(x) length(x[, 1])))
    rt <- rep(hd$retentionTime[rtsel], howmany)
    list(rt = rt, scans = scans)
  })
  
  data.long <- reactive({
    
    min.mz <- input$target.mz + kHydrogenMass - input$mz.band.halfwidth
    max.mz <- input$target.mz + kHydrogenMass + input$mz.band.halfwidth
    
    data <- scans.and.header()
    
    all.scans <- data$scans
    rt <- data$rt
    
    mz <- unlist(lapply(all.scans, function(x) x[, 1]))
    int <- unlist(lapply(all.scans, function(x) x[, 2]))

    mzsel <- mz < max.mz & mz > min.mz
    rt <- rt[mzsel]
    mz <- mz[mzsel]
    int <- int[mzsel]
    list(rt = rt, mz = mz, int = int)
  })
    
  output$plot <- renderPlot({
    par(mfrow = c(1, 1), mar = c(2, 2, 2, 2))
    data <- data.long()
    if (input$top.value) {
      int <- data$int + max(data$int) / 4  
    } else {
      int <- data$int
    }
    mz <- data$mz - kHydrogenMass
    scatter3D(data$rt, mz, data$int, pch = 16, cex = 1.5, type = "h", colkey = list(side = 1, length = 0.5, width = 0.5, cex.clab = 0.75), 
              expand = 0.5, 
              zlab = "intensity", xlab = "retention time", ylab = "m/z", 
              theta = input$angle.theta, phi = input$angle.phi, ticktype = "detailed", zlim = c(0, max(int)))
    if (input$top.value) {
      ind.max <- head(sort(data$int, decreasing = TRUE, index.return = TRUE)$ix, 1)
      text3D(data$rt[ind.max], mz[ind.max], int[ind.max], expand = 0.5, 
             theta = input$angle.theta, phi = input$angle.phi, labels = round(mz[ind.max], 6), add = TRUE)
    }
  })
})