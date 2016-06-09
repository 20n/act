package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProductsFilterTest {

  MongoDB mockMongo = Mockito.mock(MongoDB.class);

  final String VALID_INCHI = "in_db";
  final String INVALID_INCHI = "not_in_db";
  final String DUMMY_SUBSTRATE = "substrate";

  List<String> DUMMY_SUBSTRATES  = Arrays.asList(DUMMY_SUBSTRATE);

  final Ero DUMMY_ERO = new Ero();

  @Before
  public void setup() throws ReactionException, MolFormatException {
    //Set up mock mongo db
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_INCHI)).thenReturn(new Chemical());
    Mockito.when(mockMongo.getChemicalFromInChI(INVALID_INCHI)).thenReturn(null);
  }

  @Test
  public void testSingleSubstrateInDB() throws Exception {
    // Arrange
    List<String> testProducts = new ArrayList<String>();
    testProducts.add(VALID_INCHI);

    L2Prediction testPrediction = new L2Prediction(DUMMY_SUBSTRATES, DUMMY_ERO, testProducts);

    Predicate<L2Prediction> filter = new ProductsFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertTrue("Product in DB- should pass", result);
  }

  @Test
  public void testSingleSubstrateNotInDB() throws Exception {
    // Arrange
    List<String> testProducts = new ArrayList<String>();
    testProducts.add(INVALID_INCHI);

    L2Prediction testPrediction = new L2Prediction(DUMMY_SUBSTRATES, DUMMY_ERO, testProducts);

    Predicate<L2Prediction> filter = new ProductsFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertFalse("Product not in DB- should fail", result);
  }

  @Test
  public void testTwoSubstratesOneInDB() throws Exception {
    // Arrange
    List<String> testProducts = new ArrayList<String>();
    testProducts.add(VALID_INCHI);
    testProducts.add(INVALID_INCHI);

    L2Prediction testPrediction = new L2Prediction(DUMMY_SUBSTRATES, DUMMY_ERO, testProducts);

    Predicate<L2Prediction> filter = new ProductsFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertFalse("Products not both in DB- should fail", result);
  }

}
