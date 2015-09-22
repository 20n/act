package act.server.SQLInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import act.shared.Chemical;
import act.shared.ReactionType;
import act.shared.SimplifiedReaction;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class MongoDBGraph {
  private DBCollection adjacencyList, nodes;
  private Indigo indigo;
  private IndigoInchi indigoInchi;

  public MongoDBGraph(DB mongoDB, String graphName) {
    this.adjacencyList = mongoDB.getCollection(graphName);
    this.nodes = mongoDB.getCollection(graphName + "_nodes");
    indigo = new Indigo();
    indigoInchi = new IndigoInchi(indigo);
  }

  public void addEdge(SimplifiedReaction r) {
    DBObject updateQuery = new BasicDBObject();
    updateQuery.put("_id", r.getSubstrate());

    DBObject edge = new BasicDBObject();
    edge.put("id", r.getUuid());
    edge.put("type", r.getType().name());
    edge.put("originalReaction", r.getFullReaction().toString());
    edge.put("weight", r.getWeight());

    DBObject update = new BasicDBObject();
    update.put("$push", new BasicDBObject("edges." + r.getProduct(), edge));
    adjacencyList.update(updateQuery, update, true, false);
  }

  public void addNode(Chemical c) {
    DBObject chemical = new BasicDBObject();
    chemical.put("_id", c.getUuid());
    chemical.put("SMILES", c.getSmiles());
    chemical.put("InChI", c.getInChI());
    chemical.put("name", c.getShortestName());
    nodes.insert(chemical);
  }

  public void updateNode(Chemical c) {
    DBObject query = new BasicDBObject();
    query.put("_id", c.getUuid());
    DBObject chemical = new BasicDBObject();
    chemical.put("SMILES", c.getSmiles());
    chemical.put("InChI", c.getInChI());
    chemical.put("name", c.getShortestName());
    nodes.update(query, chemical);
  }

  public void setExpanded(Long id, ReactionType type) {
    DBObject updateQuery = new BasicDBObject();
    updateQuery.put("_id", id);
    DBObject update = new BasicDBObject();
    update.put(type.toString(), 1);
    nodes.update(updateQuery, update, true, false);
  }

  public boolean hasExpanded(Long id, ReactionType type) {
    DBObject query = new BasicDBObject();
    query.put("_id", id);
    DBObject node = nodes.findOne(query);
    return node != null && node.containsField(type.name());
  }

  public Long getMinNodeID() {
    DBObject orderBy = new BasicDBObject("_id", 1);
    DBCursor ordered = nodes.find().sort(orderBy);
    return ordered.hasNext() ? (Long) ordered.next().get("_id") : null;
  }

  public Chemical getNode(long id) {
    DBObject query = new BasicDBObject();
    query.put("_id", id);
    DBObject node = nodes.findOne(query);
    if (node == null) {
      return null;
    }
    Chemical chemical = new Chemical(id);
    chemical.setInchi((String) node.get("InChI"));
    chemical.setSmiles((String) node.get("SMILES"));
    chemical.setCanon((String) node.get("name"));

    if (chemical.getSmiles() == null) {
      try {
        IndigoObject mol = indigoInchi.loadMolecule(chemical.getInChI());
        chemical.setSmiles(mol.smiles());
        this.updateNode(chemical);
        //writeSmiles(id, mol.smiles(), indigoInchi.getInchi(mol));
      } catch (Exception e){
        e.printStackTrace();
        //failed to get smiles... ignore
      }
    }
    return chemical;
  }

  public List<SimplifiedReaction> getReactionsBetween(long substrate, long product) {
    DBObject query = new BasicDBObject();
    query.put("_id", substrate);
    DBObject keys = new BasicDBObject();
    keys.put("edges." + product, 1);
    DBObject res = adjacencyList.findOne(query, keys);
    DBObject edges = (BasicDBObject) res.get("edges");
    BasicDBList reactionDBObjects = (BasicDBList) edges.get(""+product);
    List<SimplifiedReaction> reactions = new ArrayList<SimplifiedReaction>();
    for (Object r : reactionDBObjects) {
      DBObject label = (DBObject) r;
      Long rxnID = (Long) label.get("id");
      ReactionType type = ReactionType.valueOf((String) label.get("type"));
      Double weight = (Double) label.get("weight");
      SimplifiedReaction reaction = new SimplifiedReaction(rxnID, substrate, product, type);
      reaction.setWeight(weight);
      reactions.add(reaction);
    }
    return reactions;
  }

  public List<Long> getProductsFrom(long id) {
    DBObject query = new BasicDBObject();
    query.put("_id", id);
    DBObject chemical = adjacencyList.findOne(query);
    if (chemical == null) return new ArrayList<Long>();
    DBObject edges = (BasicDBObject) chemical.get("edges");

    List<Long> dests = new ArrayList<Long>();
    Set<String> destStrings = edges.keySet();
    for (String dest : destStrings) {
      Long product = Long.parseLong(dest);
      dests.add(product);
    }
    return dests;
  }

  public long getNumNodes() {
    return nodes.count();
  }

  public DBIterator getNodeIDIterator() {
    return new DBIterator(nodes.find(null, new BasicDBObject("_id", 1)));
  }

  public Long getNextNodeID(DBIterator it) {
    if (it.hasNext()) {
      return (Long) it.next().get("_id");
    }
    return null;
  }

  public Chemical getNode(String inchi) {
    DBObject query = new BasicDBObject();
    query.put("InChI", inchi);
    DBObject node = nodes.findOne(query);
    if (node == null) return null;
    Chemical chemical = new Chemical((Long) node.get("_id"));
    chemical.setInchi((String) node.get("InChI"));
    chemical.setSmiles((String) node.get("SMILES"));
    chemical.setCanon((String) node.get("name"));
    return chemical;
  }
}
