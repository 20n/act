package act.server.SQLInterface;


import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import act.shared.Chemical;
import act.shared.Reaction;

public class MysqlDBStateless implements DBInterface{
  private Connection dbConnection;

  private String hostname;

  public MysqlDBStateless(String host) {
    this.hostname = host;
    initDB();
  }

  public MysqlDBStateless() {
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
      dbConnection.setAutoCommit(false);
      System.out.println("SQLBasic: " +
          "Connection established.");
    } catch (SQLException e) {
      System.out.println("SQLBasic: " +
          "Error in connection: " + e.getMessage());
      System.exit(1);
    }

  }

  static enum Qs { maxID, getRxnWithUUID, getChem, getRxns, getReactants, getProducts, getName, getRxnNameForIDs, getIDsForChemNames, getSmile }

  private PreparedStatement getQueryStmt(Qs type) {

    try {
      String query;
      switch (type) {
        case maxID: query = "SELECT max(uuid) AS uuid FROM reactions"; break;
        case getRxnWithUUID: query = "SELECT uuid,ec5_number,reaction_name FROM reactions WHERE uuid >= ? AND uuid < ?;"; break;
        case getChem: query = "SELECT pubchem_id,canonical_name,smiles FROM chemicals WHERE uuid = ?"; break;
        case getRxns: query = "SELECT R.reaction_uuid AS uuid FROM ReactionReactantXrefs R WHERE R.reactant_uuid = ?"; break;
        case getReactants: query = "SELECT R.reactant_uuid AS uuid FROM ReactionReactantXrefs R WHERE R.reaction_uuid = ?"; break;
        case getProducts: query = "SELECT P.product_uuid AS uuid FROM ReactionProductXrefs P WHERE P.reaction_uuid = ?"; break;
        case getName: query = "SELECT canonical_name AS name FROM chemicals WHERE uuid = ?"; break;

        case getRxnNameForIDs: query = "SELECT reaction_name FROM reactions WHERE uuid = ?"; break;
        case getIDsForChemNames: query = "SELECT chemical_uuid FROM synonyms WHERE synonym LIKE ? LIMIT 1;"; break;
        case getSmile: query = "SELECT smiles FROM chemicals WHERE uuid = ?"; break;
        default: query = null;
      }
      return dbConnection.prepareStatement(query);
    } catch (SQLException e) {
      System.out.println(": Error in preparing " +
          "the general MySQL query: " +
          e.getMessage());
      System.exit(1);
    }
    return null;
  }

  /*
   * Returns max uuid in reactions table
   * If error, returns -1
   */
  public long getMaxUUIDInReactionsTable() {
    long size = -1;
    try {
      PreparedStatement getMaxID = getQueryStmt(Qs.maxID);
      ResultSet rs = getMaxID.executeQuery();
      if(rs.next()) {
        size = rs.getLong("uuid");
      }
      getMaxID.close();
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
      PreparedStatement getReactions = getQueryStmt(Qs.getRxnWithUUID);
      getReactions.setLong(1, lowUUID);
      getReactions.setLong(2, highUUID);
      ResultSet rs = getReactions.executeQuery();
      while(rs.next()) {
        int uuid = rs.getInt("uuid");
        String ecnum = rs.getString("ec5_number");
        String descrip = rs.getString("reaction_name");
        List<Long> reactants = getReactants(new Long(uuid));
        List<Long> products = getProducts(new Long(uuid));

        Long[] orgIDs = {};
        result.add(new Reaction(uuid, reactants.toArray(new Long[1]),products.toArray(new Long[1]),ecnum,descrip));

      }
      getReactions.close();
      rs.close();

      return result;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  public Chemical getChemical(long uuid) {
    try {
      PreparedStatement getChem = getQueryStmt(Qs.getChem);
      getChem.setLong(1, uuid);
      ResultSet rs = getChem.executeQuery();
      if(rs.next()) {
        return new Chemical(uuid,rs.getLong("pubchem_id"),
            rs.getString("canonical_name"),rs.getString("smiles"));
      }
      getChem.close();
      rs.close();

      return null;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  @Override
  public List<Long> getRxnsWith(Long reactant) {
    try {
      List<Long> rxns = new ArrayList<Long>();
      PreparedStatement getRxns = getQueryStmt(Qs.getRxns);
      getRxns.setLong(1, reactant);
      ResultSet rs = getRxns.executeQuery();
      while (rs.next()) {
        Long toAdd = rs.getLong("uuid");
        rxns.add(toAdd.longValue());
      }
      getRxns.close();
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
      PreparedStatement getReactants = getQueryStmt(Qs.getReactants);
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
      getReactants.close();
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
      PreparedStatement getProducts = getQueryStmt(Qs.getProducts);
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
      getProducts.close();
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
      PreparedStatement getName = getQueryStmt(Qs.getName);
      for(Long r : rxns) {
        getName.setLong(1, r);
        ResultSet rs = getName.executeQuery();
        while (rs.next()) {
          String toAdd = rs.getString("name");
          names.add(toAdd);
        }
        rs.close();
      }
      getName.close();
      return names;
    } catch (SQLException ex) {
      Logger.getLogger(MysqlDB.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  public List<String> convertRxnIDsToNames(Iterable<Long> rxns) {
    PreparedStatement getNames = getQueryStmt(Qs.getRxnNameForIDs);

    ArrayList<String> result = new ArrayList<String>();

    try {
      for(Long r : rxns) {
        getNames.setLong(1, r);
        ResultSet rs = getNames.executeQuery();
        if(rs.next()) {
          result.add(rs.getString("reaction_name"));
        } else {
          System.out.println("Cannot find: " + r);
        }
        rs.close();
      }
      getNames.close();
    } catch (SQLException e) {
      System.err.println("SQLBasic: " + e.getMessage());
    }
    return result;
  }


  public List<Long> convertNamesToIDs(Iterable<String> names) {
    PreparedStatement getIDs = getQueryStmt(Qs.getIDsForChemNames);

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
      getIDs.close();
    } catch (SQLException e) {
      System.err.println("SQLBasic: " + e.getMessage());
    }
    return result;
  }

  @Override
  public List<String> convertIDsToSmiles(List<Long> ids) {
    try {
      List<String> retList = new ArrayList<String>();
      PreparedStatement getSmile = getQueryStmt(Qs.getSmile);
      for(Long id : ids) {
        getSmile.setLong(1,id);
        ResultSet rs = getSmile.executeQuery();
        if(rs.next())
          retList.add(rs.getString("smiles"));
        rs.close();
      }
      getSmile.close();
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
  public List<Long> getRxnsWith(Long compound, Boolean product) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public HashMap<Long, Double> getRarity(Long compound, Boolean product) {
    // TODO Auto-generated method stub
    return null;
  }

}
