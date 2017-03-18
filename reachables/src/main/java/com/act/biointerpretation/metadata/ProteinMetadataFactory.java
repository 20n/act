/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.biointerpretation.metadata;

import act.server.NoSQLAPI;
import act.shared.Reaction;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

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
    private Map<String, Integer> dataMap = new HashMap<>();

    //Data used in algorithms
    private Set<String> modificationTermsTrue;
    private Set<String> modificationTermsFalse;
    private Map<String, Map<Host, Integer>> clonedtermToScore;
    private Map<String, Map<Host, Localization>> termToHostLocalization;

    private ProteinMetadataFactory() {}

    public static ProteinMetadataFactory initiate() throws Exception {
        //Construct the Sets to hold modification data terms
        Set<String>  modTrue = new HashSet<>();
        Set<String>  modFalse = new HashSet<>();

        // TODO: Move this to resources directory?
        File termfile = new File("data/ProteinMetadata/2016_12_07-modification_terms.txt");
        String data = FileUtils.readFileToString(termfile);
        String[] lines = data.split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            String line = lines[i];
            String[] tabs = line.split("\t");
            String term = tabs[0].toLowerCase();
            boolean val = Boolean.parseBoolean(tabs[1]);

            if(val==true) {
                modTrue.add(term);
            } else {
                modFalse.add(term);
            }
        }

        //Construct term data for 'cloned'
        Map<String,String> termToGenus = new HashMap<>();

        // TODO: Move this to resources directory?
        termfile = new File("data/ProteinMetadata/2016_12_07-cloned_term_to_genus.txt");
        data = FileUtils.readFileToString(termfile);
        lines = data.split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            String line = lines[i];
            String[] tabs = line.split("\t");
            if(tabs[1].isEmpty() || tabs[0].isEmpty()) {
                continue;
            }
            termToGenus.put(tabs[0],tabs[1]);
        }

        //Pre-Compute distance to hosts for all terms for "cloned"
        Map<String, Genus> nameToGenus = Genus.parseGenuses();
        Map<String, Map<Host, Integer>> termToScore = new HashMap<>();
        for(String term : termToGenus.keySet()) {
            String tgenus = termToGenus.get(term);
            Genus ggenus = nameToGenus.get(tgenus);

            Map<Host, Integer> hostToScore = new HashMap<>();
            for(Host host : Host.values()) {
                Genus hostgenus = nameToGenus.get(host.toString());
                Integer score = Genus.similarity(ggenus, hostgenus);
                hostToScore.put(host, score);
            }
            termToScore.put(term, hostToScore);
        }

        //Construct the data to handle localization
        Map<String, Map<Host, Localization>> locMap = new HashMap<>();

        // TODO: Move this to resources directory?
        termfile = new File("data/ProteinMetadata/2016_12_06-localization.txt");
        data = FileUtils.readFileToString(termfile);
        lines = data.split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            //Create the host map for each term and initially assume all values are 'questionable'
            Map<Host, Localization> hostToLoc = new HashMap<>();
            for(Host host : Host.values()) {
                hostToLoc.put(host, Localization.questionable);
            }

            //Parse the line and put in replacement values per host
            String line = lines[i];
            String[] tabs = line.split("\t");

            String term = tabs[0];
            for(int x=1; x<tabs.length; x++) {
                try {
                    String sloc = tabs[x];
                    Localization loc = Localization.valueOf(sloc);
                    //Ecoli	Bsubtilis	Cglutamicum	Scerevisiae	Ppasteuris	Aniger	Hsapiens	Sfrugiperda
                    if(x==1) {
                        hostToLoc.put(Host.Ecoli, loc);
                    } else if(x==2) {
                        hostToLoc.put(Host.Bsubtilis, loc);
                    } else if(x==3) {
                        hostToLoc.put(Host.Cglutamicum, loc);
                    } else if(x==4) {
                        hostToLoc.put(Host.Scerevisiae, loc);
                    } else if(x==5) {
                        hostToLoc.put(Host.Ppasteuris, loc);
                    } else if(x==6) {
                        hostToLoc.put(Host.Aniger, loc);
                    } else if(x==7) {
                        hostToLoc.put(Host.Hsapiens, loc);
                    } else if(x==8) {
                        hostToLoc.put(Host.Sfrugiperda, loc);
                    }
                } catch(Exception err) {
                    err.printStackTrace();
                }
            }

            locMap.put(term, hostToLoc);
        }

        //Create the factory and put in data
        ProteinMetadataFactory factory = new ProteinMetadataFactory();
        factory.modificationTermsTrue = modTrue;
        factory.modificationTermsFalse = modFalse;
        factory.clonedtermToScore = termToScore;
        factory.termToHostLocalization = locMap;

        return factory;
    }

    public ProteinMetadata create(JSONObject json) throws Exception {
        Double kcatkm = handleKcatKm(json);
        Double specificActivity = handleSpecificActivity(json);
        Boolean heteroSubunits = handlesubunits(json);
        Boolean modifications = handleModifications(json);
        Map<Host, Integer> cloning = handleCloned(json);
        Map<Host, Localization> localization = handleLocalization(json);
        List<Long> seqIds = handleSequences(json);

        ProteinMetadata out = new ProteinMetadata();
        out.kcatkm = kcatkm;
        out.specificActivity = specificActivity;
        out.heteroSubunits = heteroSubunits;
        out.modifications = modifications;
        out.cloned = cloning;
        out.localization = localization;
        out.sequences = seqIds;
        return out;
    }

    private List<Long> handleSequences(JSONObject json) {
        //Try to pull the data
        JSONArray jarray = null;
        List<Long> seqIds = new ArrayList<>();

        try {
            jarray = json.getJSONArray("sequences");
        } catch (Exception err) {
            return seqIds;
        }

        //If there is no data, the value is undefined
        if (jarray.length() == 0) {
            return seqIds;
        }

        for (int i = 0; i < jarray.length(); i++) {
            Long data = (Long) jarray.get(i);
            seqIds.add(data);
        }

        return seqIds;
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
        // TODO: Move this to resources directory?
        File testfile = new File("data/ProteinMetadata/2016_12_07-subunit_testset.txt");
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

    private Boolean handleModifications(JSONObject json) {
        //Try to pull the data
        JSONArray jarray = null;
        try {
            jarray = json.getJSONArray("post_translational_modification");
        } catch (Exception err) {
            return null;
        }

        //If there is no data, nothing is known
        if (jarray.length() == 0) {
            return null;
        }

        //If many observations are given for the protein, return the consensus with a true value dominating
        if (jarray.length() > 1) {
            Boolean out = null;
            for (int i = 0; i < jarray.length(); i++) {
                try {
                    JSONObject obj = jarray.getJSONObject(i);
                    Boolean bval = assignModificationHelper(obj);
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
            Boolean bval = assignModificationHelper(obj);
            return bval;
        } catch (Exception err) {
            return null;
        }
    }

    private Boolean assignModificationHelper(JSONObject json) {
        try {
            String ptm = json.getString("post_translational_modification");
            if (this.modificationTermsTrue.contains(ptm)) {
                return true;
            }
            if (this.modificationTermsFalse.contains(ptm)) {
                return false;
            }
            return null;
        } catch(Exception err) {
            return null;
        }
    }

    private Map<Host, Integer> handleCloned(JSONObject json) {
        //Populate output with null for all hosts (no prediction)
        Map<Host, Integer> out = new HashMap<>();
        for(Host host : Host.values()) {
            out.put(host, null);
        }

        //Read in any data from JSON and interpret prediction based on phylogenetic distance to host
        try {
            JSONArray jarray = json.getJSONArray("cloned");

            for(int i=0; i<jarray.length(); i++) {
                JSONObject obj = jarray.getJSONObject(i);
                String comment = obj.getString("comment");

                String[] words = comment.toLowerCase().split("[\\s,;]+");
                for(String word : words) {
                    Map<Host,Integer> hosttoint = this.clonedtermToScore.get(word);
                    if(hosttoint == null) {
                        continue;
                    }
                    for(Host host : hosttoint.keySet()) {
                        Integer currval = out.get(host);
                        if(currval == null) {
                            currval = -99999;
                        }
                        Integer newval = hosttoint.get(host);
                        if(newval > currval) {
                            out.put(host, newval);
                        }
                    }
                }
            }
        } catch (Exception err) {
        }

        return out;
    }

    // Dead code: This was being called from within `handleCloned` but not now.
    private void printoutHosts(JSONObject json) {
        try {
            JSONArray jarray = json.getJSONArray("cloned");

            for(int i=0; i<jarray.length(); i++) {
                JSONObject obj = jarray.getJSONObject(i);
                String comment = obj.getString("comment");

                int index = comment.indexOf("in ");
                if(index < 0) {
                    continue;
                }

                String[] words = comment.substring(index).toLowerCase().split("[\\s,;]+");

                String datapoint = "";
                int limit = 1;
                int counter = 0;

                for(String word : words) {
                    if(counter>limit) {
                        break;
                    }
                    counter++;

                    datapoint += word + "\t";
                }

                Integer currval = dataMap.get(datapoint);
                if(currval == null) {
                    currval = 0;
                }
                currval++;
                dataMap.put(datapoint, currval);

            }
        } catch (Exception err) {
        }
    }

    private Map<Host, Localization> handleLocalization(JSONObject json) {
        //Construct the output with unknown for all Hosts
        Map<Host, Localization> out = new HashMap<>();
        for(Host host : Host.values()) {
            out.put(host, Localization.unknown);
        }

        //Parse out localization information from json and populate the output map with predictions
        JSONArray jarray = null;
        try {
            jarray = json.getJSONArray("localization");
        } catch (Exception err) {
            err.printStackTrace();
        }

        //If there is no metadata, all values should be "unknown"
        if(jarray.length() == 0) {
            return out;
        }

        //Scan through each observation
        for (int i = 0; i < jarray.length(); i++) {
            try {
                JSONObject obj = jarray.getJSONObject(i);
                String term = obj.getString("val");

                Map<Host, Localization> hostToLoc = this.termToHostLocalization.get(term);
                if(hostToLoc == null) {
                    System.out.println("missing term: " + term);
                    dataList.add(term);
                    continue;
                }

                for (Host host : hostToLoc.keySet()) {
                    Localization currval = out.get(host);       // Whatever is currently in the Map
                    Localization newval = hostToLoc.get(host);  // The potential new value

                    //If the current value is "unknown", replace that value the one with the term
                    if (currval == Localization.unknown) {
                        out.put(host, newval);
                    }

                    //If the current value is "questionable", stay with questionable
                    else if (currval == Localization.questionable) {
                        out.put(host, Localization.questionable);
                    }

                    //If currval and newval have non-identical, but aren't unknown/questionable, then
                    //There must be 2 non-identical predictions, in which case this becomes questionable
                    else if (currval != newval) {
                        out.put(host, Localization.questionable);
                    }
                }
            } catch (Exception err) {
                err.printStackTrace();
            }
        }

        return out;
    }

    public static void main(String[] args) throws Exception {
        // TODO: This is referencing a temporary collection. Change it!
        // TODO: FIX THIS BEFORE MERGE!
        NoSQLAPI api = new NoSQLAPI("actv01_vijay_proteins", "actv01_vijay_proteins");
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

        //Create a single instance of the factory method to use for all json
        ProteinMetadataFactory factory = ProteinMetadataFactory.initiate();

        //Run some tests
        try {
            if(factory.testHandlesubunits() == true) {
                System.out.println("Subunit test OK");
            }
        } catch (Exception err) {
            System.err.println("Failed to test subunits");
        }

        //Create a list to aggregate the results of the database scan
        List<ProteinMetadata> agg = new ArrayList<>();

        //Scan the database and store ProteinMetadata objects
        while (iterator.hasNext()) {
            Reaction rxn = iterator.next();

            Reaction.RxnDataSource source = rxn.getDataSource();
            if (!source.equals(Reaction.RxnDataSource.BRENDA)) {
                continue;
            }

            Set<JSONObject> jsons = rxn.getProteinData();


            for (JSONObject json : jsons) {
                ProteinMetadata meta = factory.create(json);
                agg.add(meta);
            }
        }

        //Write out any messages to file
        StringBuilder sb = new StringBuilder();
        for(String aline : factory.dataList) {
            sb.append(aline).append("\n");
        }

        File outfile = new File("output/ProteinMetadata/Factory_output.txt");
        if(outfile.exists()) {
            outfile.delete();
        }
        FileUtils.writeStringToFile(outfile, sb.toString());

        sb = new StringBuilder();
        for(String key : factory.dataMap.keySet()) {
            int value = factory.dataMap.get(key);
            sb.append(key + "\t" + value + "\n");
        }

        outfile = new File("output/ProteinMetadata/Factory_output_map.txt");
        if(outfile.exists()) {
            outfile.delete();
        }
        FileUtils.writeStringToFile(outfile, sb.toString());

        //Count up the results of modifications to get statistics
        int falsecount = 0;
        int truecount = 0;
        int nullcount = 0;

        for(ProteinMetadata datum : agg) {
            if(datum == null) {
                System.err.println("null datum");
                continue;
            }
            if(datum.modifications == null) {
                nullcount++;
            } else if(datum.modifications == false) {
                falsecount++;
            } else if(datum.modifications == true) {
                truecount++;
            }
        }
        System.out.println("Total # protein metadata: " + agg.size());
        System.out.println();
        System.out.println("modification true count: " + truecount);
        System.out.println("modification false count: " + falsecount);
        System.out.println("modification null count: " + nullcount);
        System.out.println();

        //Get some statistics for cloned
        nullcount = 0;
        int emptycount = 0;
        int colicount = 0;
        int humancount = 0;
        int bothcount = 0;
        for(ProteinMetadata datum : agg) {
            if(datum == null) {
                System.err.println("null datum");
                continue;
            }
            if(datum.cloned == null) {
                nullcount++;
                continue;
            }
            if(datum.cloned.isEmpty()) {
                emptycount++;
                continue;
            }
            Integer human = datum.cloned.get(Host.Hsapiens);
            if(human !=null && human > 0) {
                humancount++;
            }
            Integer coli = datum.cloned.get(Host.Ecoli);
            if(coli !=null && coli > 0) {
                colicount++;
                if(human !=null && human > 0) {
                    bothcount++;
                }
            }
        }

        System.out.println("cloned null count: " + nullcount);
        System.out.println("cloned empty count: " + emptycount);
        System.out.println("cloned coli count: " + colicount);
        System.out.println("cloned human count: " + humancount);
        System.out.println("cloned both count: " + bothcount);
        System.out.println();
    }
}
