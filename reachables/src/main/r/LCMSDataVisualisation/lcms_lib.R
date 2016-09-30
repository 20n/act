# Main library. Contains functions related to LCMS data processing likely to be re-used.

kLCMSDataLocation <- "/mnt/data-level1/lcms-ms1/"
kLCMSDataCacheLocation <- "/mnt/data-level1/lcms-ms1-rcache/"
kPeakDisplaySep <- " - "
kIntensityThreshold <- 10000
kSSRatio <- 20

# reachables-assembly-0.1.jar -> symlink to a "fat jar" created through sbt assembly
kFatJarLocation <- "reachables-assembly-0.1.jar"

loginfo("Loading Scala interpreter from fat jar at %s.", kFatJarLocation)
kScalaInterpreter=scalaInterpreter(kFatJarLocation)
loginfo("Done loading Scala interpreter.")


# Saves a 
#
# Args:
#   x: One of two vectors whose sample covariance is to be calculated.
#   y: The other vector. x and y must have the same length, greater than one,
#      with no missing values.
#   verbose: If TRUE, prints sample covariance; if not, not. Default is TRUE.
#
# Returns:
#   The sample covariance between x and y.

saveMoleculeStructure <- {
  # Documentation for `saveMoleculeStructure`
  # Render a molecule's structure and saves a .png to file.
  #
  # Args:
  #   inchiString: input inchi string 
  #   file: absolute file path for saving the structure image file
  importMoleculeImporter <- 'import com.act.analysis.chemicals.molecules.MoleculeImporter'
  importReactionRenderer <- 'import com.act.biointerpretation.mechanisminspection.ReactionRenderer'
  importFile <- 'import java.io.File'
  declareNewReactionRenderer <- 'val reactionRenderer: ReactionRenderer = new ReactionRenderer'
  kScalaInterpreter%~%importMoleculeImporter
  kScalaInterpreter%~%importReactionRenderer
  kScalaInterpreter%~%importFile
  kScalaInterpreter%~%declareNewReactionRenderer
  getSaveMolStructFunctionDef <- 'reactionRenderer.drawMolecule(MoleculeImporter.importMolecule(inchiString), new File(file))'
  intpDef(kScalaInterpreter, 'inchiString: String, file: String', getSaveMolStructFunctionDef) 
}

getIonMz <- {
  # Documentation for `getIonMz`
  # Compute the m/z value for a molecular mass and ion mode
  #
  # Args:
  #   mass: Molecular mass
  #   mode: Ion for the m/z computation. One of com.act.lcms.MS1.ionDeltas
  #
  # Returns:
  #   A Double representing the Ion m/z value
  importMS1 <- 'import com.act.lcms.MS1'
  kScalaInterpreter%~%importMS1
  getIonMzFunctionDef <- 'MS1.computeIonMz(mass, MS1.ionDeltas.filter(i => i.getName.equals(mode)).head)'
  intpDef(kScalaInterpreter, 'mass: Double, mode: String', getIonMzFunctionDef) 
}

getAndCachePlate <- function(filename) {
  # Get and serialize a netCDF plate. 
  #
  # Args:
  #   filename: input filename (Assumes input files are in `kLCMSDataLocation`)
  #
  # Returns:
  #   a list of three objects: filename, hd, ms1.scans
  
  # Parameters validation
  shiny::validate(
    need(filename != "", "Please choose an input file!")
  )
  filepath <- paste0(kLCMSDataLocation, filename)
  cachename <- gsub(".nc", ".rds", filename)
  cachepath <- paste0(kLCMSDataCacheLocation, cachename)
  
  if (file.exists(cachepath)) {
    loginfo("Reading plate (%s) from cache at %s.", filename, cachepath)
    plate <- readRDS(cachepath)
    loginfo("Done reading plate (%s) from cache at %s.", filename, cachepath)
  } else {
    shiny::validate(
      need(file.exists(filepath), "Input plate was not found in the cache or in default directory (NAS/data-level1/lcms-ms1/)")
    )
    loginfo("Reading plate (%s) from disk at %s.", filename, filepath)
    msfile <- openMSfile(filepath, backend="netCDF")
    hd <- header(msfile)
    ms1 <- which(hd$msLevel == 1)
    ms1.scans <- peaks(msfile, ms1)
    plate <- list(filename = filename, hd = hd, ms1.scans = ms1.scans)
    loginfo("Saving plate (%s) in the cache at %s.", filename, cachepath)
    saveRDS(plate, file = cachepath)
    loginfo("Done saving plate (%s) in the cache.", filename)
  }
  return(plate)
}

getScans <- function(plate, retention.time.range) {
  # Get scans corresponding to a time range
  #
  # Args:
  #   plate: a list of three objects: filename, hd, ms1.scans
  #   retention.time.range: tuple, retention time range selected
  #
  # Returns:
  #   list: filename, scans, retention.time, retention.time.range
  
  # Parameters validation
  shiny::validate(
    need(length(retention.time.range) == 2, "Rentention time range is not a tuple. Please fix!"),
    need(is.numeric(retention.time.range), "Rentention time range was not numeric. Please fix!"),
    need(length(plate$ms1.scans) > 0, "Found 0 scans in loaded data. Please check the input file or the cached data!")
  )
  min.rt <- retention.time.range[1]
  max.rt <- retention.time.range[2]
  # Extract the relevant scans from the full dataset
  header <- plate$hd
  ms1 <- which(header$msLevel == 1)
  rtsel <- header$retentionTime[ms1] > min.rt & header$retentionTime[ms1] < max.rt # vector of boolean
  loginfo("Found %d scans with retention time in range [%.1f, %.1f] for plate %s.", sum(rtsel), min.rt, max.rt, plate$filename)
  scans <- plate$ms1.scans[rtsel]
  
  # We need to replicate the retention time as many times as the length of each scan
  scan.lengths <- unlist(lapply(scans, nrow))
  retention.time <- rep(header$retentionTime[rtsel], scan.lengths)
  list(filename = plate$filename, scans = scans, retention.time = retention.time, retention.time.range = retention.time.range)
}

getPeaksInScope <- function(scans.with.time, target.mz.value, mz.band.halfwidth) {
  # Get peaks in an mz window and retention time range
  #
  # Args:
  #   scans.with.time: list - filename, scans, retention.time, retention.time.range
  #   target.mz.value: Double - m/z value, center of the m/z window
  #   mz.band.halfwidth: Double - m/z bandwidth on each side
  #
  # Returns:
  #   list: filename, peaks, retention.time.range, mz.range
  
  # Parameters validation
  shiny::validate(
    need(target.mz.value >= 50 && target.mz.value <= 950, "Target mz value should be between 50 and 950"),
    need(mz.band.halfwidth >= 0.00001, "M/Z band halfwidth should be >= 0.00001"),
    need(mz.band.halfwidth <= 1, "Avoid values of M/Z band halfwidth > 1 that can make the server crash")
  )
  min.ionic.mass <- target.mz.value - mz.band.halfwidth
  max.ionic.mass <- target.mz.value + mz.band.halfwidth
  
  # extract mz/intensity values from scans to a dataframe
  peaks <- with(scans.with.time, {
    shiny::validate(
      need(length(scans) > 0, "Found 0 scans in input time range")
    )
    mz <- unlist(lapply(scans, function(x) x[, "mz"]))
    intensity <- unlist(lapply(scans, function(x) x[, "intensity"]))
    data.frame(mz = mz, retention.time = retention.time, intensity = intensity)
  })
  # now we can manipulate triples (retention.time, mz, intensity)
  peaks.in.scope <- peaks %>% 
    dplyr::filter(mz < max.ionic.mass & mz > min.ionic.mass)
  loginfo("Found %d peaks in mz window [%.4f, %.4f] for plate %s.", 
          nrow(peaks.in.scope), min.ionic.mass, max.ionic.mass, scans.with.time$filename)
  list(filename = scans.with.time$filename, peaks = peaks.in.scope, 
       retention.time.range = scans.with.time$retention.time.range, mz.range = c(min.ionic.mass, max.ionic.mass))
}

# memoise the function `getPeaksInScope` since it is repreatedly called with the same parameters
memGetPeaksInScope <- memoise(getPeaksInScope)

drawScatterplot <- function(plot.data, plot.parameters, ...) {
  # Draw a 3D scatterplot of the data with given angle parameters
  #
  # Args:
  #   plot.data: list: filename, peaks, retention.time.range, mz.range
  #   plot.parameters: list of theta and phi angles (in degrees)
  #   ... (zlim, clim): intensity and color scale - used when normalizing graphs
  with(plot.data, {
    scatter3D(peaks$retention.time, peaks$mz, peaks$intensity, 
              # pch: plotting symbol, cex: label magnification factor
              pch = 16, cex = 1.5, 
              # type: adds vertical sticks to the drawn points
              type = "h", 
              # colkey: plots a color legend
              colkey = list(side = 1, length = 0.5, width = 0.5, cex.clab = 0.75), 
              # expand: vertical expansion of the graph, ticktype: ticks on all axis
              expand = 0.5, ticktype = "detailed", 
              # main: title, {x,y,z}lab: axis labels
              main = filename, zlab = "Intensity", xlab = "Retention time (sec)", ylab = "m/z (Da)",
              # theta: azimuthal (left <> right) angle, phi: colatitude (down <> up) angle
              theta = plot.parameters$angle.theta, phi = plot.parameters$angle.phi, 
              # {x,y,z}lim: limits of the graph, clim: limits for the color scale
              xlim = retention.time.range, ylim = mz.range, ...)
  })
}

detectPeaks <- function(peaks) {
  # Apply a simple peak detection method on a set of peaks (mz, rt, intensity triples)
  #
  # Args:
  #   peaks: dataframe with columns: mz, retention.time, intensity
  #
  # Returns:
  #   one or two rows of the above dataframe
  
  # select peaks above intensity threshold
  data <- peaks %>%
    dplyr::filter(intensity > kIntensityThreshold)
  # if no peak meets that criterion, display error message
  shiny::validate(
    need(nrow(data) > 0, sprintf("No peak found above the clustering threshold: %d", kIntensityThreshold))
  )
  # set seed for reproducibility of the results
  set.seed(2016)
  # run kmeans with k=2
  fit <- kmeans(data$mz, centers = 2)
  # assess separation of clusters (kSSRatio is experimental)
  if (fit$betweenss / fit$tot.withinss > kSSRatio) {
    intervals <- classIntervals(data$mz, n = 2, style = "kmeans")  
    mean.mz.break <- intervals$brks[2]
    peak1 <- data %>%
      dplyr::filter(mz < mean.mz.break) %>%
      top_n(1, intensity)
    peak2 <- data %>%
      dplyr::filter(mz >= mean.mz.break) %>%
      top_n(1, intensity)
    # if clusters are separated well enough, return two peaks
    rbind(peak1, peak2)
  } else {
    peak1 <- data %>%
      top_n(1, intensity)
    # otherwise return one peak
    peak1
  }
}

getAndValidateConfigFile <- function(input.file) {
  # Read and validate a JSON file based on a shiny::fileInput
  #
  # Args:
  #   input.file: result from shiny's fileInput
  #
  # Returns:
  #   the parsed config file
  shiny::validate(
    need(!is.null(input.file), "Please upload a configuration file.") 
  )
  config <- fromJSON(file(input.file$datapath))
  layout <- config$layout
  platenames <- config$plates$filename
  shiny::validate(
    need(layout$nrow * layout$ncol >= length(platenames), 
         "Too many plates for input layout. Please double check the layout."), 
    need(layout$nrow >= 1 && layout$nrow <= 3, "Number of rows in the layout should be in the range [1, 3]"),
    need(layout$ncol >= 1 && layout$nrow <= 3, "Number of cols in the layout should be in the range [1, 3]")
  )
  config
}
