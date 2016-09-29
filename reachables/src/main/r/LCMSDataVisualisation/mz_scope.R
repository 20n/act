kModes <- c("M (use mass as target mz value)", "M+H", "M-H", "M+Na", "M+Li", "M+H-H2O")
kDefaultMzValue <- 463.184234
kDefaultMzBandwidthValue <- 0.01

mzScopeInput <- function(id) {
  ns <- NS(id)
  tagList(
    h3("M/Z scope"),
    selectInput(ns("mode"), label = "m/z mode", choices = kModes, selected = "M+H"),
    numericInput(ns("target.monoisotopic.mass"), label = "Target monoisotopic mass", value = kDefaultMzValue, step = 0.001),
    numericInput(ns("mz.band.halfwidth"), label = "Mass charge band halfwidth", value = kDefaultMzBandwidthValue, step = 0.01)
  )
}

mzScope <- function(input, output, session) {
  return(input)
}
