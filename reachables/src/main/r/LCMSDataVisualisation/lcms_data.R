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
    getPeaksInScope(scans, target.mz, mz.band.halfwidth)
  })
  
  return(peaks)
}

lcmsPlatesData <- function(input, output, session, platenames, retention.time.range, target.mz, mz.band.halfwidth) {
  
  plates <- reactive({
    shiny::validate(
      need(is.reactive(platenames), "`platename` should be a reactive value.")
    )
    lapply(platenames(), getAndCachePlate)
  })
  
  scans <- reactive({
    shiny::validate(
      need(is.reactive(retention.time.range), "`retention.time.range` should be a reactive value."),
      need(is.reactive(plates), "`plates` should be a reactive value.")
    )
    retention.time.range <- retention.time.range()
    shiny::validate(
      need(!is.null(retention.time.range), "Retention time range is missing.")
    )
    lapply(plates(), function(x) getScans(x, retention.time.range))
  })
  
  peaks <- reactive({
    shiny::validate(
      need(is.reactive(target.mz), "`target.mz` should be a reactive value."),
      need(is.reactive(mz.band.halfwidth), "`mz.band.halfwidth` should be a reactive value."),
      need(is.reactive(scans), "`scans` should be a reactive value.")
    )
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    lapply(scans(), function(x) getPeaksInScope(x, target.mz, mz.band.halfwidth))
  })
  
  return(peaks)
}
