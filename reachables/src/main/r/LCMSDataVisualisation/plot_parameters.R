# Plot parameters module

plotParametersInput <- function(id) {
  ns <- NS(id)
  tagList(
    h3("Plot parameters"),
    sliderInput(ns("angle.theta"), label = "Azimuthal Angle (left <-> right)", 
                min = 0, max = 90, value = 90, step = 5),
    sliderInput(ns("angle.phi"), label = "Colatitude Angle (down <-> up)",
                min = 0, max = 90, value = 20, step = 5)
  )
}

plotParameters <- function(input, output, session) {
  return(input)
}
