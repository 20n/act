package act.server.Search;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import act.render.RenderChemical;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;

public class ReactionsHypergraph <N, E>{
	private Set<N> initialSet;
	private Set<N> allowedProducts;
	
	/**
	 *  Maps reaction to its required reactants
	 */
	private SetBuckets<E, N> reactionReactants;
	
	/**
	 *  The reverse mapping of the above 
	 */
	private SetBuckets<N, E> reactantReactions;
	
	/**
	 *  Maps products to reactions that produce it
	 */
	private SetBuckets<N, E> productReactions;
	
	/**
	 *  The reverse mapping of the above 
	 */
	private SetBuckets<E, N> reactionProducts;
	
	private Map<N, List<String>> chemicalInfo;
	private Map<E, List<String>> reactionInfo;
	protected Map<E, Object> reactionLabels;
	private Map<N, String> chemicalColor;
	private Map<N, String> chemicalImage;
	
	private Set<N> chemicals;
	protected Set<E> reactions;
	
	private IdType myIdType;
	
	private enum IdType {
		DB_ID,
		SMILES,
		OTHER
	}
	
	public ReactionsHypergraph() {
		reactionReactants = new SetBuckets<E, N>();
		reactionProducts = new SetBuckets<E, N>();
		reactantReactions = new SetBuckets<N, E>();
		productReactions = new SetBuckets<N, E>();
		reactions = new HashSet<E>();
		chemicals = new HashSet<N>();
		myIdType = IdType.SMILES;
		chemicalInfo = new HashMap<N, List<String>>();
		reactionInfo = new HashMap<E, List<String>>();
		reactionLabels = new HashMap<E, Object>();
		chemicalColor = new HashMap<N, String>();
		chemicalImage = new HashMap<N, String>();
	}
	
	/**
	 * The idType should be set depending on what is used as the chemical ids.
	 * Depending on what it is set as, they will be treated differently when producing a dot file
	 * with writeDOT.
	 */
	public void setIdTypeSMILES() {
		myIdType = IdType.SMILES;
	}	
	public void setIdTypeDB_ID() {
		myIdType = IdType.DB_ID;
	}
	public void setIdTypeOTHER() {
		myIdType = IdType.OTHER;
	}
	
	
	public void setInitialSet(Set<N> initialSet) {
		this.initialSet = initialSet;
	}
	
	public Set<N> getInitialSet() {
		return new HashSet<N>(initialSet);
	}
	
	public void removeFromInitialSet(N n) {
		initialSet.remove(n);
	}
	
	public void setAllowedProducts(Set<N> products) {
		allowedProducts = new HashSet<N>(products);
	}
	
	public int getNumChemicals() {
		return chemicals.size();
	}
	
	public int getNumReactions() {
		return reactions.size();
	}
	
	public Set<E> getReactions() {
		return new HashSet<E>(reactions);
	}
	
	public Set<N> getChemicals() {
		return new HashSet<N>(chemicals);
	}
	
	/**
	 * @param reaction
	 */
	public void delete(E reaction) {
		reactions.remove(reaction);
		Set<N> products = reactionProducts.get(reaction);
		for (N p : products) {
			productReactions.get(p).remove(reaction);
		}
		reactionProducts.clear(reaction);
		Set<N> reactants = reactionReactants.get(reaction);
		for (N r : reactants) {
			reactantReactions.get(r).remove(reaction);
		}
		reactionReactants.clear(reaction);
	}
	
	public void addReaction(E reaction, N[] reactants, N[] products) {
		addReaction(reaction, Arrays.asList(reactants), Arrays.asList(products));
	}
	
	public void addReaction(E reaction, Collection<N> reactants, Collection<N> products) {
		addReactants(reaction, reactants);
		Set<E> reactionSet = new HashSet<E>();
		reactionSet.add(reaction);
		if (products != null)
			for (N product : products)
				addReactions(product, reactionSet);
	}
	
	/**
	 * Adds edges between the reactants and the reaction.
	 * @param reaction
	 * @param reactants
	 */
	public void addReactants(E reaction, Collection<N> reactants) {
		chemicals.addAll(reactants);
		reactions.add(reaction);
		reactionReactants.putAll(reaction, reactants);
		reactantReactions.putAll(reactants, reaction);
	}
	
	/**
	 * Adds edges between the a chemical and the bag of reactions that produce it.
	 * @param productID
	 * @param reactions
	 */
	public void addReactions(N productID, Collection<E> reactions) {
		chemicals.add(productID);
		this.reactions.addAll(reactions);
		productReactions.putAll(productID, reactions);
		reactionProducts.putAll(reactions, productID);
	}
	
	public Set<E> getReactionsFrom(N reactant) {
		Set<E> retval = reactantReactions.get(reactant);
		if (retval == null) return new HashSet<E>();
		return retval;
	}
	
	public Set<E> getReactionsTo(N product) {
		Set<E> retval = productReactions.get(product);
		if (retval == null) return new HashSet<E>();
		return retval;
	}
	
	public Set<P<E, N>> getReactionReactantEdges() {
		Set<P<E, N>> edges = new HashSet<P<E, N>>();
		for (E reaction: reactionReactants.keySet()) {
			Set<N> reactants = reactionReactants.get(reaction);
			for (N reactant : reactants) {
				edges.add(new P<E, N>(reaction, reactant));
			}
		}
		return edges;
 	}
	
	public Set<P<N, E>> getProductReactionEdges() {
		Set<P<N, E>> edges = new HashSet<P<N, E>>();
		for (N product: productReactions.keySet()) {
			Set<E> reactions = productReactions.get(product);
			for (E reaction : reactions) {
				edges.add(new P<N, E>(product, reaction));
			}
		}
		return edges;
 	}
	
	public Set<N> getReactants(E reaction) {
		Set<N> retval = reactionReactants.get(reaction);	
		if (retval == null) retval = new HashSet<N>();
		return retval;
	}
	
	public Set<N> getProducts(E reaction) {
		Set<N> retval = reactionProducts.get(reaction);	
		if (retval == null) retval = new HashSet<N>();
		return retval;
	}
	
	private String setToSimpleString(Set<E> set) {
		String result = "";
		if (set == null) return result;
		for (E s : set) {
			result = result + s + " ";
		}
		return result;
	}
	
	/**
	 * See below.
	 * @param fname
	 * @param db
	 * @throws IOException
	 * @throws  
	 */
	public void writeDOT(String fname, MongoDB db) throws IOException {
		this.writeDOT(fname, db, false);
	}
	
	public void writeDOT(String fname, MongoDB db, boolean genChemicalImages) throws IOException {
		if (genChemicalImages) {
			writeDOT(fname, db, "ReactionsHypergraphImages");
		} else {
			writeDOT(fname, db, null);
		}
	}
	
	/**
	 * Configuration parameters for drawing
	 */
	public String reactionShape = "box";
	
	
	/**
	 * Given file name, outputs graph in dot format.
	 * N and E's toString methods are assumed to have no quotes/other characters 
	 * with special meaning in a dot file as those are used as ids.
	 * @param fname
	 * @param db
	 * @param imgDir Generate chemical images Graphviz can include in the graph in this directory.
	 * @param absImgDirPath Path dot file can use to reference images
	 * @throws IOException
	 * @throws JSONException 
	 */
	@SuppressWarnings("unchecked")
	public void writeDOT(String fname, MongoDB db, String imgDir) throws IOException {
		boolean genChemicalImages = (imgDir != null);
		String absImgDirPath = null;
		if (genChemicalImages) {
			File file = new File(imgDir);
			file.mkdirs();
			absImgDirPath = file.getAbsolutePath();
		}
		
		BufferedWriter dotf = new BufferedWriter(new FileWriter(fname, false)); // open for overwrite
		// taking hints from http://en.wikipedia.org/wiki/DOT_language#A_simple_example
		String graphname = "paths";
		
		Set<N> cofactors = new HashSet<N>();
		SetBuckets<E, N> reactionReactantCofactors = new SetBuckets<E, N>();
		SetBuckets<E, N> reactionProductCofactors = new SetBuckets<E, N>();
		SetBuckets<E, N> reactionMainReactants = new SetBuckets<E, N>();
		SetBuckets<E, N> reactionMainProducts = new SetBuckets<E, N>();
		Set<N> chemicalIDs = new HashSet<N>();
		
		SetBuckets<P<Set<N>, Set<N>>, E> hyperedges = simplifyReactions(
				cofactors, reactionReactantCofactors, reactionProductCofactors,
				reactionMainReactants, reactionMainProducts, chemicalIDs);
		
		//We want to split compounds with no parents into different graph nodes for neatness.
		Set<N> compoundsToSplit = new HashSet<N>();

		JSONObject cascade = new JSONObject();
		try {
			dotf.write("digraph \"\" {\n");
			//dotf.write("\tratio = .67\n");
			if (genChemicalImages)
				dotf.write("\tnode [color=none]\n");
			else
				dotf.write("\tnode [color=black]\n");
			
			for (N chemicalID : chemicalIDs) {
				JSONObject chemjson = new JSONObject();
				chemjson.put("id", chemicalID);
				Long c = null;
				String label = "label=\"\", ";
				String idName = "";
				String color = "";
				String fillcolor = "";
				String img = "";
				String smiles = null;
				String url = "";
				
				if (chemicalColor.containsKey(chemicalID)) {
					fillcolor = "style=\"filled\", fillcolor=\"" + chemicalColor.get(chemicalID) + "\"";
				} else {
					fillcolor = "style=\"filled\", fillcolor=\"" + "#FFFFFF"+ "\"";
				}
	
				/* Red indicates a compound without a reaction that makes it in the graph */
				if (!productReactions.containsKey(chemicalID)) {
					color = "color=\"red\"";
					compoundsToSplit.add(chemicalID);
				}
				
				if (myIdType == IdType.DB_ID) {
					c = (Long) chemicalID;
					idName = "";
					//idName = "tooltip=\"" + db.getShortestName(c) + "\"";
					Chemical chemical = db.getChemicalFromChemicalUUID(c);
					
					/* Green indicates a native or cofactor */
					if (chemical != null && 
							(initialSet != null && initialSet.contains(chemicalID))) {
						color = "color=\"green\"";
						label = "label=\"" + chemical.getFirstName() + "\"";
						compoundsToSplit.add(chemicalID);
						chemjson.put("isCofactor", true);
					} else {
						chemjson.put("isCofactor", false);
					}
					
					if (chemical != null) {
						smiles = chemical.getSmiles();
						/*
							idName = "label=< <TABLE>" +
									"<TR><TD>" + db.getShortestName(c).replaceAll(">|<", "-") + "</TD></TR> " +
									"<TR><TD TITLE=\"\" HREF=\"http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + 
									chemical.getPubchemID() +"\"><FONT COLOR=\"blue\">Pubchem link</FONT></TD></TR> ";
							if (chemicalInfo.get(chemicalID) != null) {
								for (String info : chemicalInfo.get(chemicalID)) {
									idName += "<TR><TD>" + info + "</TD></TR> ";
								}
							}
							idName += "</TABLE> > ";
						 */
						//url = "[URL=\"http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + 
						//		chemical.getPubchemID() + "\"]";
						String infoList = "";
						if (chemicalInfo.get(chemicalID) != null) {
							for (String info : chemicalInfo.get(chemicalID)) {
								infoList += " " + info + " ";
							}
						}
						
						idName = "tooltip=\"" + chemical.getFirstName().replace(">|<", "-") + 
								" act: " + chemical.getUuid() + "" +
								" pubchem:" + chemical.getPubchemID() + "" +
								" other info: " + infoList + "\", ";
						chemjson.put("name", chemical.getFirstName().replace(">|<", "-"));
						chemjson.put("SMILES", smiles);
						chemjson.put("act_id", chemical.getUuid());
						chemjson.put("pubchem_id", chemical.getPubchemID());
						chemjson.put("other_info", infoList);
					}
				} else if (myIdType == IdType.SMILES) {
					idName = "tooltip=\"" + chemicalID + "\", ";
					smiles = (String) chemicalID;
				}
				
				if (!(initialSet != null && //don't use image if chemical is in initialSet and we can get name from db
						initialSet.contains(chemicalID) && 
						myIdType == IdType.DB_ID)) {
					if (genChemicalImages && smiles != null) {
						String relImgFilename = absImgDirPath + "/" + chemicalID + ".png";
						String imgFilename = imgDir + "/" + chemicalID + ".png";
						RenderChemical.renderToFile(imgFilename, smiles);
						img = "image=\"" + relImgFilename + "\", ";
					}
				}
				if (chemicalImage.containsKey(chemicalID)) {
					img = "image=\"" + chemicalImage.get(chemicalID)  + "\", ";
					//label = "label=<<IMG SRC=\"" + chemicalImage.get(chemicalID) + "\"/>>, ";
				}
			
				
				
				if (compoundsToSplit.contains(chemicalID) && null != reactantReactions.get(chemicalID)) {
					Set<String> uniqueReactions = new HashSet<String>();
					for (E reactionID : reactantReactions.get(chemicalID)) {
						if (!reactionMainReactants.get(reactionID).contains(chemicalID)) continue;
						String reaction = setToSimpleString(hyperedges.get(
								new P<Set<N>, Set<N>>(
										reactionMainReactants.get(reactionID),
										reactionMainProducts.get(reactionID))));
						uniqueReactions.add(reaction);
						
					}
					String nodeAttributes = " [width=.5, " + label + 
							color + idName + img + fillcolor + "] " + url + " \n";
					for(String reaction: uniqueReactions) {
						dotf.write(toChemicalID(chemicalID + "_" + reaction) + nodeAttributes);
					}
					
					if (productReactions.get(chemicalID) != null) {
						dotf.write(toChemicalID(chemicalID.toString()) + nodeAttributes);
					}
				} else {
	
					String nodeAttributes = " [width=.5, " + label + 
							color + idName + img + fillcolor + "] " + url + " \n";
					dotf.write(toChemicalID(chemicalID.toString()) + nodeAttributes);
				}
	
	 			// JSONObject.accumulate because if there is already an object stored under the key then a JSONArray 
	 			// is stored under the key to hold all of the accumulated values. If there is already a JSONArray, 
	 			// then the new value is appended to it.
				cascade.accumulate("chemicals", chemjson);
			}
	
			HashMap<String, JSONObject> rxnObjs = new HashMap<String, JSONObject>();
			for (P<Set<N>, Set<N>> key : hyperedges.keySet()) {
				JSONObject rxnjson = new JSONObject();
				String reactionIDs = setToSimpleString(hyperedges.get(key));
				String reactionTooltip = "";
				String color = null;
				E firstReactionID = null;
				for (E reactionID : hyperedges.get(key)) {
					if (firstReactionID == null) firstReactionID = reactionID;
					//reactionTooltip += "act: " + reactionID;				
					List<String> infos = reactionInfo.get(reactionID);
					reactionTooltip += "(";
					if (infos == null && myIdType == IdType.DB_ID) {
						Long dbID = (Long) reactionID;
						//default tooltip
						Reaction reaction = db.getReactionFromUUID(dbID);
						if (reaction == null) continue;
						String ec = reaction.getECNum();
						int actid = reaction.getUUID();
						String rxnString = reaction.getReactionName();
						reactionTooltip += "act: " + actid;
						reactionTooltip += " EC: " + ec;
						rxnjson.put("act", actid);
						rxnjson.put("EC", ec);
						rxnjson.accumulate("readable", rxnString);
						for (String orgname : extractBRENDAOrgNames(rxnString)) {
							accumulateIfNotPresent(rxnjson, "sequences", "placeholder for AA seq + Accession Code: get_seq(" + ec + "," + orgname + ")");
						}
						Set<Long> organisms = db.getOrganismIDs(dbID);
						if (organisms != null) {
							reactionTooltip += " organisms: " + organisms.size();
							rxnjson.put("organisms", organisms.size());
							for (Long org : organisms) {
								String orgname = db.getOrganismNameFromId(org);
								accumulateIfNotPresent(rxnjson, "nonauth_sequences", "placeholder for AA seq + Accession Code: get_seq(" + ec + "," + orgname + ")");
							}
						}
						// Set<String> references = db.getReferences(dbID);
						// if (references != null) {
						// 	reactionTooltip += " references: " + references.size();
						// 	rxnjson.put("pubmed_refs", references.size());
						// }
						if (dbID > 200000L) {
							color = "#ff00ff";
						} else if (dbID > 100000L) {
							color = "#8888ff";
						} else if (color == null && dbID < 0L) {
							color = "#ff0000";
						}
					} else if (infos != null) {
						for (String info : infos) {
							reactionTooltip += " " + info + " ";
						}
					}
					reactionTooltip += ") ";
				}
				if (color == null) color = "#000000";
				Object reactionLabel = reactionLabels.get(firstReactionID);
				if (reactionLabel == null) reactionLabel = hyperedges.get(key).size();
	 			dotf.write(toReactionID(reactionIDs) + "[label=\"" + 
						reactionLabel + "\" " +
						"tooltip=\"reaction " + reactionTooltip + "\" " + 
						"color=\"" + color + "\" " + 
						"shape=\"" + reactionShape + "\"" +
						"width=.5]\n");
	 			
	 			rxnjson.put("ids", reactionIDs);
	 			rxnObjs.put(reactionIDs, rxnjson);
			}
			
			System.out.println("writeDot: Chemicals: " + chemicals.size());
			
			
			
			//Write edges
			int edges = 0;
			for (P<Set<N>, Set<N>> key : hyperedges.keySet()) {
				String reactionIDs = setToSimpleString(hyperedges.get(key));
				Set<N> reactants = key.fst();
				String arrowType = "arrowhead=\"none\"";
	
				JSONObject substrjson = new JSONObject();
				String edgeLabel = "\"";
				Set<String> edgeLabels = new HashSet<String>();
				for (E reactionID : hyperedges.get(key)) {
					String names = "";
					Set<N> reactantCofactors = reactionReactantCofactors.get(reactionID);
					if (reactantCofactors == null) continue;
					for (N chemicalID : reactantCofactors) {
						if (myIdType == IdType.DB_ID) {
							Long dbId = (Long) chemicalID;
							String name = db.getShortestName(dbId);
							names += name + " ";
						} else {
							names += chemicalID + " ";
						}
					}
					edgeLabels.add(names);
				}
				for (String names : edgeLabels) {
					edgeLabel += "(" + names + ")";
					substrjson.accumulate("cofactors", names);
				}
				edgeLabel += "\"";
				String reactantEdgeColor = "color=\"green\"";
				String edgeParams = " [label=" + edgeLabel + " " + arrowType + " " + reactantEdgeColor + "]";
				if (reactants.size() == 0) System.out.println("no reactants");
				for (N reactantID : reactants) {
					if (compoundsToSplit.contains(reactantID))
						dotf.write("\t" + toChemicalID(reactantID + "_" + reactionIDs) + "->" + 
								toReactionID(reactionIDs) + edgeParams + ";\n");
					else
						dotf.write("\t" + toChemicalID(reactantID.toString()) + "->" + 
								toReactionID(reactionIDs) + edgeParams + ";\n");
					edges++;
					substrjson.accumulate("chemicals", reactantID.toString());
				}
	
				JSONObject prodjson = new JSONObject();
				edgeLabels = new HashSet<String>();
				edgeLabel = "\"";
				for (E reactionID : hyperedges.get(key)) {
					String names = "";
					Set<N> productCofactors = reactionProductCofactors.get(reactionID);
					if (productCofactors == null) continue;
					for (N chemicalID : productCofactors) {
						if (myIdType == IdType.DB_ID) {
							Long dbId = (Long) chemicalID;
							String name = db.getShortestName(dbId);
							names += name + " ";
						} else {
							names += chemicalID + " ";
						}
					}
					edgeLabels.add(names);
				}
				for (String names : edgeLabels) {
					edgeLabel += "(" + names + ")";
					prodjson.accumulate("cofactors", names);
				}
				edgeLabel += "\"";
				edgeParams = " [label=" + edgeLabel + "]";
				Set<N> products = key.snd();
				for (N productID : products) {
					dotf.write("\t" + toReactionID(reactionIDs) + "->" + 
							toChemicalID(productID.toString()) + edgeParams + ";\n");
					edges++;
					prodjson.accumulate("chemicals", productID.toString());
				}
	
				JSONObject rxnjson = rxnObjs.get(reactionIDs);
				rxnjson.put("substrates", substrjson);
				rxnjson.put("products", prodjson);
			}

			for (JSONObject rxnjson : rxnObjs.values()) 
				cascade.accumulate("rxns", rxnjson);
			
			dotf.write("}");
			dotf.close();
			System.out.println("writeDot: Edges: " + edges);
			cascade.put("get_seq", "function get_seq(ecnum, organism) { return 'http://brenda-enzymes.org/sequences/index.php4?sort=&restricted_to_organism_group=&f[TaxTree_ID_min]=0&f[TaxTree_ID_max]=0&f[stype_seq]=2&f[seq]=&f[limit_range]=10&f[stype_recom_name]=2&f[recom_name]=&f[stype_ec]=1&f[ec]=' + ecnum + '&f[stype_accession_code]=2&f[accession_code]=&f[stype_organism]=2&f[organism]=' + encodeURIComponent(organism) + '&f[stype_no_of_aa]=1&f[no_of_aa]=&f[stype_molecular_weight]=1&f[molecular_weight]=&f[stype_no_tmhs]=1&f[no_tmhs]=&Search=Search&f[limit_start]=0&f[nav]=&f[sort]='; }");
			System.out.println("CASCADE:\n" + cascade.toString(4 /*4 spaces of indent to each level*/));

		} catch (JSONException e) {
			System.out.println("Failed in JSON creation. Aborting writeDOT: " + e.getMessage());
		}
	}
	
	private void accumulateIfNotPresent(JSONObject json, String arraykey, String val) throws JSONException {
		if (json.has(arraykey) && !json.isNull(arraykey)) {
			Object keyassoc = json.get(arraykey);
			if (keyassoc instanceof String && val.equals(keyassoc)) { // if value is not array, and is equal to val	
				return; // i.e., don't accumulate already present val
			}
			if (keyassoc instanceof org.json.JSONArray) { // if value is array, check each element for ==val
				JSONArray arr = (JSONArray)keyassoc;
				for (int i = 0; i < arr.length(); i++) 
					if (val.equals(arr.getString(i)))
						return; // i.e., don't accumulate already present val
			}
		}

		json.accumulate(arraykey, val);
	}

	private List<String> extractBRENDAOrgNames(String s) {
		List<String> orgs = new ArrayList<String>();
		int st = s.indexOf('{'), en = s.indexOf('}');
		if (st == -1 || en == -1 || (en - st) <= 1)
			return orgs;
		for (String cand : s.substring(st+1, en).split(","))
			orgs.add(cand.trim());
		return orgs;
	}

	/**
	 * There are a lot of reactions going from A -> B where A and B are the main
	 * reactant and product. This method buckets all reactions with the same 
	 * set of reactants and products into the same bucket.
	 * 
	 * @return
	 */
	public SetBuckets<P<Set<N>, Set<N>>, E> simplifyReactions() {
		Set<N> cofactors = new HashSet<N>();
		SetBuckets<E, N> reactionReactantCofactors = new SetBuckets<E, N>();
		SetBuckets<E, N> reactionProductCofactors = new SetBuckets<E, N>();
		SetBuckets<E, N> reactionMainReactants = new SetBuckets<E, N>();
		SetBuckets<E, N> reactionMainProducts = new SetBuckets<E, N>();
		Set<N> chemicalIDs = new HashSet<N>();
		
		return simplifyReactions(
				cofactors, reactionReactantCofactors, reactionProductCofactors,
				reactionMainReactants, reactionMainProducts, chemicalIDs);
		
	}

	public SetBuckets<P<Set<N>, Set<N>>, E> simplifyReactions(
			Set<N> cofactors, SetBuckets<E, N> reactionReactantCofactors,
			SetBuckets<E, N> reactionProductCofactors,
			SetBuckets<E, N> reactionMainReactants,
			SetBuckets<E, N> reactionMainProducts, Set<N> chemicalIDs) {
		// Simplify graph by adding constraint of 1 hyperedge between two sets of "main" chemicals
		// involved in the reaction
		SetBuckets<P<Set<N>, Set<N>>, E> hyperedges = 
				new SetBuckets<P<Set<N>, Set<N>>, E>();
		
		for (N cofactorChemical : initialSet) {
			cofactors.add(cofactorChemical);
		}
		
		for (E reactionID : reactions) {
			for (N r : reactionReactants.get(reactionID)) {
				if (!cofactors.contains(r)) 
					reactionMainReactants.put(reactionID, r);
				else
					reactionReactantCofactors.put(reactionID, r);
			}
			Set<N> reactants = reactionMainReactants.get(reactionID);
			if (reactants == null) {
				reactionMainReactants.putAll(reactionID, reactionReactantCofactors.get(reactionID));
				reactionReactantCofactors.clear(reactionID);
			}
			
			for (N p : reactionProducts.get(reactionID)) {
				if (!cofactors.contains(p)) 
					reactionMainProducts.put(reactionID, p);
				else
					reactionProductCofactors.put(reactionID, p);
			}
			Set<N> products = reactionMainProducts.get(reactionID);
			if (products == null) {
				reactionMainProducts.putAll(reactionID, reactionProductCofactors.get(reactionID));
				reactionProductCofactors.clear(reactionID);
			}
		}
		
		for (E reactionID : reactions) {
			Set<N> reactants = reactionMainReactants.get(reactionID);
			chemicalIDs.addAll(reactants);
			Set<N> products = reactionMainProducts.get(reactionID);
			chemicalIDs.addAll(products);
			hyperedges.put(new P<Set<N>, Set<N>>(
					reactants,
					products),
					reactionID);
		}
		return hyperedges;
	}
	
	private String toChemicalID(String string) { return "\"c" + string + "\""; }
	private String toReactionID(String s) { return "\"r" + s + "\""; }
	
	/**
	 * Each element of the list will be displayed as its own row in a table.
	 * @param id
	 * @param info
	 */
	public void addChemicalInfo(N id, String info) {
		if (chemicalInfo.get(id) == null) {
			chemicalInfo.put(id, new ArrayList<String>());
		}
		chemicalInfo.get(id).add(info);
	}

	/**
	 * @param id
	 * @param color
	 */
	public void addChemicalColor(N id, String color) {
		chemicalColor.put(id, color);
	}
	
	/**
	 * @param id
	 * @param image location
	 */
	public void addChemicalImage(N id, String image) {
		chemicalImage.put(id, image);
	}

	/**
	 * @param id
	 * @param info
	 */
	public void addReactionInfo(E id, String info) {
		if (reactionInfo.get(id) == null) {
			reactionInfo.put(id, new ArrayList<String>());
		}
		reactionInfo.get(id).add(info);
	}
		
	/**
	 * Given a list of edges and a set of nodes,
	 * return the subset of edges that are applicable.
	 * @param edges
	 * @param obtained
	 * @return
	 */
	public Set<E> filterReactions(Set<E> edges, Set<N> obtained, N nodeToNotUse) {
		Set<E> result = new HashSet<E>();
		if (edges == null) return result;
		for (E edge : edges) {
			Set<N> required = getReactants(edge);
			boolean feasible = true;
			if (nodeToNotUse != null && required.contains(nodeToNotUse)) feasible = false;
			for (N r : required) {
				if (!obtained.contains(r))
					feasible = false;
			}
			
			if (allowedProducts != null) {
				Set<N> products = getProducts(edge);
				for (N p : products) {
					if (!allowedProducts.contains(p)) {
						feasible = false;
					}
				}
			}
			
			if (feasible) {
				result.add(edge);
			}
		}
		return result;
	}
	
	public Set<E> filterReactions(Set<E> edges, Set<N> obtained) {
		return filterReactions(edges, obtained, null);
	}
	
	/**
	 * Below is a list of hypergraph transformations. 
	 * They all return new hypergraphs.
	 */
	
	/**
	 * Remove all edges irrelevant to producing target, too many edges,
	 * and limit graph to nodeLimit number of nodes.
	 * 
	 * Set inEdgeThreshold and nodeLimit to -1 if no threshold is desired.
	 */
	public ReactionsHypergraph<N, E> restrictGraph(
			N target, 
			int inEdgeThreshold, 
			int nodeLimit) {
		ReactionsHypergraph<N, E> result = new ReactionsHypergraph<N, E>();
		Set<N> obtained = new HashSet<N>();
		Set<N> initialSet = getInitialSet();
    	if (initialSet.contains(target)) 
    		initialSet.remove(target);
    	obtained.addAll(initialSet);
    	LinkedList<N> fringe = new LinkedList<N>();
    	fringe.add(target);
    	while(!fringe.isEmpty()) {
    		if (nodeLimit > 0 && result.getNumChemicals() > nodeLimit) break;
    		N product = fringe.pop();
    		int skippedReactions = 0;
    		if (obtained.contains(product)) continue;
    		obtained.add(product);
    		Set<E> reactions = getReactionsTo(product);
    		if (reactions == null) continue;
    		/*if (inEdgeThreshold > 0 && reactions.size() > inEdgeThreshold) {
    			result.addChemicalInfo(product, "too many reactions: " + reactions.size());
    			continue;
    		}*/
    		int numReactions = 0;
    		for (E reaction : reactions) {
        		Set<N> reactants = getReactants(reaction);
        		if (nodeLimit > 0 && 
        				result.getNumChemicals() + reactants.size() > nodeLimit ||
        				(numReactions > inEdgeThreshold && inEdgeThreshold > 0)) {
        			skippedReactions++;
        			continue;
        		}
        		numReactions++;
    			result.addReaction(reaction, reactants, getProducts(reaction));
    			fringe.addAll(reactants);
    		}
    		if (skippedReactions > 0) {
    			result.addChemicalInfo(product, "skipped reactions: " + skippedReactions);
    			continue;
    		}
    	}	
    	result.setInitialSet(new HashSet<N>(initialSet));
		return result;
	}
	
	/**
	 * This method is slow and untested.
	 * Idea:
	 *   Include reactions that occur only without using any of its products.
	 *   1) Find set of reactions that can be applied without node
	 *   2) Any reaction to node that doesn't produce anything new can be removed
	 *   
	 * @param g
	 * @return
	 */
	public ReactionsHypergraph<N, E> cycleBreak() {
		System.out.println("CB: Chemicals: " + getNumChemicals());
		System.out.println("CB: Reactions: " + getNumReactions());
		
		Set<N> nodes = getChemicals();
		Set<N> initialSet = getInitialSet();
		Set<E> toRemove = new HashSet<E>(); //set of edges to remove
		int i = 0;
		Long starttime = System.currentTimeMillis();
		for (N nodeToNotUse : nodes) {
			if (initialSet.contains(nodeToNotUse)) continue;
			
			//find all edges applicable w/o nodeToNotUse
			Set<N> obtained = new HashSet<N>();
			Set<E> applied = new HashSet<E>();
			obtained.addAll(initialSet);
			Set<N> newNodes = initialSet;
			while (!newNodes.isEmpty()) {
				Set<E> edges = new HashSet<E>();
				for (N n : newNodes) {
					if (n.equals(nodeToNotUse)) continue;
					edges.addAll(filterReactions(getReactionsFrom(n), obtained));
				}
				newNodes = new HashSet<N>();
				for(E e : edges) {
					if (applied.contains(e)) continue;
					applied.add(e);
					for (N p : getProducts(e)) {
						if (!obtained.contains(p)) {
							obtained.add(p);
							newNodes.add(p);
						}
					}
				}
			}
			
			//remove unapplied edges to node that wasn't used
			//and does not produce any new chemicals
			Set<E> unused = getReactionsTo(nodeToNotUse);
			if (unused != null) {
				for (E e : unused) {
					if (!applied.contains(e)) {
						boolean shouldRemove = true;
						Set<N> products = getProducts(e);
						for (N p : products) {
							if (!obtained.contains(p)) shouldRemove = false;
						}
						if (shouldRemove)
							toRemove.add(e);
					}
				}
			}
			i++;
			if (i % 100 == 0) {
				System.out.println("CB: 100 nodes done: " + 
						(System.currentTimeMillis() - starttime) + "ms");
				starttime = System.currentTimeMillis();
			}
		}
		
		ReactionsHypergraph<N, E> result = new ReactionsHypergraph<N, E>();
		Set<E> edges = getReactions();
		for (E e : edges) {
			if (!toRemove.contains(e))
				result.addReaction(e, getReactants(e), getProducts(e));
		}
		for (N n : nodes) {
			if (!result.getChemicals().contains(n)) {
				System.out.println("CB: lost node " + n);
			}
		}
		System.out.println("CB: Chemicals (no cycle): " + result.getNumChemicals());
		System.out.println("CB: Reactions (no cycle): " + result.getNumReactions());
		result.setInitialSet(new HashSet<N>(initialSet));
		return result;
	}
	

	
	/**
	 * Give a reachable graph given this graph's initial set.
	 * @return
	 */
	public ReactionsHypergraph<N, E> reachableGraph() {
		return verifyPath(null);
	}
	
	/**
	 * Verifies that the given graph has all the edges needed to produce the target
	 * and returns an ordering of the edges that achieves it.
	 * Does not add any edge dependent on the target
	 * 
	 * If target is null, simply adds all applicable reactions given initial set.
	 * 
	 * @param g
	 * @param target
	 * @return
	 */
	public ReactionsHyperpath<N, E> verifyPath(N target) {
		Set<N> obtained = new HashSet<N>();
		Set<E> applied = new HashSet<E>();
		ReactionsHyperpath<N, E> result = new ReactionsHyperpath<N, E>();
		Set<N> initialSet = getInitialSet();
		obtained.addAll(initialSet);
		Set<N> newNodes = initialSet;
		while (!newNodes.isEmpty()) {
			Set<N> prevNodes = newNodes;
			newNodes = new HashSet<N>();
			for (N n : prevNodes) {
				Set<E> edges = filterReactions(getReactionsFrom(n), obtained);
				for(E e : edges) {
					Set<N> required = getReactants(e);
					if (required != null && required.contains(target)) continue;
					if (applied.contains(e)) continue;
					applied.add(e);
					Set<N> products = getProducts(e);
					result.addReaction(e, getReactants(e), products);
					result.appendReaction(e);
					for (N p : products) {
						if (!obtained.contains(p)) newNodes.add(p);
						obtained.add(p);
					}
				}
			}
		}
		if (obtained.contains(target) || target == null) {
			result.setInitialSet(new HashSet<N>(initialSet));
			return result;
		}
		return null;
	}
	
	public ReactionsHypergraph<N, E> reverse() {
		ReactionsHypergraph<N, E> result = new ReactionsHypergraph<N, E>();
		Set<E> reactions = getReactions();
		for (E reaction : reactions) {
			result.addReaction(reaction, getProducts(reaction), getReactants(reaction));
		}
		result.setInitialSet(getInitialSet());
		return result;
	}
	
	/**
	 * End of hypergraph transformations.
	 */
	
	
	/*
	 * Example usage with PathBFS. 
	 * Look at PathBFS's getGraphTo to see how a ReactionsHypergraph is created.
	 */
	public static void main(String args[]) {
		MongoDB db = new MongoDB();
		List<Chemical> natives = db.getNativeMetaboliteChems();
		Set<Long> nativeIDs = new HashSet<Long>();
		for (Chemical n : natives) nativeIDs.add(n.getUuid());
		PathBFS pathFinder = new PathBFS(db, nativeIDs);
		pathFinder.initTree();
		ReactionsHypergraph graph = pathFinder.getGraphTo(4271L, 10);
		graph.setIdTypeDB_ID();
		try {
			graph.writeDOT("pathsToFarnesene.dot", db);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
