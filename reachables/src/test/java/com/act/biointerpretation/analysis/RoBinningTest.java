package com.act.biointerpretation.analysis;

import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.analysis.similarity.ROBinning;
import com.act.biointerpretation.test.util.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class RoBinningTest {


  TestUtils utilsObject;

  @Before
  public void setUp() throws Exception {
    // In case we ever use Mockito annotations, don't forget to initialize them.
    MockitoAnnotations.initMocks(ROBinning.class);
    utilsObject = new TestUtils();
  }

  @After
  public void tearDown() throws Exception {

  }

  @Test
  public void testHashing() throws Exception {

  }
}
