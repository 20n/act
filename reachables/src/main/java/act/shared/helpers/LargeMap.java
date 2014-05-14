package act.shared.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class LargeMap<V> {
    static int LARGEMAP_UNIQID = 0;

    // we do not make these static, because we may potentially want to create instances with different parameters
    private String SWAPSPACE = "/tmp/swap";
    private int CACHESZ = 1000; // the in-memory cache, we can safely possibly keep some thousands in memory
    private int MAXSWAP; // = FANOUT ^ DEPTH

    private int FANOUT = 100;// allowing for 100^5 = 10,000,000,000 entries.
    private int DEPTH = 5;

    HashMap<LargeMapKey, V> cache;
    AccessPriority<LargeMapKey> popularity;
    String ID;
    String RootDir;

    public LargeMap() {
        this.cache = new HashMap<LargeMapKey, V>(); // only expected to retain CACHESZ number of elements
        this.popularity = new AccessPriority<LargeMapKey>();
        this.ID = "Hash" + LargeMap.LARGEMAP_UNIQID++;
        this.MAXSWAP = (int)Math.pow(this.FANOUT, this.DEPTH);
        this.RootDir = this.SWAPSPACE + "/" + this.ID;
        try {
        	initSwapSpace();
        } catch (Exception e) {
        	e.printStackTrace();
        	System.err.println("Yuck! Could not init swap space for large map.");
        }
    }
    
    public void put(LargeMapKey key, V val) throws Exception {
        // we just received this, it cannot possibly be as popular as our cache
        if (key.Keyid() >= this.MAXSWAP)
            throw new Exception("Key value too large. Not enough swap space.");
        sendToStorage(key, val);
    }
    
    public boolean containsKey(LargeMapKey key) {
        // if it is in the in-memory cache return true
        if (this.cache.containsKey(key))
            return true;
        // else check if it is on disk
        return checkIfInStorage(key);
    }

    public V get(LargeMapKey key) throws Exception {
        if (this.cache.containsKey(key)) {
            this.popularity.changePriority(key, this.popularity.get(key) + 1);
            return this.cache.get(key);
        }
        
        // else get objects from storage, put into cache and boot the least popular one out

        if (this.cache.size() >= this.CACHESZ) {
            // boot least popular one
            LargeMapKey k = this.popularity.removeLeastPopular();
            V valBooted = this.cache.remove(k);
            sendToStorage(k, valBooted);
        }
        
        // get requested object from storage
        V val = getFromStorage(key);
        // put (key,val) into cache and popularity
        this.cache.put(key, val);
        this.popularity.add(key, 0);
        
        return val;
    }

    void sendToStorage(LargeMapKey k, V value) throws Exception {
        // the name of the file itself is going to be k.LevelId(0, this.FANOUT) which is why we do not iterate to the last level
        String path = this.RootDir;
        for (int level = this.DEPTH - 1; level > 0; level--) {
            int levelid = k.LevelId(level, this.FANOUT);
            path += "/" + levelid;
            new File(path).mkdir();
        }
        // System.out.println("path = " + path + "; file = " + k.LevelId(0, this.FANOUT));
        serialize(value, path, k.LevelId(0, this.FANOUT) + "");
    }

    private String getPathForKey(LargeMapKey k) {
        String path = this.RootDir;
        for (int level = this.DEPTH - 1; level > 0; level--) {
            int levelid = k.LevelId(level, this.FANOUT);
            path += "/" + levelid;
        }
        return path;
    }
    
    V getFromStorage(LargeMapKey k) throws Exception {
        String path = getPathForKey(k);
        return deserialize(path, k.LevelId(0, this.FANOUT) + "");
    }
    
    boolean checkIfInStorage(LargeMapKey k) {
        String path = getPathForKey(k);
        File f = new File(path, k.LevelId(0, this.FANOUT) + "");
        return f.exists();
    }

    private void initSwapSpace() throws Exception {
        File f = new File(this.RootDir);
        System.out.println("Purge + Initialize new swap space: " + f);
        if (f.exists())
            try { delete(f); } catch (IOException ex) { throw new Exception("Fatal error: Could not delete old: " + f); }

        boolean success = f.mkdirs();
        if (!success)
            throw new Exception("Fatal error: Could not init swap space: " + f);

        System.out.println("Swap space: " + this.RootDir + " created");
    }
    
    void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            throw new FileNotFoundException("Failed to delete file: " + f);
        }
    }

    private void serialize(V data, String path, String filename) throws Exception {
        File f = new File(path, filename);
        // System.out.println("Serializing to " + f.getAbsolutePath());
        // System.out.format("\t Object [%s]: %s\n", data.getClass().toString(), data.toString());

        FileOutputStream fos = new FileOutputStream(f);
        ObjectOutputStream out = new ObjectOutputStream(fos);
        out.writeObject(data);
        out.close();

    }

    private V deserialize(String path, String filename) throws Exception {
        File f = new File(path, filename);
        // System.out.println("Deserializing from " + f.getAbsolutePath());

        FileInputStream fis = new FileInputStream(f);
        ObjectInputStream ois = new ObjectInputStream(fis);
        V obj = (V) ois.readObject();
        ois.close();
        return obj;
    }

    public static void UnitTest() throws Exception {
        LargeMap<String> lotsOfStrings = new LargeMap<String>();
        for (int i = 0; i < 1000; i++) {
            // System.out.println("Adding key: " + i);
            lotsOfStrings.put(new TestKey(i), "stringid" + i);
        }
        for (int i = 0; i < 1000; i++) {
            // System.out.println("Adding key: " + i);
            String val = lotsOfStrings.get(new TestKey(i));
            // System.out.println("test \"stringid" + i + "\" ==? \"" + val + "\"");
            if (!("stringid" + i).equals(val)) {
               throw new UnsupportedOperationException("Unit test on largemap failed.");
            }
        }
        for (int i = 1000; i < 1500; i++) {
            // System.out.println("Adding key: " + i);
            lotsOfStrings.put(new TestKey(i), "stringid" + i);
        }
        for (int i = 200; i < 1200; i++) {
            // System.out.println("Adding key: " + i);
            String val = lotsOfStrings.get(new TestKey(i));
            // System.out.println("test \"stringid" + i + "\" ==? \"" + val + "\"");
            if (!("stringid" + i).equals(val)) {
               throw new UnsupportedOperationException("Unit test on largemap failed.");
            }
        }
    }
}

class AccessPriority<K> {

    HashMap<K, Integer> count;
    List<K> elem;

    public AccessPriority() {
        this.elem = new ArrayList<K>();
        this.count = new HashMap<K, Integer>();
    }

    K removeLeastPopular() {
        // this assumes the invariant that when you add or change
        K e = this.elem.remove(0); // remove from ordered list
        this.count.remove(e); // remove from hashmap of counts too
        return e;
    }

    void changePriority(K ofKey, int newP) {
        int oldP = this.count.get(ofKey).intValue();
        if (oldP == newP)
            return;
        this.count.remove(ofKey); // remove from hashmap of counts
        this.elem.remove(ofKey); // remove from ordered list
        add(ofKey, newP);
    }

    void add(K key, int priority) {

        int newpos = 0;
        while (newpos < this.elem.size() && this.count.get(this.elem.get(newpos)).intValue() < priority)
            newpos++;
        if (newpos == this.elem.size())
            this.elem.add(key);
        else
            this.elem.add(newpos, key);
        this.count.put(key, new Integer(priority)); // put into hashmap with new priority
    }

    int get(K key) {
        return this.count.get(key).intValue();
    }
}