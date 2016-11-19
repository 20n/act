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
    num_rxns <- rxns$size() - 1

    for (rxnid in 0:num_rxns) {
      rxn <- rxns$apply(rxnid)

      # substrates
      substrates <- rxn$substrates()
      num_substrates <- substrates$size() - 1
      for (sid in 0:num_substrates) {
        name <- substrates$apply(sid)$name()
        cat('Substrate:', name, '\n')
      }

      #products
      products <- rxn$products()
      num_products <- products$size() - 1
      for (pid in 0:num_products) {
        name <- products$apply(pid)$name()
        cat('Product:', name, '\n')
      }

      # ros
      ros <- rxn$getRONames()
      ro_count <- ros$size() - 1
      for (roid in 0:ro_count) {
        ro <- ros$apply(roid)
        cat('Mechanism:', ro, '\n')
      }
    }

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
