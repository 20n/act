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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FakeCofactorCorpus {
  private static final String FAKE_COFACTORS_FILE_PATH = "fake_cofactors.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private HashMap<String, String> fakeCofactorNameToRealCofactorName = new LinkedHashMap<>();
  private static final Logger LOGGER = LogManager.getLogger(FakeCofactorCorpus.class);

  @JsonProperty("fake_cofactors")
  private List<FakeCofactorMapping> fake_cofactors;

  public List<FakeCofactorMapping> getFake_cofactors() {
    return fake_cofactors;
  }

  public void setFake_cofactors(List<FakeCofactorMapping> cofactors) {
    this.fake_cofactors = cofactors;
  }

  public FakeCofactorCorpus() {}

  public void loadCorpus() throws IOException {
    InputStream cofactorsStream = INSTANCE_CLASS_LOADER.getResourceAsStream(FAKE_COFACTORS_FILE_PATH);
    FakeCofactorCorpus corpus = OBJECT_MAPPER.readValue(cofactorsStream, FakeCofactorCorpus.class);

    List<FakeCofactorMapping> cofactors = corpus.getFake_cofactors();

    Map<Integer, FakeCofactorMapping> rankToCofactor = new TreeMap<>();
    for (FakeCofactorMapping cofactor : cofactors) {
      if (rankToCofactor.containsKey(cofactor.getRank())) {
        LOGGER.error(String.format("The corpus has two fake ichis of similar rank, which should not happen. " +
            "The cofactor name is: %s", cofactor.getCofactor_name()));
        throw new RuntimeException("The corpus has two fake ichis of similar rank, which should not happen");
      } else {
        rankToCofactor.put(cofactor.getRank(), cofactor);
      }
    }

    for (Map.Entry<Integer, FakeCofactorMapping> entry : rankToCofactor.entrySet()) {
      fakeCofactorNameToRealCofactorName.put(entry.getValue().getFake_cofactor_name(),
          entry.getValue().getCofactor_name());
    }
  }

  public Map<String, String> getFakeCofactorNameToRealCofactorName() {
    return fakeCofactorNameToRealCofactorName;
  }
}
