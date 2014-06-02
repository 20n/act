package act.installer.metacyc;

import org.biopax.paxtools.model.BioPAXElement;

import org.biopax.paxtools.model.level3.EntityFeature;
import org.biopax.paxtools.model.level3.ModificationFeature;
import org.biopax.paxtools.model.level3.SequenceSite;
import org.biopax.paxtools.model.level3.SequenceModificationVocabulary;
import org.biopax.paxtools.model.level3.PhysicalEntity;

public class SeenButNotHandled {

  // NOTE: IF THIS IS FOR leishcyc/biopax-level3.owl:
  // has two very bad data cases Example4 and Example5.  
  // We actually edit those bad annotation out. The diff is in Example(4,5)diff
  // Do not put exceptions here for such bad data. Instead just tweak the datafile

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

Example4:
Source: leishcyc/biopax-level3.owl:
    // physical entities in and of themselves are completely fine. its just that
    // we have handle their subclasses Protein, SmallMolecule, Rna, Dna etc
    // but in just one file leishcyc/biopax-level3.owl a Relationship that is
    // typically a (type, id, db) tuple has id as a physical Entity and simply
    // called a rdf:ID="Protein" with no number id etc. Really bad data. (Example4)

    <bp:relationshipType rdf:resource="#RelationshipTypeVocabulary14033"/>
    <bp:id>
      <bp:PhysicalEntity rdf:ID="Protein">
        <bp:comment rdf:datatype="http://www.w3.org/2001/XMLSchema#string">A physical entity consisting of a sequence of amino-acids; a protein monomer; a      single polypeptide chain.  An example is 
the EGFR protein.</bp:comment>
      </bp:PhysicalEntity>
    </bp:id>
    <bp:db rdf:datatype="http://www.w3.org/2001/XMLSchema#string">LeishCyc</bp:db>
  </bp:RelationshipXref>
   ===========================================================================

Example5:
Source: leishcyc/biopax-level3.owl:

     <bp:left rdf:resource="#SmallMolecule25819"/>
     is referenced in a reaction as a reactant, but its definition does not 
     contain a SmallMoleculeRef, and instead is just junk that says it is DNA as below.
     So we just remove the above reference from the reaction!

    <bp:SmallMolecule rdf:ID="SmallMolecule25819">
        <bp:xref rdf:resource="#RelationshipXref25820"/>
        <bp:standardName rdf:datatype="http://www.w3.org/2001/XMLSchema#string">a deoxyribonucleic acid</bp:standardName>
        <bp:entityReference rdf:datatype="http://www.w3.org/2001/XMLSchema#string">NIL</bp:entityReference>
        <bp:dataSource rdf:resource="#Provenance14019"/>
        <bp:comment rdf:datatype="http://www.w3.org/2001/XMLSchema#string">DNA is a high molecular weight linear polymer composed of nucleotides containing     deoxyribose and linked by phosphodiester bonds.</bp:comment>
        <bp:cellularLocation rdf:resource="#CellularLocationVocabulary14046"/>
      </bp:SmallMolecule>

   ===========================================================================

Example(4,5)diff:
Source: leishcyc/biopax-level3.owl:
19921c19921,19926
<     <bp:id rdf:datatype="http://www.w3.org/2001/XMLSchema#string">A physical entity consisting of a sequence of amino-acids; a protein monomer; a single polypeptide chain.  An example is the EGFR protein.</bp:id>
---
>     <bp:id>
>       <bp:PhysicalEntity rdf:ID="Protein">
>         <bp:comment rdf:datatype="http://www.w3.org/2001/XMLSchema#string">A physical entity consisting of a sequence of amino-acids; a protein monomer; a single polypeptide chain.  An example is 
> the EGFR protein.</bp:comment>
>       </bp:PhysicalEntity>
>     </bp:id>
126569a126575
>     <bp:left rdf:resource="#SmallMolecule25819"/>


   ===========================================================================

ExampleN:
Source: XXXXXXXXXXXXX/biopax-level3.owl:

   ===========================================================================
*/

