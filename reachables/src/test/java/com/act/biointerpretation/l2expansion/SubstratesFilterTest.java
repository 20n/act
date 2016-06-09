package com.act.biointerpretation.l2expansion;

import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubstratesFilterTest {

  final String VALID_INCHI = "in_db";
  final String INVALID_INCHI = "not_in_db";
  final String DUMMY_PRODUCT = "product";

  List<String> DUMMY_PRODUCTS  = Arrays.asList(DUMMY_PRODUCT);

  final Ero DUMMY_ERO = new Ero();

  MongoDB mockMongo = Mockito.mock(MongoDB.class);

  @Before
  public void setup() throws ReactionException, MolFormatException {
    //Set up mock mongo db
    Mockito.when(mockMongo.getChemicalFromInChI(VALID_INCHI)).thenReturn(new Chemical());
    Mockito.when(mockMongo.getChemicalFromInChI(INVALID_INCHI)).thenReturn(null);
  }

  @Test
  public void testSingleSubstrateInDB() {
    // Arrange
    List<String> testSubstrate = Arrays.asList(VALID_INCHI);

    L2Prediction testPrediction = new L2Prediction(testSubstrate, DUMMY_ERO, DUMMY_PRODUCTS);

    Predicate<L2Prediction> filter = new SubstratesFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertTrue("Substrate in DB- should pass.", result);
  }

  @Test
  public void testSingleSubstrateNotInDB() {
    // Arrange
    List<String> testSubstrate = Arrays.asList(INVALID_INCHI);

    L2Prediction testPrediction = new L2Prediction(testSubstrate, DUMMY_ERO, DUMMY_PRODUCTS);

    Predicate<L2Prediction> filter = new SubstratesFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertFalse("Substrate not in DB- should fail.", result);
  }

  @Test
  public void testTwoSubstratesOneInDB() {
    // Arrange
    List<String> testSubstrate = Arrays.asList(VALID_INCHI, INVALID_INCHI);

    L2Prediction testPrediction = new L2Prediction(testSubstrate, DUMMY_ERO, DUMMY_PRODUCTS);

    Predicate<L2Prediction> filter = new SubstratesFilter(mockMongo);

    // Act
    boolean result = filter.test(testPrediction);

    // Assert
    assertFalse("Substrates not both in DB- should fail.", result);
  }
}
