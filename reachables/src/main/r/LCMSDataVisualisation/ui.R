shinyUI(fluidPage(
  fluidRow(
    class = "myRow1",
    column(2, imageOutput("logo", height = "100%")),
    column(8, headerPanel("LCMS data explorer"), align = "center"),
    column(2)
  ),
  sidebarLayout(
    sidebarPanel(
      h3("Scans selection"),
      textInput("filename", label = "File name", value = "Plate_jaffna3_B1_0815201601.nc"),
      sliderInput("retention.time.range", label = "Retention Time range",
                  min = 0, max = 450, value = c(130, 160)),
      h3("M/Z scope"),
      numericInput("target.monoisotopic.mass", label = "Target monoisotopic mass", value = 463.184234, step = 0.001),
      numericInput("mz.band.halfwidth", label = "Mass charge band halfwidth", value = 0.01, step = 0.01),
      h3("Plot parameters"),
      sliderInput("angle.theta", label = "Azimuthal Angle (left <-> right)", 
                  min = 0, max = 360, value = 90),
      sliderInput("angle.phi", label = "Colatitude Angle (up <-> down)",
                  min = 0, max = 90, value = 20),
      checkboxInput("top.value", label = "Display m/z value for highest peak", value = FALSE)
    ),
    mainPanel(
      plotOutput("plot", height = "800px")
    )
  )
)
)