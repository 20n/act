package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChemicalsFilterTest {

  final String VALID_SUBSTRATE = "substrate";
  final String VALID_PRODUCT = "product";
  final String INVALID_INCHI = "not_in_db";

  final Long SUBSTRATE_ID = new Long(1);
  final Long PRODUCT_ID = new Long(2);

  final Ero DUMMY_ERO = new Ero();

  MongoDB mockMongo = Mockito.mock(MongoDB.class);

  @Before
  public void setup() {
    //Set up mock mongo db
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_SUBSTRATE)).thenReturn(new Chemical(SUBSTRATE_ID));
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_PRODUCT)).thenReturn(new Chemical(PRODUCT_ID));
    Mockito.when(mockMongo.getChemicalFromInChI(INVALID_INCHI)).thenReturn(null);
  }

  @Test
  public void testChemicalsInDB() {
    // Arrange
    List<String> testSubstrates = Arrays.asList(VALID_SUBSTRATE);
    List<String> testProducts = Arrays.asList(VALID_PRODUCT);
    L2Prediction testPrediction = new L2Prediction(testSubstrates, DUMMY_ERO, testProducts);

    Function<L2Prediction, List<L2Prediction>> filter = new ChemicalsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertEquals("Chemicals in DB- should return one result.", 1, result.size());
    assertEquals("Should contain one substrate ID.", 1, result.get(0).getSubstrateIds().size());
    assertEquals("Should contain one product ID.", 1, result.get(0).getProductIds().size());
    assertEquals("Should contain correct substrate ID.", SUBSTRATE_ID, result.get(0).getSubstrateIds().get(0));
    assertEquals("Should contain correct product ID.", PRODUCT_ID, result.get(0).getProductIds().get(0));
  }

  @Test
  public void testSubstrateNotInDB() {
    // Arrange
    List<String> testSubstrates = Arrays.asList(INVALID_INCHI);
    List<String> testProducts = Arrays.asList(VALID_PRODUCT);
    L2Prediction testPrediction = new L2Prediction(testSubstrates, DUMMY_ERO, testProducts);

    Function<L2Prediction, List<L2Prediction>> filter = new ChemicalsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertTrue("Substrate not in DB- should return empty list.", result.isEmpty());
  }

  @Test
  public void testProductNotInDB() {
    // Arrange
    List<String> testSubstrates = Arrays.asList(VALID_SUBSTRATE);
    List<String> testProducts = Arrays.asList(INVALID_INCHI);
    L2Prediction testPrediction = new L2Prediction(testSubstrates, DUMMY_ERO, testProducts);

    Function<L2Prediction, List<L2Prediction>> filter = new ChemicalsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.apply(testPrediction);

    // Assert
    assertTrue("Product not in DB- should return empty list.", result.isEmpty());
  }

}
