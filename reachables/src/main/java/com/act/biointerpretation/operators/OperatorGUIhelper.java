package com.act.biointerpretation.operators;

import com.act.biointerpretation.utils.FileUtils;
import com.act.biointerpretation.utils.JSONFileEditor;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

/**
 * Created by jca20n on 12/1/15.
 */
public class OperatorGUIhelper {
    Map<String, Integer> hcEROPathToCount = new HashMap<>();
    List<String> hcEROsOrdered;

    int hcEROindex = -1;
    int rxnFileIndex = 0;
    
    public static void main(String[] args) {
        OperatorGUIhelper helper = new OperatorGUIhelper();
        helper.initiate();


        int count = 0;
        for(String hash : helper.hcEROsOrdered) {
            System.out.println(hash + "\t" + helper.hcEROPathToCount.get(hash));
            if(helper.hcEROPathToCount.get(hash) > 5) {
                count ++;
            }
        }

        System.out.println("count: " + count);

        helper.goNextERO();
    }

    /**
     * Proceed to the next ERO, starting with first reaction
     */
    public void goNextERO() {
        System.out.println("Going to next ERO");
        try {
            hcEROindex++;
            String hcEROpath = hcEROsOrdered.get(hcEROindex);
            File hcEROdir = new File(hcEROpath);

            File jsonFile = new File(hcEROdir.getAbsolutePath() + "/hcERO.json");
            try {
                String data = FileUtils.readFile(jsonFile.getAbsolutePath());
                JSONObject json = new JSONObject(data);
                boolean isValid = json.getBoolean("validation");
                goNextERO();
                return;
            } catch(Exception err) {}

            //Pull the first file and instantiate a new GUI
            File firstFile = hcEROdir.listFiles()[rxnFileIndex];
            OperatorGUI gui = new OperatorGUI(this, firstFile.getAbsolutePath());
        } catch(Exception err) {
            err.printStackTrace();
        }

    }

    /**
     * Advance the gui to the next reaction (for this RO, or the next RO)
     */
    public void goNextRxn() {
        System.out.println("Going to next REACTION");
        File nextFile = null;
        try {
            String hcEROpath = hcEROsOrdered.get(hcEROindex);
            File hcEROdir = new File(hcEROpath);

            //Update the indices
            if(rxnFileIndex < hcEROdir.listFiles().length) {
                rxnFileIndex++;
            } else {
                goNextERO();
            }

            //Pull the first file and instantiate a new GUI
            File firstFile = hcEROdir.listFiles()[rxnFileIndex];

            OperatorGUI gui = null;
            try {
                gui = new OperatorGUI(this, firstFile.getAbsolutePath());
            } catch(Exception err){
                gui.setVisible(false);
                gui.dispose();
                rxnFileIndex++;
                goNextRxn();
                return;
            }

        } catch(Exception err) {
            err.printStackTrace();
        }
    }


    public void launchJSONEditor() {
        String hcEROpath = hcEROsOrdered.get(hcEROindex);
        File hcEROdir = new File(hcEROpath);
        File jsonFile = new File(hcEROdir.getAbsolutePath() + "/hcERO.json");
        String data = "{\n\t\"name\" : \"\",\n\t\"validation\" : TRUE,\n\t\"confidence\" : \"high\"\n}";
        if(jsonFile.exists()) {
            data = FileUtils.readFile(jsonFile.getAbsolutePath());
        }

        //Instantiate a GUI to edit json
        JSONFileEditor editor = new JSONFileEditor(jsonFile, data);
        editor.setVisible(true);
    }


    public void openFolder() {
        String hcEROpath = hcEROsOrdered.get(hcEROindex);
        try {
            Runtime.getRuntime().exec("/usr/bin/open " + hcEROpath).waitFor();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private void initiate() {
        //Sort the paths based on reaction count
        File dir = new File("output/hmEROs");

        int crocount = 0;

        for(File croDir : dir.listFiles()) {
            if(!croDir.isDirectory()) {
                continue;
            }
//            System.out.println(croDir.getName());
            crocount++;
            for(File hmEROdir : croDir.listFiles()) {
                if(!hmEROdir.isDirectory()) {
                    continue;
                }
                String hmEROhash = hmEROdir.getName();
//                System.out.println("\t" + hmEROhash);

                //Count up all the observations in this ERO
                for(File hcEROdir : hmEROdir.listFiles()) {
                    if(!hcEROdir.isDirectory()) {
                        continue;
                    }
//                    System.out.println("\t\t" + hcEROdir.getName());

                    int obs = 0;
                    for(File afile : hcEROdir.listFiles()) {
                        if(afile.getName().startsWith(".")) {
                            continue;
                        }
                        if(afile.getName().startsWith("hcERO")) {
                            continue;
                        }
                        obs++;
//                        System.out.println("\t\t\t" + afile.getName());
                    }
                    //                System.out.println("\t" + obs);
                    hcEROPathToCount.put(hcEROdir.getAbsolutePath(), obs);
                }


            }
        }

        hcEROsOrdered = sort();
    }
    
    private List<String> sort() {
        //Sort the data
        Comparator comparator = new Comparator<Map.Entry<String, Integer>>() {
            public int compare( Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2 ) {
                return (o2.getValue()).compareTo( o1.getValue() );
            }
        };

        Set<Map.Entry<String, Integer>> set = hcEROPathToCount.entrySet();
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(set);
        Collections.sort(list, comparator);

        List<String> out = new ArrayList<>();
        for(Map.Entry<String, Integer> entry : list) {
            String hash = entry.getKey();
            out.add(hash);
        }
        return out;
    }

}
