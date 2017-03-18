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

package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class ChemicalsTransformerTest {

  final String VALID_SUBSTRATE = "substrate";
  final String VALID_PRODUCT = "product";
  final String INVALID_INCHI = "not_in_db";

  final Long SUBSTRATE_ID = new Long(1);
  final Long PRODUCT_ID = new Long(2);

  final String SUBSTRATE_NAME = "this_substrate_name";
  final String PRODUCT_NAME = "this_product_name";

  final Integer PREDICTION_ID = new Integer(3);

  final String GROUP_NAME = "MarkDaly";

  MongoDB mockMongo;

  @Before
  public void setup() {
    // Set up mock chemicals
    Chemical productChemical = Mockito.mock(Chemical.class);
    Mockito.when(productChemical.getUuid()).thenReturn(PRODUCT_ID);
    Mockito.when(productChemical.getFirstName()).thenReturn(PRODUCT_NAME);
    Chemical substrateChemical = Mockito.mock(Chemical.class);
    Mockito.when(substrateChemical.getUuid()).thenReturn(SUBSTRATE_ID);
    Mockito.when(substrateChemical.getFirstName()).thenReturn(SUBSTRATE_NAME);

    //Set up mock mongo db
    mockMongo = Mockito.mock(MongoDB.class);
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_SUBSTRATE)).thenReturn(substrateChemical);
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_PRODUCT)).thenReturn(productChemical);
    Mockito.when(mockMongo.getChemicalFromInChI(INVALID_INCHI)).thenReturn(null);
  }

  @Test
  public void testChemicalsInDB() {
    // Arrange
    List<L2PredictionChemical> testSubstrates =
        L2PredictionChemical.getPredictionChemicals(Arrays.asList(VALID_SUBSTRATE));
    List<L2PredictionChemical> testProducts =
        L2PredictionChemical.getPredictionChemicals(Arrays.asList(VALID_PRODUCT));

    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID, testSubstrates, GROUP_NAME, testProducts);

    Function<L2Prediction, L2Prediction> filter = new ChemicalsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("Should contain one substrate ID.", 1, result.getSubstrateIds().size());
    assertEquals("Should contain one product ID.", 1, result.getProductIds().size());
    assertEquals("Should contain correct substrate ID.",
        SUBSTRATE_ID, result.getSubstrateIds().get(0));
    assertEquals("Should contain correct substrate name.",
        SUBSTRATE_NAME, result.getSubstrateNames().get(0));
    assertEquals("Should contain correct product inchi.",
        PRODUCT_ID, result.getProductIds().get(0));
    assertEquals("Should contain correct product name.",
        PRODUCT_NAME, result.getProductNames().get(0));
  }

  @Test
  public void testSubstrateNotInDB() {
    // Arrange
    List<L2PredictionChemical> testSubstrates =
        L2PredictionChemical.getPredictionChemicals(Arrays.asList(INVALID_INCHI));
    List<L2PredictionChemical> testProducts =
        L2PredictionChemical.getPredictionChemicals(Arrays.asList(VALID_PRODUCT));

    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID, testSubstrates, GROUP_NAME, testProducts);

    Function<L2Prediction, L2Prediction> filter = new ChemicalsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("Should contain no substrate ID.", 0, result.getSubstrateIds().size());
    assertEquals("Should contain no substrate name.", 0, result.getSubstrateNames().size());
    assertEquals("Should contain one product ID.", 1, result.getProductIds().size());
    assertEquals("Should contain one product name.", 1, result.getProductNames().size());
  }

  @Test
  public void testProductNotInDB() {
    // Arrange
    List<L2PredictionChemical> testSubstrates =
        L2PredictionChemical.getPredictionChemicals(Arrays.asList(VALID_SUBSTRATE));
    List<L2PredictionChemical> testProducts =
        L2PredictionChemical.getPredictionChemicals(Arrays.asList(INVALID_INCHI));

    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID, testSubstrates, GROUP_NAME, testProducts);
    Function<L2Prediction, L2Prediction> filter = new ChemicalsTransformer(mockMongo);

    // Act
    L2Prediction result = filter.apply(testPrediction);

    // Assert
    assertEquals("Should contain one substrate ID.", 1, result.getSubstrateIds().size());
    assertEquals("Should contain one substrate name.", 1, result.getSubstrateNames().size());
    assertEquals("Should contain no product ID.", 0, result.getProductIds().size());
    assertEquals("Should contain no product name.", 0, result.getProductNames().size());
  }

}
