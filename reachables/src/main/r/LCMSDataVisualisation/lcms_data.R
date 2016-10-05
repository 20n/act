# Modules related to LCMS traces data

# lcmsSingleTracePeaks module server function
lcmsSingleTracePeaks <- function(input, output, session, scan.filename, retention.time.range, target.mz, mz.band.halfwidth, load) {
  
  scan.file <- reactive({
    getAndCacheScanFile(scan.filename())
  })
  
  scans <- eventReactive(load(), {
    retention.time.range <- retention.time.range()
    shiny::validate(
      need(!is.null(retention.time.range), "Retention time range is missing.")
    )
    getScans(scan.file(), retention.time.range)
  })
  
  peaks <- reactive({
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    getPeaksInScope(scans(), target.mz, mz.band.halfwidth)
  })
  
  peaks
}

# lcmsTracesPeaks module server function
lcmsTracesPeaks <- function(input, output, session, scan.filenames, retention.time.range, target.mz, mz.band.halfwidth) {
  
  scan.files <- reactive({
    lapply(scan.filenames(), getAndCacheScanFile)
  })
  
  scans <- reactive({
    retention.time.range <- retention.time.range()
    shiny::validate(
      need(!is.null(retention.time.range), "Retention time range is missing.")
    )
    lapply(scan.files(), function(x) getScans(x, retention.time.range))
  })
  
  peaks <- reactive({
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    lapply(scans(), function(x) getPeaksInScope(x, target.mz, mz.band.halfwidth))
  })
  
  peaks
}
