package act.server.GameServer;


import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.util.ajax.JSON;

import act.verify.Verifier;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.ggasoftware.indigo.IndigoRenderer;
import com.google.gwt.thirdparty.guava.common.net.InetAddresses;

import act.client.CommandLineRun;
import act.server.ActAdminServiceImpl;
import act.server.EnumPath.OperatorSet;
import act.server.EnumPath.OperatorSet.OpID;
import act.server.Molecules.CRO;
import act.server.Molecules.RO;
import act.server.Molecules.RxnTx;
import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.MongoDBPaths;
import act.server.Search.Counter;
import act.server.Search.HypergraphEnumerator;
import act.server.Search.PathBFS;
import act.server.Search.ReactionsHypergraph;
import act.server.Search.ReactionsHypergraphDecompose;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.ReactionType;
import act.shared.SimplifiedReactionNetwork;
import act.shared.helpers.P;

public class PathwayGameServer {
	private MongoDB db;
	private SimplifiedReactionNetwork srn;
	private OperatorSet operatorSet;
	private Server server;
	private ServerLog log;
	private ReactionsHypergraph<Long, Long> completeReachable;
	private ReactionsHypergraphDecompose<Long, Long> decomposable;
	private int port;
	private String logPath = "server.log";
	
	private String graphContainerTop, graphContainerBottom;
	
	public PathwayGameServer(MongoDBPaths mongoDB, int gamePort) {
		this.db = mongoDB;
		this.port = gamePort;
		
		//set up handlers
		HandlerList handlers = new HandlerList();
		ResourceHandler resource_handler = new ResourceHandler();
		resource_handler.setWelcomeFiles(new String[] { "index.htm" });
		resource_handler.setResourceBase("PathwayGameWeb");
		handlers.addHandler(resource_handler);
		handlers.addHandler(new GameHandler());
		
		Server s = new Server(gamePort);
		s.setHandlers(handlers.getHandlers());
		this.server = s;
		
		Set<Long> metabolites = db.getNativeIDs();
		PathBFS pathFinder = new PathBFS(db, metabolites);
		pathFinder.initTree();
	    completeReachable = pathFinder.getGraph();
	    
		decomposable = new ReactionsHypergraphDecompose<Long, Long>();
		decomposable.setIdTypeDB_ID();
		decomposable.setInitialSet(metabolites);
		List<Long> reactionIDs = db.getAllReactionUUIDs();
		for (Long reactionID : reactionIDs) {
			Reaction reaction = db.getReactionFromUUID(reactionID);
			if (reaction.isReversible() == 1) {
				decomposable.addReaction(reactionID, reaction.getSubstrates(), reaction.getProducts());
			}
			reaction.reverse();
			decomposable.addReaction(Reaction.reverseID(reactionID), reaction.getSubstrates(), reaction.getProducts());
		}
		decomposable.setReachables(completeReachable.getChemicals());
		decomposable = decomposable.getDecompositions(null);
		decomposable.setIdTypeDB_ID();
		decomposable.setInitialSet(metabolites);

		File file = new File("SVGGraphs");
		file.mkdirs();
		
		graphContainerTop = getFileContents("PathwayGameWeb/graphvizJS/aboveGraph.html");
		graphContainerBottom = getFileContents("PathwayGameWeb/graphvizJS/belowGraph.html");
		log = new ServerLog(logPath);
		log.write("", "\n");
		log.write("server", "initialized");
		System.out.println("Initialized server.");
	}
	
	private String getFileContents(String path) {
		String line, result = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(path));
			while ((line = br.readLine()) != null)
				result += line + "\n";
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	public void startServer() {
		try {
			server.start();
			System.out.println("Server starting on port " + port);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static String formatIP(byte[] ip) {
		String result = "";
		for (byte b : ip) {
			int temp = b & 0xFF;
			result += temp + 0;
			result += ".";
		}
		return result.substring(0, result.length() - 1);
	}
	
	//http://stackoverflow.com/questions/4678797/how-do-i-get-the-remote-address-of-a-client-in-servlet
	public static String getClientIpAddr(HttpServletRequest request) {  
        String ip = request.getHeader("X-Forwarded-For");  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("Proxy-Client-IP");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("WL-Proxy-Client-IP");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("HTTP_CLIENT_IP");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");  
        }  
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {  
            ip = request.getRemoteAddr();  
        }  
        int cutoff = ip.indexOf("%");
        if (cutoff != -1)
        	ip = ip.substring(0, cutoff);
        return formatIP(InetAddresses.forString(ip).getAddress()); 
    }  
	
	public class GameHandler extends ServletHandler {
		private Server server;
		
		@Override
		public boolean isFailed() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isRunning() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isStarted() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isStarting() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isStopped() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isStopping() {
			// TODO Auto-generated method stub
			return false;
		}
		
		@Override
		public void destroy() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public Server getServer() {
			return server;
		}
		
		private void outputErrorMessage(HttpServletResponse response, String msg) {
			System.out.println("Error message: " + msg);
			try {
				response.getWriter().println("<html><body>" +
						msg +
						"</body></html>"
						);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		private String resolveChemicalToInchi(String query) {
		//http://stackoverflow.com/questions/2586975/how-to-use-curl-in-java
			String result = null;
			BufferedReader reader = null;
			try {
				//doesn't handle spaces: URL url = new URL("http://cactus.nci.nih.gov/chemical/structure/"+query+"/stdinchi");
				URI uri = new URI(
					    "http", 
					    "cactus.nci.nih.gov", 
					    "/chemical/structure/"+query+"/stdinchi",
					    null);
				URL url = uri.toURL();
				reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
				result = reader.readLine();
				while (result != null && !result.startsWith("InChI")) {
					System.out.println(result);
					result = reader.readLine();
				}
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				if (reader != null) try { reader.close(); } catch (IOException ignore) {}
			}
			return result;
		}
		
		private Chemical getChemical(HttpServletRequest request) {
			Chemical chemical = null;
			String chemIDString = request.getParameter("chemid");
			String name = request.getParameter("name");
			String inchi = request.getParameter("inchi");
			if (name != null) {
				long id = db.getChemicalIDFromName(name);
				chemical = db.getChemicalFromChemicalUUID(id);
				if (chemical == null && inchi == null)
					inchi = resolveChemicalToInchi(name);
			}
			if (inchi != null) {
				chemical = db.getChemicalFromInChI(inchi);
				if (chemical == null) {
					try {
						inchi = CommandLineRun.consistentInChI(inchi, "Pathway Game Server");
						chemical = db.getChemicalFromInChI(inchi);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			if (chemIDString != null) {
				try {
					chemical = db.getChemicalFromChemicalUUID(Long.parseLong(chemIDString));
				} catch(NumberFormatException e) {
					//ignore
				}
			}
			return chemical;
		}

		// TODO: handle errors better by replying with a message
		@Override
		public void handle(String target, HttpServletRequest request,
				HttpServletResponse response, int dispatch) throws IOException,
				ServletException {
			if (target == "/")
				return;
			if (target.equals("/getChem")) {
				responseStart(response);
				Chemical chemical = getChemical(request);
				if (chemical == null)
					outputErrorMessage(response, "Cannot find chemical.");
				else
					response.getWriter().println(getChemicalJSON(chemical.getUuid()));
		        
			} else if (target.equals("/getChemImage")) {
				getChemImage(request, response);
			} else if (target.equals("/getConcreteEdges") || 
					target.equals("/getAbstractEdges")) {
				/*
				 * Response is of the form:
				 * {
				 *     "source": {... chemical json object ...},
				 *     "children": [
				 *         {... chemical json object ...},
				 *         {... chemical json object ...}, ...
				 *     ]
				 * } 
				 */
				String chemIDString = request.getParameter("chemid");
				if (chemIDString == null) return;
				Long chemID = Long.parseLong(chemIDString);
				roExpandNode(chemID);
				responseStart(response);
				response.getWriter().println("{");
				response.getWriter().println("\"source\": ");
				response.getWriter().println(getChemicalJSON(chemID));
				
				Set<Long> products = srn.getProducts(chemID);
				List<String> productJSONs = new ArrayList<String>();
				for (Long product : products) {
					String productJSON = getChemicalJSON(product);
					if (productJSON != null) productJSONs.add(productJSON);
				}
				response.getWriter().println(", ");
				response.getWriter().println("\"children\": ");
				respondChemicalJSONArray(response, productJSONs);
				response.getWriter().println("}");
				
			} else if (target.equals("/getReaction")) {
				getReaction(request, response);
			} else if (target.equals("/getAbstractEdges")) {
				// TODO: will probably need to add some synchronization
				// as this will add new chemicals and edges
				// TO BE COMPLETED
				String chemIDString = request.getParameter("chemID");
				if (chemIDString == null) return;
				Long chemID = Long.parseLong(chemIDString);
				roExpandNode(chemID);
				
				
			} else if (target.equals("/getMetabolites")) {
				responseStart(response);
				List<Chemical> metabolites = db.getNativeMetaboliteChems();

				List<String> metaboliteJSONs = new ArrayList<String>();
				for (Chemical m : metabolites) {
					String metaboliteJSON = getChemicalJSON(m);
					if (metaboliteJSON != null) metaboliteJSONs.add(metaboliteJSON);
				}
				respondChemicalJSONArray(response, metaboliteJSONs);
			} else if (target.equals("/getSequences")) {
				Long orgID;
				String ecnum = request.getParameter("ecnum");
				String orgIDString = request.getParameter("orgID");
				if (orgIDString == null || ecnum == null) {
					return;
				}
				try {
					orgID = Long.parseLong(orgIDString);
				} catch (Exception e) {
					//bad id
					return;
				}
				
				List<String> sequences = new ArrayList<String>(); 

        System.out.println("db.sequences is now deprecated. We should use the rxn->seq_ref map");
        System.out.println("seq_ref links into db.seq; which is populated using map_seq install");
        System.exit(-1);

				JSON json = new JSON();
				String jsonSequences = json.toJSON(sequences);
				responseStart(response);
				response.getWriter().println(jsonSequences);
				System.out.println(jsonSequences);
			} else if (target.equals("/verifySequence")) {
				String sequence = request.getParameter("sequence");
				responseStart(response);
				
				if (sequence == null) {
					response.getWriter().write("<html>");
					response.getWriter().write("<body>");
					response.getWriter().write("<form method=\"post\" action=\"verifySequence\">");
					response.getWriter().write("<input type=submit value=\"Submit Sequence\"><br />");
					response.getWriter().write("<textarea cols=80 rows=10 name=\"sequence\"> Place sequence here " +
							"</textarea>");

					response.getWriter().write("</form>");
					response.getWriter().write("</body></html>");
				}
				
				if (sequence != null) {
					try {
						// Generate FASTA file
						Long time = System.currentTimeMillis();
						String filename = "verifySeq" + time + ".fa";

						PrintWriter fasta = new PrintWriter(filename,"UTF-8");
						fasta.write(">verify sequence\n");
						int length = sequence.length();
						for (int i = 0; i < length; i+=80) {
							int end = length;
							if (length > i + 80) end = i + 80;
							fasta.write(sequence.substring(i, end));
							fasta.write("\n");
						}
						fasta.close();
						
						File input = new File(filename);
						String path = input.getPath();
						String syntaxCheckerDir = "/Users/production/SyntaxChecker/";
						String syntaxChecker = syntaxCheckerDir + "SyntaxChecker.py";
						ProcessBuilder pb = new ProcessBuilder("python", syntaxChecker,
								path);
						pb.directory(new File(syntaxCheckerDir));
						Process proc = pb.start();
						InputStream is = proc.getInputStream();
						InputStreamReader isr = new InputStreamReader(is);
						BufferedReader br = new BufferedReader(isr);
						String line, jsonResult = "";
						if (br.readLine() != null) { //hack to error in first line
							while ((line = br.readLine()) != null) {
								jsonResult += line + "\n";
							}
						}
						try {
							proc.waitFor();
						} catch (Exception e) {
							e.printStackTrace();
						}
						System.out.println(proc.exitValue());
						System.out.println(jsonResult);
						// Parse result
						responseStart(response);
						response.getWriter().println(jsonResult);
						
						br.close();
						isr.close();
						is.close();
						input.delete();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} else if (target.equals("/verify")) {
				String ids = request.getParameter("chemids");
				String ecnum = request.getParameter("ecnum");
				String orgIDString = request.getParameter("orgid");
				if (ids == null || ecnum == null || orgIDString == null) return;
				
				String[] chemicalIDsArray = ids.split("\\.");
				Long orgID = null;
				List<Long> substrateIDs = new ArrayList<Long>();
				try {
					for (int i = 0; i < chemicalIDsArray.length; i++) {
						substrateIDs.add(Long.parseLong(chemicalIDsArray[i]));
					}
					orgID = Long.parseLong(orgIDString);
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				
				Verifier verifier = new Verifier(db);
				Verifier.VerifierResult result = verifier.verifyReaction(substrateIDs, ecnum, orgID);
				JSON json = new JSON();
				String resultJSON = json.toJSON(result.getProperties());
				response.getWriter().write(resultJSON);
				
			} else if (target.equals("/getGraph")) {
				Long targetID = Long.parseLong(request.getParameter("target"));
				if (targetID == null) return;
				
				ReactionsHypergraph<Long, Long> hypergraph = completeReachable.restrictGraph( 
						targetID, 1000, 100);
				String edgeJSON = "{ \"edges\" : {";
				int count = 0;
				Set<String> vertexSet = new HashSet<String>();
				for (P<Long, Long> i : hypergraph.getReactionReactantEdges()) {
					edgeJSON = edgeJSON +"\"edge" + i.fst() + "r_" + i.snd() + "c\":\"" + i.fst() + "r," + i.snd() + "c\"";
					count++;
					vertexSet.add("\"" + i.fst() + "r\"");
					vertexSet.add("\"" + i.snd() + "c\"");
					edgeJSON += ", ";
				}
				
				count = 0;
				for (P<Long, Long> i : hypergraph.getProductReactionEdges()) {
					edgeJSON = edgeJSON +"\"edge" + i.fst() + "c_" + i.snd() + "r\":\"" + i.fst() + "c," + i.snd() + "r\"";
					count++;
					vertexSet.add("\"" + i.fst() + "c" + "\"");
					vertexSet.add("\"" + i.snd() + "r" + "\"");
					if (count!=hypergraph.getProductReactionEdges().size()) {
						edgeJSON += ", ";
					}
				}
				
				count = 0;
				edgeJSON = edgeJSON + "}, \"vertices\" : [";
				for (String i : vertexSet) {
					edgeJSON = edgeJSON + i;
					count++;
					if (count != vertexSet.size()) {
						edgeJSON += ", ";
					}
				}
				edgeJSON = edgeJSON + "]}"; 
				response.getWriter().write(edgeJSON);
			} else if (target.equals("/getpaths")) {
				// loading files here for easier debugging
				graphContainerTop = getFileContents("PathwayGameWeb/graphvizJS/aboveGraph.html");
				graphContainerBottom = getFileContents("PathwayGameWeb/graphvizJS/belowGraph.html");
				getCascade(request, response);
			} else if (target.equals("/getchem")) {
				Long id = null;
				Chemical chemical = getChemical(request);
				if (chemical != null) {
					id = chemical.getUuid();
				}

				responseStart(response);
				if (id == null)
					outputErrorMessage(response, "Cannot find chemical.");
				else
					response.getWriter().write(db.getChemicalDBJSON(id));
				
			} else if (target.equals("/adminstatistics")) {
				getStatisticsTable(request, response);
			} else return;
			
			Request base_request = (request instanceof Request) ? (Request)request:HttpConnection.getCurrentConnection().getRequest();
	        base_request.setHandled(true);	
		}

		private void getChemImage(HttpServletRequest request,
				HttpServletResponse response) throws IOException {
			Indigo indigo = new Indigo();
			IndigoRenderer renderer = new IndigoRenderer(indigo);
			indigo.setOption("render-output-format", "svg");
			Chemical c = getChemical(request);
			responseStart(response);
			if (c == null || c.getSmiles() == null) {
				outputErrorMessage(response, "Cannot find chemical.");
				return;
			}
			String smiles = c.getSmiles();
			response.setContentType( "image/svg+xml" );
			byte[] svg = renderer.renderToBuffer(indigo.loadMolecule(smiles));
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write(svg,0,svg.length);
			response.getWriter().write(baos.toString());
		}

		private void getReaction(HttpServletRequest request,
				HttpServletResponse response) throws IOException {
			String reactionIDString = request.getParameter("rxnid");
			responseStart(response);
			if (reactionIDString == null) {
				outputErrorMessage(response, "Please provide rxnid.");
				return;
			}
			Long reactionID = Long.parseLong(reactionIDString);
			Reaction reaction = db.getReactionFromUUID(reactionID);
			if (reaction == null) {
				outputErrorMessage(response, "Cannot find reaction.");
				return;
			}
			response.getWriter().write(getReactionJSON(reaction));
		}

		
		private void getCascade(HttpServletRequest request,
				HttpServletResponse response) throws IOException {

			boolean interactive = true; //use graph container for interactive graph
			boolean cacheSVGs = true; //existing svgs won't be regenerated
			Long id = null;
			int thresh = 10;
			Chemical chemical = getChemical(request);
			if (chemical != null) {
				id = chemical.getUuid();
			}
			responseStart(response);

			if (id == null) {
				outputErrorMessage(response, "Cannot find chemical.");
				log.write("nochemical", request.getQueryString() + "," + getClientIpAddr(request));
				return;
			} 
			
			boolean autoChooseThresh = true;
			String numParentsThresh = request.getParameter("thresh");
			if (numParentsThresh != null) {
				try {
					thresh = Integer.parseInt(numParentsThresh);
					autoChooseThresh = false;
				} catch (NumberFormatException e) {
					autoChooseThresh = true;
				}
			}

			String nodeLimitString = request.getParameter("nodeLimit");
			int nodeLimit = 30;
			if (nodeLimitString != null) {
				try {
					nodeLimit = Integer.parseInt(nodeLimitString);
				} catch (NumberFormatException e) {
					nodeLimit = 30;
				}
			}
			
			boolean decompose = false;
			String decomposeString = request.getParameter("decompose");
			if (decomposeString != null && !decomposeString.equals("false")) 
				decompose = true;
			
			//hard limit
			if (nodeLimit > 100) nodeLimit = 100; 

			request.getParameter("rankBy");
			String filename = "SVGGraphs/" + id + "_" + nodeLimit + "_" + thresh;
			if (decompose)
				filename += "_decompose";
			String dotFile = filename + ".dot";
			String svgFile = filename + ".svg";

			responseStart(response);
			response.setContentType("text/html;charset=utf-8");

			ReactionsHypergraph<Long, Long> g = null;
			if (decompose)
				g = decomposable.getDecompositions(id);
			else
				g = completeReachable.verifyPath(id);

			if (g == null || 0 == g.getNumReactions()) {
				outputErrorMessage(response, "No paths found for id=" + chemical.getUuid());
				log.write("nopaths", id + "," + getClientIpAddr(request));
				return;
			} 
			log.write("paths", id + "," + getClientIpAddr(request));
			
			g.removeFromInitialSet(id);
			if (autoChooseThresh) thresh = 15;
			File temp = new File(svgFile);
			if (!temp.exists() && cacheSVGs) {
				ReactionsHypergraph<Long, Long> restrictedGraph = g.restrictGraph(
						id, 
						thresh, 
						nodeLimit);

				//automatically choose a threshold based on how many nodes are desired
				while (autoChooseThresh && 
						restrictedGraph.getChemicals().size() < nodeLimit/4 && 
						thresh < 10000) {
					thresh *= 4;
					restrictedGraph = g.restrictGraph(
							id, 
							thresh, 
							nodeLimit);
				}
				if (decompose) 
					g = restrictedGraph.reverse();
				else
					g = restrictedGraph;
				g.setIdTypeDB_ID();
				Set<Long> chemicalNodes = g.getChemicals();
				for (Long c : chemicalNodes) {
					g.addChemicalColor(c, "#EAF2D3");
				}
				g.addChemicalColor(id, "#80FF80");
			}
			
			//TODO: this can be done better with a lock for each file
			synchronized (this) { 
				if (!temp.exists() && cacheSVGs) {
					g.writeDOT(dotFile, db);

					Runtime run = Runtime.getRuntime();
					Process pr = run.exec("/usr/local/bin/dot -Tsvg " + dotFile + " -o " + svgFile);
					try {
						pr.waitFor();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			if (interactive)
				response.getWriter().write(graphContainerTop);
			try{
				FileInputStream fstream = new FileInputStream(svgFile);
				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in));
				String line;
				if (interactive)
					br.readLine(); //remove !Doc line
				while ((line = br.readLine()) != null)   {
					if (line.startsWith("<svg")) {
						line = line.replaceFirst("width", "originalWidth");
						line = line.replaceFirst("height", "originalHeight");
						response.getWriter().write(line);
						break;
					}
				}
				while ((line = br.readLine()) != null)   {
					response.getWriter().write(line);
				}
				in.close();
			} catch (Exception e){
				System.err.println("Error: " + e.getMessage());
			}
			if (interactive)
				response.getWriter().write(graphContainerBottom);



		}

		private void roExpandNode(Long chemID) {
			Indigo indigo = new Indigo();
			IndigoInchi indigoInchi = new IndigoInchi(indigo);
			Chemical chemical = srn.getChemical(chemID);
			/* 
			 * Saurabh: RxnTx.expandChemical2AllProducts works over 
			 *                DotNotation(and really only over smiles not inchis)
			 *                so do the conversion before passing reactants to it
			 */
			// Don't do this:
			// List<String> reactants = new ArrayList<String>(); reactants.add(chemical.getInChI());
			// Instead do this:
			List<String> reactants = ActAdminServiceImpl.getSubstrateForROAppl(chemical.getInChI(), indigo, indigoInchi);
			
			HashMap<OpID, CRO> allOperators = operatorSet.getAllCROs();
			for(OpID id : allOperators.keySet()) {
				RO op = allOperators.get(id);
				List<List<String>> expanded = 
						RxnTx.expandChemical2AllProducts(
								reactants, 
								op, 
								indigo, 
								indigoInchi);
				System.out.println("applying" + id);
				for (List<String> productInChIs : expanded) {
					System.out.println("Paul: Double check this, now the enumeration returns a list of lists. Each inner list is a set of products out of one application. But since the ro can be applicable in multiple location on the molecule, we can have multiple of these lists.");
					for (String p : productInChIs) {
						Chemical product = srn.getChemical(p);
						if (product == null) {
							product = new Chemical(srn.getMinChemicalId());
							String productSMILES = indigoInchi.loadMolecule(p).smiles();
							product.setInchi(p);
							product.setSmiles(productSMILES);
						}
	
						srn.addChemicalObject(product);
						Long[] reactantIDs = { chemID };
						Long[] productIDs = { product.getUuid() };
						Reaction reaction = new Reaction(op.ID(), reactantIDs, productIDs, "", "");
						srn.addEdges(reaction, ReactionType.CRO,1.0);
					}
				}
			}
		}

		private void respondChemicalJSONArray(HttpServletResponse response,
				List<String> productJSONs) throws IOException {
			Iterator<String> it = productJSONs.iterator();
			response.getWriter().println("[");
			while(it.hasNext()) {
				response.getWriter().print(it.next());
				if (it.hasNext()) {
					response.getWriter().print(",");
				}
				response.getWriter().println();
			}
			response.getWriter().println("]");
		}

		/**
		 * See next method.
		 * @param chemID
		 * @return
		 */
		private String getChemicalJSON(Long chemID) {
			Chemical chemical = db.getChemicalFromChemicalUUID(chemID);
			return getChemicalJSON(chemical);
		}
		
		/**
		 * Returns the JSON for a chemical object. Example:
		 * {
		 *   "id": "10779",
		 *   "name": "butan-1-ol",
		 *   "SMILES": "CCCCO",
		 *   "InChI": "InChI=/some stuff/",
		 *   "PubID": "some pubchem id",
		 *   "isNative": "true if native metabolite"
		 * }
		 * @param chemical
		 * @return 
		 */
		private String getChemicalJSON(Chemical chemical) {
			String shortestName = chemical.getCanon();
			
			String smiles = chemical.getSmiles();
			String inchi = chemical.getInChI();
			Long pubchemID = chemical.getPubchemID();
			Boolean isNative = chemical.isNative();
			
			String json = "{"+ 
					"\"id\" : \"" + chemical.getUuid() + "\"," +
					"\"name\": \"" + shortestName + "\"," +
					"\"SMILES\": \"" + smiles + "\"," +
					"\"InChI\": \"" + inchi + "\"," + 
					"\"PubID\": \"" + pubchemID + "\"," +
					"\"isNative\": \"" + isNative + "\"" +
			"}";
			return json;
		}
		
		private String getReactionJSON(Reaction reaction) {
			String json = "{"+ 
					"\"id\" : \"" + reaction.getUUID() + "\"," +
					"\"desc\": \"" + reaction.getReactionName() + "\"," +
					"\"ecnum\": \"" + reaction.getECNum() + "\"" +
			"}";
			return json;
		}
		
		private String getHtmlRows(Map map, String col1Header, String col2Header) {
			String result = "<table>\n";
			result += "<tr><th>" + col1Header + "</th><th>" + col2Header + "</th></tr>\n";
			for (Object o : map.keySet()) {
				result += "<tr><td>" + o + "</td><td>" + map.get(o) + "</td></tr>\n";
			}
			result += "</table>";
			return result;
		}
		
		private String getHtmlRows(Map<String, List<Object>> foundPathsRows, List<String> colHeaders) {
			String result = "<table>\n";
			result += "<tr>";
			for (String colHeader : colHeaders) {
				result += "<th>" + colHeader + "</th>";
			}
			result += "</tr>\n";
			for (Object o : foundPathsRows.keySet()) {
				result += "<tr><td>" + o + "</td>";
				for (Object p : foundPathsRows.get(o)) {
					result += "<td>" + p + "</td>";
				}
				result += "</tr>\n";
			}
			result += "</table>";
			return result;
		}
		
		private void getStatisticsTable(HttpServletRequest request,
				HttpServletResponse response) throws IOException {
			Counter<String> foundPaths = new Counter<String>();
			Counter<String> noPaths = new Counter<String>();
			Counter<String> noChemical = new Counter<String>();
			Map<String, String> latestIP = new HashMap<String, String>();
			
			String line;
			try {
				BufferedReader br = new BufferedReader(new FileReader(logPath));
				while ((line = br.readLine()) != null) {
					String[] temp = line.split(",");
					if (line.startsWith("paths")) {
						foundPaths.inc(temp[2]);
						latestIP.put(temp[2], temp[3]);
					} else if (line.startsWith("nopaths")) {
						noPaths.inc(temp[2]);
						latestIP.put(temp[2], temp[3]);
					} else if (line.startsWith("nochemical")) {
						noChemical.inc(temp[2]);
						latestIP.put(temp[2], temp[3]);
					}
				}
				br.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			List<String> header = new ArrayList<String>();
			header.add("Id"); header.add("Name"); header.add("Count"); header.add("Latest IP");
			Map<String, List<Object>> foundPathsRows = new HashMap<String, List<Object>>();
			for (String stringId : foundPaths.keySet()) {
				Long id = Long.parseLong(stringId);
				String name = db.getShortestName(id);
				List<Object> row = new ArrayList<Object>();
				row.add("<a href=\"getpaths?chemid=" + id + "\">" + name + "</a>");
				row.add(foundPaths.get(stringId));
				row.add(latestIP.get(stringId));
				foundPathsRows.put(stringId, row);
				
			}
			
			Map<String, List<Object>> noPathsRows = new HashMap<String, List<Object>>();
			for (String stringId : noPaths.keySet()) {
				Long id = Long.parseLong(stringId);
				String name = db.getShortestName(id);
				List<Object> row = new ArrayList<Object>();
				row.add(name);
				row.add(noPaths.get(stringId));
				row.add(latestIP.get(stringId));
				noPathsRows.put(stringId, row);
			}
			
			responseStart(response);
			response.getWriter().write("<html><header><style type=\"text/css\"></style></header><body>");
			response.getWriter().write("<h2>Successful path searches<h2>");
			response.getWriter().write(getHtmlRows(foundPathsRows, header));
			response.getWriter().write("<h2>Unsuccessful path searches<h2>");
			response.getWriter().write(getHtmlRows(noPathsRows, header));
			response.getWriter().write("<h2>Queries with chemicals not found<h2>");
			response.getWriter().write(getHtmlRows(noChemical.getMap(), "Query", "Count"));
			response.getWriter().write("</body></html>");
		}

		private void responseStart(HttpServletResponse response)
				throws IOException {

			response.addHeader("Access-Control-Allow-Origin", "*");
			response.setStatus(HttpServletResponse.SC_OK);
		}

		@Override
		public void setServer(Server server) {
			this.server = server;
		}
		
	}

	
	public static void main(String[] args) {
		PathwayGameServer gameServer = new PathwayGameServer(new MongoDBPaths("localhost",27017,"actv01"), 8080);
		gameServer.startServer();
		
	}
}
