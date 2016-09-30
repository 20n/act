kMolStructureCacheFolder <- "/home/thomas/data/mol-structure-cache/"
#kMolStructureCacheFolder <- "/Users/tom/20n-plots/mol-structure-cache/"

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

moleculeRendererUI <- function(id) {
  ns <- NS(id)
  imageOutput(ns("molecule"))
}