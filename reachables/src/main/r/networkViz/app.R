
debug <- FALSE


# Shiny library (should already be loaded when app started)
require(shiny)
require(visNetwork)
require(jsonlite)
require(logging)
require(digest)
require(rscala)

# Set logging level
basicConfig('DEBUG')
if (!debug) {
  source("../LCMSDataVisualisation/molecule_renderer.R")  
  source("lib.R")  
}

loginfo("Done loading libraries and sourcing modules")

# 20nlogo -> symlink to the 20n logo. Should be in the working directory of the server.
k20logoLocation <- "20nlogo"


getNetworkJson <- function(input.file) {
  shiny::validate(
    need(!is.null(input.file), "Please upload a graph file.") 
  )
  fromJSON(input.file$datapath)
}

# The server side performs the computations behind the scenes
# It collects a list of inputs from ui.R and produces a list of output
# Here, the logic is mainly delegated to the modules
server <- function(input, output, session) {
  
  network <- reactive(
    getNetworkJson(input$dot.graph.file)
  )
  
  # Render 20n logo
  output$logo <- renderImage({
    list(src = k20logoLocation, contentType = "image/png",
         width = "200", height = "120", alt = "20n Logo")
    # Keep the file after rendering!
  }, deleteFile = FALSE)
  
  output$network <- renderVisNetwork({
    viz <- visNetwork(nodes = network()$nodes, edges = network()$edges) %>%
      visNodes(shadow = TRUE) %>%
      visEvents(selectNode = "function(nodes) {
                Shiny.onInputChange('current_node_id', nodes);
                ;}") %>%
      visEdges(arrows = list(middle = TRUE)) %>%
      visOptions(highlightNearest = TRUE)
    if (input$hierarchical) {
      viz %>%
        visLayout(hierarchical = TRUE) %>%
        visHierarchicalLayout(direction = "LR")
    } else {
      viz
    }
  
  })
  
  inchi <- reactive({
    shiny::validate(
      need(length(input$current_node_id$node) == 1, "Please select a node on the graph!!")
    )
    nodes <- network()$nodes
    nodes$inchi[which(nodes$id == input$current_node_id$node[[1]])]
  })
  
  output$shiny_return <- renderText({
    inchi()
  })
  
  observe({
    if (!debug) {
      callModule(moleculeRenderer, "molecule", reactive(c(inchi(), "")), "400px")  
    }
  })
  
  observe({
    if (input$disable.physics) {
      visNetworkProxy("network") %>%
        visPhysics(enable = FALSE)
    } else {
      visNetworkProxy("network") %>%
        visPhysics(enable = TRUE)
    }
    if (input$nodes.selection) {
      visNetworkProxy("network") %>%
        visOptions(nodesIdSelection = list(enabled = TRUE, useLabels = FALSE))
    } else {
      visNetworkProxy("network") %>%
        visOptions(nodesIdSelection = list(enabled = FALSE))
    }
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
    column(2, tagList(
      imageOutput("logo", height = "100%"),
      p()
      )),
    column(8, headerPanel("Network visualisation"), align = "center"),
    column(2)
  ),
  fluidRow(
    class = "Body",
    column(4, 
           tagList(
             wellPanel(
               fileInput("dot.graph.file", label = "Choose a network file (JSON format)"),
               p("Try loading ", strong("/Volumes/shared-data/Thomas/network-viz/sample/network.json")),
               p(),
               checkboxInput("nodes.selection", label = "Add node selection drop-down menu"),
               checkboxInput("disable.physics", label = "Disable physics"),
               checkboxInput("hierarchical", label = "Hierarchical display")
             ),
             em("InChI representation and structure"),
             p(),
             textOutput("shiny_return"),
             moleculeRendererUI("molecule")  
           ),
          align = "center"),
    column(8, 
           tagList(
             visNetworkOutput("network", height = "700px"),
             em("Try selecting nodes and dragging them around")
             ),
           align = "center")
  )
)

shinyApp(ui = ui, server = server)
