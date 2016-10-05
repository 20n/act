# This file (app.R) contains the core of teh visualisation app.
# Its main tasks are as follows:
# - loading the various libraries needed across the app
# - sourcing the modules
# - defining the core UI and server functions

# Note: Scala and a handful of R libraries should be installed on the server.
# Please run in R:
# install.packages(c("shiny", "rscala", "dplyr", "plot3D", "mzR", "classInt", "jsonlite", "logging", "digest"))
# scalaInstall()
# If package `mzR` fails to install, please follow the steps below:
# source("https://bioconductor.org/biocLite.R") # try http:// if https:// URLs are not supported
# biocLite("mzR")

# JSON parser
library(jsonlite)
# Shiny library (should already be loaded when app started)
require(shiny)
# 3D plotting library
library(plot3D)
# unified parser for MS data 
library(mzR)
# data manipulation package: provides functions like filter/arrange on data frames
library(dplyr)
# classification package (used for finding intervals in kmeans)
library(classInt)
# Logging library (yay!)
library(logging)
# Scala interpreter in R. Gives us access to all our Java functionalities
library(rscala)
# Basic hashing package
library(digest)

# Set logging level
basicConfig('DEBUG')

source("lcms_lib.R")
source("mz_scope.R")
source("plot_parameters.R")
source("lcms_data.R")
source("lcms_plot.R")
source("molecule_renderer.R")
source("lcms_single_plate.R")
source("lcms_multi_plate.R")
source("lcms_config_plates.R")

loginfo("Done loading libraries and sourcing modules")

# 20nlogo -> symlink to the 20n logo. Should be in the working directory of the server.
k20logoLocation <- "20nlogo"

# The server side performs the computations behind the scenes
# It collects a list of inputs from ui.R and produces a list of output
# Here, the logic is mainly delegated to the modules
server <- function(input, output, session) {
  
  observe({
    # Parse query string and update selected tab based on the parameter 'window'
    query <- parseQueryString(session$clientData$url_search)
    window <- query[['window']]
    if (!is.null(window) && window %in% c("simple", "multi", "config") ) {
      updateNavbarPage(session, "main-navbar", selected = window)
      logdebug("Active tab was updated to %s", window)
    }
  })
  
  # Render 20n logo
  output$logo <- renderImage({
    list(src = k20logoLocation, contentType = "image/png",
         width = "200", height = "120", alt = "20n Logo")
    # Keep the file after rendering!
  }, deleteFile = FALSE)
  
  # Call the main modules
  callModule(lcmsSinglePlate, "simple")
  callModule(lcmsMultiPlate, "multi")
  callModule(lcmsConfigPlates, "config")
}

# The UI side defines the layout and display of the different app components.
# The UI can render outputs computed by the server
# (ex: plotOutput("plot", height = "700px") plots the output "plot" computed by the server)
# or define inputs that will be communicated with the server (ex: sliderInput("retention.time.range", ...))
# Again, the magic mainly happens in the *UI modules components
ui <- fluidPage(
  
  # Header panel, containing the logo and the app title
  fluidRow(
    class = "Header",
    column(2, imageOutput("logo", height = "100%")),
    column(8, headerPanel("LCMS data explorer"), align = "center"),
    column(2)
  ),
  # NavBarPage, with a menu and selected tabs.
  navbarPage("Visualisation mode:", id = "main-navbar",
             selected = "config",
             tabPanel("Simple", value = "simple",
                      sidebarPanel(lcmsSinglePlateInput("simple")),
                      mainPanel(lcmsSinglePlateUI("simple"))
             ),
             tabPanel("Multi", value = "multi",
                      sidebarPanel(lcmsMultiPlateInput("multi")),
                      mainPanel(lcmsMultiPlateUI("multi"))
             ),
             tabPanel("Configuration-based", value = "config",
                      sidebarPanel(lcmsConfigPlatesInput("config")),
                      mainPanel(lcmsConfigPlatesUI("config"))
             )
  )
)

shinyApp(ui = ui, server = server)