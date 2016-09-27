# ui.R defines the layout and display of the different app components.

# The UI can render outputs computed by the server
# (ex: plotOutput("plot", height = "700px") plots the output "plot" computed by server.R)
# or define inputs that will be communicated with the server (ex: sliderInput("retention.time.range", ...))

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


# lcmsPlate Module UI function
lcmsSinglePlateInput <- function(id, label = "LCMS single plate") {
  # Create a namespace function using the provided id
  ns <- NS(id)
  
  tagList(
    h3("Scans selection"),
    textInput(ns("filename"), label = "File name", value = "Plate_jaffna3_B1_0815201601.nc"),
    sliderInput(ns("retention.time.range"), label = "Retention Time range",
                min = 0, max = 450, value = c(130, 160), step = 5),
    actionButton(ns("load"), "Refresh scans!", icon("magic"), width = "100%", 
                 style="color: #fff; background-color: #337ab7; border-color: #2e6da4"),
    mzScopeInput("mz.scope"),
    plotParametersInput("plot.parameters")
  )
}


lcmsMultiPlateInput <- function(id, label = "LCMS multi plate") {
  # Create a namespace function using the provided id
  ns <- NS(id)
  tagList(
    h3("Scans selection"),
    textInput(ns("filename1"), label = "Filename - Plate 1", value = "Plate_jaffna3_A1_0815201601.nc"),
    textInput(ns("filename2"), label = "Filename - Plate 2", value = "Plate_jaffna3_B1_0815201601.nc"),
    textInput(ns("filename3"), label = "Filename - Plate 3", value = "Plate_jaffna3_C1_0815201601.nc"),
    sliderInput(ns("retention.time.range"), label = "Retention Time range",
                min = 0, max = 450, value = c(130, 160), step = 5),
    actionButton(ns("load"), "Refresh scans!", icon("magic"), width = "100%", 
                 style="color: #fff; background-color: #337ab7; border-color: #2e6da4"),
    mzScopeInput("mz.scope.multi"),
    plotParametersInput("plot.parameters.multi")
  )
}

lcmsConfigPlatesInput <- function(id, label = "LCMS config plates") {
  # Create a namespace function using the provided id
  ns <- NS(id)
  tagList(
    h3("Input configuration"),
    fileInput(ns("config.file"), label = "Choose a configuration file", accept=c("application/json")),
    p("Example config: '/shared-data/Thomas/lcms_viz/FR_config_file/sample_config.json'"),
    h3("Peak selection"),
    uiOutput(ns("ui.peaks")),
    em("Peak format is {mz-value} - {retention-time} - {rank-factor}"),
    uiOutput(ns("ui.rt.mz.scope")),
    plotParametersInput("plot.parameters.config"),
    checkboxInput(ns("normalize"), "Normalize values", value = FALSE)
  )
}

lcmsSinglePlateUI <- function(id) {
  ns <- NS(id)
  tagList(
    em("Disclaimer: the peak detection will only detect peaks of intensity more than 1e4 and can't (by design) detect more than 2 peaks."),
    h4("Target m/z value"),
    textOutput(ns("target.mz")),
    h4("Detected peaks"),
    tableOutput(ns("detected.peaks")),
    h4("3D scatterplot of the raw data"),
    lcmsPlotOutput(ns("plot"), height = "700px")
  )
}

lcmsMultiPlateUI <- function(id) {
  ns <- NS(id)
  tagList(
    h4("Target m/z value"),
    textOutput(ns("target.mz")),
    h4("3D scatterplot of the raw data"),
    lcmsPlotOutput(ns("plot1"), height = "450px"),
    lcmsPlotOutput(ns("plot2"), height = "450px"),
    lcmsPlotOutput(ns("plot3"), height = "450px")  
  )
}

lcmsConfigPlatesUI <- function(id) {
  ns <- NS(id)
  uiOutput(ns("plots"))
}


lcmsPlotOutput <- function(id, ...) {
  ns <- NS(id)
  plotOutput(ns("plot"), ...)
}


shinyUI(fluidPage(
  fluidRow(
    class = "Header",
    column(2, imageOutput("logo", height = "100%")),
    column(8, headerPanel("LCMS data explorer"), align = "center"),
    column(2)
  ),
  navbarPage("Visualisation mode:", 
             selected = "Configuration-based",
             tabPanel("Simple",
                      sidebarPanel(
                        lcmsSinglePlateInput("simple")
                      ),
                      mainPanel(
                        lcmsSinglePlateUI("simple")
                      )
             ),
             tabPanel("Multi",
                      sidebarPanel(
                        lcmsMultiPlateInput("multi")
                      ),
                      mainPanel(
                        lcmsMultiPlateUI("multi")
                      )
             ),
             tabPanel("Configuration-based",
                      sidebarPanel(
                        lcmsConfigPlatesInput("config")
                      ),
                      mainPanel(
                        lcmsConfigPlatesUI("config")
                      )
             )
  )
)
)
