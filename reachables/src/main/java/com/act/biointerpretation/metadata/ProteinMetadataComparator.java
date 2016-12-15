package com.act.biointerpretation.metadata;

import act.installer.brenda.BrendaSupportingEntries;
import act.server.NoSQLAPI;
import act.shared.Reaction;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

/**
 * Created by jca20n on 12/14/16.
 */
public class ProteinMetadataComparator implements Comparator {

    private Host host;  //The ranking is contextualized on a host
    private Localization localization;  //The ranking is contextualized on a location within that host

    public ProteinMetadataComparator(Host host, Localization localization) {
        this.host = host;
        this.localization = localization;
    }

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public Localization getLocalization() {
        return localization;
    }

    public void setLocalization(Localization localization) {
        this.localization = localization;
    }

    @Override
    public int compare(Object o1, Object o2) {
        ProteinMetadata p1 = (ProteinMetadata) o1;
        ProteinMetadata p2 = (ProteinMetadata) o2;

        int score1 = score(p1);
        int score2 = score(p2);

        return score2 - score1;
    }

    private int score(ProteinMetadata pmd) {
        double out = 0.0;

        //Score enzyme efficiency, will result in picking the highest values as the dominant consideration, biased on kcatkm
        if(pmd.kcatkm != null) {
            out = (Math.log(pmd.kcatkm)) * 20;
        }
        if(pmd.specificActivity != null) {
            out += (Math.log(pmd.specificActivity)) * 20;
        }

        //Score modifications
        if(pmd.modifications == null) {
            //No prediction no change
        } else if(pmd.modifications == true) {
            out += -50;
        } else {
            out += 30;
        }

        //Score subunits
        if(pmd.heteroSubunits == null) {
            //No prediction no change
        } else if(pmd.heteroSubunits == true) {
            out += -10;  //If needs multiple subunits, this is potentially problematic
        } else {
            out += 30;   //Great if there is a clear indication that there are no subunits
        }

        //Score cloned
        if(pmd.cloned != null) {
            Integer cloned = pmd.cloned.get(host);
            if (cloned == null) {
                //No prediction no change
            } else {
                out += cloned * 20;  //Will be positive or negative, scales with organism similarity up to 140 (or -140)
            }
        }

        //Score localization
        if(pmd.localization != null) {
            Localization prediction = pmd.localization.get(host);
            if (prediction == Localization.unknown) {
                //No prediction no change
            } else if (prediction != Localization.questionable) {
                out += -20;  //Small penalty for ambiguity about the location
            } else if (prediction != localization) {
                out += -30;  //larger penalty if the prediction is not where you want it
            } else if (prediction == localization) {
                out += 20; //Otherwise a small bonus if things match up
            } else {
                System.err.println("This should never happen - localization");
            }
        }

        double round = Math.round(out);
        return (int) round;
    }


    public static void main(String[] args) throws Exception {
        ProteinMetadataComparator comp = new ProteinMetadataComparator(Host.Ecoli, Localization.cytoplasm);

        //Connect to the database
        NoSQLAPI api = new NoSQLAPI("actv01_vijay_proteins", "actv01_vijay_proteins");
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

        //Create a single instance of the factory method to use for all json
        ProteinMetadataFactory factory = ProteinMetadataFactory.initiate();

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

        System.out.println("All Metadata's parsed: " + agg.size());

        //For each protein metadata, gather up ones that have a non-zero score into a new list
        List<ProteinMetadata> agg2 = new ArrayList<>();
        for(ProteinMetadata pmd : agg) {
            //Consider if it is invalid (meaning a really crappy enzyme) and if so ignore it
            if(!pmd.isValid(Host.Ecoli)) {
                continue;
            }

            //Score the protein
            int score = comp.score(pmd);
            if(score > 0) {
                agg2.add(pmd);
            }
        }

        System.out.println("Non-zero Metadata's: " + agg2.size());

        //Sort the non-zero metadata's using this Comparator
        Collections.sort(agg2, comp);


        System.out.println("done");
    }
}
