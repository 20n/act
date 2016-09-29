# server.R performs the computations behind the scenes
# It collects a list of inputs from ui.R and produces a list of output

# Note that the R libraries "shiny" and "rscala" well as Scala should be installed on the machine.
# To perform these tasks, please run in R:
# install.packages(c("shiny", "rscala", "dplyr", "plot3D", "mzR", "classInt"))
# scalaInstall()

# If package `mzR` fails to install, please follow the steps below:
# source("https://bioconductor.org/biocLite.R") # try http:// if https:// URLs are not supported
# biocLite("mzR")

library(rscala)
library(shiny)

# Finally, this assumes that two symlinks have been created and are located in the app directory:
# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
# 20nlogo -> symlink to the 20n logo in the resources directory
k20logoLocation <- "20nlogo"

source("lcms_plate.R")
source("lcms_lib.R")

shinyServer(function(input, output, session) {
  
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
})
