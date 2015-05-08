package act.installer;

import java.net.URLEncoder;
import java.net.URL;
import java.net.HttpURLConnection;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.XML;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import act.shared.helpers.P;
import act.shared.helpers.MongoDBToJSON;
import act.client.CommandLineRun;
import act.server.SQLInterface.MongoDB;

import com.mongodb.DBObject;

import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.FileWriter;

public class ChemSpider {
  static String api_InChIToCSID  = "http://www.chemspider.com/InChI.asmx/InChIToCSID";
  static String api_CSID2ExtRefs = "http://www.chemspider.com/Search.asmx/CSID2ExtRefs";

  // This is the API security token as registered in saurabh's name on the RSC site
  static String api_token = "b3d12bfe-1bcd-4960-a30f-ba876fe7a0fb";

  private List<P<String, String>> api_InChIToCSID_data(String inchi) {
    List<P<String, String>> data = new ArrayList<>();
    data.add(new P<String, String>("inchi", inchi));
    return data;
  }

  private List<P<String, String>> api_CSID2ExtRefs_data(Integer csid, String token, String[] datasrcs) {
    List<P<String, String>> data = new ArrayList<>();
    data.add(new P<String, String>("CSID", csid.toString()));
    data.add(new P<String, String>("token", token));
    for (String datasrc : datasrcs)
      data.add(new P<String, String>("datasources", datasrc));
    return data;
  }

  public JSONArray getVendors(String inchi) {
    Integer csid = getCSID(inchi);

    if (csid == null) {
      return new JSONArray();
    }
    
    JSONArray vendors_json = getVendors(csid);
    
    return vendors_json;
  }

  public Integer getCSID(String inchi) {
    String xml = api_call(api_InChIToCSID, api_InChIToCSID_data(inchi));
    // should return something like:
    // <?xml version="1.0" encoding="utf-8"?><string xmlns="http://www.chemspider.com/">1906</string>
    JSONObject toJSON = XML.toJSONObject(xml);
    // toJSON.string.content should have the ID
    try {
      Integer id = toJSON.getJSONObject("string").getInt("content");
      return id;
    } catch (Exception e) {
      return null;
    }
  }

  private JSONArray getVendors(Integer csid) {
    String xml = api_call(api_CSID2ExtRefs, api_CSID2ExtRefs_data(csid, this.api_token, this.datasrcs));
    JSONObject toJSON = XML.toJSONObject(xml);
    try {
      Object vendor_list = toJSON.getJSONObject("ArrayOfExtRef").get("ExtRef");
      JSONArray wrapped = new JSONArray();
      if (vendor_list instanceof JSONArray) {
        wrapped = (JSONArray)vendor_list;
      } else if (vendor_list instanceof JSONObject) {
        wrapped.put(vendor_list);
      } else {
        throw new Exception(vendor_list.getClass().getName());
      }

      return wrapped;
    } catch (Exception e) {
      // if we cant extract the array then probably no vendors available
      String returned_xml_when_no_vendors = "<?xml version=\"1.0\" encoding=\"utf-8\"?><ArrayOfExtRef xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns=\"http://www.chemspider.com/\" />";
      if (!returned_xml_when_no_vendors.equals(xml)) {
        System.out.println("ChemSpider: SHOULD NOT HAPPEN. Vendor XML parsing failed. Exception: " + e.getMessage() + " CSID: " + csid + " XML retrieved: " + xml);
      } 

      // else it is all normal and return the empty array.
      return new JSONArray();
    }
  }

  private String api_call(String endpoint, List<P<String, String>> data) {
    StringBuilder postData = new StringBuilder();

    try {
      URL url = new URL(endpoint);

      for (P<String,String> param : data) {
        if (postData.length() != 0) postData.append('&');
        postData.append(URLEncoder.encode(param.fst(), "UTF-8"));
        postData.append('=');
        postData.append(URLEncoder.encode(String.valueOf(param.snd()), "UTF-8"));
      }
      byte[] postDataBytes = postData.toString().getBytes("UTF-8");

      HttpURLConnection conn = (HttpURLConnection)url.openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
      conn.setDoOutput(true);

      conn.getOutputStream().write(postDataBytes);

      DataInputStream in = new DataInputStream(conn.getInputStream());
      String resp = "", line;
      while ( (line = in.readLine()) != null) {
        resp += line;
      }
      return resp;
    } catch (IOException e) {
      System.out.println("ChemSpider: SHOULD NOT HAPPEN: Failed API call: " + e.getMessage() + " on data: " + postData);
      return "";
    }
  }

  public void addChemVendors(MongoDB db, String vendors_file) {

    // first get all chemicals in the db; we are going to try
    // and install vendors for each of them
		System.out.println("reading all chemicals that will be vendor-ized");
    Map<String, Long> all_db_chems = db.constructAllInChIs();

    // read the cached vendors file (inchi<TAB>json_vendors)
		System.out.println("reading vendors for chemicals");
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(vendors_file))));
			String vendorline;
			while ((vendorline = br.readLine()) != null) {
        JSONObject cached = deconstruct_cache_format(vendorline);
        String compound = cached.getString("inchi");
        Integer csid = cached.getInt("csid");
        Integer num_vendors = cached.getInt("num_vend");
        JSONObject vendors_json_cached = cached.getJSONObject("vend_json");

        // now use the paged in data
        DBObject vendors = MongoDBToJSON.conv(vendors_json_cached);
        String inchi = CommandLineRun.consistentInChI(compound, "Adding chemical vendors");   
        // install the vendor data into the db
				db.updateChemicalWithVendors(compound, csid, num_vendors, vendors);
        // mark this chemical as installed in the db
        all_db_chems.remove(compound);
      }
			br.close();
    } catch (FileNotFoundException e) {
      // this happens when initializing the DB completely from
      // scratch, and not even a single chemical has been looked
      // up on ChemSpider for vendors. Ignore, as the lookups 
      // below will initialize a file...
      
		} catch (Exception e) {
			e.printStackTrace();
		}

    // the remaining inchis in all_db_chems did not have a vendor
    try {
      PrintWriter vendors_cache = new PrintWriter(new BufferedWriter(new FileWriter(vendors_file, true)));

      int total = all_db_chems.size(), pulled = 0, w_vendors = 0, wo_vendors = 0;
      for (String chem : all_db_chems.keySet()) {

        // Dont waste time processing a fake or malformed inchis
        if (chem.startsWith("InChI=/FAKE") || chem.startsWith("none") || chem.contains("&gt;"))
          continue;
      
        // get vendors by calling ChemSpider's web-api
        // note that this can return an empty JSON
        JSONArray vendors_json = new JSONArray();
        // first check that the chemical is on ChemSpider, get CSID
        Integer csid = getCSID(chem);
        // if the chemical is on ChemSpider retrieve its vendors
        Integer num_vendors = 0;
        if (csid != null) {
          vendors_json = getVendors(csid);
          num_vendors = count_vendors(vendors_json);
        }

        DBObject vendors = MongoDBToJSON.conv(vendors_json);
        // add these vendors to db
        db.updateChemicalWithVendors(chem, csid, num_vendors, vendors);

        // concatenate the retrieved vendors to this.chem_vendors file
        // so that for this chemical we dont have to retrieve the 
        // vendors again in the future 

        vendors_cache.println(cache_format(chem, csid, num_vendors, vendors_json));
        vendors_cache.flush();

        pulled++;
        if (num_vendors > 0) w_vendors++; else wo_vendors++;
        System.out.format("%f\t%d (retrieved) / %d (total)\t\t%d (have vendors)\t%d (no vendors)\r", 100*((float)pulled/total), pulled, total, w_vendors, wo_vendors);

      }
      System.out.println();

      vendors_cache.close();

    } catch (IOException e) {
      System.out.println("===> Could not open vendors cache file " + vendors_file + " failed. Abort install."); System.exit(-1);
    }

  }

  int count_vendors(JSONArray vendor_json) {
    Set<String> uniq_vendors = new HashSet<String>();
    int len = vendor_json.length();
    for (int i = 0; i < len; i++) {
      uniq_vendors.add(vendor_json.getJSONObject(i).getString("ds_name"));
    }
    return uniq_vendors.size();
  }

  // this function should be in sync with the fn deconstruct_cache_format below
  String cache_format(String inchi, Integer csid, Integer num_vendors, JSONArray vendors_json) {
    return inchi + "\t" +
            csid + "\t" +
            num_vendors + "\t" +
            vendors_json.toString();
  }

  // this function should be in sync with the fn cache_format above
  JSONObject deconstruct_cache_format(String vendorline) {
	  String[] tokens = vendorline.split("\t");
    JSONObject cache_read = new JSONObject();
    cache_read.put("inchi"    , tokens[0]);
    cache_read.put("csid"     , Integer.parseInt(tokens[1]));
    cache_read.put("num_vend" , Integer.parseInt(tokens[2]));
    cache_read.put("vend_json", new JSONArray(tokens[3]));
    return cache_read;
  }

  // This data comes from act/reachables/src/main/resources/chemspider-vendors
  // See the script vendors_from_inchi.sh and the step{1-5} that extract these
  // sources from ChemSpider. Steps{1-5} result in a file called vendor_names.txt
  // and that file is pasted here...
  static String[] datasrcs = new String[] {
    "ASINEX",
    "ChemBridge",
    "Specs",
    "Enamine",
    "AKos",
    "R&D Chemicals",
    "Synthon-Lab",
    "UkrOrgSynthesis",
    "CiVentiChem",
    "SynChem",
    "Ryan Scientific",
    "TOSLab",
    "Bio-Vin",
    "ChemDiv",
    "Otava Chemicals",
    "Aronis",
    "Life Chemicals",
    "Calyx",
    "Activate Scientific",
    "Argus Chemicals",
    "AsisChem",
    "Boron Molecular",
    "ChemPacific",
    "Microsource",
    "Trylead Chemical",
    "Sigma-Aldrich",
    "Afid Therapeutics",
    "Alfa Aesar",
    "Vitas-M",
    "Key Organics",
    "Matrix Scientific",
    "PepTech",
    "Pharmeks",
    "Trans World Chemicals",
    "Astatech",
    "Chess Chemical",
    "JRD Fluorochemicals",
    "Ubichem",
    "AnalytiCon Discovery",
    "MP Biomedicals",
    "Oakwood",
    "Exclusive Chemistry",
    "OmegaChem",
    "HDH Pharma",
    "Rieke Metals",
    "ASDI",
    "Florida Center for Heterocyclic Compounds",
    "Synthonix",
    "Shanghai Sinofluoro Scientific",
    "Hetcat",
    "Borochem",
    "Biosynth",
    "True PharmaChem",
    "Cayman Chemical",
    "Dipharma",
    "ACB Blocks",
    "Chemik",
    "Sequoia Research Products",
    "Apollo Scientific Limited",
    "Spectrum Info",
    "Infarmatik",
    "Rudolf Boehm Institute",
    "Timtec",
    "Tocris Bioscience",
    "Princeton Biomolecular",
    "Hangzhou Sage Chemical Co., Ltd.",
    "Viwit Pharmaceutical",
    "MicroCombiChem",
    "SelectLab Chemicals GmbH",
    "Ramdev Chemicals",
    "Extrasynthese",
    "Gelest",
    "Bridge Organics",
    "Jiangsu WorldChem",
    "Baihua Bio-Pharmaceutical",
    "Szintekon Ltd",
    "Excel Asia",
    "Alinda Chemical",
    "ennopharm",
    "Manchester Organics",
    "Globe Chemie",
    "Shanghai Haoyuan Chemexpress ",
    "Shanghai Elittes organics",
    "Cooper Chemicals",
    "Hangzhou APIChem Technology ",
    "Mizat Chemicals ",
    "Frinton Laboratories",
    "BePharm",
    "HE Chemical",
    "Molport",
    "BioBlocks Inc.",
    "Zerenex Molecular ",
    "Innovapharm",
    "Research Organics",
    "Creasyn Finechem",
    "Alchem Pharmtech",
    "iThemba Pharmaceuticals",
    "Sun BioChem, Inc.",
    "Santa Cruz Biotechnology ",
    "DSL Chemicals",
    "AvaChem Scientific",
    "SynQuest",
    "Evoblocks",
    "CDN Isotopes",
    "Endeavour Speciality Chemicals",
    "Shanghai Race Chemical",
    "Shanghai IS Chemical Technology",
    "DanYang HengAn Chemical Co.,Ltd",
    "ChiroBlock",
    "Platte Valley Scientific",
    "TCI",
    "Finetech Industry",
    "Nagase",
    "Annker Organics",
    "Ark Pharm, Inc.",
    "Aconpharm",
    "Endotherm GmbH",
    "InterBioScreen",
    "Fluorochem ",
    "Accela ChemBio",
    "ChemFuture",
    "Syntide",
    "Paragos",
    "DiverChim",
    "oriBasePharma",
    "Chiralix",
    "AChemo",
    "Selleck Chemicals",
    "Watson International Ltd",
    "Excenen",
    "Shanghai Boyle Chemical Co., Ltd.",
    "Alfa Pyridines",
    "Shanghai Excellent chemical",
    "Chiral Quest",
    "AMRI",
    "Letopharm",
    "Santai Labs",
    "Adesis",
    "AOKChem",
    "Nanjing Pharmaceutical Factory Co., Ltd",
    "DAY Biochem",
    "zealing chem",
    "ABI Chemicals",
    "AOKBIO",
    "Reddy N Reddy Pharmaceuticals",
    "Chengdu D-innovation",
    "Avistron Chemistry",
    "Abacipharm",
    "Centec",
    "Focus Synthesis",
    "Georganics Ltd.",
    "Rare Chem",
    "Annova Chem",
    "Chicago Discovery Solutions",
    "Solaronix",
    "Apeiron Synthesis",
    "Indofine",
    "J and K Scientific",
    "Porse Fine Chemical",
    "Cool Pharm",
    "Livchem",
    "Fragmenta",
    "AEchem Scientific",
    "Mole-Sci.Tech",
    "Irvine Chemistry Laboratory ",
    "Synergy-Scientific",
    "Angene",
    "CoachChem",
    "Abblis Chemicals",
    "Abcam",
    "Jalor-Chem",
    "AK Scientific",
    "Acorn PharmaTech",
    "Zylexa Pharma",
    "Chemren Bio-Engineering",
    "Isosep",
    "Selleck Bio",
    "BOC Chem",
    "Advanced ChemBlocks",
    "Juhua Group",
    "Capot Chemical",
    "LGC Standards",
    "Biochempartner",
    "Adooq Bioscience",
    "Novochemy",
    "Atomole Scientific",
    "Huili Chem",
    "P3 BioSystems",
    "Beijing LYS Chemicals",
    "Hangzhou Chempro",
    "Abmole Bioscience",
    "Watec Laboratories",
    "Apexmol",
    "Conier Chem",
    "Amadis Chemical",
    "Alfa Chemistry",
    "ADVAMACS",
    "Jupiter Sciences",
    "Arking Pharma",
    "Wisdom Pharma",
    "KaironKem",
    "Alchemist-Pharm",
    "Natural Remedies",
    "LeadGen Labs",
    "Acentex Scientific",
    "Anward",
    "Rosewell Industry Co.",
    "Chembo Pharma",
    "Achemica",
    "EDASA Scientific",
    "Sunshine Chemlab",
    "Acesobio",
    "Syncozymes",
    "Chengdu Kaixin",
    "AminoLogics",
    "AldLab Chemicals",
    "ChangChem",
    "ApexBio",
    "BerrChem",
    "Medchem Express",
    "Merck Millipore",
    "ChemScene",
    "Glentham Life Sciences",
    "Viva Corporation",
    "PhyStandard",
    "King Scientific",
    "eNovation Chemicals",
    "Thoreauchem",
    "MolMall",
    "ACINTS",
    "Chemodex",
    "Labseeker",
    "Axon Medchem",
    "BroadPharm",
    "Rosewachem",
    "Renaissance Chemicals",
    "CEG Chemical",
    "GFS Chemicals",
    "OXchem",
    "ACT Chemical",
    "Bide Pharmatech",
    "Arromax",
    "Sinova",
    "Atomax",
    "TOKU-E",
    "Mcule",
    "Active Biopharma",
    "Finornic Chemicals",
    "Apollo Scientific Adopted",
    "LKT Labs",
    "Carbosynth",
    "ChemStep",
    "Wecoochem",
    "Aromalake",
    "W&J PharmaChem, Inc.",
    "Leverton-Clarke",
    "Airedale Chemical",
    "Corvinus Chemicals",
    "Akerr Pharma",
    "Debyesci",
    "Xinyanhe Pharmatech",
    "Megazyme International",
    "Arkema",
    "Advanced Technology & Industrial",
    "Shenzhen Nexconn Pharmatechs Ltd.",
    "Aspira Scientific",
    "Shanghai Pengteng Fine Chemical Co., Ltd. ",
    "Wylton Jinglin",
    "AZEPINE",
    "Attomarker",
    "OlainFarm",
    "TripleBond",
    "Exim",
    "Helix Molecules",
    "Santiago Laboratory Equipment",
    "ChiralStar",
    "Wolves Chemical",
    "Hello Bio",
    "SLI Technologies",
    "A1 BioChem Labs",
    "Tubepharm",
    "A&J Pharmtech",
    "Aoyi International",
    "4C Pharma Scientific",
    "ACO Pharm",
    "Chemcia Scientific",
    "Natural Products Discovery Institute",
    "A2Z Chemical",
    "GuiChem",
    "Acemol",
    "Boerchem",
    "Suntto Chemical",
    "SynInnova",
    "Founder Pharma"
  };

  public static void main(String[] args) {
    String paracetamol = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)";
    String paracetamol_faulty = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,10)";
    String nobiletin = "InChI=1S/C21H22O8/c1-23-13-8-7-11(9-15(13)24-2)14-10-12(22)16-17(25-3)19(26-4)21(28-6)20(27-5)18(16)29-14/h7-10H,1-6H3";
    ChemSpider c = new ChemSpider();
    System.out.println(c.getVendors(paracetamol       ).toString(2));
    System.out.println(c.getVendors(nobiletin         ).toString(2));
    System.out.println(c.getVendors(paracetamol_faulty).toString(2));
  }
}
