package act.installer;

import java.io.PrintStream;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;

import org.json.JSONObject;
import org.json.JSONArray;

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

import act.shared.helpers.P;

public class ChEBIParser {

    private static int INDENT = 4;
    private final OWLOntology ontology;
    private final PrintStream out, err;
    private HashMap<String, EntryTypes> entryTypes;

    private ChEBIParser(OWLOntology _ontology) {
      ontology = _ontology;
      out = System.out;
      err = System.err;
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


    private HashMap<OWLClass, P<OWLClass, HashMap<String, EntryTypes>>> 
      getAllElementsWithParentsIn(Set<OWLClass> parentCategories) throws OWLException {
      HashMap<OWLClass, P<OWLClass, HashMap<String, EntryTypes>>> selected;
      selected = new HashMap<OWLClass, P<OWLClass, HashMap<String, EntryTypes>>>();
      for (OWLClass cl : ontology.getClassesInSignature()) {
        Set<OWLClass> parents = get_has_role_parents(cl);
        HashMap<String, EntryTypes> data = getData(cl);

        for (OWLClass p : parents) {
          if (parentCategories.contains(p)) {
            // this class has a parent within the set we are looking for.
            // so it qualifies as a class to be returned.. add it to map
            P<OWLClass, HashMap<String, EntryTypes>> p_data;
            p_data = new P<OWLClass, HashMap<String, EntryTypes>>(p, data);
            selected.put(cl, p_data);
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
          err.print(" ");
        }
        err.println(labelFor(clazz, ontology));
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
      // LabelExtractor is not available in the version of OWLAPI
      // Instead: Use the getAnnotations like we do in getData
      System.out.println("This is unverified. ChEBI parser:");
      System.out.println("LabelExtractor is not available in this OWLAPI v");
      System.out.println("So we extract the label manually. But not sure");
      System.out.println("if this code is correct. Need to check. Pause...");
      System.console().readLine();

      String chebiID = clazz.getIRI().getFragment();
      String label = null;
      for (OWLAnnotation a : clazz.getAnnotations(ontology)) {
        // We got the "if code" below from 
        // http://grepcode.com/file/repo1.maven.org/maven2/net.sourceforge.owlapi/owlapi-contract/3.4/uk/ac/manchester/owl/owlapi/tutorial/LabelExtractor.java
        if (a.getProperty().isLabel()) {
          OWLLiteral c = (OWLLiteral) a.getValue();
          label = c.getLiteral();
        }
      }
      // OLD code using LabelExtractor: 
      // LabelExtractor le = new LabelExtractor();
      // Set<OWLAnnotation> annotations = clazz.getAnnotations(ontology);
      // for (OWLAnnotation anno : annotations) {
      //   anno.accept(le);
      // }
      // label = le.getResult();

      /* Print out the label if there is one. Else ID */
      if (label != null) {
        return chebiID + "(" + label + ")";
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
              // err.println("\t\t Added parent: " + has_role_parent);
            } 
            // else {
            //   err.println("Was expecting singleton sets for properties and classes.");
            //   err.println("Got more/less: " + properties + " " + classes);
            //   System.exit(-1);
            // }
            break;
          default:
            // err.println("\t Default (SubClassOf): " + sup);
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
        // err.println("\t\t ObjProp: " + prop);
        // err.println("\t\t ObjProp: " + p);
        if (prop == null) System.exit(-1);
        return EntryTypes.has_role == prop;
      }
      return false;
    }

    private HashMap<String, EntryTypes> getData(OWLClass clazz) {
      HashMap<String, EntryTypes> data = new HashMap<String, EntryTypes>();
      for (OWLAnnotation a : clazz.getAnnotations(ontology)) {
        // err.println("\t\t Annotation Property: " + a.getProperty());
        String id = a.getProperty().toStringID();
        if (id.equals("rdfs:label")) continue;
        EntryTypes prop = entryTypes.get(id);
        String val = ((OWLLiteral)a.getValue()).getLiteral();
        // err.println("\t\t Annotation Property: " + prop);
        // err.println("\t\t Annotation Literal : " + value);
        data.put(val, prop);
      }
      return data;
    }
 
    private static void find(String subtreeRoot, IRI documentIRI, IRI inchiIRI) throws OWLException, IOException {
      OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
      // Load the ontology...
      OWLOntology ontology = manager.loadOntologyFromOntologyDocument(documentIRI);
      // Print metadata about ontology...
      System.err.println("Ontology Loaded...");
      System.err.println("Document IRI: " + documentIRI);
      System.err.println("Ontology    : " + ontology.getOntologyID());
      System.err.println("Format      : " + manager.getOntologyFormat(ontology));
      
      HashMap<String, String> inchis = readInchis(inchiIRI);
      
      ChEBIParser chebi = new ChEBIParser(ontology); 

      OWLClass clazz = manager.getOWLDataFactory().getOWLThing();
      System.err.println("Toplevel    : " + clazz);
      clazz = chebi.recurseToSubtreeRoot(clazz, subtreeRoot);
      System.err.println("Requested   : " + clazz);
      // Print the hierarchy
      HashMap<OWLClass, OWLClass> treeParents = new HashMap<OWLClass, OWLClass>();
      boolean doPrintSubtree = true;
      chebi.readSubtree(clazz, treeParents, doPrintSubtree);

      Set<OWLClass> metabolite_categories = treeParents.keySet();
      // System.err.println("Metabolite categories: " + metabolite_categories);

      // The function getAllElementsWithParentsIn picks out elements from the ontology that
      // have a "has_role" relationship with any member of metabolite_categories. 
      // E.g., chemA has_role eukaryotic_metabolite; chemB has_role metabolite
      //      then { chemA -> eukaryotic_metabolite, chemB -> metabolite } will be in the
      //      returned map.
      // The fn also gathers data for each of the elems
      // e.g., { Sy->Synonyms, X->xref, S->SMILES, I -> InChI, Ik -> InChIKey }
      HashMap<OWLClass, P<OWLClass, HashMap<String, EntryTypes>>> elems;
      elems = chebi.getAllElementsWithParentsIn(metabolite_categories);
      for (OWLClass e : elems.keySet())
        output(e, elems.get(e), inchis, ontology);
    }

    private static HashMap<String, String> readInchis(IRI documentIRI) throws IOException {
      String loc = documentIRI.getNamespace() + documentIRI.getFragment();
      if (loc.startsWith("file:")) {
        loc = loc.substring("file:".length());
      } else {
        System.err.println("Expecting inchi file as file:///absolute/path");
        System.exit(-1);
      }
      BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(loc)));
      String line;
      HashMap<String, String> inchis = new HashMap<String, String>();
      while ((line = in.readLine()) != null) {
        String[] id_inc = line.split("\t");
        String id = "CHEBI:" + id_inc[0];
        String inchi = id_inc[1];
        inchis.put(id, inchi);
      }
      return inchis;
    }

    private static void output(OWLClass elem, P<OWLClass, HashMap<String, EntryTypes>> meta, HashMap<String, String> inchis, OWLOntology ontology) {
      OWLClass parent = meta.fst();
      HashMap<String, EntryTypes> data = meta.snd();

      String id = getID(elem);
      String inchi = inchis.get(id);
      if (inchi == null) {
        // If there is no inchi we cannot merge this chemical into Act
        // so ignore...
        System.err.println("no inchi for " + id);
        return;
      }
      JSONObject metadata = new JSONObject();
      metadata.put("of_type", getID(parent));

      // Synonym, Definition, SMILES, xref, InChI, InChIKey, 
      // has_role, has_part, has_functional_parent, has_parent_hydride,
      // is_conjugate_acid_of, is_conjugate_base_of, is_enantiomer_of,
      // is_substituent_group_from, is_tautomer_of
      JSONArray d;
      if ((d = getMeta(data, EntryTypes.Definition)) != null && d.length() != 0)
        metadata.put(EntryTypes.Definition.toString(), d);
      if ((d = getMeta(data, EntryTypes.Synonym)) != null && d.length() != 0)
        metadata.put(EntryTypes.Synonym.toString(), d);
      if ((d = getMeta(data, EntryTypes.xref)) != null && d.length() != 0)
        metadata.put(EntryTypes.xref.toString(), d);

      // Need to output in format 
      // CHEBI<tab><ChebiID><tab><inchi><tab><json metadata>
      String row = "CHEBI\t";
      row += id + "\t";
      row += inchi + "\t";
      row += metadata.toString();
      System.out.println(row);

      // System.out.println(labelFor(elem, ontology) + "\t" + labelFor(type, ontology));
    }

    private static String getID(OWLClass c) {
      String frag = c.getIRI().getFragment(); // return CHEBI_25212 etc.
      return frag.replace('_', ':');
    }

    private static JSONArray getMeta(HashMap<String, EntryTypes> map, EntryTypes typToAdd) {
      JSONArray vals = new JSONArray();
      for (String val : map.keySet()) {
        if (typToAdd.equals(map.get(val)))
          vals.put(val);
      }
      return vals;
    }

    public static void main(String[] args) throws OWLException,
          InstantiationException, IllegalAccessException,
          ClassNotFoundException, IOException {
      // We load an ontology from the URI specified
      IRI documentIRI = IRI.create(args[0]);
      IRI inchiIRI = IRI.create(args[1]);
    
      String subtree = "CHEBI_25212(metabolite)";
      if (args.length > 2)
        subtree = args[2]; // override the default

      find(subtree, documentIRI, inchiIRI);
    }
}
