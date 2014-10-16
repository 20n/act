package act.installer;

import java.io.PrintStream;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public class ChEBIParser {

    private static int INDENT = 4;
    private final OWLOntology ontology;
    private final PrintStream out;
    private HashMap<String, EntryTypes> entryTypes;

    private ChEBIParser( OWLOntology _ontology) {
      ontology = _ontology;
      out = System.out;
      entryTypes = new HashMap<String, EntryTypes>();
      entryTypes.put("http://purl.obolibrary.org/obo#Synonym",    EntryTypes.Synonym );
      entryTypes.put("http://purl.obolibrary.org/obo#Definition", EntryTypes.Definition );
      entryTypes.put("http://purl.obolibrary.org/obo#SMILES",     EntryTypes.SMILES );
      entryTypes.put("http://purl.obolibrary.org/obo#xref",       EntryTypes.xref );
      entryTypes.put("http://purl.obolibrary.org/obo#InChI",      EntryTypes.InChI );
      entryTypes.put("http://purl.obolibrary.org/obo#InChIKey",   EntryTypes.InChIKey );

      entryTypes.put("http://purl.obolibrary.org/obo#has_role",   EntryTypes.has_role );
      entryTypes.put("http://purl.obolibrary.org/obo#has_part",   EntryTypes.has_part );
      entryTypes.put("http://purl.obolibrary.org/obo#has_functional_parent",      EntryTypes.has_functional_parent );
      entryTypes.put("http://purl.obolibrary.org/obo#has_parent_hydride",         EntryTypes.has_parent_hydride );
      entryTypes.put("http://purl.obolibrary.org/obo#is_conjugate_acid_of",       EntryTypes.is_conjugate_acid_of );
      entryTypes.put("http://purl.obolibrary.org/obo#is_conjugate_base_of",       EntryTypes.is_conjugate_base_of );
      entryTypes.put("http://purl.obolibrary.org/obo#is_enantiomer_of",           EntryTypes.is_enantiomer_of );
      entryTypes.put("http://purl.obolibrary.org/obo#is_substituent_group_from",  EntryTypes.is_substituent_group_from );
      entryTypes.put("http://purl.obolibrary.org/obo#is_tautomer_of",             EntryTypes.is_tautomer_of );
    }

    public enum EntryTypes { Synonym, Definition, SMILES, xref, InChI, InChIKey, 
                              has_role, has_part, has_functional_parent, has_parent_hydride,
                              is_conjugate_acid_of, is_conjugate_base_of, is_enantiomer_of,
                              is_substituent_group_from, is_tautomer_of
                            }


    private HashMap<OWLClass, OWLClass> getAllElementsWithParentsIn(Set<OWLClass> parentCategories) throws OWLException {
      HashMap<OWLClass, OWLClass> selected = new HashMap<OWLClass, OWLClass>();
      for (OWLClass cl : ontology.getClassesInSignature()) {
        Set<OWLClass> parents = get_has_role_parents(cl);
        HashMap<String, EntryTypes> data = getData(cl);

        for (OWLClass p : parents) {
          if (parentCategories.contains(p)) {
            // this class has a parent within the set we are looking for.
            // so it qualifies as a class to be returned.. add it to map
            selected.put(cl, p);
          }
        }
      }
      return selected;
    }

    /**
     * Print the class hierarchy for the given ontology from this class down,
     * assuming this class is at the given level. Makes no attempt to deal
     * sensibly with multiple inheritance.
     */
    private void readSubtree(OWLClass clazz, HashMap<OWLClass, OWLClass> parents, boolean doPrint) throws OWLException {
      parents.put(clazz, null); // install in parents map the root we are looking for
      readSubtree(clazz, parents, 0, doPrint);
    }

    /**
     * Print the class hierarchy from this class down, assuming this class is at
     * the given level. Makes no attempt to deal sensibly with multiple
     * inheritance.
     */
    private void readSubtree(OWLClass clazz, HashMap<OWLClass, OWLClass> parents, int level, boolean doPrint) throws OWLException {
      if (doPrint) {
        for (int i = 0; i < level * INDENT; i++) {
          out.print(" ");
        }
        out.println(labelFor(clazz, ontology));
      }
      /* Find the children and recurse */
      for (OWLClassExpression c : clazz.getSubClasses(ontology)) {
        OWLClass child = c.asOWLClass();
        if (!child.equals(clazz)) {
          parents.put(child, clazz); // install parents in map
          readSubtree(child, parents, level + 1, doPrint); // recurse
        }
      }
    }

    private OWLClass recurseToSubtreeRoot(OWLClass curr_root, String subtree) throws OWLException {
      String label = labelFor(curr_root, ontology);
      if (subtree.equals(label))
        return curr_root;
      
      /* Else find the children and recurse */
      OWLClass descendant = null;
      for (OWLClassExpression c : curr_root.getSubClasses(ontology)) {
        OWLClass child = c.asOWLClass();
        if (!child.equals(curr_root)) {
          descendant = recurseToSubtreeRoot(child, subtree);
          if (descendant != null)
            break;
        }
      }

      return descendant;
    }

    private static String labelFor(OWLClass clazz, OWLOntology ontology) {
      /* Use visitor to extract label annotation */
      LabelExtractor le = new LabelExtractor();
      Set<OWLAnnotation> annotations = clazz.getAnnotations(ontology);
      for (OWLAnnotation anno : annotations) {
        anno.accept(le);
      }
      String chebiID = clazz.getIRI().getFragment();
      /* Print out the label if there is one. Else ID */
      if (le.getResult() != null) {
        return chebiID + "(" + le.getResult().toString() + ")";
      } else {
        return chebiID;
      }
    }

    private Set<OWLClass> get_has_role_parents(OWLClass clazz) {
      Set<OWLClass> roles = new HashSet<OWLClass>();
      for (OWLClassExpression sup: clazz.getSuperClasses(ontology)) {
        ClassExpressionType typ = sup.getClassExpressionType();
        switch (typ) {
          case OBJECT_SOME_VALUES_FROM: 
            Set<OWLObjectProperty> properties = sup.getObjectPropertiesInSignature();
            Set<OWLClass> classes = sup.getClassesInSignature();
            if (singletonPropertyHasRole(properties) && classes.size() == 1) {
              OWLClass has_role_parent = classes.toArray(new OWLClass[0])[0];
              roles.add(has_role_parent);
              // out.println("\t\t Added parent: " + has_role_parent);
            } 
            // else {
            //   out.println("Was expecting singleton sets for properties and classes.");
            //   out.println("Got more/less: " + properties + " " + classes);
            //   System.exit(-1);
            // }
            break;
          default:
            // out.println("\t Default (SubClassOf): " + sup);
            break;
        }
      }
      return roles;
    }

    private boolean singletonPropertyHasRole(Set<OWLObjectProperty> properties) {
      if (properties.size() != 1)
        return false;
      for (OWLObjectProperty p : properties) {
        EntryTypes prop = entryTypes.get(p.toStringID());
        // out.println("\t\t ObjProp: " + prop);
        // out.println("\t\t ObjProp: " + p);
        if (prop == null) System.exit(-1);
        return EntryTypes.has_role == prop;
      }
      return false;
    }

    private HashMap<String, EntryTypes> getData(OWLClass clazz) {
      HashMap<String, EntryTypes> data = new HashMap<String, EntryTypes>();
      for (OWLAnnotation a : clazz.getAnnotations(ontology)) {
        // out.println("\t\t Annotation Property: " + a.getProperty());
        String id = a.getProperty().toStringID();
        if (id.equals("rdfs:label")) continue;
        EntryTypes prop = entryTypes.get(id);
        String val = ((OWLLiteral)a.getValue()).getLiteral();
        // out.println("\t\t Annotation Property: " + prop);
        // out.println("\t\t Annotation Literal : " + value);
        data.put(val, prop);
      }
      return data;
    }
 
    private static void find(String subtreeRoot, IRI documentIRI) throws OWLException {
      OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
      // Load the ontology...
      OWLOntology ontology = manager
              .loadOntologyFromOntologyDocument(documentIRI);
      // Print metadata about ontology...
      System.out.println("Ontology Loaded...");
      System.out.println("Document IRI: " + documentIRI);
      System.out.println("Ontology    : " + ontology.getOntologyID());
      System.out.println("Format      : " + manager.getOntologyFormat(ontology));
      ChEBIParser chebi = new ChEBIParser( ontology);

      OWLClass clazz = manager.getOWLDataFactory().getOWLThing();
      System.out.println("Toplevel    : " + clazz);
      clazz = chebi.recurseToSubtreeRoot(clazz, subtreeRoot);
      System.out.println("Requested   : " + clazz);
      // Print the hierarchy
      HashMap<OWLClass, OWLClass> treeParents = new HashMap<OWLClass, OWLClass>();
      boolean doPrintSubtree = true;
      chebi.readSubtree(clazz, treeParents, doPrintSubtree);

      Set<OWLClass> metabolite_categories = treeParents.keySet();
      // System.out.println("Metabolite categories: " + metabolite_categories);

      // The function getAllElementsWithParentsIn picks out elements from the ontology that
      // have a "has_role" relationship with any member of metabolite_categories. 
      // E.g., chemA has_role eukaryotic_metabolite; chemB has_role metabolite
      //      then { chemA -> eukaryotic_metabolite, chemB -> metabolite } will be in the
      //      returned map.
      // TODO: Additionally the function does also gather data from each of the elems
      // e.g., { Sy -> Synonyms, X -> xref, S -> SMILES, I -> InChI, Ik -> InChIKey }
      // but right now does not return it back here.
      HashMap<OWLClass, OWLClass> elems = chebi.getAllElementsWithParentsIn(metabolite_categories);
      for (OWLClass e : elems.keySet())
        System.out.format("%s\t\t%s\n", labelFor(e, ontology), labelFor(elems.get(e), ontology));
    }

    public static void main(String[] args) throws OWLException,
          InstantiationException, IllegalAccessException,
          ClassNotFoundException {
      // We load an ontology from the URI specified
      IRI documentIRI = IRI.create(args[0]);
    
      String subtree = "CHEBI_25212(metabolite)";
      if (args.length > 1)
        subtree = args[1]; // override the default

      find(subtree, documentIRI);
    }
}
