package act.installer.metacyc;

import org.biopax.paxtools.model.BioPAXElement;

// Not yet handled, but will be in the future:
import org.biopax.paxtools.model.level3.EntityFeature;
import org.biopax.paxtools.model.level3.ModificationFeature;
import org.biopax.paxtools.model.level3.SequenceSite;
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
        || e instanceof SequenceModificationVocabulary
        || e instanceof SequenceSite) {
      // protein annotations such as phosphorylation appear as ModificationFeature's (Example1)
      // and these annotations might be specified to apply on a site as SequenceSite (Example3)
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

Example3:
Source: ecocyc/biopax-level3.owl:
Comment: Illustrates SequenceSite annotation to modification (e.g., of type phosphorylation)

      <bp:Protein rdf:ID="Protein147222">
        <bp:xref rdf:resource="#RelationshipXref147209"/>
        <bp:standardName rdf:datatype="http://www.w3.org/2001/                  XMLSchema#string">QseC sensory histidine kinase</bp:standardName>
        <bp:notFeature>
          <bp:ModificationFeature rdf:ID="ModificationFeature147212">
            <bp:modificationType>
              <bp:SequenceModificationVocabulary rdf:                           ID="SequenceModificationVocabulary77431">
                <bp:xref rdf:resource="#UnificationXref77432"/>
                <bp:term rdf:datatype="http://www.w3.org/2001/                  XMLSchema#string">phosphorylation</bp:term>
              </bp:SequenceModificationVocabulary>
            </bp:modificationType>
            <bp:featureLocation>
              <bp:SequenceSite rdf:ID="SequenceSite147213">
                <bp:sequencePosition rdf:datatype="http://www.w3.org/2001/      XMLSchema#int">246</bp:sequencePosition>
                <bp:positionStatus rdf:datatype="http://www.w3.org/2001/        XMLSchema#string">EQUAL</bp:positionStatus>
              </bp:SequenceSite>
            </bp:featureLocation>
            <bp:evidence>
              <bp:Evidence rdf:ID="Evidence145993">
                <bp:xref rdf:resource="#PublicationXref145992"/>
                <bp:evidenceCode>
                  <bp:EvidenceCodeVocabulary rdf:                               ID="EvidenceCodeVocabulary79023">
                    <bp:xref rdf:resource="#UnificationXref79024"/>
                    <bp:term rdf:datatype="http://www.w3.org/2001/              XMLSchema#string">EV-COMP-HINF</bp:term>
                  </bp:EvidenceCodeVocabulary>
                </bp:evidenceCode>
              </bp:Evidence>
            </bp:evidence>
          </bp:ModificationFeature>
        </bp:notFeature>
   ===========================================================================

ExampleN:
Source: XXXXXXXXXXXXX/biopax-level3.owl:

   ===========================================================================
*/

