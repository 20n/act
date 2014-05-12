package com.act.reachables;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;

public class Utils {

	public static MongoDB createActConnection(String host, int port, String host_backup, int port_backup) {
		MongoDB conn = null;
		System.out.print("Attempting connection to " + host + ":" + port + "/actv01....");
		try {
			conn = new MongoDB(host, port, "actv01");
		} catch (IllegalArgumentException e) {
			System.out.print("Attempting connection to " + host_backup + ":" + port_backup + "/actv01....");
			try {
				conn = new MongoDB(host_backup, port_backup, "actv01");
			} catch (IllegalArgumentException ee) { }
		}
		System.out.println("Connection opened.");
		
		return conn;
	}
	
	static void setEdgeProperty(Edge r, String prop, int val) {
		Edge.setAttribute(r.getIdentifier(), prop, val);
	}

	static void setNodeProperty(Node cn, String prop, int val) {
		Node.setAttribute(cn.getIdentifier(), prop, val);
	}

	static void setNodeProperty(Node cn, String prop, String val) {
		Node.setAttribute(cn.getIdentifier(), prop, val);
	}

	public static Node createNodeInNetwork(String inchi, String prefix) {
		Long minChemID = min(ActData.chem_ids) - 1;
		Long nodeid = createNode(minChemID, inchi, prefix + minChemID); // create a new node with new inchi
		
		// createNode adds to ActData.chemMetadata and chemInchis, but not chemids, or chemsInAct
		ActData.chem_ids.add(nodeid);
		Node node = Node.get(nodeid + "", true);
		ActData.chemsInAct.put(nodeid, node);
		ActData.Act.addNode(node, nodeid);
		ActData.chemsInAct.put(nodeid, node);

		setNodeProperty(node, "InChI", inchi);
		return node;
	}
	
	public static Long createNode(Long id, String inchi, String someNameIdeallyCanonical) {
		// see loadAct.finalize.loadChemMetadata for what metadata to install about this chem...
		Chemical chem = new Chemical(id); 
		chem.setInchi(inchi);
		chem.setCanon(someNameIdeallyCanonical);
		ActData.chemMetadata.put(id, chem);
		ActData.chemInchis.put(inchi, id);
		
		// we don't need to manually add the id to chem_ids because that will be done automatically 
		// when addEdgesToNw is called on the reaction which this node will be a part of.
		
		return id;
	}

	public static long min(Collection<Long> chem_ids) {
		long min = Long.MAX_VALUE;
		for (Long l : chem_ids) 
			min = min > l ? l : min;
		return min;
	}
	
	public static String constructRxnSMILES(Reaction r) {
		// we are going to construct a string "s1\ts2\ts3\t>>\tp1\tp2" for "s1 + s2 + s3 -> p1 + p2"
		// where s1..3, p1..2 are inchi strings, and rely on render.sh to make the correct judgement
		String s = "";
		for (Long sid : r.getSubstrates()) {
			Chemical sc = ActData.chemMetadata.get(sid);
			s += sc.getInChI() + "(#)";
		}
		String p = "";
		for (Long pid : r.getProducts()) {
			Chemical sc = ActData.chemMetadata.get(pid);
			p += "(#)" + sc.getInChI();
		}
		return s + ">>" + p;
	}
}

class RxnMetadata {
	public RxnMetadata(String tabbed_img_rxnid_desc) {
    	String[] split = tabbed_img_rxnid_desc.split("\t");
    	this.img_src = split[0];
    	this.rxn_id = split[1];
    	this.smiles = split[2];
    	this.desc = split[3];
    	this.pmids = Arrays.asList(split[4].split(",")); 
    	this.ec = split[5];
    	this.organism = split[6];
    	
    	System.out.format("RxnMetadata-- EC num: %s\nRxnMetadata-- Organism: %s\n", this.ec, this.organism );
	}
	String img_src;
	String rxn_id;
	String smiles;
	String desc;
	String ec;
	String organism;
	List<String> pmids;
}

class ConsumeStream extends Thread {
	BufferedReader stream;
	List<String> consumed;

	public ConsumeStream(BufferedReader stream) {
		this.stream = stream;
		this.consumed = new ArrayList<String>();
	}
	
    public void run() {
    	String line;
    	try {
			while ((line = this.stream.readLine()) != null)
				this.consumed.add(line);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
	
}
