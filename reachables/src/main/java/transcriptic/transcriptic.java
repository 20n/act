package transcriptic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class transcriptic {
  public static String url = "https://secure.transcriptic.com/20nlabs/inventory/samples/";

  public static String httpGet(String urlStr) throws IOException {
    URL url = new URL(urlStr);
    HttpURLConnection conn =
        (HttpURLConnection) url.openConnection();

    conn.addRequestProperty("x-user-email" , "chris@20n.com");
    conn.addRequestProperty("x-user-token" , "QHy8JzodXN5_t8gyDDKe");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Accept", "application/json");

    if (conn.getResponseCode() != 200) {
      throw new IOException(conn.getResponseMessage());
    }

    // Buffer the result into a string
    BufferedReader rd = new BufferedReader(
        new InputStreamReader(conn.getInputStream()));
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = rd.readLine()) != null) {
      sb.append(line);
    }
    rd.close();

    conn.disconnect();
    return sb.toString();
  }

  public static String fetchPlateJSON(String id) throws IOException {
    String fullUrl = url + id;
    System.out.println(httpGet(fullUrl));
    return fullUrl;
  }

  public static void main(String[] args) throws IOException {
    System.out.println(fetchPlateJSON(args[0]));
  }
}
