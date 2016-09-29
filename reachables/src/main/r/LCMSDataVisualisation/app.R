k20logoLocation <- "20nlogo"

source("lcms_lib.R")
source("mz_scope.R")
source("plot_parameters.R")
source("lcms_data.R")
source("lcms_plot.R")
source("lcms_single_plate.R")
source("lcms_multi_plate.R")
source("lcms_config_plates.R")

# server.R performs the computations behind the scenes
# It collects a list of inputs from ui.R and produces a list of output

# Note that the R libraries "shiny" and "rscala" well as Scala should be installed on the machine.
# To perform these tasks, please run in R:
# install.packages(c("shiny", "rscala", "dplyr", "plot3D", "mzR", "classInt"))
# scalaInstall()

# If package `mzR` fails to install, please follow the steps below:
# source("https://bioconductor.org/biocLite.R") # try http:// if https:// URLs are not supported
# biocLite("mzR")

# Finally, this assumes that two symlinks have been created and are located in the app directory:
# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
# 20nlogo -> symlink to the 20n logo in the resources directory
k20logoLocation <- "20nlogo"

server <- function(input, output, session) {
  
  # 20n logo rendering
  output$logo <- renderImage({
    # Return a list containing the filename
    list(src = k20logoLocation,
         contentType = "image/png",
         width = "200",
         height = "120",
         alt = "20n Logo")
  }, deleteFile = FALSE)
  
  callModule(lcmsSinglePlate, "simple")
  callModule(lcmsMultiPlate, "multi")
  callModule(lcmsConfigPlates, "config", plot.parameters)
}


# ui.R defines the layout and display of the different app components.

# The UI can render outputs computed by the server
# (ex: plotOutput("plot", height = "700px") plots the output "plot" computed by server.R)
# or define inputs that will be communicated with the server (ex: sliderInput("retention.time.range", ...))

ui <- fluidPage(
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

shinyApp(ui = ui, server = server)