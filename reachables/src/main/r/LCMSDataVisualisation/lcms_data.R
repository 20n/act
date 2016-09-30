# Modules for compute LCMS plates data

# lcmsSinglePlateData module server function
lcmsSinglePlateData <- function(input, output, session, platename, retention.time.range, target.mz, mz.band.halfwidth, load) {
  
  plate <- reactive({
    getAndCachePlate(platename())
  })
  
  scans <- eventReactive(load(), {
    retention.time.range <- retention.time.range()
    plate <- plate()
    shiny::validate(
      need(!is.null(retention.time.range), "Retention time range is missing.")
    )
    getScans(plate, retention.time.range)
  })
  
  peaks <- reactive({
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    scans <- scans()
    memGetPeaksInScope(scans, target.mz, mz.band.halfwidth)
  })
  
  peaks
}

# lcmsPlatesData module server function
lcmsPlatesData <- function(input, output, session, platenames, retention.time.range, target.mz, mz.band.halfwidth) {
  
  plates <- reactive({
    lapply(platenames(), getAndCachePlate)
  })
  
  scans <- reactive({
    retention.time.range <- retention.time.range()
    shiny::validate(
      need(!is.null(retention.time.range), "Retention time range is missing.")
    )
    lapply(plates(), function(x) getScans(x, retention.time.range))
  })
  
  peaks <- reactive({
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    lapply(scans(), function(x) memGetPeaksInScope(x, target.mz, mz.band.halfwidth))
  })
  
  peaks
}
