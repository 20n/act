# Module for rendering molecule structures

kMolStructureCacheFolder <- "/home/thomas/data/mol-structure-cache/"

# Module server function
moleculeRenderer <- function(input, output, session, inchiString, height) {
  
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
}

# Module UI function
moleculeRendererUI <- function(id) {
  ns <- NS(id)
  imageOutput(ns("molecule"))
}
