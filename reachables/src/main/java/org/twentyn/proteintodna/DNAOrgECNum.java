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

package org.twentyn.proteintodna;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

public class DNAOrgECNum {

  public String getDna() {
    return dna;
  }

  public void setDna(String dna) {
    this.dna = dna;
  }

  public List<Set<ProteinInformation>> getListOfProteinInformation() {
    return listOfProteinInformation;
  }

  public void setListOfProteinInformation(List<Set<ProteinInformation>> listOfProteinInformation) {
    this.listOfProteinInformation = listOfProteinInformation;
  }

  public Integer getNumProteins() {
    return numProteins;
  }

  public void setNumProteins(Integer numProteins) {
    this.numProteins = numProteins;
  }

  @JsonProperty("dna")
  String dna;

  /**
   * setOfOrganismAndEcNums is a set of sets since the following reason:
   * A dna seq is derived from a set of reactions protein sequences. A given reaction protein sequence can
   * have multiple (organism, ecnum) pairs associated to that protein sequence. Therefore, we need different sets to represent
   * the different (organism, ecnum) pairs of the reaction in the pathway. This is why setOfOrganismAndEcNums is a set
   * of sets.
   */
  @JsonProperty("org_ec")
  List<Set<ProteinInformation>> listOfProteinInformation;

  @JsonProperty("num_proteins")
  private Integer numProteins;

  public DNAOrgECNum(String dna, List<Set<ProteinInformation>> listOfProteinInformation, Integer numProteins) {
    this.dna = dna;
    this.listOfProteinInformation = listOfProteinInformation;
    this.numProteins = numProteins;
  }

  private DNAOrgECNum() {
  }
}
