package act.installer.genbank;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import org.json.JSONObject;

import act.installer.sequence.SequenceEntry;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;

import java.util.Iterator;
import java.util.LinkedHashMap;

import org.biojavax.SimpleNamespace;
import org.biojavax.bio.seq.RichSequence;
import org.biojavax.bio.seq.RichSequenceIterator;
import org.biojava.bio.seq.Feature;
import org.biojavax.Note;
import org.biojavax.RichObjectFactory;
import org.biojavax.RichAnnotation;
import org.biojavax.ontology.ComparableTerm;

/*
 * This will process entries downloaded from:
 * ftp://ftp.ncbi.nih.gov/genbank/ -- DOES NOT INCLUDE WGS
 *     Descriptions of the subsets bct (bacteria), env (environmental) etc:
 *     http://www.ncbi.nlm.nih.gov/genbank/htgs/divisions/
 *     gbbct1.seq - bacterial sequences
 *     gbenv1.seq - environmental sampling sequences
 *     gbest1.seq - expressed sequence tag (http://www.ncbi.nlm.nih.gov/dbEST/)
 *              stats: http://www.ncbi.nlm.nih.gov/genbank/dbest/dbest_summary/
 *     gbinv1.seq - invertebrate
 *     gbmam1.seq - mamalian
 *     gbpat1.seq - Patented sequences
 *     gbphg1.seq - phage
 *     gbpln1.seq - plant and fungi
 *     gbpri1.seq - primates
 *     gbrod1.seq - rodent
 *     gbsyn1.seq - synthetic
 *     gbvrl1.seq - viral
 *     gbvrt1.seq - other vertebrate
 *
 *     Stats on distributions within the 547GB files from Jeff:
 *     https://docs.google.com/document/d/1qsXzUDcrXy6qZZZJlVRYTifR9lOEpiZQxQ-WknLRW6E/edit#heading=h.r8tyjsata0sr
 *     https://docs.google.com/presentation/d/1FKvATGlnkVKkB6ZOJuLWMMFII4pbqVqvrp-UT1sZa8g/edit#slide=id.gb9d05972_00
 *
 * ftp://ftp.ncbi.nih.gov/genbank/wgs/
 *     The readme ftp://ftp.ncbi.nih.gov/genbank/README.genbank tells us
 *     that whole genome shotgun sequences are available elsewhere.
 *     We will need this later when doing chem->org->genome mappings
 *
 */

public class GenbankEntry {
  JSONObject data;

  public static Set<SequenceEntry> parsePossiblyMany(String gbFile) throws Exception {
    Set<SequenceEntry> all_entries = new HashSet<SequenceEntry>();
    read_all(gbFile);

    return all_entries;
  }

  static int here = 0;
  private static void here() { System.out.println("loc: " + here++); }

  private GenbankEntry(JSONObject gbEntry) {
    this.data = gbEntry;
  }

  // API is http://www.biojava.org/docs/api1.9.1/
  private static void read_all(String gbFile) throws Exception {
    BufferedReader br = new BufferedReader(new FileReader(gbFile));
    SimpleNamespace ns = new SimpleNamespace("biojava");

    // You can use any of the convenience methods found in the BioJava 1.6 API
    RichSequenceIterator rsi = RichSequence.IOTools.readGenbankDNA(br,ns);

    // contain more than a sequence, you need to iterate over rsi
    while(rsi.hasNext()){
      RichSequence rs = rsi.nextRichSequence();
      print(rs);
    }
  }

  // API is http://www.biojava.org/docs/api1.9.1/
  private static void print(RichSequence seq) {
    System.out.println( seq.getAccession() );
    System.out.println( seq.getDescription() );
    for (Feature f : seq.getFeatureSet()) {
      print(f);
      for(Iterator cfi = f.features(); cfi.hasNext(); ) {
        Feature cf = (Feature)cfi.next();
        print(cf);
      }
    }
    System.out.println( seq.getTaxon() );
    System.out.println( seq.getInternalSymbolList() );
  }

  // API is http://www.biojava.org/docs/api1.9.1/
  private static void print(Feature f) {
    System.out.println( "F " + f.getType() + " - " + f );
    //Get the annotation of the feature
    RichAnnotation ra = (RichAnnotation)f.getAnnotation();

    //Use BioJava defined ComparableTerms
    ComparableTerm geneTerm = new RichSequence.Terms().getGeneNameTerm();
    ComparableTerm synonymTerm = new RichSequence.Terms().getGeneSynonymTerm();
    //Create the required additional ComparableTerms
    ComparableTerm locusTerm = RichObjectFactory.getDefaultOntology().getOrCreateTerm("locus_tag");
    ComparableTerm productTerm = RichObjectFactory.getDefaultOntology().getOrCreateTerm("product");
    ComparableTerm proteinIDTerm = RichObjectFactory.getDefaultOntology().getOrCreateTerm("protein_id");

    for (Iterator <Note> it = ra.getNoteSet().iterator(); it.hasNext();){
      Note note = it.next();
      System.out.println("\tN " + note);
    }
  }

  public static void main(String[] args) throws Exception {
    read_all(args[0]);
  }

}
