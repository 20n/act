package act.server;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.rpc.ServiceException;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;

public class Canonicalizer {

  public Canonicalizer(MongoDB mongoDB) {
  }

  public HashMap<String, List<Chemical>> canonicalizeAll(List<String> commonNames) {

    HashMap<String, List<Chemical>> all = new HashMap<String, List<Chemical>>();
    for (String name : commonNames)
      all.put(name, canonicalize(name));
    for (String name : commonNames)
      if (all.get(name).size() > 0 && all.get(name).get(0).getUuid() >= 0) // not a spellaid
        Logger.printf(0, "Name: %s\n- Exact match: %s\n", name, all.get(name).get(0).getCanon());
    return all;
  }

  public List<Chemical> canonicalize(String synonym) {
    String name = synonym; // "benzene";
    String nameSource = "All databases", result = null;

    Service service = new Service();
    Call call;
    try {
      call = (Call) service.createCall();

      String endpoint = "http://chemspell.nlm.nih.gov/axis/SpellAid.jws";
      call.setTargetEndpointAddress(new URL(endpoint));

      // name=args[0];
      // nameSource=args[1];
      call.setOperationName(new QName("http://chemspell.nlm.nih.gov",
          "getSugList"));
      result = (String) call.invoke(new Object[] { new String(name),
          new String(nameSource) });
      Logger.printf(10, "For %s\nGot canonical names : \n%s\n", synonym, result);
    } catch (ServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (MalformedURLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (RemoteException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    List<Chemical> chemicals = new ArrayList<Chemical>();
    String[] chemStrs = result.split("\n");
    boolean isApproximateMatch = chemStrs.length > 0 && chemStrs[0].equals("<SpellAid>"); // else it will be <Synonym>
    for (String chems : chemStrs) {
      if (!chems.startsWith("<Name>"))
        continue;
      String namestr = chems.trim();
      namestr = namestr.substring(6, namestr.length() - 7); // trim the <Name></Name> away
      chemicals.add(new Chemical(
          isApproximateMatch? -1 : 0 /*uuid*/,
          0L/*pubchemid*/, namestr, ""));
    }
    /*
    for (String[] chemStr : chemicalStrings) {
      int uuid, pc_id; String canon, smiles;
      uuid = Integer.parseInt(chemStr[0]); pc_id = Integer.parseInt(chemStr[1]);
      canon = chemStr[2]; smiles = chemStr[3];
      chemicals.add(new Chemical(uuid, pc_id, canon, smiles));
    }
    */
    return chemicals;
  }
}
