package act.server.Molecules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import act.graph.Graph;
import act.graph.Cluster;
import act.graph.HierarchicalCluster;
import act.graph.Node;
import act.server.Logger;
import act.server.SQLInterface.MongoDB;
import act.shared.AAMFailException;
import act.shared.Chemical;
import act.shared.MalFormedReactionException;
import act.shared.Reaction;
import act.shared.helpers.P;

public class NROClasses {
  HashMap<NRO, CRO> nroClasses;
  TheoryROClasses roClasses;
  MongoDB DB;

  public NROClasses(MongoDB db, TheoryROClasses classes) {
    this.DB = db;
    this.roClasses = classes;
    this.nroClasses = new HashMap<NRO, CRO>();
    for (TheoryROs ro : classes.getROs()) {
      List<NRO> nros;
      try {
        nros = computeNROs(ro, classes);
        Logger.printf(0, "[NROCls] NROs computed for TheoryRO: TheoryRO=%s; \n", ro);
        for (int i =0;i<nros.size(); i++)
          Logger.printf(0, "[NROCls] -- NRO[%d] is %s\n", i, nros.get(i));
        for (NRO n : nros) {
          Logger.printf(0, "[NROCls] Adding NRO->CRO mapping for %s\n", n);
          System.err.println("Omitting putting the NRO -> CRO mapping as we cannot yet hash operators. Need to add R-groups to operators.");
          // this.nroClasses.put(n, cro);
        }
      } catch (AAMFailException e) {
        System.err.println("Could not map atoms for MCS/NRO computation; which is very strange, given the atom mapping succeeded for CRO computation.");
        System.exit(-1);
      } catch (MalFormedReactionException e) {
        System.err.println("Malformed reaction in MCS/NRO computation; should not happen as CRO computation preceeded it, and sanity checks were done.");
        System.exit(-1);
      }
    }
  }

  public List<NRO> computeNROs(TheoryROs ro, TheoryROClasses classes) throws AAMFailException, MalFormedReactionException {
    // generate a NRO for the given cro and list of reactions
    List<Reaction> rxns = classes.getRORxnSet(ro);
    List<Boolean> dirs = classes.getRODirSet(ro);
    List<P<MolGraph, MolGraph>> nroGraphs = getNRO(rxns, dirs);
    List<NRO> nros = new ArrayList<NRO>();
    for (P<MolGraph, MolGraph> nroGraph : nroGraphs) {
      nros.add(new NRO(null));
      throw new MalFormedReactionException("Need to construct RO from Molgraphs like we do for CROs and EROs in SMILES:ToReactionTransform()");
    }
    return nros;
  }

  private List<P<MolGraph, MolGraph>> getNRO(List<Reaction> rxns, List<Boolean> dirs) throws AAMFailException, MalFormedReactionException {
    // for each pair of rxns find their mcs
    // compute distance(r1,r2)
    // k-cluster
    // compute mcs within each cluster, which is the nro = (added,del)

    // find n^2 mcs, n^2 distances, add to distances graph...
    Graph<Reaction, Double> distances = new Graph<Reaction, Double>();
    HashMap<Reaction, Node<Reaction>> nodes = new HashMap<Reaction, Node<Reaction>>();
    Double dist;
    Reaction rxn1, rxn2; MCS mcsS, mcsP;
    boolean dir1, dir2;
    final boolean substr = true;
    for (int i = 0; i<rxns.size()-1; i++) {
      for (int j = i+1; j<rxns.size(); j++) {
        rxn1 = rxns.get(i); rxn2 = rxns.get(j);
        dir1 = dirs.get(i); dir2 = dirs.get(j);
        addNode(rxn1, nodes); addNode(rxn2, nodes);
        // the boolean "substr" indicates that we wish to extract the substrate (or not)
        // we xor with !"dir"i because we want to flip if the direction was the opposite...
        // "dir"i holds whether the direction is same: if the direction is the same then we want to "A xor false" (=A), else "A xor true" (=!A)
        mcsS = new MCS(getMolGraph(rxn1, substr ^ !dir1), getMolGraph(rxn2, substr ^ !dir2));
        mcsP = new MCS(getMolGraph(rxn1, !substr ^ !dir1), getMolGraph(rxn2, !substr ^ !dir2));
        dist = new DistanceMCS(mcsS, mcsP).getDist();
        distances.AddEdge(nodes.get(rxn1), nodes.get(rxn2), dist);
        Logger.printf(0, "[NROCls] -- distance=%f; between UUID %d and UUID %d\n", dist, rxn1.getUUID(), rxn2.getUUID());
      }
    }
    for (Node<Reaction> n : nodes.values())
      distances.AddNode(n);

    // debug... testing clustering...
    // HierarchicalCluster.unitTest();

    // cluster...
    Cluster<Reaction> cls = new HierarchicalCluster<Reaction>();
    cls.setDataLayout(distances);

    Logger.printf(0, "[NROCls] clusters computed; now mcs for each cluster is NRO.\n");
    // compute mcs within each cluster
    List<P<MolGraph, MolGraph>> nros = new ArrayList<P<MolGraph, MolGraph>>();
    for (List<Reaction> cluster : cls.getClusters()) {
      Logger.printf(0, "[NROCls] Cluster(%d) = %s\n", nros.size(), getUUIDs(cluster));
      List<List<String>> s = new ArrayList<List<String>>(), p = new ArrayList<List<String>>();
      for (Reaction r : cluster) {
        // add's r's substrates and products to s,p
        s.add(getMolGraph(r, true));
        p.add(getMolGraph(r, false));
      }
      mcsS = new MCS(s);
      mcsP = new MCS(p);
      // System.err.println("Currently omitting writing to nro set...NROClasses.getNRO");
      nros.add(new P<MolGraph, MolGraph>(mcsS.getMCS(), mcsP.getMCS()));
    }

    return nros;
  }

  private List<Integer> getUUIDs(List<Reaction> rs) {
    List<Integer> uuids = new ArrayList<Integer>();
    for (Reaction r: rs) uuids.add(r.getUUID());
    return uuids;
  }

  private void addNode(Reaction r, HashMap<Reaction, Node<Reaction>> nodes) {
    // if we have already seen this reaction, then do not add another node for it
    if (nodes.containsKey(r))
      return;
    // if this is a new reaction, then give it a new id, and add the node to the map
    int id = nodes.size();
    Node<Reaction> node = new Node<Reaction>(id, r);
    nodes.put(r, node);
  }

  private List<String> getMolGraph(Reaction r, boolean substrates) {
    Long[] mols = substrates ? r.getSubstrates() : r.getProducts();

    List<String> molecules = new ArrayList<String>();
    for (long s : mols) {
      Chemical substr = this.DB.getChemicalFromChemicalUUID(s);
      String smiles = substr.getSmiles();
      molecules.add(smiles);
    }
    // MolGraph g = SMILES.ToGraph(molecules);
    return molecules;
  }

}
