package act.installer;

import act.shared.Chemical;
import com.act.biointerpretation.test.util.MockedMongoDB;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HMDBParserTest {
  public static final String TEST_HMDB_FILE_NAME = "HMDB01859.xml";

  Document doc;

  @Before
  public void setUp() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder documentBuilder = factory.newDocumentBuilder();

    InputStream testFileStream = this.getClass().getResourceAsStream(TEST_HMDB_FILE_NAME);
    doc = documentBuilder.parse(testFileStream);
  }

  @Test
  public void testExtractChemicalFromXMLDocument() throws Exception {
    MockedMongoDB mockedDb = new MockedMongoDB();
    HMDBParser parser = HMDBParser.Factory.makeParser(mockedDb.getMockMongoDB());
    Chemical chem = parser.extractChemicalFromXMLDocument(doc);

    assertEquals("Canonical name matches", "acetaminophen", chem.getCanon().toLowerCase());
    assertEquals("InChI matches", "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)", chem.getInChI());
    assertEquals("SMILES matches", "CC(=O)NC1=CC=C(O)C=C1", chem.getSmiles());
    assertTrue("Expected synonyms appear", chem.getSynonyms().containsAll(Arrays.asList(
        "tylenol",       // Why can you never find tylenol on pirate ships?
        "acetaminophen", // ...
        "paracetamol",   // <-- Because parrots eat 'em all!
        "apap"           // ... HAH!
        // Any many more!
    )));
    assertEquals("Pubchem id matches", Long.valueOf(1983), chem.getPubchemID()); // My favorite!

    // Pick apart the XRef
    JSONObject xref = chem.getRef(Chemical.REFS.HMDB);

    assertEquals("Metlin ID matches", "6353", xref.getString("metlin_id"));
    assertEquals("ChEBI ID matches", "46195", xref.getString("chebi_id"));

    assertEquals("Fluid length matches", 3, xref.getJSONObject("location").getJSONArray("fluid").length());
    assertEquals("First fluid matches", "Blood", xref.getJSONObject("location").getJSONArray("fluid").getString(0));
    assertEquals("Tissue length matches", 1, xref.getJSONObject("location").getJSONArray("tissue").length());
    assertEquals("First tissue matches",
        "All Tissues", xref.getJSONObject("location").getJSONArray("tissue").getString(0));

    assertEquals("Proteins length matches", 4, xref.getJSONArray("proteins").length());
    assertEquals("First protein name matches",
        "Cytochrome P450 1A2", xref.getJSONArray("proteins").getJSONObject(0).getString("name"));
    assertEquals("First protein uniprot_id matches",
        "P05177", xref.getJSONArray("proteins").getJSONObject(0).getString("uniprot_id"));
    assertEquals("First protein gene name matches",
        "CYP1A2", xref.getJSONArray("proteins").getJSONObject(0).getString("gene_name"));

    assertEquals("First ontology origin matches",
        "Drug", xref.getJSONObject("ontology").getJSONArray("origins").getString(0));

    // TODO: consider another test using data that has disease/pathway names.
  }

}
