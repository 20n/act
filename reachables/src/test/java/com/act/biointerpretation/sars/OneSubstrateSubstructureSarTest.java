package com.act.biointerpretation.sars;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OneSubstrateSubstructureSarTest {

  private static final String BENZENE_INCHI = "InChI=1S/C6H6/c1-2-4-6-5-3-1/h1-6H";
  private static final String CONTAINS_BENZENE_INCHI =
      "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)";
  private static final String NO_BENZENE_INCHI = "InChI=1S/C3H8/c1-3-2/h3H2,1-2H3";

  private static final String INCHI_SETTINGS = "inchi";

  private Molecule benzene;
  private Molecule containsBenzene;
  private Molecule noBenzene;

  @Before
  public void init() throws MolFormatException {
    benzene = MolImporter.importMol(BENZENE_INCHI, INCHI_SETTINGS);
    containsBenzene = MolImporter.importMol(CONTAINS_BENZENE_INCHI, INCHI_SETTINGS);
    noBenzene = MolImporter.importMol(NO_BENZENE_INCHI, INCHI_SETTINGS);
  }

  @Test
  public void oneSubstrateContainsSubstructure() throws SearchException {
    Sar sar = new OneSubstrateSubstructureSar(benzene);
    List<Molecule> substrates = Arrays.asList(containsBenzene);

    assertTrue("Substrate contains substructure.", sar.test(substrates));
  }

  @Test
  public void oneSubstrateDoesntContainsSubstructure() throws SearchException {
    Sar sar = new OneSubstrateSubstructureSar(benzene);
    List<Molecule> substrates = Arrays.asList(noBenzene);

    assertFalse("Substrate does not contain substructure.", sar.test(substrates));
  }

  @Test
  public void twoSubstratesRejects() throws SearchException {
    Sar sar = new OneSubstrateSubstructureSar(benzene);
    List<Molecule> substrates = Arrays.asList(noBenzene, containsBenzene);

    assertFalse("Can't operate on two substrates", sar.test(substrates));
  }

  @Test
  public void NoSubstratesRejects() throws SearchException {
    Sar sar = new OneSubstrateSubstructureSar(benzene);
    List<Molecule> substrates = new ArrayList<>();

    assertFalse("Can't operate on no substrates", sar.test(substrates));
  }


}
