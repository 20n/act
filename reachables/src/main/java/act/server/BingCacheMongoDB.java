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

package act.server;

import act.installer.bing.NameSearchResults;
import act.installer.bing.SearchResult;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.Set;

public class BingCacheMongoDB {
  private DBCollection dbBingCache; // the Bing Search collection is located in a separate database
  private String hostname;
  private int port;
  private String database;
  private DB mongoDB;

  private static final String BING_CACHE_COLLECTION_NAME = "cache";
  private static final String WARNING_MESSAGE =
      "\nWARNING!!!\n" +
          "No \"cache\" collection seems to exist in the \"bingsearch\" Mongo database.\n" +
          "Bing Cache queries should be found in a MongoDB instance running on Chimay on port 27777.\n" +
          "Please check that Chimay (Or your provided database) can be accessed from the host you are running the Bing Searcher on.\n" +
          "Note that it is possible to restore the Bing Search cache dump living on the NAS with " +
          "the \"mongorestore\" command.\n" +
          "If you don't take any action before continuing, all queries will be counted against our " +
          "monthly transaction quota.\n" +
          "Please enter [y] to continue, or [n] if you want to abort:";

  public BingCacheMongoDB(String hostname, int port, String database) {
    this.hostname = hostname;
    this.port = port;
    this.database = database;
    init();
  }

  public void init() {
    try {
      MongoClient mongo = new MongoClient(hostname, port);
      mongoDB = mongo.getDB(database);

    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid host for Mongo Bing Cache server.");
    } catch (MongoException e) {
      throw new IllegalArgumentException("Could not initialize Mongo driver.");
    }
    if (!mongoDB.getCollectionNames().contains(BING_CACHE_COLLECTION_NAME) ||
        mongoDB.getCollection(BING_CACHE_COLLECTION_NAME).count() == 0) {
      System.out.println(WARNING_MESSAGE);
     if (!askConfirmationWhenCacheEmpty()) {
        System.out.println("Aborting the run.");
        System.exit(1);
      }
    }
    this.dbBingCache = mongoDB.getCollection(BING_CACHE_COLLECTION_NAME);
  }

  private boolean askConfirmationWhenCacheEmpty() {
    try(Scanner reader = new Scanner(System.in)) {
      String userDecision = reader.next();
      if (userDecision.equals("n")) {
        return false;
      } else if (userDecision.equals("y")) {
        return true;
      }
    }
    System.out.println("Incorrect input! Please enter either [y] or [n]. Asking again...");
    return askConfirmationWhenCacheEmpty();
  }

  public BasicDBObject getNameSearchResultDBObjectFromName(String formattedName) {
    BasicDBObject whereQuery = new BasicDBObject();
    BasicDBObject allFields = new BasicDBObject();
    whereQuery.put("name", formattedName);
    return (BasicDBObject) dbBingCache.findOne(whereQuery, allFields);
  }

  public void cacheNameSearchResult(NameSearchResults nameSearchResults) {
    BasicDBObject nameSearchResultDBObject  = new BasicDBObject();
    nameSearchResultDBObject.put("name", nameSearchResults.getName());
    Long totalCountSearchResults = nameSearchResults.getTotalCountSearchResults();
    if (totalCountSearchResults >= 0) {
      nameSearchResultDBObject.put("totalCountSearchResults", totalCountSearchResults);
    }
    Set<SearchResult> topSearchResults = nameSearchResults.getTopSearchResults();
    if (topSearchResults != null) {
      BasicDBList topSearchResultsList = new BasicDBList();
      for (SearchResult topSearchResult : topSearchResults) {
        topSearchResultsList.add(topSearchResult.getBasicDBObject());
      }
      nameSearchResultDBObject.put("topSearchResults", topSearchResultsList);
    }
    dbBingCache.save(nameSearchResultDBObject);
  }

  public void updateTotalCountSearchResults(String formattedName, NameSearchResults nameSearchResults) {
    BasicDBObject whereQuery = new BasicDBObject();
    whereQuery.put("name", formattedName);
    // Update the existing document in the cache
    BasicDBObject newTotalCountSearchResults = new BasicDBObject();
    newTotalCountSearchResults.append("$set",
        new BasicDBObject().append("totalCountSearchResults", nameSearchResults.getTotalCountSearchResults()));
    dbBingCache.update(whereQuery, newTotalCountSearchResults);
  }

  public void updateTopSearchResults(String formattedName, NameSearchResults nameSearchResults) {
    BasicDBObject whereQuery = new BasicDBObject();
    whereQuery.put("name", formattedName);
    // Transform Set into BasicDBList for storage
    BasicDBList topSearchResultsList = new BasicDBList();
    for (SearchResult topSearchResult : nameSearchResults.getTopSearchResults()) {
      topSearchResultsList.add(topSearchResult.getBasicDBObject());
    }
    // Update the existing document in the cache
    BasicDBObject newTopSearchResults = new BasicDBObject();
    newTopSearchResults.append("$set", new BasicDBObject().append("topSearchResults", topSearchResultsList));
    dbBingCache.update(whereQuery, newTopSearchResults);
  }
}
