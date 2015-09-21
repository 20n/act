package act.server.Molecules;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.Reaction;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

public class BadRxns {
  static HashMap<Integer, String> BadRxnData, AlgCouldBeImproved, ExceptionalCases;

  static {
    BadRxnData = new HashMap<Integer, String>();
    AlgCouldBeImproved = new HashMap<Integer, String>();
    ExceptionalCases = new HashMap<Integer, String>();

    // BadRxnData.put(127, "pattern fix available: gains a C");
    // BadRxnData.put(700, "pattern fix available: loses a CO");
    // BadRxnData.put(702, "pattern fix available: from an OH to a C=O");
    // BadRxnData.put(813, "pattern fix available: Gains an OH");
    // BadRxnData.put(929, "pattern fix available: Gains a =O");
    // BadRxnData.put(944, "pattern fix available: Gains an OH");
    // BadRxnData.put(948, "pattern fix available: Loses a O");
    // BadRxnData.put(980, "pattern fix available: Gains a O");
    // BadRxnData.put(1640, "pattern fix available: Loses a CO2");
    // AlgCouldBeImproved.put(14, "AAM is unable to process very small small-molecules.");
    // AlgCouldBeImproved.put(76, "AAM is unable to process very small small-molecules.");
    // AlgCouldBeImproved.put(82, "AAM is unable to process very small small-molecules.");

    // #13 is a weird case because ethanol comes out 99% more common than beta-NAD+; we just get away with deleting the right one because of sizes...
    // AlgCouldBeImproved.put(13, "Bad non-delete of beta-NAD+ in the substrates; big, but not rare enough molecule. Need to implement pairwise deletion.");
    // -- AlgCouldBeImproved.put(322, "We delete NADH in products, but leave NAD+ in substrates. Maximal matching will fix this issue.");
    // -- AlgCouldBeImproved.put(323, "We delete NADH in products, but leave NAD+ in substrates. Maximal matching will fix this issue.");

    AlgCouldBeImproved.put(46, "Core AAM faulty!!! Very symmetric molecule, and so the AAM chosen by Indigo"
        + "results in a mapping C=O -> HCH and CH -> CO- giving a bad CRO. "
        + "The alternative C=O -> CO- and CH -> HCH would have given a much better CRO.");
    BadRxnData.put(66, "Really bad data. The products and substrates are same chemical: 2-methylpropionaldehyde vs 2-methylpropanal");
    // 88: O jumps atoms; so the CRO is very strange; interesting to look at.
    AlgCouldBeImproved.put(88, "O jumps atoms from C=O to CO- elsewhere; so the CRO is very strange.");
    AlgCouldBeImproved.put(122, "Core AAM faulty!!! Very symmetric molecule, and so the AAM chosen by Indigo"
        + "results in a mapping CO -> CH and CH2 -> C=O giving a bad CRO. "
        + "The alternative CO -> C=O and CH2 -> CH would have given a much better CRO.");
    BadRxnData.put(157, "Really bad data. The products and substrates are same chemical: 1-heptanol vs heptanol");
    BadRxnData.put(170, "Omitted product: Missing NADP+");
    // is this really bad data. Double check?
    BadRxnData.put(182, "Don't know if this is bad data: Unbalanced atoms: Gains a C; but adding a CO2 gives a very strange CRO");
    BadRxnData.put(196, "Unbalanced atoms: Loses a C2OH"); // LOOK
    BadRxnData.put(197, "Unbalanced atoms: Loses a C2OH2"); // LOOK
    BadRxnData.put(216, "Omitted product: Missing NADP+");
    BadRxnData.put(363, "Omitted product: Missing NADH");
    BadRxnData.put(365, "Omitted product: Missing NADPH");
    BadRxnData.put(369, "Wrong product: should be NADH, but provided NADPH");
    AlgCouldBeImproved.put(373, "AAM gets confused because of 2 waters.");
    AlgCouldBeImproved.put(403, "Products name both isomers that can be produced. Imbalance as reactants mentions only one");
    AlgCouldBeImproved.put(478, "AAM gets confused because of CO2.");
    AlgCouldBeImproved.put(493, "AAM gets confused because of symmetrical ring. But not sure how it matches it against another ring.");
    AlgCouldBeImproved.put(502, "Products name both isomers that can be produced. Imbalance as reactants mentions only one");
    AlgCouldBeImproved.put(520, "Products name both isomers that can be produced. Imbalance as reactants mentions only one");
    BadRxnData.put(565, "Unbalanced atoms: Loses C2H4"); // LOOK
    BadRxnData.put(566, "Omitted product: Missing NAD+");
    AlgCouldBeImproved.put(599, "AAM gets confused because of CO2.");
    BadRxnData.put(736, "Omitted product: Missing NADP+");
    BadRxnData.put(740, "Omitted product: Missing NADPH");
    AlgCouldBeImproved.put(744, "Matching: Matches CoA to acetyl-CoA and deletes both; while acetaldehyde is left standing...");
    AlgCouldBeImproved.put(816, "Strange! AAM maps together two very different O's.");
    AlgCouldBeImproved.put(826, "Symmetric reactant with O in ring. AAM fails.");
    // 884-891 look suspicious
    BadRxnData.put(884, "Unbalanced atoms: Gains a CCH"); // LOOK
    BadRxnData.put(885, "Unbalanced atoms: Loses a CCH"); // LOOK
    BadRxnData.put(890, "Unbalanced atoms: Gains a CCH"); // LOOK
    BadRxnData.put(891, "Unbalanced atoms: Loses a CCH"); // LOOK
    BadRxnData.put(933, "Omitted product: Missing NADPH");
    // 1061 looks like bad data
    BadRxnData.put(1016, "Unbalanced atoms: Gains a CCH3"); // LOOK
    // 1031: "interesting to look at: what would the GRO be?"
    AlgCouldBeImproved.put(1031, "AAM gets confused because of the extra water.");
    BadRxnData.put(1051, "Omitted product: Missing NADH");
    BadRxnData.put(1115, "Duplicated product! Strange typo.");
    AlgCouldBeImproved.put(1206, "AAM gets confused by formaldehyde C being very similar to another one.");
    AlgCouldBeImproved.put(1211, "AAM gets confused by formaldehyde C being very similar to another one.");
    AlgCouldBeImproved.put(1214, "AAM gets confused by C in CO2 being very similar to another C=O within a molecule.");
    AlgCouldBeImproved.put(1215, "AAM gets confused by C in CO2 being very similar to another C=O within a molecule.");
    AlgCouldBeImproved.put(1250, "Stoichiometry fix: 2 molecules of substrate needed as CoA is a separate product.");
    BadRxnData.put(1251, "Omitted product: CoA dissapears in the products.");
    BadRxnData.put(1261, "Omitted product: Missing NADH");
    BadRxnData.put(1287, "Omitted product: Missing NAD+");
    AlgCouldBeImproved.put(1289, "AAM cannot decide which O in the 2 CO2's to send where.");
    BadRxnData.put(1313, "Omitted product: Missing NAD+");
    AlgCouldBeImproved.put(1326, "This is a good eg of a difficult case; where the AAM should get confused. "
        + "One approach to fixing it would be take doubly matched atoms on one side; "
        + "and match them to unmatched atoms on the other as opposed to aborting.");
    AlgCouldBeImproved.put(1327, "AAM confused. Same case as 1326");
    AlgCouldBeImproved.put(1328, "AAM confused. Same case as 1326");
    AlgCouldBeImproved.put(1329, "AAM confused. Same case as 1326");
    AlgCouldBeImproved.put(1330, "AAM confused. Same case as 1326");
    AlgCouldBeImproved.put(1331, "AAM confused. Same case as 1326");
    AlgCouldBeImproved.put(1332, "AAM confused. Same case as 1326");
    BadRxnData.put(1375, "Unbalanced atoms: Loses a CCH"); // like 884
    BadRxnData.put(1412, "Omitted product: Missing NADP+");
    BadRxnData.put(1415, "Omitted product: Missing NADP+");
    AlgCouldBeImproved.put(1464, "Stoichiometry fix: 2 molecules of substrate needed for output mixture.");
    AlgCouldBeImproved.put(1582, "AAM confused. Difficult case, CO2 on one side, and 2H2O on the other.");
    AlgCouldBeImproved.put(1583, "AAM confused. Same case as 1582.");
    AlgCouldBeImproved.put(1659, "Matching: Matches NADH to CoA, and 3-hydroxy-3-methyl-glutaryl-CoA to NAD+; deleting (NADH,CoA). Then AAM takes forever.");
    BadRxnData.put(1756, "Unbalanced atoms: Loses a C2OH"); // like 196
    AlgCouldBeImproved.put(1861, "AAM confused. Difficult case,  (R)-lactate is a crazy big molecule and the =O ends up matching inside it...");
    AlgCouldBeImproved.put(1865, "Pure AAM fail. Maps together two very different C's. Two rings. One changes from aromatic to not, so confusing for AAM.");
    AlgCouldBeImproved.put(1866, "ref: 1865");
    AlgCouldBeImproved.put(1867, "AAM fail. Two rings. Maps together two very different C's.");
    AlgCouldBeImproved.put(1868, "ref: 1867");
    AlgCouldBeImproved.put(1869, "ref: 1865");
    AlgCouldBeImproved.put(1870, "ref: 1865");
    AlgCouldBeImproved.put(1871, "ref: 1865");
    AlgCouldBeImproved.put(1872, "ref: 1865");
    AlgCouldBeImproved.put(1873, "ref: 1865");
    AlgCouldBeImproved.put(1874, "ref: 1865");
    AlgCouldBeImproved.put(1882, "ref: 1865");
    AlgCouldBeImproved.put(1885, "AAM fail. O in H2O on one side goes to peroxide and other O's.");
    AlgCouldBeImproved.put(1886, "ref: 1865");
    AlgCouldBeImproved.put(1890, "AAM fail. Expectedly. Very small molecules, all contain O's and so hard to decide what goes where. Its best if we keep this isolated case as is and not generalize.");
    AlgCouldBeImproved.put(1931, "Stoichiometry fix: 2 molecules of the rings needed to form the double ringed output structure.");
    AlgCouldBeImproved.put(1937, "ref: 1865");
    BadRxnData.put(1938, "Our missing C fix fails to realize that the output contains peroxide and so we don't need to add 2H2O but instead just fixing the stoichiometry on the products will fix it.");
    AlgCouldBeImproved.put(1940, "ref: 1865");
    AlgCouldBeImproved.put(1987, "AAM fail. Expectedly. Too many O transfers.");
    AlgCouldBeImproved.put(1988, "AAM fail. Expectedly. Probably fixable. Stoichiometry fix. Then realization that breaking a small molecule is easier than attaching to a big molecule. etc.");
    AlgCouldBeImproved.put(1989, "ref: 1988");
    AlgCouldBeImproved.put(2006, "AAM fail. Fixable: O2 on one side, H2O on the other. They have to share the O's. So should we pre-encode their mapping together?");
    AlgCouldBeImproved.put(2007, "AAM fail. Complete screwup in making sense of symmetrical ring.");
    BadRxnData.put(2024, "Stoichiometry fix: 3 molecules of substrate needed for output mixture.");
    AlgCouldBeImproved.put(2025, "AAM fail. H2O split + H2O2 complication. Genuine AAM fail. Heuristic fix?: Delete labeling of doubles; then run wavefront");
    BadRxnData.put(2028, "Strange imbalance. Extra O2 on one side. Delete it?");
    AlgCouldBeImproved.put(2031, "AAM fail. Same as 2025.");
    BadRxnData.put(2032, "AAM fail. Extra water on one side. Added another water on other. AAM gets confused. DO THEY EVEN REALIZE WHAT THEY ARE WRITING IS IMBALANCED?");
    BadRxnData.put(2035, "AAM fail. Same as 2032.");
    AlgCouldBeImproved.put(2036, "AAM fail. benzoquinone and hydroquinone are very symmetric molecules; AAM fails on these many times.");
    BadRxnData.put(2037, "AAM fail. Same as 2032.");
    BadRxnData.put(2037, "AAM fail. Same as 2032.");
    AlgCouldBeImproved.put(2067, "AAM fail. O2 -> 2H2O and AAM gets confused about symmetry breaking.");
    AlgCouldBeImproved.put(2069, "AAM fail. Same as 2067.");
    AlgCouldBeImproved.put(2103, "AAM fail. Predictably. Insane triangle ring with O that cleaves off to water.");
    AlgCouldBeImproved.put(2104, "AAM fail. Same as 2103.");
    AlgCouldBeImproved.put(2105, "AAM fail. Same as 2103.");
    AlgCouldBeImproved.put(2106, "AAM fail. O in triangle that cleaves. Pure failure.");
    AlgCouldBeImproved.put(2107, "AAM fail. Same as 2103.");
    AlgCouldBeImproved.put(2111, "AAM fail. OH -> O= is a very common pattern that trips the AAM.");
    AlgCouldBeImproved.put(2114, "AAM fail. Similar to 2111.");
    AlgCouldBeImproved.put(2116, "AAM fail. Bond changes within rings completely throw the AAM alg off.");
    AlgCouldBeImproved.put(2117, "AAM fail. Same as 2116.");
    AlgCouldBeImproved.put(2118, "AAM fail. Same as 2036. quinone and quinol are very symmetric molecules.");
    AlgCouldBeImproved.put(2120, "AAM fail. Same as 2036. symmetric molecules + OH -> O= change missed by AAM.");
    AlgCouldBeImproved.put(2121, "AAM fail. Same as 2036.");
    AlgCouldBeImproved.put(2126, "6 of CN- and therefore confusion.");
    AlgCouldBeImproved.put(2127, "Same as 2126.");
    AlgCouldBeImproved.put(2132, "Same as 2036.");
    BadRxnData.put(2133, "CoQ on one side and nothing on the other!");
    AlgCouldBeImproved.put(2134, "Same as 2036."); AlgCouldBeImproved.put(2137, "Same as 2036."); AlgCouldBeImproved.put(2138, "Same as 2036."); AlgCouldBeImproved.put(2141, "Same as 2036."); AlgCouldBeImproved.put(2149, "Same as 2036."); AlgCouldBeImproved.put(2150, "Same as 2036.");
    AlgCouldBeImproved.put(2151, "Very difficult AAM; don't bother inferring automatically.");
    AlgCouldBeImproved.put(2152, "Similar to 2036. Essentially bond movements (pi systems?) are difficult to handle.");
    AlgCouldBeImproved.put(2153, "AAM fail. OH -> O=. Similar to 2111. Ring make break. But also that the two substrates and products have same number of carbons and somehow the AAM thinks that we should make a ring and break another!");
    AlgCouldBeImproved.put(2154, "Same as 2153.");
    AlgCouldBeImproved.put(2155, "Same as 2153.");
    AlgCouldBeImproved.put(2156, "Same as 2036."); AlgCouldBeImproved.put(2157, "Same as 2036."); AlgCouldBeImproved.put(2159, "Same as 2036."); AlgCouldBeImproved.put(2164, "Same as 2036.");
    AlgCouldBeImproved.put(2165, "AAM fail. OH -> O= trips the alg.");
    AlgCouldBeImproved.put(2169, "Same as 2036.");
    AlgCouldBeImproved.put(2170, "AAM fail. Same as 2165");
    AlgCouldBeImproved.put(2171, "Same as 2126.");
    AlgCouldBeImproved.put(2172, "The 2 substrates and 2 products are very similar.");
    AlgCouldBeImproved.put(2173, "Same as 2036."); AlgCouldBeImproved.put(2174, "Same as 2036."); AlgCouldBeImproved.put(2175, "Same as 2036."); AlgCouldBeImproved.put(2176, "Same as 2036."); AlgCouldBeImproved.put(2177, "Same as 2036.");
    AlgCouldBeImproved.put(2178, "Same as 2036."); AlgCouldBeImproved.put(2179, "Same as 2036."); AlgCouldBeImproved.put(2180, "Same as 2036."); AlgCouldBeImproved.put(2181, "Same as 2036."); AlgCouldBeImproved.put(2182, "Same as 2036.");
    AlgCouldBeImproved.put(2183, "Same as 2036.");
    AlgCouldBeImproved.put(2184, "Same as 2036.");
    AlgCouldBeImproved.put(2185, "Same as 2036.");
    // common thread seems to be that we need to handle pisystem bond movements....



    ExceptionalCases.put(2897, "Crashes indigo's layout. Strange molecule. 1.14.13.75: {Rauwolfia serpentina} vinorine + NADPH + H+ + O2 -?> vomilenine + NADP+ + H2O");
    ExceptionalCases.put(4242, "Subgraph matching goes exponential. Incredibly symmetric molecules. 1.3.1.76: precorrin-2 + NAD+ -?> sirohydrochlorin + NADH + H+. http://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:50602 and http://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:18023");
    ExceptionalCases.put(4243, "Subgraph matching goes exponential. Same as 4242");
    ExceptionalCases.put(4713, "Crashes indigo's layout. Strange molecule. 1.5.1.32: vomilenine + NADPH -?> 2-beta-(R)-1,2-dihydrovomilenine + NADP+");
    ExceptionalCases.put(4714, "Crashes indigo's layout. Strange molecule. 1.5.1.32: 1,2-dihydrovomilenine + NADP+ -?> vomilenine + NADPH + H+");
    ExceptionalCases.put(5037, "Subgraph matching goes exponential. insulin has C256 H381 N65 O76 S6. also pointless (!): insulin + NADPH -?> insulin + NADP+");
    ExceptionalCases.put(5115, "Subgraph matching goes exponential. 2.1.1.101: S-adenosyl-L-methionine + macrocin -?> S-adenosylhomocysteine + tylosin http://www.brenda-enzymes.info/Mol/reaction-popup.php4?id=43860&type=I&displayType=marvin");
    ExceptionalCases.put(5116, "Same as 5115"); ExceptionalCases.put(5117, "Same as 5115"); ExceptionalCases.put(5118, "Same as 5115"); ExceptionalCases.put(5119, "Same as 5115");
    ExceptionalCases.put(5154, "Same as 4242. precorrin-2 involved.");
    ExceptionalCases.put(5155, "Same as 4242. precorrin-something involved.");
    ExceptionalCases.put(5156, "Same as 4242. precorrin-something involved.");

    // ExceptionalCases.put(3200--3475, "N+ problems");
    ExceptionalCases.put(4194, "molecule: allowRGroupOnRSite(): rgroup number 33 is invalid");

    ExceptionalCases.put(10098, "Crashes indigo's layout. Strange molecule. 3.2.1.125: raucaffricine + H2O -?> D-glucose + vomilenine");

    ExceptionalCases.put(10772, "IndigoException: core: Unknown radical type when setRadical: one electron?: uuid: 10772; 1.6.5.5: {Mus musculus, Homo sapiens, Rattus norvegicus} NADPH + H+ + quinone -?> NADP+ + semiquinone");
    ExceptionalCases.put(23080, "IndigoException: core: Unknown radical type: uuid: 23080 methylcholine has N+.: ec5: 3.1.1.7 {Homo sapiens} acetyl-beta-methylcholine + H2O -?> methylcholine + acetate ");
    ExceptionalCases.put(23360, "Same as 23080");
    ExceptionalCases.put(23393, "Same as 23080");
    ExceptionalCases.put(38562, "Same as 23080");
    ExceptionalCases.put(38563, "Same as 23080");

    BadRxnData.put(33388, "Seems like bad molecule Lys-Pro-Leu-Glu-Phe-Phe(NO2)-Arg-Leu has an -NOOH attached to a phenyl: element: can not calculate valence on N, charge 0, connectivity 4");
  }

  public static boolean isBad(Reaction r, MongoDB DB) {

    int id = r.getUUID();
    boolean isBad = BadRxnData.containsKey(id);
    boolean isBadlyProc = AlgCouldBeImproved.containsKey(id);
    boolean isExceptional = ExceptionalCases.containsKey(id);
    boolean notHandling = isBad || isBadlyProc || isExceptional;

    // do we need to render the bad reaction?
    if (notHandling && Configuration.getInstance().renderBadRxnsForDebug) {
      // add comment to reaction rendered
      String comment = "Unknown problem";
      if (isBad)
        comment = "Bad data: " + BadRxnData.get(id);
      if (isBadlyProc)
        comment = "Heustics fail: " + AlgCouldBeImproved.get(id);
      if (isExceptional)
        comment = "Corner case fail: " + ExceptionalCases.get(id);

      new File("badrxns").mkdir();
      renderReaction(r, comment, "badrxns/bad-rxn-" + id, DB);
    }

    return notHandling;
  }

  public static void logReactionToArbitraryDir(Reaction r, File dir, MongoDB db) {
    // not necessarily a bad reaction; just one the user wants rendered
    String comment = r.getECNum() + ":" + r.getReactionName();
    int id = r.getUUID();
    renderReaction(r, comment, dir.getAbsolutePath() + "/rxn-" + id, db);
  }

  public static void logReaction(Reaction r, MongoDB db) {
    // not necessarily a bad reaction; just one the user wants rendered
    String comment = r.getECNum() + ":" + r.getReactionName();
    int id = r.getUUID();
    // create logrxns dir if it does not exists...
    new File("RO-log-rxns").mkdir();
    renderReaction(r, comment, "RO-log-rxns/rxn-" + id, db);
  }

  public static void logUnprocessedReaction(Reaction r, String comment, MongoDB db) {
    int id = r.getUUID();
    // create dir if it does not exists...
    new File("RO-unprocessed-rxns").mkdir();
    renderReaction(r, comment, "RO-unprocessed-rxns/rxn-" + id, db);
  }

  public static void logAAMFails(Reaction r, String comment, MongoDB db) {
    int id = r.getUUID();
    // create dir if it does not exists...
    new File("RO-aam-fail").mkdir();
    // System.err.println("Move the before-reaction-diff to ammfailsrxns/rxn-" + id + ".diff.png so that we know the faults.");
    new File("before-diff-rxn.png").renameTo(new File("RO-aam-fail/rxn-aam-" + id + ".png"));
    renderReaction(r, comment, "RO-aam-fail/rxn-" + id, db);
  }

  public static void logROInferFail(Reaction r, String comment, MongoDB db) {
    int id = r.getUUID();
    // create dir if it does not exists...
    new File("RO-infer-fail").mkdir();
    renderReaction(r, comment, "RO-infer-fail/rxn-" + id, db);
  }

  public static void logCouldNotCanonicalizeRO(Reaction r, String comment, MongoDB db) {
    int id = r.getUUID();
    // create dir if it does not exists...
    new File("RO-canonicalization-fail").mkdir();
    renderReaction(r, comment, "RO-canonicalization-fail/rxn-" + id, db);
  }

  @Deprecated
  private static void renderReactionOld(Reaction r, String comment, String fname, MongoDB dB) {
    List<String> substrates = new ArrayList<String>();
    List<String> products = new ArrayList<String>();
    for (long s : r.getSubstrates()) {
      Chemical substr = dB.getChemicalFromChemicalUUID(s);
      substrates.add(substr.getSmiles());
    }
    for (long p : r.getProducts()) {
      Chemical prod = dB.getChemicalFromChemicalUUID(p);
      String smiles = prod.getSmiles();
      products.add(smiles);
    }

    String substrateFull = "", productFull = "";
    for (int i = 0; i < substrates.size(); i++)
      substrateFull += (substrateFull.isEmpty() ? "" : ".") + substrates.get(i);
    for (int i = 0; i < products.size(); i++)
      productFull += (productFull.isEmpty() ? "" : ".") + products.get(i);
    String fullRxn = substrateFull + ">>" + productFull;
    // Render the easy to read version of the original reaction
    Indigo indigo = new Indigo();
    System.out.println("--------- RENDERING REACTION: \n" + fullRxn + "\n");
    SMILES.renderReaction(indigo.loadReaction(fullRxn), fname + ".png", comment, indigo);

    String metafile = fname + ".txt";
    BufferedWriter out;
    try {
      out = new BufferedWriter(new FileWriter(metafile));
      out.write(comment); out.newLine();
      out.write("EC: " + r.getECNum()); out.newLine();
      out.write("Verbatim Brenda Entry: " + r.getReactionName()); out.newLine();
      out.write("Substrates UUIDs: " + Arrays.toString(r.getSubstrates())); out.newLine();
      out.write("Product UUIDs: " + Arrays.toString(r.getProducts())); out.newLine();
      out.write(substrates + "\n>>\n" + products); out.newLine();
      out.close();
    } catch (IOException e) {
      System.err.println("Could not write to metadata file for reaction: " + fname);
    }
  }

  private static void renderReaction(Reaction r, String comment, String fname, MongoDB dB) {
    Indigo ind = new Indigo();
    IndigoInchi inc = new IndigoInchi(ind);
    IndigoObject reaction = ind.createReaction();
    for (long s : r.getSubstrates())
      reaction.addReactant(getIndigoMolFromChem(dB.getChemicalFromChemicalUUID(s), ind, inc));
    for (long p : r.getProducts())
      reaction.addProduct(getIndigoMolFromChem(dB.getChemicalFromChemicalUUID(p), ind, inc));

    // Render the easy to read version of the original reaction
    SMILES.tryRenderReaction(reaction, fname + ".png", comment, ind);

    String metafile = fname + ".txt";
    BufferedWriter out;
    try {
      out = new BufferedWriter(new FileWriter(metafile));
      out.write(comment); out.newLine();
      out.write("EC: " + r.getECNum()); out.newLine();
      out.write("Verbatim Brenda Entry: " + r.getReactionName()); out.newLine();
      out.write("Substrates UUIDs: " + Arrays.toString(r.getSubstrates())); out.newLine();
      out.write("Product UUIDs: " + Arrays.toString(r.getProducts())); out.newLine();
      // out.write(substrates + "\n>>\n" + products); out.newLine();
      out.close();
    } catch (IOException e) {
      System.err.println("Could not write to metadata file for reaction: " + fname);
    }
  }

  private static IndigoObject getIndigoMolFromChem(Chemical c, Indigo ind, IndigoInchi inc) {
    String smiles = c.getSmiles();
    String inchi = c.getInChI();
    IndigoObject o;
    try {
      if (smiles != null)
        o = ind.loadMolecule(smiles);
      else if (inchi != null)
        o = inc.loadMolecule(inchi);
      else
        o = ind.loadMolecule("[*]"); // smiles == null and inchi == null!
    } catch (IndigoException e) {
      System.err.println("Could not load molecule [" + c.getUuid() + "]. Most probably SMILES=null because Inchi is invalid from Brenda.");
      o = ind.loadMolecule("[*]");
    }

    return o;
  }
}