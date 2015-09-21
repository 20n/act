package act.shared;

import java.io.Serializable;
import java.util.List;

public class Configuration implements Serializable {
  private static final long serialVersionUID = 42L;

  public enum ReactionSimplification { HardCodedCoFactors, SimpleRarity, PairedRarity }

  public static class ConfigStructure {
    public static final String ConfigFile = "config.xml";
    public static final String actHost = "actHost";
    public static final String actPort = "actPort";
    public static final String actDB = "actDB";
    public static final String renderBadFlag = "renderBadRxnsForDebug";
    public static final String graphMatchingDiff = "graphMatchingDiff";
    public static final String rxnSimplifyUsing = "rxnSimplifyUsing";
    public static final String logLevel = "log-level";
    public static final String diffStart = "diff.start";
    public static final String diffEnd = "diff.end";
    public static final String diffDebugIDList = "diff.debug-dump";
    public static final String theoryROOutfile = "ROsFile";
    public static final String theoryROHierarchyFile = "ROsHierarchyFile";
    public static final String UIversion = "UIversion";
  }

  private static Configuration configInstance;
  public static Configuration getInstance() {
    if (configInstance == null)
      configInstance = new Configuration();
    return configInstance;
  }

  public String actHost;
  public int actPort;
  public String actDB;

  // flags and global params
  public boolean renderBadRxnsForDebug;
  public boolean graphMatchingDiff;
  public ReactionSimplification rxnSimplifyUsing;
  public int logLevel;
  public String theoryROOutfile, theoryROHierarchyFile;
  public int UIversion;

  // diff params
  public int diff_start;
  public int diff_end;
  public List<Integer> debug_dump_uuid;
}
