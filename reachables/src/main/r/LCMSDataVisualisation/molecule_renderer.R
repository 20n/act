# Module for rendering molecule structures

kMolStructureCacheFolder <- "/home/thomas/data/mol-structure-cache/"

# Module server function
moleculeRenderer <- function(input, output, session, inchi, height) {
  
  inchi.string <- reactive({
    inchi <- inchi()
    inchi.string <- inchi[1]
    logdebug(inchi.string)
    shiny::validate(
      need(startsWith(inchi.string, "InChI="), "Should start with InChI")
    )
    inchi.string
  })
  
  inchi.name <- reactive({
    inchi <- inchi()
    inchi[2]
  })
  
  imageFilepath <- reactive({
    inchiHash <- digest(inchi.string())
    filepath <- paste0(c(kMolStructureCacheFolder, inchiHash, ".png"), collapse = "")
    if (!file.exists(filepath)) {
      saveMoleculeStructure(inchi.string(), filepath)
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
    if (!is.na(inchi.name)) {
     inchi.name 
    }
  })
}

# Module UI function
moleculeRendererUI <- function(id) {
  ns <- NS(id)
  textOutput(ns("molecule.name"))
  imageOutput(ns("molecule"))
}
