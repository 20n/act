# Shiny library (should already be loaded when app started)
require(shiny)
# Scala interpreter in R. Gives us access to all our Java functionalities
library(rscala)
# Logging library (yay!)
library(logging)

source("text_to_rxns.R")

chemStructureCacheFolder <- "test2rxns.chem.structs"

server <- function(input, output, session) {
  reactions <- reactive({
    shiny::validate(
      need(input$text != "", "Please input text!")
    )

    rxns <- extractFrom(input$text)
    num_rxns <- rxns$size() - 1

    acc <- c()
    for (rxnid in 0:num_rxns) {
      rxn <- rxns$apply(rxnid)

      rxnDesc <- rxn$apply(0L)
      rxnImg <- rxn$apply(1L)
      newR <- c(rxnDesc, rxnImg)
      acc <- cbind(acc, newR)
    }

    acc
  })
  
  output$textData <- renderUI({
    rr <- reactions()
    descs <- c()
    for (i in 1:ncol(rr)) {
      desc <- paste("<b>Reaction ", i, "</b> ", rr[1,i])
      descs <- c(descs, desc)
    }
    allrxns <- paste(descs, collapse="<br/>")
    print(allrxns)
    HTML(allrxns)
  })

  output$molecule <- renderImage({
    rr <- reactions()
    list(src = rr[2,1],
         contentType = "image/png",
         alt = "reaction")
  }, deleteFile = FALSE)
}

ui <- pageWithSidebar(
  headerPanel('20n Biochemical Reactions Miner'),
  sidebarPanel(
    textInput("text", label = "Biochemical text", value = "Convert p-aminophenylphosphocholine and H2O to p-aminophenol and choline phosphate in 3.1.4.38"),
    textInput("url", label = "Internet location of text", value = ""),
    fileInput("pdf", label = "PDF file")
  ),
  mainPanel(
    htmlOutput("textData"),
    imageOutput("molecule")
  )
)

shinyApp(ui = ui, server = server)
