package act.server.SQLInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.BasicBSONList;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

import act.shared.RONode;
import act.shared.helpers.P;

/**
 * This class contains functions used to find the common paths.
 *
 * @author JeneLi
 *
 */
public class MongoDBPaths extends MongoDB {
  private DBCollection dbOperatorPaths;
  private DBCollection dbOperatorPathSets;

  public MongoDBPaths(String mongoActHost, int port, String dbs) {
     super(mongoActHost, port, dbs);
     init();
  }

  private void init() {
    try {
       this.dbOperatorPaths = mongoDB.getCollection("operpaths");
       this.dbOperatorPathSets = mongoDB.getCollection("operpathsets");
     } catch (MongoException e) {
       throw new IllegalArgumentException("Could not initialize Mongo driver.");
     }
  }

  /**
   * filterReactionByRarity() takes a given reactionID and returns a pair with
   *  the product and reactant that has the highest rarity.
   * @param reactionID
   * @return P<Long, Long> with (Reactant, Product)
   */
  public P<Long, Long> filterReactionByRarity(Long reactionID) {
    DBObject dummy = new BasicDBObject();
    dummy.put("_id", reactionID);
    DBObject reaction = this.dbAct.findOne(dummy);

    BasicDBList products = (BasicDBList) ((DBObject) reaction
        .get("enz_summary")).get("products");
    BasicDBList substrates = (BasicDBList) ((DBObject) reaction
        .get("enz_summary")).get("substrates");
    Double minProdValue = new Double(10000);
    Long minProduct = (long) -1;
    for (int i = 0; i < products.size(); i++) {
      DBObject compound = (DBObject) products.get(i);
      Double rarity = (Double) compound.get("rarity");
      if (rarity < minProdValue) {
        minProdValue = rarity;
        minProduct = (Long) compound.get("pubchem");
      }
    }

    Double minSubsValue = new Double(10000);
    Long minSubstrate = new Long(-1);
    for (int i = 0; i < substrates.size(); i++) {
      DBObject compound = (DBObject) substrates.get(i);
      Double rarity = (Double) compound.get("rarity");
      if (rarity < minSubsValue) {
        minSubsValue = rarity;
        minSubstrate = (Long) compound.get("pubchem");
      }
    }


    return new P<Long, Long>(minSubstrate,minProduct);
  }

  /**
   * Given a tree of RONodes, adds to database.
   */
  public void submitToActROTree(RONode root) {
    List<RONode> list = RONode.flattenTree(root);
    for(RONode r : list) {
      DBObject obj = new BasicDBObject();
      obj.put("_id", r.getID());
      obj.put("count", r.getCount());
      obj.put("ro", r.getRO());
      obj.put("depth", r.getDepth());
      BasicDBList paths = new BasicDBList();
      for(List<Long> p : r.getExampleRxn().keySet()) {
        DBObject pathObj = new BasicDBObject();

        BasicDBList path = new BasicDBList();
        path.addAll(p);
        BasicDBList organisms = new BasicDBList();
        organisms.addAll(r.getExampleRxn().get(p));
        pathObj.put("path", path);
        pathObj.put("organisms",organisms);
        paths.add(pathObj);
      }
      /*BasicDBList children = new BasicDBList();
      for(Integer c : r.getChildren()) {
        children.add(r.getChild(c).getID());
      }
      obj.put("children", children);*/
      obj.put("parent", r.getParentID());
      obj.put("example", paths);
      dbOperatorPaths.insert(obj);
    }
  }

  private RONode dbObjToRONode(DBObject o) {
    RONode r = new RONode((Integer)o.get("ro"),(Long) o.get("_id"));
    //r.setCount((Integer)o.get("count"));
    r.setParentID((Long)o.get("parent"));
    r.setDepth((Integer) o.get("depth"));
    BasicDBList example = (BasicDBList) o.get("example");
    for(Object pathObj : example) {
      BasicDBList path = (BasicDBList) ((BasicDBObject) pathObj).get("path");
      BasicDBList organisms = (BasicDBList) ((BasicDBObject) pathObj).get("organisms");
      List<Long> pList = new ArrayList<Long>();
      for(Object chemid : (BasicDBList)path) {
        pList.add((Long)chemid);
      }
      for(Object org : organisms) {
        r.addExampleRxn(pList,(Long)org);
      }
    }

    return r;
  }

  //If written tree never needs to be fully read, the following may not be useful
  /**
   * Given operator and parent, find the node with that operator and parent.
   * parent adds the found child.
   * @return the found child RONode
   */
  public RONode getChildNode(int ro, RONode parent) {
    DBObject query = new BasicDBObject();
    DBObject roMatch = new BasicDBObject();
    roMatch.put("ro", ro);
    DBObject parentMatch = new BasicDBObject();
    Long parentID = parent != null ? parent.getID() : null;
    parentMatch.put("parent", parentID); //if root parent is null

    BasicDBList andList = new BasicDBList();
    andList.add(roMatch);
    andList.add(parentMatch);
    query.put("$and", andList);
    DBObject obj = dbOperatorPaths.findOne(andList);
    if(obj == null) return null;

    RONode ret = new RONode(ro,(Long) obj.get("_id"));
    //ret.setCount((Integer)obj.get("count"));
    parent.addChild(ret);
    return ret;
  }

  public RONode getNodeByID(Long id) {
    DBObject query = new BasicDBObject();
    query.put("_id",id);
    DBObject o = dbOperatorPaths.findOne(query);
    if(o == null) return null;
    RONode r = dbObjToRONode(o);
    return r;
  }

  /**
   * @param k
   * @return k nodes with the highest counts
   */
  public List<RONode> getTopKNodes(int k) {
    List<RONode> list = new ArrayList<RONode>();
    DBObject greaterThan = new BasicDBObject();
    greaterThan.put("$gt", 1);
    DBObject filter = new BasicDBObject();
    filter.put("depth", greaterThan);
    DBObject orderBy = new BasicDBObject();
    orderBy.put("count", -1);
    DBCursor cursor = dbOperatorPaths.find(filter).sort(orderBy);
    if(cursor == null) return null;
    int i = 0;
    for(DBObject o : cursor) {
      RONode r = dbObjToRONode(o);
      list.add(r);
      i++;
      if(i >= k) break;
    }
    cursor.close();
    return list;
  }

  public P<RONode,List<Integer>> getROPath(Long pathID) {
    RONode curNode = getNodeByID(pathID);
    RONode startNode = curNode;
    if(curNode == null) return null;
    List<Integer> curPath = new LinkedList<Integer>();
    curPath.add(curNode.getRO());
    while (curNode.getParentID() != null) {
      curNode = getNodeByID(curNode.getParentID());
      curPath.add(curNode.getRO());
    }
    Collections.reverse(curPath);
    return new P<RONode,List<Integer>>(startNode,curPath);
  }

  public void addROPathSet(long id, Map<Integer,Integer> ros, List<P<List<Long>,List<Long>>> examples) {
    DBObject obj = new BasicDBObject();
    obj.put("_id", id);
    BasicDBList roList = new BasicDBList();
    int depth = 0;
    for(Integer ro : ros.keySet()) {
      DBObject roObj = new BasicDBObject();
      roObj.put("ro", ro);
      int cnt = ros.get(ro);
      roObj.put("count", cnt);
      roList.add(roObj);
      depth+=cnt;
    }
    obj.put("ros", roList);
    obj.put("depth", depth);

    BasicDBList exampleList = new BasicDBList();
    int count = 0;
    for(P<List<Long>,List<Long>> p : examples) {
      DBObject pathObj = new BasicDBObject();

      BasicDBList path = new BasicDBList();
      path.addAll(p.fst());
      BasicDBList organisms = new BasicDBList();
      organisms.addAll(p.snd());
      pathObj.put("path", path);
      pathObj.put("organisms",organisms);
      exampleList.add(pathObj);
      count++;
    }
    obj.put("paths", exampleList);
    obj.put("count", count);
    dbOperatorPathSets.insert(obj);
  }

  public Map<Integer,Integer> getPathSet(Long id) {
    Map<Integer,Integer> ros = new HashMap<Integer,Integer>();
    BasicDBObject aro = (BasicDBObject) dbOperatorPathSets.findOne(new BasicDBObject("_id",id));
    if(aro==null) return null;
    BasicDBList dbROs = (BasicDBList) aro.get("ros");
    for(Object ro : dbROs) {
      DBObject dbRO = (DBObject)ro;
      ros.put((Integer) dbRO.get("ro"),(Integer) dbRO.get("count"));
    }
    return ros;
  }

  public List<Long> getTopKPathSets(int k, List<Integer> ignoreROs) {
    DBObject greaterThan = new BasicDBObject();
    greaterThan.put("$gt", 1);
    DBObject filter = new BasicDBObject();
    filter.put("count", greaterThan);
    DBObject orderBy = new BasicDBObject();
    orderBy.put("depth", -1);
    orderBy.put("count", -1);


    BasicDBList ignoreDBList = new BasicDBList();
    ignoreDBList.addAll(ignoreROs);
    filter.put("ros.ro", new BasicDBObject("$nin",ignoreDBList));

    DBCursor cursor = dbOperatorPathSets.find(filter).sort(orderBy);
    if(cursor == null) return null;
    int i = 0;

    List<Long> ids = new ArrayList<Long>();
    for(DBObject o : cursor) {
      ids.add((Long) o.get("_id"));
      if(i >= k) break;
      i++;
    }
    cursor.close();
    return ids;
  }

  public Set<P<List<Long>,List<Long>>> getPathSetExamples(long id) {
    Set<P<List<Long>,List<Long>>> examples = new HashSet<P<List<Long>,List<Long>>>();
    BasicDBObject query = new BasicDBObject("_id",id);

    DBObject o = dbOperatorPathSets.findOne(query);
    BasicDBList paths = (BasicDBList) o.get("paths");
    for(Object path : paths) {

      BasicDBList dbpath = (BasicDBList)((DBObject) path).get("path");
      BasicDBList dborganisms = (BasicDBList)((DBObject) path).get("organisms");
      List<Long> pathList = new ArrayList<Long>();
      List<Long> orgList = new ArrayList<Long>();
      for(Object chem : dbpath) {
        pathList.add((Long)chem);
      }
      for(Object org : dborganisms) {
        orgList.add((Long)org);
      }
      examples.add(new P<List<Long>,List<Long>>(pathList,orgList));
    }
    return examples;
  }
}
