# Module for rendering molecule structures

kMolStructureCacheFolder <- "/home/thomas/data/mol-structure-cache/"

# Module server function
moleculeRenderer <- function(input, output, session, inchiString, inchiName, height) {
  
  imageFilepath <- reactive({
    inchiHash <- digest(inchiString())
    filepath <- paste0(c(kMolStructureCacheFolder, inchiHash, ".png"), collapse = "")
    if (!file.exists(filepath)) {
      saveMoleculeStructure(inchiString(), filepath)
    }
    filepath
  })
  
  output$molecule <- renderImage({
    list(src = imageFilepath(),
         contentType = "image/png",
         height = height,
         alt = "molecule")
  }, deleteFile = FALSE)
  
  output$molecule.name <- renderText({
    inchiName
  })
}

# Module UI function
moleculeRendererUI <- function(id) {
  ns <- NS(id)
  textOutput(ns("molecule.name"))
  imageOutput(ns("molecule"))
}
