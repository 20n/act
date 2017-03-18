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

package com.twentyn;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TargetMolecule {
  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();


  Molecule molecule;
  String inchi;
  String displayName;
  String inchiKey;
  String imageName;

  public TargetMolecule(Molecule molecule, String inchi, String displayName, String inchiKey, String imageName) {
    this.molecule = molecule;
    this.inchi = inchi;
    this.displayName = displayName;
    this.inchiKey = inchiKey;
    this.imageName = imageName;
  }

  public Molecule getMolecule() {
    return molecule;
  }

  public String getInchi() {
    return inchi;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getInchiKey() {
    return inchiKey;
  }

  public String getImageName() {
    return imageName;
  }

  public static TargetMolecule fromCSVRecord(CSVRecord record) throws MolFormatException {
    String inchi = record.get("inchi");
    String displayName = record.get("display_name");
    String inchiKey = record.get("inchi_key");
    String imageName = record.get("image_name");

    Molecule mol = MolImporter.importMol(inchi);

    return new TargetMolecule(mol, inchi, displayName, inchiKey, imageName);
  }

  public static List<TargetMolecule> loadTargets(File inputFile) throws IOException {
    List<TargetMolecule> results = new ArrayList<>();
    try (CSVParser parser = new CSVParser(new FileReader(inputFile), TSV_FORMAT)) {
      for (CSVRecord record : parser) {
        results.add(TargetMolecule.fromCSVRecord(record));
      }
    }
    return results;
  }

}
