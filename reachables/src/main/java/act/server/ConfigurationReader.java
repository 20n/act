package act.server;

import java.util.ArrayList;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

import act.shared.Configuration;

public class ConfigurationReader {
	
	ConfigurationReader(String optional_config_location, boolean quiet) {
		Configuration c = Configuration.getInstance();
		   
		// Choose some sensible defaults, and then override them with config file if one exists
		c.actHost = "hz.cs.berkeley.edu";
		c.actPort = 27017;
		c.actDB = "actv01";
		
		c.theoryROOutfile = "ROs.csv";
		c.theoryROHierarchyFile = "ROsHier.csv";
		    
		c.renderBadRxnsForDebug = true;
		c.graphMatchingDiff = false;
		c.rxnSimplifyUsing = Configuration.ReactionSimplification.PairedRarity;
		c.logLevel = 0;
		c.UIversion = 1; // by default use Jene's new UI
		    
		c.diff_start = 1;
		c.diff_end = 50000;
		c.debug_dump_uuid = new ArrayList<Integer>();
		
		try
		{
			// Tutorial: http://commons.apache.org/configuration/userguide-1.2/howto_xml.html
		    XMLConfiguration config;
		    if (optional_config_location == null)
		    	config = new XMLConfiguration(Configuration.ConfigStructure.ConfigFile);
		    else 
		    	config = new XMLConfiguration(optional_config_location);
		    
		    c.actHost = config.getString(Configuration.ConfigStructure.actHost);
		    c.actPort = config.getInt(Configuration.ConfigStructure.actPort);
		    c.actDB = config.getString(Configuration.ConfigStructure.actDB);

		    c.theoryROOutfile = config.getString(Configuration.ConfigStructure.theoryROOutfile);
		    c.theoryROHierarchyFile = config.getString(Configuration.ConfigStructure.theoryROHierarchyFile);
		    
		    c.renderBadRxnsForDebug = config.getBoolean(Configuration.ConfigStructure.renderBadFlag);
		    c.graphMatchingDiff = config.getBoolean(Configuration.ConfigStructure.graphMatchingDiff);
		    c.rxnSimplifyUsing = Enum.valueOf(Configuration.ReactionSimplification.class, config.getString(Configuration.ConfigStructure.rxnSimplifyUsing));
		    c.logLevel = config.getInt(Configuration.ConfigStructure.logLevel);
		    
		    c.UIversion = config.getInt(Configuration.ConfigStructure.UIversion);
		    
		    c.diff_start = config.getInt(Configuration.ConfigStructure.diffStart);
		    c.diff_end = config.getInt(Configuration.ConfigStructure.diffEnd);
		    c.debug_dump_uuid = new ArrayList<Integer>();
		    for (Object o : config.getList(Configuration.ConfigStructure.diffDebugIDList))
		    	c.debug_dump_uuid.add(Integer.parseInt((String)o));
		    if (c.debug_dump_uuid.contains(new Integer(-1)))
		    	c.debug_dump_uuid = null; // if "-1" is in the list then everything is dumped out!!! 
		}
		catch(ConfigurationException cex)
		{
			if (!quiet) {
				System.err.println("[Just a warning] No config file found! Choosen sensible defaults; but you should consider getting a config file.");
				System.err.println("[Just a warning] Continuing with default values.");
			}
			return;
		}
		if (!quiet)
			System.out.println("Successfully read config. Good.");
	}
	
}
