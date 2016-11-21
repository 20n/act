# Shiny library (should already be loaded when app started)
require(shiny)
# Scala interpreter in R. Gives us access to all our Java functionalities
library(rscala)
# Logging library (yay!)
library(logging)

source("text_to_rxns.R")

chemStructureCacheFolder <- "test2rxns.chem.structs"
emptyPNG <- "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

server <- function(input, output, session) {
  reactions <- reactive({
    shiny::validate(
      need(input$text != "" || input$url != "" || input$pdf != "", "Please input text!")
    )

    acc <- c()

    print(paste("text:", input$text))
    print(paste("pdf:", input$pdf))
    print(paste("url:", input$url))
    if (input$text != "") {
      print("Extracting rxns from plain text")
      rxns <- extractFromPlainText(input$text)
    } else if (input$url != "") {
      print("Extracting rxns from url")
      rxns <- extractFromURL(input$url)
    }

    num_rxns <- rxns$size() - 1
    print(paste("Found reactions. Count:", num_rxns))

    for (rxnid in 0:num_rxns) {
      rxn <- rxns$apply(rxnid)

      rxnDesc <- rxn$apply(0L)
      rxnImg <- rxn$apply(1L)
      newR <- c(rxnDesc, rxnImg)
      acc <- cbind(acc, newR)
    }

    acc
  })

  output$reaction_1 <- renderImage({
    rr <- reactions()
    if (ncol(rr) >= 1) {
      rxnid <- 1
      desc <- rr[1,rxnid]
      list(src = rr[2,rxnid],
           contentType = "image/png",
           height = "400px",
           alt = "")
    } else {
      list(src = emptyPNG, contentType = "image/png", height = "0px")
    }
  }, deleteFile = FALSE)

  output$reaction_desc_1 <- renderUI({
    rr <- reactions()
    if (ncol(rr) >= 1) {
      rxnid <- 1
      desc <- paste("<b>Reaction ", rxnid, "</b> ", rr[1,rxnid])
      HTML(desc)
    } else {
      HTML("")
    }
  })

  output$reaction_2 <- renderImage({
    rr <- reactions()
    if (ncol(rr) >= 2) {
      rxnid <- 2
      desc <- rr[1,rxnid]
      list(src = rr[2,rxnid],
           contentType = "image/png",
           height = "400px",
           alt = "")
    } else {
      list(src = emptyPNG, contentType = "image/png", height = "0px")
    }
  }, deleteFile = FALSE)

  output$reaction_desc_2 <- renderUI({
    rr <- reactions()
    if (ncol(rr) >= 2) {
      rxnid <- 2
      desc <- paste("<b>Reaction ", rxnid, "</b> ", rr[1,rxnid])
      HTML(desc)
    } else {
      HTML("")
    }
  })

  output$reaction_3 <- renderImage({
    rr <- reactions()
    if (ncol(rr) >= 3) {
      rxnid <- 3
      desc <- rr[1,rxnid]
      list(src = rr[2,rxnid],
           contentType = "image/png",
           height = "400px",
           alt = "")
    } else {
      list(src = emptyPNG, contentType = "image/png", height = "0px")
    }
  }, deleteFile = FALSE)

  output$reaction_desc_3 <- renderUI({
    rr <- reactions()
    if (ncol(rr) >= 3) {
      rxnid <- 3
      desc <- paste("<b>Reaction ", rxnid, "</b> ", rr[1,rxnid])
      HTML(desc)
    } else {
      HTML("")
    }
  })

  output$over_flow <- renderUI({
    rr <- reactions()
    descs <- c()
    num_overflow <- ncol(rr) - 3
    if (num_overflow > 0) {
      descs <- c(descs, paste("<b><br/><br/>", num_overflow, " more reaction(s):</b>", "<br/>"))
      for (i in 4:ncol(rr)) {
        desc <- paste("<b>Reaction ", i, "</b> ", rr[1,i])
        descs <- c(descs, desc)
      }
      overflow <- paste(descs, collapse="<br/>")
      print(overflow)
      HTML(overflow)
    } else {
      HTML("")
    }
  })
}

ui <- pageWithSidebar(
  headerPanel('20n Biochemical Reactions Miner'),
  sidebarPanel(
    # textInput("text", label = "Biochemical text", value = "Convert H2O and p-aminophenylphosphocholine to p-aminophenol and choline phosphate, a reaction that is from the EC class 3.1.4.38. The cell also converted pyruvate to lactate."),
    textInput("text", label = "Biochemical text", value = ""),
    textInput("url", label = "Internet location of text", value = "https://www.ncbi.nlm.nih.gov/pubmed/20564561?dopt=Abstract&report=abstract&format=text"),
    fileInput("pdf", label = "PDF file")
  ),
  mainPanel(
    imageOutput("reaction_1", height = "auto"),
    htmlOutput("reaction_desc_1"),
    imageOutput("reaction_2", height = "auto"),
    htmlOutput("reaction_desc_2"),
    imageOutput("reaction_3", height = "auto"),
    htmlOutput("reaction_desc_3"),
    htmlOutput("over_flow")
  )
)

shinyApp(ui = ui, server = server)
