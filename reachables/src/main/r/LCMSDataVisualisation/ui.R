# ui.R defines the layout and display of the different app components.

# The UI can render outputs computed by the server
# (ex: plotOutput("plot", height = "700px") plots the output "plot" computed by server.R)
# or define inputs that will be communicated with the server (ex: sliderInput("retention.time.range", ...))

kModes <- c("M (use mass as target mz value)", "M+H", "M-H", "M+Na", "M+Li", "M+H-H2O")

shinyUI(fluidPage(
  fluidRow(
    class = "Header",
    column(2, imageOutput("logo", height = "100%")),
    column(8, headerPanel("LCMS data explorer"), align = "center"),
    column(2)
  ),
  navbarPage("Visualisation mode:",
             tabPanel("Simple",
                      sidebarPanel(
                        h3("Scans selection"),
                        textInput("filename", label = "File name", value = "Plate_jaffna3_B1_0815201601.nc"),
                        sliderInput("retention.time.range", label = "Retention Time range",
                                    min = 0, max = 450, value = c(130, 160), step = 5),
                        actionButton("load", "Refresh scans!", icon("magic"), width = "100%", 
                                     style="color: #fff; background-color: #337ab7; border-color: #2e6da4"),
                        h3("M/Z scope"),
                        selectInput("mode", label = "m/z mode", choices = kModes, selected = "M+H"),
                        numericInput("target.monoisotopic.mass", label = "Target monoisotopic mass", value = 463.184234, step = 0.001),
                        numericInput("mz.band.halfwidth", label = "Mass charge band halfwidth", value = 0.01, step = 0.01),
                        h3("Plot parameters"),
                        sliderInput("angle.theta", label = "Azimuthal Angle (left <-> right)", 
                                    min = 0, max = 360, value = 90, step = 5),
                        sliderInput("angle.phi", label = "Colatitude Angle (down <-> up)",
                                    min = 0, max = 90, value = 20, step = 5)
                      ),
                      mainPanel(
                        em("Disclaimer: the peak detection will only detect peaks of intensity more than 1e4 and can't (by design) detect more than 2 peaks."),
                        h4("Target m/z value"),
                        textOutput("target.mz"),
                        h4("Detected peaks"),
                        tableOutput("detected.peaks"),
                        h4("3D scatterplot of the raw data"),
                        plotOutput("plot", height = "700px")
                      )
             ),
             tabPanel("Multi",
                      sidebarPanel(
                        h3("Scans selection"),
                        div(
                          div(style="width:80%; display:inline-block", textInput("filename1", label = "File name", value = "Plate_jaffna3_A1_0815201601.nc")),
                          div(style="display:inline-block", actionButton("load.1", icon("refresh"),
                                                                         style="color: #fff; background-color: #337ab7; border-color: #2e6da4"))
                        ),
                        div(
                          div(style="width:80%; display:inline-block", textInput("filename2", label = "File name", value = "Plate_jaffna3_B1_0815201601.nc")),
                          div(style="display:inline-block", actionButton("load.2", icon("refresh"),
                                                                         style="color: #fff; background-color: #337ab7; border-color: #2e6da4"))
                        ),
                        div(
                          div(style="width:80%; display:inline-block", textInput("filename3", label = "File name", value = "Plate_jaffna3_C1_0815201601.nc")),
                          div(style="display:inline-block", actionButton("load.3", icon("refresh"),
                                                                         style="color: #fff; background-color: #337ab7; border-color: #2e6da4"))
                        ),
                        div(
                          div(style="width:80%; display:inline-block", sliderInput("retention.time.range.multi", label = "Retention Time range",
                                                                        min = 0, max = 450, value = c(130, 160), step = 5)),
                          div(style="display:inline-block", actionButton("load.multi.time", icon("refresh"),
                                                                         style="color: #fff; background-color: #337ab7; border-color: #2e6da4"))
                        ),
                        em("Press the time range refresh button to display plots"),
                        h3("M/Z scope"),
                        selectInput("mode.multi", label = "m/z mode", choices = kModes, selected = "M+H"),
                        numericInput("target.monoisotopic.mass.multi", label = "Target monoisotopic mass", value = 463.184234, step = 0.001),
                        numericInput("mz.band.halfwidth.multi", label = "Mass charge band halfwidth", value = 0.01, step = 0.01),
                        h3("Plot parameters"),
                        sliderInput("angle.theta.multi", label = "Azimuthal Angle (left <-> right)", 
                                    min = 0, max = 360, value = 90, step = 5),
                        sliderInput("angle.phi.multi", label = "Colatitude Angle (down <-> up)",
                                    min = 0, max = 90, value = 20, step = 5)
                      ),
                      mainPanel(
                        h4("Target m/z value"),
                        textOutput("target.mz.multi"),
                        h4("3D scatterplot of the raw data"),
                        plotOutput("plot1", height = "500px"),
                        plotOutput("plot2", height = "500px"),
                        plotOutput("plot3", height = "500px")
                      )
             )
  )
)
)
