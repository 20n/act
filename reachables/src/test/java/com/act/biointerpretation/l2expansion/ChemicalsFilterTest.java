package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChemicalsFilterTest {

  final String VALID_SUBSTRATE = "substrate";
  final String VALID_PRODUCT = "product";
  final String INVALID_INCHI = "not_in_db";

  final Long SUBSTRATE_ID = new Long(1);
  final Long PRODUCT_ID = new Long(2);

  final String PRODUCT_NAME = "this_product_name";

  final Integer PREDICTION_ID = new Integer(3);

  final Ero DUMMY_ERO = new Ero();

  MongoDB mockMongo;

  @Before
  public void setup() {
    // Set up mock chemical product
    Chemical productChemical = Mockito.mock(Chemical.class);
    Mockito.when(productChemical.getUuid()).thenReturn(PRODUCT_ID);
    Mockito.when(productChemical.getFirstName()).thenReturn(PRODUCT_NAME);

    //Set up mock mongo db
    mockMongo = Mockito.mock(MongoDB.class);
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_SUBSTRATE)).thenReturn(new Chemical(SUBSTRATE_ID));
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_PRODUCT)).thenReturn(productChemical);
    Mockito.when(mockMongo.getChemicalFromInChI(INVALID_INCHI)).thenReturn(null);
  }

  @Test
  public void testChemicalsInDB() {
    // Arrange
    List<String> testSubstrates = Arrays.asList(VALID_SUBSTRATE);
    List<String> testProducts = Arrays.asList(VALID_PRODUCT);
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID, testSubstrates, DUMMY_ERO, testProducts);

    Function<L2Prediction, Optional<L2Prediction>> filter = new ChemicalsFilter(mockMongo);

    // Act
    Optional<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertTrue("Chemicals in DB- should return result.", result.isPresent());
    assertEquals("Should contain one substrate ID.", 1, result.get().getSubstrateIds().size());
    assertEquals("Should contain one product ID.", 1, result.get().getProductIds().size());
    assertEquals("Should contain correct (substrate Inchi, substrate ID) pair.",
            SUBSTRATE_ID, result.get().getSubstrateIds().get(VALID_SUBSTRATE));
    assertEquals("Should contain correct (product inchi, product ID) pair.",
            PRODUCT_ID, result.get().getProductIds().get(VALID_PRODUCT));
    assertEquals("Should contain correct (product inchi, product name) pair.",
            PRODUCT_NAME, result.get().getProductNames().get(VALID_PRODUCT));
  }

  @Test
  public void testSubstrateNotInDB() {
    // Arrange
    List<String> testSubstrates = Arrays.asList(INVALID_INCHI);
    List<String> testProducts = Arrays.asList(VALID_PRODUCT);
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID, testSubstrates, DUMMY_ERO, testProducts);

    Function<L2Prediction, Optional<L2Prediction>> filter = new ChemicalsFilter(mockMongo);

    // Act
    Optional<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertFalse("Substrate not in DB- should return empty result.", result.isPresent());
  }

  @Test
  public void testProductNotInDB() {
    // Arrange
    List<String> testSubstrates = Arrays.asList(VALID_SUBSTRATE);
    List<String> testProducts = Arrays.asList(INVALID_INCHI);
    L2Prediction testPrediction = new L2Prediction(PREDICTION_ID, testSubstrates, DUMMY_ERO, testProducts);

    Function<L2Prediction, Optional<L2Prediction>> filter = new ChemicalsFilter(mockMongo);

    // Act
    Optional<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertFalse("Product not in DB- should return empty result.", result.isPresent());
  }

}
