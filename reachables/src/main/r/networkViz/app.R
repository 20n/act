
# Shiny library (should already be loaded when app started)
require(shiny)
require(visNetwork)
require(logging)
require(digest)
require(rscala)

# Set logging level
basicConfig('DEBUG')

source("../LCMSDataVisualisation/molecule_renderer.R")
source("../LCMSDataVisualisation/lcms_lib.R")
source("lib.R")

loginfo("Done loading libraries and sourcing modules")

# 20nlogo -> symlink to the 20n logo. Should be in the working directory of the server.
k20logoLocation <- "20nlogo"

# The server side performs the computations behind the scenes
# It collects a list of inputs from ui.R and produces a list of output
# Here, the logic is mainly delegated to the modules
server <- function(input, output, session) {
  
  inputFileString <- reactive(
    getInputFileAsString(input$dot.graph.file)
  )
  
  # Render 20n logo
  output$logo <- renderImage({
    list(src = k20logoLocation, contentType = "image/png",
         width = "200", height = "120", alt = "20n Logo")
    # Keep the file after rendering!
  }, deleteFile = FALSE)
  
  output$network <- renderVisNetwork({
    visNetwork(dot = inputFileString()) %>%
      visEvents(selectNode = "function(nodes) {
                Shiny.onInputChange('current_node_id', nodes);
                ;}")
  })
  
  output$shiny_return <- renderPrint({
    unlist(input$current_node_id$node)
  })
  
  
  
  
  observe({
    callModule(moleculeRenderer, "molecule", reactive(unlist(input$current_node_id$node)), "200px")
    #visNetworkProxy("network") %>%
    #  visOptions(manipulation = TRUE)
  })

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
    column(8, headerPanel("Network magic"), align = "center"),
    column(2)
  ),
  sidebarPanel(
    fileInput("dot.graph.file", label = "Choose a graph file (DOT format)")
  ),
  mainPanel(
    visNetworkOutput("network", height = "700px"),
    textOutput("shiny_return"),
    moleculeRendererUI("molecule")
  )
)

shinyApp(ui = ui, server = server)
