package act.server.GameServer;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServerLog {
  private FileWriter logWriter;
  private static DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

  public ServerLog(String filename) {
    try {
      logWriter = new FileWriter(filename, true);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public synchronized void write(String tag, String message) {
    Date date = new Date();

    try {
      logWriter.write(String.format("%s,%s,%s\n",
          tag,
          dateFormat.format(date),
          message));
      logWriter.flush();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
