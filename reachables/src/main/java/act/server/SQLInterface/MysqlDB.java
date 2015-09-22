package act.server.SQLInterface;


import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import act.shared.Chemical;
import act.shared.Reaction;

public class MysqlDB implements DBInterface{
  private Connection dbConnection;
  private PreparedStatement getRxns, getRxnsProd, getReactants, getProducts, getName;
  private PreparedStatement getMaxID, getReactions, getChem, getSmile, getIDs, getReactionName;

  private String hostname;

  public MysqlDB(String host) {
    this.hostname = host;
    initDB();
  }

  public MysqlDB() {
    this.hostname = "localhost";
    initDB();
  }

  private void initDB() {
    String dbHost = this.hostname;
    String dbPort = "3306";
    String dbName = "pathway";
    String username = "root";
    String password = "pathway";

    try {
      Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      System.out.println("SQLBasic: " +
          "JDBC driver not found.");
      System.out.println("SQLBasic: Error description: " +
          e.getMessage());
      System.exit(1);
    }

    try {
      String dbUrl = "jdbc:mysql://";
      dbUrl += dbHost;
      dbUrl += ":" + dbPort;
      dbUrl += "/" + dbName;
      dbUrl += "?user=" + username;
      dbUrl += "&password=" + password;
      System.out.println("SQLBasic: " +
          "Attempting to establish a connection...");
      dbConnection = DriverManager.getConnection(dbUrl);
      System.out.println("SQLBasic: " +
          "Connection established.");
    } catch (SQLException e) {
      System.out.println("SQLBasic: " +
          "Error in connection: " + e.getMessage());
      System.exit(1);
    }

    StringBuilder getRxnsQuery = new StringBuilder("SELECT R.reaction_uuid AS uuid FROM ReactionReactantXrefs R WHERE R.reactant_uuid = ?");
    StringBuilder getRxnsProdQuery = new StringBuilder("SELECT R.reaction_uuid AS uuid FROM ReactionProductXrefs R WHERE R.product_uuid = ?");
    StringBuilder getReactantsQuery = new StringBuilder("SELECT R.reactant_uuid AS uuid FROM ReactionReactantXrefs R WHERE R.reaction_uuid = ?");
    StringBuilder getProductsQuery = new StringBuilder("SELECT P.product_uuid AS uuid FROM ReactionProductXrefs P WHERE P.reaction_uuid = ?");
    StringBuilder getNameQuery = new StringBuilder("SELECT canonical_name AS name FROM chemicals WHERE uuid = ?");
    StringBuilder maxID = new StringBuilder("SELECT max(uuid) AS uuid FROM reactions");
    StringBuilder getRxnsDetail = new StringBuilder("SELECT uuid,ec5_number,reaction_name FROM reactions WHERE uuid >= ? AND uuid < ?;");
    StringBuilder getChemDetail = new StringBuilder("SELECT pubchem_id,canonical_name,smiles FROM chemicals WHERE uuid = ?");
    StringBuilder getSmileQ = new StringBuilder("SELECT smiles FROM chemicals WHERE uuid = ?");
    StringBuilder getIDsQ = new StringBuilder("SELECT chemical_uuid FROM synonyms WHERE synonym LIKE ? LIMIT 1;");
    StringBuilder getReactionNameQ = new StringBuilder("SELECT reaction_name FROM reactions WHERE uuid = ?");

    try {
      getRxns = dbConnection.prepareStatement(getRxnsQuery.toString());
      getRxnsProd = dbConnection.prepareStatement(getRxnsProdQuery.toString());
      getReactants = dbConnection.prepareStatement(getReactantsQuery.toString());
      getProducts = dbConnection.prepareStatement(getProductsQuery.toString());
      getName = dbConnection.prepareStatement(getNameQuery.toString());
      getMaxID = dbConnection.prepareStatement(maxID.toString());
      getReactions = dbConnection.prepareStatement(getRxnsDetail.toString());
      getChem = dbConnection.prepareStatement(getChemDetail.toString());
      getSmile = dbConnection.prepareStatement(getSmileQ.toString());
      getIDs = dbConnection.prepareStatement(getIDsQ.toString());
      getReactionName = dbConnection.prepareStatement(getReactionNameQ.toString());
    } catch (SQLException e) {
      System.out.println(": Error in preparing " +
          "the general MySQL query: " +
          e.getMessage());
      System.exit(1);
    }
  }

  /*
   * Returns max uuid in reactions table
   * If error, returns -1
   */
  public long getMaxUUIDInReactionsTable() {
    long size = -1;
    try {
      ResultSet rs = getMaxID.executeQuery();
      if(rs.next()) {
        size = rs.getLong("uuid");
      }
      rs.close();
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return size;
  }

  /*
   * Returns a list of reaction objects in the range lowUUID (including) to highUUID (excluding)
   * Returns null if error
   */
  public List<Reaction> getReactionWithUUID(long lowUUID, long highUUID) {
    try {
      List<Reaction> result = new ArrayList<Reaction>();
      getReactions.setLong(1, lowUUID);
      getReactions.setLong(2, highUUID);
      ResultSet rs = getReactions.executeQuery();
      while(rs.next()) {
        int uuid = rs.getInt("uuid");
        String ecnum = rs.getString("ec5_number");
        String descrip = rs.getString("reaction_name");
        List<Long> reactants = getReactants(new Long(uuid));
        List<Long> products = getProducts(new Long(uuid));

        result.add(new Reaction(uuid, reactants.toArray(new Long[1]),products.toArray(new Long[1]),ecnum,descrip));

      }
      rs.close();

      return result;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  public Chemical getChemical(long uuid) {
    try {
      getChem.setLong(1, uuid);
      ResultSet rs = getChem.executeQuery();
      if(rs.next()) {
        return new Chemical(uuid,rs.getLong("pubchem_id"),
            rs.getString("canonical_name"),rs.getString("smiles"));
      }
      rs.close();

      return null;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  @Override
  public List<Long> getRxnsWith(Long reactant) {
    return getRxnsWith(reactant, false);
  }

  @Override
  public List<Long> getRxnsWith(Long compound, Boolean product) {
    try {
      List<Long> rxns = new ArrayList<Long>();
      ResultSet rs;
      if (product) {
        getRxnsProd.setLong(1, compound);
        rs = getRxnsProd.executeQuery();
      } else {
        getRxns.setLong(1, compound);
        rs = getRxns.executeQuery();
      }
      while (rs.next()) {
        Long toAdd = rs.getLong("uuid");
        rxns.add(toAdd.longValue());
      }
      rs.close();
      return rxns;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  @Override
  public List<Long> getReactants(Long rxn) {
    try {
      getReactants.setLong(1, rxn);
      ResultSet rs = getReactants.executeQuery();
      ArrayList<Long> reactants = new ArrayList<Long>();
      while (rs.next()) {
        Long reactant = rs.getLong("uuid");
        if (reactants.contains(reactant)) {
          continue;
        }
        reactants.add(reactant);
      }
      rs.close();
      return reactants;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  @Override
  public List<Long> getProducts(Long rxn) {
    try {
      getProducts.setLong(1, rxn);
      ResultSet rs = getProducts.executeQuery();
      List<Long> products = new ArrayList<Long>();
      while (rs.next()) {
        Long toAdd = rs.getLong("uuid");
        if (products.contains(toAdd)) {
          continue;
        }
        products.add(toAdd);
      }
      rs.close();
      return products;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  @Override
  public List<String> getCanonNames(Iterable<Long> rxns) {
    try {
      List<String> names = new ArrayList<String>();
      for(Long r : rxns) {
        getName.setLong(1, r);
        ResultSet rs = getName.executeQuery();
        while (rs.next()) {
          String toAdd = rs.getString("name");
          names.add(toAdd);
        }
        rs.close();
      }
      return names;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }


  public List<String> convertRxnIDsToNames(Iterable<Long> rxns) {
    ArrayList<String> result = new ArrayList<String>();

    try {
      for(Long r : rxns) {
        getReactionName.setLong(1, r);
        ResultSet rs = getReactionName.executeQuery();
        if(rs.next()) {
          result.add(rs.getString("reaction_name"));
        } else {
          System.out.println("Cannot find: " + r);
        }
        rs.close();
      }
    } catch (SQLException e) {
      System.err.println("SQLBasic: " + e.getMessage());
    }
    return result;
  }

  public List<Long> convertNamesToIDs(Iterable<String> names) {
    ArrayList<Long> result = new ArrayList<Long>();

    try {
      for(String n: names) {
        getIDs.setString(1, n);
        ResultSet rs = getIDs.executeQuery();
        if(rs.next()) {
          result.add(rs.getLong("chemical_uuid"));
        } else {
          System.out.println("Cannot find: " + n);
        }
        rs.close();
      }
    } catch (SQLException e) {
      System.err.println("SQLBasic: " + e.getMessage());
    }
    return result;
  }

  @Override
  public List<String> convertIDsToSmiles(List<Long> ids) {
    try {
      List<String> retList = new ArrayList<String>();
      for(Long id : ids) {
        getSmile.setLong(1,id);
        ResultSet rs = getSmile.executeQuery();
        if(rs.next())
          retList.add(rs.getString("smiles"));
        rs.close();
      }
      return retList;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  @Override
  public String getEC5Num(Long rxn) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getDescription(Long rxn) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public HashMap<Long, Double> getRarity(Long compound, Boolean product) {
    // TODO Auto-generated method stub
    return null;
  }

}
