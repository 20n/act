package com.act.biointerpretation.metadata;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Requires newer database:
 * mongod --dbpath /Users/jca20n/Downloads/2016-11-22-full-run-w-validation & disown %1
 * <p>
 * This one has the modification and cloning data also included
 * mongod --dbpath /Users/jca20n/Downloads/2016-12-08-actv01_vijay_proteins_only & disown %1
 * Created by jca20n on 12/1/16.
 */
public class ProteinMetadataFactory {

    private Set<String> dataList = new HashSet<>();


    public ProteinMetadata create(JSONObject json) {
//        System.out.println(json.toString());


        Double kcatkm = handleKcatKm(json);
        Double specificActivity = handleSpecificActivity(json);
//        Boolean modifications = handleModifications(json);
        Boolean heteroSubunits = handlesubunits(json);
//        Map<Host, Boolean> cloning = handleCloned(json);
//        Localization localization = handleLocation(json);

        ProteinMetadata out = new ProteinMetadata();
        return out;
    }

    private Boolean handleModifications(JSONObject json) {
        return null;
    }


    private Map<Host, Boolean> handleCloned(JSONObject json) {

        Map<Host, Boolean> out = new HashMap<>();
        try {
//            JSONArray jarray = json.getJSONArray("specific_activity");
            JSONArray jarray = json.getJSONArray("Cloned");

            if (jarray.length() == 0) {
                return out;
            }
            if (jarray.length() > 1) {
                System.err.println("A location field has more than one object in it");
//                System.exit(0);  //This should never happen
                return out;
            }

            //obj is the terminal json with val and comment fields
            JSONObject obj = jarray.getJSONObject(0);
            dataList.add(obj.toString());

//            Double val = obj.getDouble("val");
//
//            if(out.containsKey(val)) {
//                int newcount = out.get(val)+1;
//                out.put(val, newcount);
//            } else {
//                out.put(val, 1);
//            }

        } catch (Exception err) {
        }

        return out;
    }


    private Localization handleLocation(JSONObject json) {
        Localization out = Localization.undefined;
        try {
            JSONArray jarray = json.getJSONArray("localization");

            if (jarray.length() == 0) {
                return out;
            }
            if (jarray.length() > 1) {
                System.err.println("A location field has more than one object in it");
//                System.exit(0);  //This should never happen
//                return handleMultipleLocations(json);
            }

            //obj is the terminal json with val and comment fields
            JSONObject obj = jarray.getJSONObject(0);
            String val = obj.getString("val");


        } catch (Exception err) {
        }

        return out;
    }

    private Double handleKcatKm(JSONObject json) {
        //Try to pull the data
        JSONArray jarray = null;
        try {
            jarray = json.getJSONArray("kcat/km");
        } catch (Exception err) {
            return null;
        }

        //If there is no data, the value is undefined
        if (jarray.length() == 0) {
            return null;
        }

        //If many observations are given for the protein, return the highest value observed
        /*TODO:  the name of chemical in the substrate field could be matched to the substrate of the reaction
        Currently I am just picking the highest value, but it would be best to pick the highest value for the
        substrate being examined.  This complicates the wiring, so I didn't try to implement
         */
        if (jarray.length() > 1) {
            Double highest = -1.0;
            for (int i = 0; i < jarray.length(); i++) {
                try {
                    JSONObject obj = jarray.getJSONObject(i);
                    Double dval = obj.getDouble("val");
                    if (dval > highest) {
                        highest = dval;
                    }
                } catch (Exception err) {
                }
            }

            if (highest > 0) {
                return highest;
            } else {
                return null;
            }
        }

        //Otherwise return the one value
        try {
            JSONObject obj = jarray.getJSONObject(0);
            Double val = obj.getDouble("val");
            return val;
        } catch (Exception err) {
            return null;
        }
    }

    private Double handleSpecificActivity(JSONObject json) {
        //Try to pull the data
        JSONArray jarray = null;
        try {
            jarray = json.getJSONArray("specific_activity");
        } catch (Exception err) {
            return null;
        }

        //If there is no data, the value is undefined
        if (jarray.length() == 0) {
            return null;
        }

        //If many observations are given for the protein, return the highest value observed
        if (jarray.length() > 1) {
            Double highest = -1.0;
            for (int i = 0; i < jarray.length(); i++) {
                try {
                    JSONObject obj = jarray.getJSONObject(i);
                    Double dval = obj.getDouble("val");
                    if (dval > highest) {
                        highest = dval;
                    }
                } catch (Exception err) {
                }
            }

            if (highest > 0) {
                return highest;
            } else {
                return null;
            }
        }

        //Otherwise return the one value
        try {
            JSONObject obj = jarray.getJSONObject(0);
            Double val = obj.getDouble("val");
            return val;
        } catch (Exception err) {
            return null;
        }
    }


    private Boolean handlesubunits(JSONObject json) {
        //Try to pull the data
        JSONArray jarray = null;
        try {
            jarray = json.getJSONArray("subunits");
        } catch (Exception err) {
            return null;
        }

        //If there is no data, the value is undefined
        if (jarray.length() == 0) {
            return null;
        }

        //If many observations are given for the protein, return the consensus with a true value dominating
        if (jarray.length() > 1) {
            Boolean out = null;
            for (int i = 0; i < jarray.length(); i++) {
                try {
                    JSONObject obj = jarray.getJSONObject(i);
                    Boolean bval = assignSubunitHelper(obj);
                    if (bval == true) {
                        return true;
                    } else if(bval == false) {
                        out = false;
                    }
                } catch (Exception err) {
                }
            }

            return out;
        }

        //Otherwise return the one value
        try {
            JSONObject obj = jarray.getJSONObject(0);
            Boolean bval = assignSubunitHelper(obj);
            return bval;
        } catch (Exception err) {
            return null;
        }
    }

    private Boolean assignSubunitHelper(JSONObject json) {
        try {
            String val = json.getString("val");
            String comment = json.getString("comment");

            if(val.equals("?")) {
                return null;
            }
            if(val.contains("hetero")) {
                return true;
            }
            if(comment.contains("hetero")) {
                return true;
            }
            if(val.contains("monomer")) {
                return false;
            }
            if(val.startsWith("homo")) {
                return false;
            }
            if(comment.contains("alpha") && comment.contains("beta")) {
                return true;
            }
            return false;
        } catch(Exception err) {
            return null;
        }
    }


    /**
     * This is a unit test for handleSubunits
     * @throws Exception
     */
    private boolean testHandlesubunits() throws Exception {
        File testfile = new File("/Users/jca20n/Dropbox (20n)/20n Team Folder/act_data/ProteinMetadata/2016_12_07-subunit_testset.txt");
        String data = FileUtils.readFileToString(testfile);
        data = data.replaceAll("\"\"", "\"");
        String[] lines = data.split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            String line = lines[i];
            String[] tabs = line.split("\t");

            //Pull out the json for each test and re-wrap it
            String arrayStr = tabs[0];
            if(arrayStr.startsWith("\"")) {
                arrayStr = arrayStr.substring(1);
            }
            if(arrayStr.endsWith("\"")) {
                arrayStr = arrayStr.substring(0, arrayStr.length() -1);
            }

            String jsonstr = "{\"subunits\":" + arrayStr + "}";
            JSONObject json = new JSONObject(jsonstr);

            //Run the test and compare result to expected
            Boolean result = this.handlesubunits(json);
            Boolean expected = null;
            if(!tabs[1].equals("null")) {
                expected = Boolean.parseBoolean(tabs[1]);
            }

            if(result != expected) {
                System.err.println("Subunit testing error:\n" + json.toString()  + "  " + tabs[1]);
                System.err.println("Expect: " + expected + " Found: " + result);
                return false;
            }


        }

        return true;
    }

    public static void main(String[] args) throws Exception {
        //Connect to the database
        NoSQLAPI api = new NoSQLAPI("actv01_vijay_proteins", "actv01_vijay_proteins");
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

        //Create a single instance of the factory method to use for all json
        ProteinMetadataFactory factory = new ProteinMetadataFactory();

        //Run some unit tests
        try {
            if(factory.testHandlesubunits() == true) {
                System.out.println("Subunit test OK");
            }
        } catch (Exception err) {
            System.err.println("Failed to test subunits");
        }
//
//        //Create a list to aggregate the results of the database scan
//        List<ProteinMetadata> agg = new ArrayList<>();
//
//        //Scan the database and store ProteinMetadata objects
//        while (iterator.hasNext()) {
//            Reaction rxn = iterator.next();
//
//            Reaction.RxnDataSource source = rxn.getDataSource();
//            if (!source.equals(Reaction.RxnDataSource.BRENDA)) {
//                continue;
//            }
//
//            Set<JSONObject> jsons = rxn.getProteinData();
//
//
//            for (JSONObject json : jsons) {
//                ProteinMetadata meta = factory.create(json);
//                agg.add(meta);
//            }
//        }
//
//        //Write out any aggregated messages to file
//        StringBuilder sb = new StringBuilder();
//        for(String aline : factory.dataList) {
//            aline = aline.replaceAll("\t", " ");
//            sb.append(aline).append("\t\n");
//        }
//
//        File outfile = new File("output/ProteinMetadata/Facotry_output.txt");
//        if(outfile.exists()) {
//            outfile.delete();
//        }
//        FileUtils.writeStringToFile(outfile, sb.toString());
//        System.out.println("done");
    }
}
