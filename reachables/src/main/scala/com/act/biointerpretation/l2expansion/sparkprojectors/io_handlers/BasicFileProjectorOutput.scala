/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.l2expansion.sparkprojectors.io_handlers

import java.io.File

import org.apache.log4j.LogManager

trait BasicFileProjectorOutput extends BasicProjectorOutput {
  private val LOGGER = LogManager.getLogger(getClass)

  final def createOutputDirectory(directory: File): Unit = {
    if (directory.exists() && !directory.isDirectory) {
      LOGGER.error(s"Found file when expected directory at ${directory.getAbsolutePath}.  " +
        "Unable to set create output directories.")
      System.exit(1)
    } else {
      LOGGER.info(s"Creating output directory at ${directory.getAbsolutePath}")
      directory.mkdirs()
    }
  }
}
