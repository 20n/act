##########################################################################
#                                                                        #
#  This file is part of the 20n/act project.                             #
#  20n/act enables DNA prediction for synthetic biology/bioengineering.  #
#  Copyright (C) 2017 20n Labs, Inc.                                     #
#                                                                        #
#  Please direct all queries to act@20n.com.                             #
#                                                                        #
#  This program is free software: you can redistribute it and/or modify  #
#  it under the terms of the GNU General Public License as published by  #
#  the Free Software Foundation, either version 3 of the License, or     #
#  (at your option) any later version.                                   #
#                                                                        #
#  This program is distributed in the hope that it will be useful,       #
#  but WITHOUT ANY WARRANTY; without even the implied warranty of        #
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         #
#  GNU General Public License for more details.                          #
#                                                                        #
#  You should have received a copy of the GNU General Public License     #
#  along with this program.  If not, see <http://www.gnu.org/licenses/>. #
#                                                                        #
##########################################################################

# Modules related to LCMS traces data

# lcmsSingleTracePeaks module server function
lcmsSingleTracePeaks <- function(input, output, session, scan.filename, retention.time.range, target.mz, mz.band.halfwidth, load) {
  
  scan.file <- reactive({
    getAndCacheScanFile(scan.filename())
  })
  
  scans <- eventReactive(load(), {
    retention.time.range <- retention.time.range()
    shiny::validate(
      need(!is.null(retention.time.range), "Retention time range is missing.")
    )
    getScans(scan.file(), retention.time.range)
  })
  
  peaks <- reactive({
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    getPeaksInScope(scans(), target.mz, mz.band.halfwidth)
  })
  
  peaks
}

# lcmsTracesPeaks module server function
lcmsTracesPeaks <- function(input, output, session, scan.filenames, retention.time.range, target.mz, mz.band.halfwidth) {
  
  scan.files <- reactive({
    lapply(scan.filenames(), getAndCacheScanFile)
  })
  
  scans <- reactive({
    retention.time.range <- retention.time.range()
    shiny::validate(
      need(!is.null(retention.time.range), "Retention time range is missing.")
    )
    lapply(scan.files(), function(x) getScans(x, retention.time.range))
  })
  
  peaks <- reactive({
    target.mz <- target.mz()
    mz.band.halfwidth <- mz.band.halfwidth()
    shiny::validate(
      need(!is.null(target.mz), "Target m/z is missing."),
      need(!is.null(mz.band.halfwidth), "m/z band halfwidth is missing.")
    )
    lapply(scans(), function(x) getPeaksInScope(x, target.mz, mz.band.halfwidth))
  })
  
  peaks
}
