# Shiny library (should already be loaded when app started)
require(shiny)
# Scala interpreter in R. Gives us access to all our Java functionalities
library(rscala)
# Logging library (yay!)
library(logging)

source("text_to_rxns.R")
# source("../LCMSDataVisualization/molecule_renderer.R")

server <- function(input, output, session) {
  output$textData <- renderText({
    shiny::validate(
      need(input$text != "", "Please input text!")
    )
    rxns <- extractFrom(input$text)
    print(rxns)

    name <- rxns$apply(0L)$apply(0L)$apply(0L)$apply(0L)
    print(name)

  })
}

ui <- pageWithSidebar(
  headerPanel('20n Biochemical Reactions Miner'),
  sidebarPanel(
    textInput("text", label = "Biochemical text", value = "Convert p-aminophenylphosphocholine and H2O to p-aminophenol and choline phosphate in 3.1.4.38"),
    textInput("url", label = "Internet location of text", value = ""),
    fileInput("pdf", label = "PDF file")
  ),
  mainPanel(textOutput("textData"))
)

shinyApp(ui = ui, server = server)
