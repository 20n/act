package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChemicalsFilterTest {

  final String VALID_SUBSTRATE = "substrate";
  final String VALID_PRODUCT = "product";
  final String INVALID_INCHI = "not_in_db";

  List<String> DUMMY_SUBSTRATES  = Arrays.asList(VALID_SUBSTRATE);

  final Ero DUMMY_ERO = new Ero();

  MongoDB mockMongo = Mockito.mock(MongoDB.class);

  @Before
  public void setup() {
    //Set up mock mongo db
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_PRODUCT)).thenReturn(new Chemical());
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_SUBSTRATE)).thenReturn(new Chemical());
    Mockito.when(mockMongo.getChemicalFromInChI(INVALID_INCHI)).thenReturn(null);
  }

  @Test
  public void testSingleSubstrateInDB() {
    // Arrange
    List<String> testProducts = Arrays.asList(VALID_PRODUCT);

    L2Prediction testPrediction = new L2Prediction(DUMMY_SUBSTRATES, DUMMY_ERO, testProducts);

    PredictionFilter filter = new ChemicalsFilter(mockMongo);

    // Act
    List<L2Prediction> result = filter.applyFilter(testPrediction);

    // Assert
    assertEquals("Product in DB- should return one result.", 1, result.size());
  }

  @Test
  public void testSingleSubstrateNotInDB() {
    // Arrange
    List<String> testProducts = Arrays.asList(INVALID_INCHI);

    L2Prediction testPrediction = new L2Prediction(DUMMY_SUBSTRATES, DUMMY_ERO, testProducts);

    PredictionFilter filter = new ChemicalsFilter(mockMongo);

    // Act
    List<L2Prediction>  result = filter.applyFilter(testPrediction);

    // Assert
    assertTrue("Product not in DB- should return empty list.", result.isEmpty());
  }

  @Test
  public void testTwoSubstratesOneInDB() {
    // Arrange
    List<String> testProducts = Arrays.asList(VALID_PRODUCT, INVALID_INCHI);

    L2Prediction testPrediction = new L2Prediction(DUMMY_SUBSTRATES, DUMMY_ERO, testProducts);

    PredictionFilter filter = new ChemicalsFilter(mockMongo);

    // Act
    List<L2Prediction>  result = filter.applyFilter(testPrediction);

    // Assert
    assertTrue("Products not both in DB- should return empty list.", result.isEmpty());
  }

}
