package com.act.biointerpretation.sars.vanillinL3;

import act.server.MongoDB;
import act.shared.Reaction;
import act.shared.Seq;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.SeqDBReactionGrouper;
import com.act.jobs.FileChecker;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by gil on 11/30/16.
 */
public class PathwayReactionFinder {


  private static final Logger LOGGER = LogManager.getFormatterLogger(PathwayReactionFinder.class);

  private static final String LOCAL_HOST = "localhost";
  private static final Integer MONGO_PORT = 27017;
  private static final String MONGO_DB = "wiki_reachables";
  private static final String PATHWAY_COLLECTION = "pathways_gil";

  public static void main(String[] args) throws Exception {
    Mongo mongo = new Mongo(LOCAL_HOST, MONGO_PORT);
    DB db = mongo.getDB(MONGO_DB);

    DBCollection collection = db.getCollection(PATHWAY_COLLECTION);

    DBCursor cursor = collection.find(new BasicDBObject("target", 878L));

    Set<Long> reactionIds = new HashSet<>();

    cursor.forEach(dbObject -> {
      BasicDBList pathway = (BasicDBList) dbObject.get("path");
       if (pathway != null) {
         pathway.forEach(step -> {
           BasicDBObject dbStep = (BasicDBObject) step;

           BasicDBList ids = (BasicDBList) ((BasicDBObject) dbStep.get("attr")).get("reaction_ids");
           if (ids != null) {
             ids.forEach(id -> {
               reactionIds.add((Long) id);
             });
           }
         });
       }

    });

    BufferedWriter writer = new BufferedWriter(new FileWriter(new File("/mnt/shared-data/Gil/vanillin/relevant_reactions.txt")));
    for (Long reactionId : reactionIds) {
      writer.write(reactionId.toString());
      writer.newLine();
    }
    writer.close();

    System.out.println("Number of reactions: " + reactionIds.size());
  }
}
