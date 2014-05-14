package act.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import act.server.Logger;
import act.server.Molecules.MolGraph;
import act.shared.Reaction;
import act.shared.helpers.P;

class ClusterNode {
	ClusterNode c1, c2; // could be null if leaves
	int data; // could be -1 if internal node
	int m; // the id of this cluster; if leaf node then 0
	double L; // the level of this cluster; if leaf then 0
	boolean isLeaf;
	
	ClusterNode(int leaf_data) {
		this.c1 = null; this.c2 = null;
		this.L = 0.0;
		this.m = 0;
		if (leaf_data < 0) { System.err.println("Cannot install leaf with negative id"); System.exit(-1); }
		this.data = leaf_data;
		this.isLeaf = true;
	}
	
	ClusterNode(ClusterNode c1, ClusterNode c2, int m, double L) {
		this.c1 = c1; this.c2 = c2; this.m = m; this.L = L;
		this.data = -1;
		this.isLeaf = false;
	}
	
	@Override
	public String toString() {
		if (this.isLeaf) {
			return this.data + "";
		} else {
			String left = this.c1.toString();
			String right = this.c2.toString();
			return "(" + left + ")<- " + this.m + "[" + this.L + "]" + " ->(" + right + ")"; 
		}
	}
}

public class HierarchicalCluster<T> implements Cluster<T> {
	// http://en.wikipedia.org/wiki/Complete-linkage_clustering
		
	// single linkage, 
	// complete linkage, 
	// unweighted average, 
	// weighted average, 
	// unweighted centroid, 
	// weighted centroid and joint between-within. 
	
	private Graph<T, Double> distances;
	private List<List<T>> clusters;
	private ClusterNode clusterRoot;
	
	// bookkeeping data
	private double[][] matrix;
	private HashMap<Integer, T> assignedId2Data;
	private double Lcutoff;

	@Override
	public List<List<T>> getClusters() {
		return this.clusters;
	}
	
	@Override
	public void setDataLayout(Graph<T, Double> distances) {
		this.distances = distances;
		// Logger.println(0, "[Cluster] In: " + distances);
		getDistanceMatrix(this.distances);
		this.clusterRoot = computeCluster();
		Logger.println(0, "[Cluster] Clusters: " + this.clusterRoot);
		this.Lcutoff = computeCutoff(this.clusterRoot);
		this.clusters = collapseTree(this.clusterRoot, this.Lcutoff);
	}

	private double computeCutoff(ClusterNode root) {
		/*
		 * our current approaches are pretty brain-dead. Please improve....
		 */
		int which = 0;
		switch(which) {
		case 0: // two clustering
			return (root.L + Math.max(root.c1.L, root.c2.L))/2.0;
			
		case 1: // average of the levels!!
			double cutoff = 0.0;
			int num = 0;
			List<ClusterNode> nodes = new ArrayList<ClusterNode>();
			nodes.add(root);
			while (!nodes.isEmpty()) {
				num++;
				ClusterNode n = nodes.remove(0);
				cutoff += n.L;
				if (n.isLeaf)
					continue;
				nodes.add(n.c1); nodes.add(n.c2);
			}
			 // average of the cluster levels
			// braindead; we know... but works for debugging...)
			return cutoff/num;
			
		default: // single clustering
			return root.L;
		}
	}

	private void getDistanceMatrix(Graph<T, Double> dist) {
		List<P<Integer, T>> data = new ArrayList<P<Integer, T>>();
		for (int id : dist.GetNodeIDs()) {
			data.add(new P<Integer, T>(id, dist.GetNodeData(id)));
		}
		// Logger.println(0, "[Cluster] Data: " + data);
		double[][] distmat = new double[data.size()][data.size()]; 
		for (int i =0; i<data.size(); i++)
			for (int j = 0; j<data.size(); j++) {
				int a = data.get(i).fst();
				int b = data.get(j).fst();
				// diagonal elements are identical and so 0.0 far away from itself.
				distmat[i][j] = i==j? 0.0 : dist.GetEdgeType(a, b);
			}
		
		HashMap<Integer, T> dataMap = new HashMap<Integer, T>();
		for (int i = 0; i<data.size(); i++)
			dataMap.put(i, data.get(i).snd());

		Logger.println(0, "[Cluster] DataMap: " + dataMap);
		
		this.assignedId2Data = dataMap;
		this.matrix = distmat;
	}

	private List<List<T>> collapseTree(ClusterNode root, Double Lcutoff) {
		// traverse the root down to the cutpoint height
		// then for each subtree under that, collect all leaf nodes (which are then translated to List<T>)
		// for each leaf node it contains the leafifid inside. then lookup assignedId2Data(leafid)

		List<ClusterNode> worklist;
		List<ClusterNode> treetops = new ArrayList<ClusterNode>();
		// compute the treetops; i.e., the nodes whose levels are below the Lcutoff and whose parents are above...
		
		worklist = new ArrayList<ClusterNode>();
		worklist.add(root);
		while (!worklist.isEmpty()) {
			ClusterNode n = worklist.remove(0);
			if (n.L <= Lcutoff || n.isLeaf) {
				treetops.add(n);
				continue; // do not traverse down...
			}
			worklist.add(n.c1); worklist.add(n.c2);
		}
		
		// accumulate the nodes under each treetop and put them into one cluster...
		List<List<T>> clusters = new ArrayList<List<T>>();
		for (ClusterNode treetop : treetops) {
			List<T> subtree = new ArrayList<T>();
			
			worklist = new ArrayList<ClusterNode>();
			worklist.add(treetop);
			while (!worklist.isEmpty()) {
				ClusterNode n = worklist.remove(0);
				if (n.isLeaf) {
					subtree.add(this.assignedId2Data.get(n.data));
				} else {
					worklist.add(n.c1); worklist.add(n.c2);
				}
			}
			
			clusters.add(subtree);
		}
		
		return clusters;
	}

	private ClusterNode computeCluster() {

		double[][] mat = this.matrix;
		
		HashMap<Integer, HashMap<Integer, Double>> dist_mat = new HashMap<Integer, HashMap<Integer, Double>>();
		for (int i = 0; i < mat.length; i++) {
			HashMap<Integer, Double> dists = new HashMap<Integer, Double>();
			for (int j = 0; j < mat[i].length; j++)
				dists.put(j, mat[i][j]);
			dist_mat.put(i, dists);
		}

		/*
		 * 1. Begin with the disjoint clustering having level L(0) = 0 and sequence number m = 0.
		 * 2. Find the most similar pair of clusters in the current clustering, say pair (r), (s), 
		 *    according to d[(r),(s)] = max d[(i),(j)] where the maximum is over all pairs of clusters 
		 *    in the current clustering.
		 * 3. Increment the sequence number: m = m + 1. Merge clusters (r) and (s) into a single 
		 *    cluster to form the next clustering m. Set the level of this clustering to L(m) = d[(r),(s)]
		 * 4. Update the proximity matrix, D, by deleting the rows and columns corresponding to 
		 *    clusters (r) and (s) and adding a row and column corresponding to the newly formed cluster. 
		 *    The proximity between the new cluster, denoted (r,s) and old cluster (k) is defined 
		 *    as d[(k), (r,s)] = max d[(k),(r)], d[(k),(s)].
		 * 5. If all objects are in one cluster, stop. Else, go to step 2
		 */
		HashMap<Integer, ClusterNode> tree = new HashMap<Integer, ClusterNode>();
		for (Integer leaf : dist_mat.keySet()) {
			tree.put(leaf, new ClusterNode(leaf)); // 1.
		}
		int r, s; double L;
		int m = dist_mat.size();
		Logger.printf(0, "[Cluster] sz = %d\n", m);
		Logger.println(0, "[Cluster] D = " + dist_mat);
		while (dist_mat.size() > 1) {
			P<Integer, Integer> most_similar = find_most_similar(dist_mat); // 2.
			r = most_similar.fst(); s = most_similar.snd(); // 2.
			L = get_distance(dist_mat, r, s); // 3.
			ClusterNode merged = new ClusterNode(tree.get(r), tree.get(s), m, L); // 3.
			tree.put(m, merged); // 3.
			dist_mat.put(m, get_cluster_distances(dist_mat, r, s)); // 4.
			dist_mat.remove(r); dist_mat.remove(s); // 4.
			add_dist_to_m(dist_mat, m); // 4.
			remove_all_cols(dist_mat, r); remove_all_cols(dist_mat, s);
			Logger.printf(0, "[Cluster] new %d(level=%f)\n", m, L);
			m++;
		}
		int root = -1; for (Integer i : dist_mat.keySet()) root = i; // root is the singleton element left
		return tree.get(root);
	}

	private void remove_all_cols(HashMap<Integer, HashMap<Integer, Double>> dist_mat, int index) {
		for (int i : dist_mat.keySet()) {
			HashMap<Integer, Double> dists = dist_mat.get(i);
			if (dists.containsKey(index))
				dists.remove(index);
		}
	}

	private void add_dist_to_m(HashMap<Integer, HashMap<Integer, Double>> dist_mat, int m) {
		HashMap<Integer, Double> mDists = dist_mat.get(m);
		for (int i : dist_mat.keySet()) {
			if (i == m) continue;
			Double m2iDist = mDists.get(i); // we calculated these in get_cluster_distances
			dist_mat.get(i).put(m, m2iDist); // update the dist list of i; i.e., add a m->dist(m) 
		}
	}

	private HashMap<Integer, Double> get_cluster_distances(HashMap<Integer, HashMap<Integer, Double>> dist_mat, int r, int s) {
		/*
		 *  4. The proximity between the new cluster, denoted (r,s) and old cluster (k) is defined 
		 *    as d[(k), (r,s)] = min d[(k),(r)], d[(k),(s)].
		 */
		HashMap<Integer, Double> newDist = new HashMap<Integer, Double>();
		for (int k : dist_mat.keySet()) {
			if (k == r || k == s) continue;
			HashMap<Integer, Double> dists = dist_mat.get(k);
			newDist.put(k, Math.min(dists.get(r), dists.get(s))); // the new distances...
		}
		return newDist;
	}

	private double get_distance(HashMap<Integer, HashMap<Integer, Double>> dist_mat, int r, int s) {
		/*
		 * 3. Merge clusters (r) and (s) into a single 
		 *    cluster to form the next clustering m. Set the level of this clustering to L(m) = d[(r),(s)]
		 */
		double r2s = dist_mat.get(r).get(s);
		double s2r = dist_mat.get(s).get(r);
		if (r2s != s2r) { System.err.printf("The distance matrix is not symmetric. %f != %f for %d and %d\n", r2s, s2r, r, s); System.exit(-1);}
		return r2s;
	}

	private P<Integer, Integer> find_most_similar(HashMap<Integer, HashMap<Integer, Double>> dist_mat) {
		/*
		 * 	2. Find the most similar pair of clusters in the current clustering, say pair (r), (s), 
		 *    according to d[(r),(s)] = min d[(i),(j)] where the minimum is over all pairs of clusters 
		 *    in the current clustering.
		 */
		double min = Double.POSITIVE_INFINITY;
		int mini = -1, minj = -1;
		for (int i : dist_mat.keySet()) {
			HashMap<Integer, Double> dists = dist_mat.get(i);
			for (int j : dists.keySet()) {
				if (i == j) continue;
				if (min > dists.get(j)) {
					min = dists.get(j);
					mini = i; minj = j;
				}
			}
		}
		return new P<Integer, Integer>(mini, minj);
	}

	public static void unitTest() {
		int[] data = new int[] { 
				// 1,2,
				// 15,12,
				// 99,98,
				// 34,43,
				
				1,2,3,4,5,
				10,11,15,12,
				99,98,100,87,90,91,95,
				34,43,54,45,44,
		};
		Cluster<Integer> cls = new HierarchicalCluster<Integer>();
		Graph<Integer, Double> distances = new Graph<Integer, Double>(); 
		HashMap<Integer, Node<Integer>> nodes = new HashMap<Integer, Node<Integer>>();
		for (int d : data) {
			nodes.put(d, new Node<Integer>(d, d));
		}
		for (int d1 : data)
			for (int d2 : data)
				if (d1 != d2)
					distances.AddEdge(nodes.get(d1), nodes.get(d2), (double)Math.abs(d1 - d2));
		for (int d : data)
			distances.AddNode(nodes.get(d)); // we add the nodes later, as this is what is done in NROClasses

		// compute clusters
		cls.setDataLayout(distances);
		
		// print clusters
		int cl_id = 0;
		for (List<Integer> cluster : cls.getClusters()) 
			System.out.printf("[Cluster] Cluster(%d) = %s\n", cl_id++, cluster);
	}

}
