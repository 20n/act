package act.installer.metacyc;

import org.biopax.paxtools.model.BioPAXElement;

// Not yet handled, but will be in the future:
import org.biopax.paxtools.model.level3.EntityFeature;
import org.biopax.paxtools.model.level3.ModificationFeature;
import org.biopax.paxtools.model.level3.SequenceModificationVocabulary;
import org.biopax.paxtools.model.level3.ComplexAssembly;

public class SeenButNotHandled {
  public static boolean haveSeen(BioPAXElement e) {
    // biopax standard is all encompassing,
    // metacyc references a portion of it,
    // we handle most of metacyc, but there might be features that we intend
    // to handle later, and are annotations we have understood where they 
    // appear in the level3 files (and we ret = true for them).
    //
    // Once things are handled specifically, we remove them from this function
    
    if (e instanceof EntityFeature 
        || e instanceof ModificationFeature 
        || e instanceof SequenceModificationVocabulary) {
      // protein annotations such as phosphorylation appear as ModificationFeature's (Example1)
      return true;
    }

    if (e instanceof ComplexAssembly) {
      // The assembling Conversion of a complex is annotated as such (Example2)
      return true;
    }

    return false; // default, this thing was never seen before
  }

}
/*
   ===========================================================================
Example1:
Source: aara574087cyc/biopax-level3.owl:

     <bp:ModificationFeature rdf:ID="ModificationFeature26252">
     <bp:modificationType>
       <bp:SequenceModificationVocabulary rdf:ID="SequenceModificationVocabulary26253">
         <bp:xref rdf:resource="#UnificationXref26254"/>
         <bp:term rdf:datatype="http://www.w3.org/2001/XMLSchema#string">phosphorylation</bp:term>
     </bp:SequenceModificationVocabulary>
     </bp:modificationType>
     </bp:ModificationFeature>
   ===========================================================================
Example2:
Source: ano2cyc/biopax-level3.owl:

      <bp:ComplexAssembly rdf:ID="ComplexAssembly53653">
        <bp:xref rdf:resource="#UnificationXref53654"/>
        <bp:standardName rdf:datatype="http://www.w3.org/2001/XMLSchema#string">&lt;i>all-trans&lt;/i>-retinol + a cellular-retinol-binding protein  &amp;rarr;  an &lt;i>all-trans&lt;/i> retinol-[cellular-retinol-binding-protein]</bp:standardName>
        <bp:right rdf:resource="#Protein53643"/>
        <bp:participantStoichiometry rdf:resource="#Stoichiometry53646"/>
        <bp:participantStoichiometry rdf:resource="#Stoichiometry53640"/>
        <bp:participantStoichiometry rdf:resource="#Stoichiometry34555"/>
        <bp:left rdf:resource="#SmallMolecule34554"/>
        <bp:left rdf:resource="#Protein53639"/>
        <bp:dataSource rdf:resource="#Provenance30449"/>
      </bp:ComplexAssembly>

   ===========================================================================
ExampleN:
Source: XXXXXXXXXXXXX/biopax-level3.owl:

   ===========================================================================
*/

