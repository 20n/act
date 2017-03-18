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

package com.act.biointerpretation.cofactorremoval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CofactorsCorpus {
  private static final String COFACTORS_FILE_PATH = "cofactors.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private Map<String, String> inchiToName = new HashMap<>();
  private Map<String, Integer> inchiToRank = new HashMap<>();

  @JsonProperty("cofactors")
  private List<Cofactor> cofactors;

  public List<Cofactor> getCofactors() {
    return cofactors;
  }

  public void setCofactors(List<Cofactor> cofactors) {
    this.cofactors = cofactors;
  }

  public CofactorsCorpus() {}

  public void loadCorpus() throws IOException {
    InputStream cofactorsStream = INSTANCE_CLASS_LOADER.getResourceAsStream(COFACTORS_FILE_PATH);
    CofactorsCorpus corpus = OBJECT_MAPPER.readValue(cofactorsStream, CofactorsCorpus.class);

    this.cofactors = corpus.getCofactors();
    for (Cofactor cofactor : cofactors) {
      inchiToName.put(cofactor.getInchi(), cofactor.getName());
      inchiToRank.put(cofactor.getInchi(), cofactor.getRank());
    }
  }

  public Map<String, String> getInchiToName() {
    return inchiToName;
  }

  public Map<String, Integer> getInchiToRank() {
    return inchiToRank;
  }
}
