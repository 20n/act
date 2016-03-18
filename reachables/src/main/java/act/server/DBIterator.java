package act.server;

import java.util.Iterator;

import com.mongodb.DBCursor;
import com.mongodb.DBObject;

// wrapper class around DBCursor, so that users do not have to include DBCursor...
public class DBIterator implements Iterator<DBObject>{
  DBCursor cursor;

  DBIterator(DBCursor c) { this.cursor = c; }

  public DBObject next() {
    return this.cursor.next();
  }

  public boolean hasNext() {
    return this.cursor.hasNext();
  }

  public void close() {
    this.cursor.close();
  }

  @Override
  public void remove() {
    // TODO Auto-generated method stub

  }
}
