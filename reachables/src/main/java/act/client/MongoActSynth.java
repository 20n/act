package act.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.GenePubmedCaseStudy;
import act.shared.Organism;
import act.shared.Parameters;
import act.shared.Path;
import act.shared.ReactionDetailed;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DecoratorPanel;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.RootLayoutPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TabLayoutPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class MongoActSynth implements EntryPoint {
	/**
	 * The message displayed to the user when the server cannot be reached or
	 * returns an error.
	 */
	private static final String SERVER_ERROR = "An error occurred while "
			+ "attempting to contact the server. Please check your network "
			+ "connection and try again.";

	private static final String AdminReadmeHTML = "<b>Steps:</b><ul>"
			+ "<li> Read SQL DB and populate MongoDB Act (<b>Import to Act from SQL</b> below)"
			+ "<li> Add rarity measures to reaction substrates/products: 1) <b>Export Act to Local Dump</b> below, 2) scp dump to hz 3) <b>Act Analytics &rarr; AssignRarity</b>"
			+ "<li> Augment db.chemicals with Graph; reactions with diff operators: Run <b>Diff Reactions</b> below"
			+ "</ul>";
	
	/**
	 * Create a remote service proxy to talk to the server-side Greeting service.
	 */
	private final ActAdminServiceAsync actAdminServer = GWT.create(ActAdminService.class);
	private Configuration config;
	
	/**
	 * This is the entry point method.
	 */
	public void onModuleLoad() {
		actAdminServer.serverInitConfig(
				null, /* config file location is the default config.xml in the war/ directory */
				true, /* be quiet */
				new AsyncCallback<Configuration>() {
					public void onSuccess(Configuration result) {
						config = result;
						System.out.println("Loading UI version: "  + config.UIversion);
						switch(config.UIversion) {
						case 0: loadMainUI_v0();break;
						case 1: loadMainUI_v1();break;
						default: System.err.println("Invalid UI version in config file.");
						}
					}
					public void onFailure(Throwable caught) {
						throwErrorWindow(caught);
					}
				});
	}

	/**
	 * loadMainUI() loads the core client-side UI elements.
	 */
	@SuppressWarnings("deprecation")
	private void loadMainUI_v1() {
		final SuggestBox commandBox;
		final Button adminButton;
		final PushButton searchButton;
		final AdminPanel adminPanel;
		final RootLayoutPanel rootPanel;
	
		rootPanel = RootLayoutPanel.get();
		
		final DockLayoutPanel mainPanel = new DockLayoutPanel(Unit.EM);
		rootPanel.add(mainPanel);
		mainPanel.setSize("100pct", "100pct");
		
		adminPanel = new AdminPanel(config);
		RootPanel.get().add(adminPanel);
		adminPanel.center();
		adminPanel.hide();
		
		final LayoutPanel bodyPanel = new LayoutPanel();
		mainPanel.add(bodyPanel);
		bodyPanel.setSize("100pct", "100pct");
		bodyPanel.setStyleName("bodyPanel");
		
		adminButton = new Button("Admin");
//		bodyPanel.add(adminButton);
//		bodyPanel.setWidgetRightWidth(adminButton, 10.0, Unit.PX, 81.0, Unit.PX);
//		bodyPanel.setWidgetTopHeight(adminButton, 10.0, Unit.PX, 30.0, Unit.PX);
		
		MultiWordSuggestOracle commandOracle = new MultiWordSuggestOracle();
		commandOracle.add("synthesize");
		commandOracle.add("synthesize-abstract");
		commandOracle.add("synonym");
		commandOracle.add("admin");
		commandOracle.add("getcommonpath");
		commandBox = new SuggestBox(commandOracle);
		bodyPanel.add(commandBox);
		bodyPanel.setWidgetTopHeight(commandBox, 40.0, Unit.PCT, 3.0, Unit.EM);
		bodyPanel.setWidgetLeftWidth(commandBox, 25.0, Unit.PCT, 49.0, Unit.PCT);
		commandBox.setText("synthesize SMILES:O=C1OCCCCC1");
		commandBox.setStyleName("commandBox");
		
		searchButton = new PushButton("Go");
//		bodyPanel.add(searchButton);
//		searchButton.setHeight("4em");
//		bodyPanel.setWidgetLeftWidth(searchButton, 75.0, Unit.PCT, 2.5, Unit.PCT);
//		bodyPanel.setWidgetTopHeight(searchButton, 40, Unit.PCT, 2.0, Unit.EM);
		
		// TODO Use this link to display JSDraw or some other Molecular Input 
		//final Hyperlink drawLink = new Hyperlink("Draw Structure", false, "drawScreen");
		//bodyPanel.add(drawLink);
		//bodyPanel.setWidgetLeftWidth(drawLink, 45.0, Unit.PCT, 103.0, Unit.PX);
		//bodyPanel.setWidgetTopHeight(drawLink, 45, Unit.PCT, 18.0, Unit.PX);	
		
		//Handlers
		
		class AdminClickHandler implements ClickHandler {
			public void onClick(ClickEvent event) {
				adminPanel.mongoHost.setText(config.actHost);
				adminPanel.mongoPort.setText(Integer.toString(config.actPort));
				adminPanel.mongoDB.setText(config.actDB);
				adminPanel.hide();
				enableMainPanel();
			}
			public void okay() {
				config.actHost = adminPanel.mongoHost.getText();
				config.actPort = Integer.parseInt(adminPanel.mongoPort.getText());
				config.actDB = adminPanel.mongoDB.getText();
				adminPanel.hide();
				enableMainPanel();
			}
			/**
			 * enableMainPanel() reenables all widgets on the main panel
			 */
			private void enableMainPanel() {
				
				adminButton.setEnabled(true);
				searchButton.setEnabled(true);
			}
		}
		
		/**
		 * ClickHandler class for finding pathways
		 * @author JeneLi
		 *
		 */
		class PathwayClickHandler implements ClickHandler, KeyUpHandler {
			String command;
			PathwayPanel pathwayResults;
			String[] filteredCommand;
			int numOps = 10; // number of operators to extract from DB to expand using; assuming abstract expansion...
			int maxChems = 10000; // threshold of number of compounds to stop expansion at; assuming abstract expansion...
			int numPaths = 5; // at least 5 paths should appear in the graph before we threshold search...
			boolean allowKey = false; // a hack now to avoid double key ups
			MongoActSynth synth;

			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					System.out.println(this);
					command = commandBox.getText();
					filteredCommand = command.split("\\s+", 2);
					System.out.println("key up enter");
					if (allowKey) {
						processCommand();
						allowKey = false;
					} else {
						allowKey = true;
					}
				}
			}
			
			public void onClick(ClickEvent event) {
				command = commandBox.getText();
				filteredCommand = command.toLowerCase().split("\\s+", 2);
				processCommand();
			}
			
			private void processCommand() {
				if (filteredCommand[0].equals("synthesize") || filteredCommand[0].equals("synthesize-weighted") ) {
					String name = filteredCommand[1];
					boolean weighted = true;
					if (filteredCommand[0].equals("synthesize")) weighted = false;
					actAdminServer.findPathway(config.actHost, config.actPort, config.actDB, 
							null /* optional source chemical */, name, null /* targetSMILES */, null /*targetCommonNames*/,
							-1 /*num similar if targetSMILES search*/, numOps, maxChems, numPaths,
							-1 /* do not do any ro instantiation on dfs search */, null /* no augmented network */,
							true /* addNativeSources */, true /* findConcrete */, false /* useFnGrpAbstraction */, null /* set of ignored chemicals */,
							weighted, new AsyncCallback<List<Path>>() {
								public void onSuccess(List<Path> result) {
									System.out.println("success");
									if (result.size() == 0) {
										throwErrorWindow(new Exception("No Paths"));
									}else {
										getPathway(result, true);
										System.out.println("done getting pathway");
									}
								}	
								public void onFailure(Throwable caught) {
									throwErrorWindow(caught);
									/*pathwayResults.removeFromParent();*/
								}	
						});
				}else if (filteredCommand[0].equals("synthesize-abstract")) {
					actAdminServer.findPathway(config.actHost, config.actPort, config.actDB, 
							null /* optional source chemical */, filteredCommand[1], null /* targetSMILES */, null /*targetCommonNames*/,
							-1 /*num similar if targetSMILES search*/, numOps, maxChems, numPaths, 
							-1 /* do not do any ro instantiation on dfs search */, null /* no augmented network */,
							true /* addNativeSources */, false /* findConcrete */, false /* useFnGrpAbstraction */, null /* set of ignored chemicals */,
							false /* weighted */, new AsyncCallback<List<Path>>() {
								public void onSuccess(List<Path> result) {
									System.out.println("success");
									if (result.size() == 0) {
										throwErrorWindow(new Exception("No Paths"));
									}else {
										getPathway(result, true);
										System.out.println("done getting pathway");
									}
								}	
								public void onFailure(Throwable caught) {
									throwErrorWindow(caught);
									pathwayResults.removeFromParent();
								}	
						});
				}else if (filteredCommand[0].equals("synonym")) {
					actAdminServer.canonicalizeAll(
							config.actHost, config.actPort, config.actDB, Arrays.asList(GenePubmedCaseStudy.GeneEntries25Words),
							new AsyncCallback<HashMap<String, List<Chemical>>>() {
								public void onFailure(Throwable caught) {
									throwErrorWindow(caught);
									pathwayResults.removeFromParent();
								}

								public void onSuccess(HashMap<String, List<Chemical>> result) {
									getCanonicalNames(result);
								}
							});
				}else if (filteredCommand[0].equals("admin")) {
					adminPanel.show();
					adminPanel.center();
					searchButton.setEnabled(false);
					adminButton.setEnabled(false);
				}else if (filteredCommand[0].equals("getcommonpath")) {
					actAdminServer.getCommonPaths(1, 
							new AsyncCallback<List<Path>>() {
								public void onFailure(Throwable caught) {
									throwErrorWindow(caught);
									//pathwayResults.removeFromParent();
								}
								public void onSuccess(List<Path> result) {
									getPathway(result, true);
								}
					});
				}else {
					throwErrorWindow(new Exception(filteredCommand[0] + " is not a recognized command."));
				}
				// TODO Add entries for other commands
			}
			
			private void getCanonicalNames(HashMap<String, List<Chemical>> rawNameData) {
				pathwayResults = new PathwayPanel(rawNameData);
				rootPanel.add(pathwayResults);
				rootPanel.setWidgetLeftWidth(pathwayResults, 0.0, Unit.PCT, 100, Unit.PCT);
				rootPanel.setWidgetTopHeight(pathwayResults, 0.0, Unit.PCT, 100, Unit.PCT);
			}
			
			private void getPathway(List<List<ReactionDetailed>> rawResultData) {		
				pathwayResults = new PathwayPanel(rawResultData);
				rootPanel.add(pathwayResults);
				rootPanel.setWidgetLeftWidth(pathwayResults, 0.0, Unit.PCT, 100, Unit.PCT);
				rootPanel.setWidgetTopHeight(pathwayResults, 0.0, Unit.PCT, 100, Unit.PCT);				
			}
			
			private void getPathway(List<Path> rawResultData, boolean x) {
				if (pathwayResults == null) {
					pathwayResults = new PathwayPanel(rawResultData, true);
				} else {
					pathwayResults = new PathwayPanel(rawResultData, pathwayResults.ignoreList);
				}
				pathwayResults.researchButton.addClickHandler(new ClickHandler() {
					public void onClick(ClickEvent event) {
						rootPanel.remove(pathwayResults);
						boolean weighted = true;
						if (filteredCommand[0].equals("synthesize")) weighted = false;
						if (pathwayResults.researchCompound != null) {
							filteredCommand[1] = pathwayResults.researchCompound;
						}
						actAdminServer.findPathway(config.actHost, config.actPort, config.actDB, 
								null /* optional source chemical */, filteredCommand[1], null /* targetSMILES */, null /*targetCommonNames*/,
								-1 /*num similar if targetSMILES search*/, numOps, maxChems, numPaths,
								-1 /* do not do any ro instantiation on dfs search */, null /* no augmented network */,
								true /* addNativeSources */, true /* findConcrete */, false /* useFnGrpAbstraction */, pathwayResults.ignoreList /* set of ignored chemicals */,
								weighted, new AsyncCallback<List<Path>>() {
									public void onSuccess(List<Path> result) {
										System.out.println("success");
										if (result.size() == 0) {
											throwErrorWindow(new Exception("No Paths"));
										}else {
											getPathway(result, true);
											System.out.println("done getting pathway");
										}
									}	
									public void onFailure(Throwable caught) {
										throwErrorWindow(caught);
										/*pathwayResults.removeFromParent();*/
									}	
							});
					}
				});
				rootPanel.add(pathwayResults);
				rootPanel.setWidgetLeftWidth(pathwayResults, 0.0, Unit.PCT, 100, Unit.PCT);
				rootPanel.setWidgetTopHeight(pathwayResults, 0.0, Unit.PCT, 100, Unit.PCT);
				System.out.println("added panels");
			}
		}
		
			//Admin Panel Handlers
		adminButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				adminPanel.show();
				adminPanel.center();
				searchButton.setEnabled(false);
				adminButton.setEnabled(false);
			}
		});
		
		adminPanel.exitButton.addClickHandler(new AdminClickHandler());
		adminPanel.cancelButton.addClickHandler(new AdminClickHandler());
		adminPanel.okayButton.addClickHandler(new AdminClickHandler() {
			public void onClick(ClickEvent event) {
				super.okay();
			}
		});
		adminPanel.sqlToACTButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				adminPanel.sqlToACTButton.setEnabled(false);
				actAdminServer.populateActEnzymesFromSQL(config.actHost, config.actPort, config.actDB, 
						adminPanel.sqlHost.getText(), Long.parseLong(adminPanel.blockSize.getText()), 
						Long.parseLong(adminPanel.maxUUID.getText()),
						new AsyncCallback<String>() {
							public void onFailure(Throwable caught) {
								throwErrorWindow(caught);
							}
							public void onSuccess(String result) {
								throwErrorWindow(new Exception("Success"));
							}
						});
			}
		});
		adminPanel.assignRarity.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				actAdminServer.execAnalyticsScript(config.actHost, config.actPort, config.actDB, Parameters.AnalyticsScripts.AssignRarity,
						new AsyncCallback<List<ReactionDetailed>>() {
							public void onFailure(Throwable caught) {
								throwErrorWindow(caught);
							}
							public void onSuccess(List<ReactionDetailed> result) {
								throwErrorWindow(new Exception("Success"));
							}
						}
				);
			}
		});
		adminPanel.exportActToDump.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				actAdminServer.dumpAct2File(config.actHost, config.actPort, config.actDB, adminPanel.actToDump.getText(), 
						new AsyncCallback<String>() {
							public void onFailure(Throwable caught) {
								throwErrorWindow(caught);
							}
							public void onSuccess(String result) {
								throwErrorWindow(new Exception("Success"));
							}
						}
				);
			}
		});
		adminPanel.diffReactions.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				actAdminServer.diffReactions(config.actHost, config.actPort, config.actDB, Long.parseLong(adminPanel.lowUUID.getText()),
						Long.parseLong(adminPanel.highUUID.getText()), null /* whitelist rxns file name */, adminPanel.addROsToDB.getValue(), 
						new AsyncCallback<String>() {
							public void onFailure(Throwable caught) {
								throwErrorWindow(caught);
							}
							public void onSuccess(String result) {
								throwErrorWindow(new Exception("Success"));
							}
						}
				);
			}
		});
		// TODO Add CmpdsMain Handler
		adminPanel.cmpdsMain.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				actAdminServer.execAnalyticsScript(config.actHost, config.actPort, config.actDB, Parameters.AnalyticsScripts.CmpdsMain,
						new AsyncCallback<List<ReactionDetailed>>() {
							public void onFailure(Throwable caught) {
								throwErrorWindow(caught);
							}
							public void onSuccess(List<ReactionDetailed> result) {
								// TODO Throw up a results panel.
							}
						}
				);
			}
		});
		// TODO JSDraw Handler
		/*drawLink.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				LayoutPanel jsDrawPanel = new LayoutPanel();
				Frame jsDraw = new Frame("jsdraw.htm");
				jsDrawPanel.add(jsDraw);
				bodyPanel.add(jsDrawPanel);
				bodyPanel.setWidgetLeftWidth(jsDrawPanel, 10, Unit.PCT, 900, Unit.PX);
				bodyPanel.setWidgetTopHeight(jsDrawPanel, 50, Unit.PCT, 400, Unit.PX);
			}
		});*/
		// Searchbar Handlers
		//searchButton.addClickHandler(new PathwayClickHandler()); 
		commandBox.addKeyUpHandler(new PathwayClickHandler());
		
	}
	
	/**
	 * throwErrorWindow handles errors based on the type of error for debugging purposes.
	 * @param error is the error that is thrown by the application
	 */
	private void throwErrorWindow(Throwable error) {
		// TODO Handle possible errors
		final PopupPanel errorWindow = new PopupPanel();
		Label errorLabel;
		DockLayoutPanel errorPanel = new DockLayoutPanel(Unit.EM);
		errorPanel.setSize("511px", "293px");
		errorWindow.add(errorPanel);
		
		LayoutPanel headerPanel = new LayoutPanel();
		errorPanel.addNorth(headerPanel, 3.2);
		
		Button btnX = new Button("X");
		headerPanel.add(new Label("Error"));
		headerPanel.add(btnX);
		headerPanel.setWidgetLeftWidth(btnX, 430.0, Unit.PX, 81.0, Unit.PX);
		headerPanel.setWidgetTopHeight(btnX, 0.0, Unit.PX, 30.0, Unit.PX);
		btnX.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				errorWindow.hide();
				errorWindow.removeFromParent();
			}
		});
		
		LayoutPanel bodyPanel = new LayoutPanel();
		errorPanel.add(bodyPanel);
		if (error.getLocalizedMessage() != null){
			errorLabel = new Label(error.getLocalizedMessage()); //Change errorLabel based on error
		}else {
			errorLabel = new Label(error.toString());
		}
		bodyPanel.add(errorLabel);
		errorLabel.setStyleName("errorLabel");
		bodyPanel.setWidgetLeftWidth(errorLabel, 100.0, Unit.PX, 400.0, Unit.PX);
		bodyPanel.setWidgetTopHeight(errorLabel, 50.0, Unit.PX, 200.0, Unit.PX);
		
		RootPanel.get().add(errorWindow);
		btnX.setFocus(true);
		errorWindow.center();
	}
	
	
	public void loadMainUI_v0() {
		
		// The Admin interface
		final Button sendButton = new Button("Import from SQL to Act");
		
		final Label actHostLabel = new Label("Act Mongo Host:");
		final TextBox actHostField = new TextBox();
		// actHostField.setText("hz.cs.berkeley.edu");
		actHostField.setText(config.actHost);
		
		final Label sqlHostLabel = new Label("SQL DB Host:");
		final TextBox sqlHostField = new TextBox();
		sqlHostField.setText("hz.cs.berkeley.edu"); // TODO: read this from the config
		
		final Label actPortLabel = new Label("Act Mongo Port:");
		final TextBox actPortField = new TextBox();
		actPortField.setText(config.actPort + "");
		
		final Label actDBLabel = new Label("Act DB:");
		final TextBox actDBField = new TextBox();
		actDBField.setText(config.actDB);
		
		final Label blocksLabel = new Label("BlockSize for querying SQLDB:");
		final TextBox blocksField = new TextBox();
		blocksField.setText("50"); // TODO: read this from the config
		
		final Label upperLimitLabel = new Label("Max UUID to populate:");
		final TextBox upperLimitField = new TextBox();
		upperLimitField.setText("75000"); // TODO: read this from the config
		
		final Label errorLabel = new Label();

		// We can add style names to widgets
		sendButton.addStyleName("sendButton");
		
		final TextBox writeDB2File = new TextBox();
		writeDB2File.setText("act-dump.tsv"); // TODO: read this from the config
		final Button writeDB2FileButt = new Button("Dump from Act to File");

		// create a tabbed panel for the toplevel "Admin", "Synthesizer", "Act Query" tabs
	    TabLayoutPanel tabPanel = new TabLayoutPanel(2.5, Unit.EM);
	    tabPanel.setAnimationDuration(200);
	    tabPanel.getElement().getStyle().setMarginBottom(10.0, Unit.PX);

	    FlowPanel adminContainer = new FlowPanel();
	    FlowPanel synthContainer = new FlowPanel();
	    FlowPanel nameCanonicalizerContainer = new FlowPanel();
	    FlowPanel queryContainer = new FlowPanel();
	    
	    DecoratorPanel decContainer  = new DecoratorPanel();
	    
	    FlowPanel actInfoPanel = new FlowPanel();
	    actInfoPanel.add(actHostLabel);
	    actInfoPanel.add(actHostField);
		
	    actInfoPanel.add(actPortLabel);
	    actInfoPanel.add(actPortField);
		
	    actInfoPanel.add(actDBLabel);
	    actInfoPanel.add(actDBField);
	    
	    decContainer.setWidget(actInfoPanel);
	    adminContainer.add(decContainer);

	    DisclosurePanel readmeContainer = new DisclosurePanel("README FIRST"); 
	    
	    FlowPanel readme = new FlowPanel();
	    final HTML readmeTxt = new HTML(AdminReadmeHTML);
	    readme.add(readmeTxt);
	    
	    readmeContainer.setContent(readme);
	    readmeContainer.setAnimationEnabled(true);
	    adminContainer.add(readmeContainer);
	    
	    DisclosurePanel importFromSQLContainer = new DisclosurePanel("Import to Act from SQL"); 
	    
	    FlowPanel importFromSQLPanel = new FlowPanel();
	    importFromSQLPanel.add(sqlHostLabel);
	    importFromSQLPanel.add(sqlHostField);
		
	    importFromSQLPanel.add(blocksLabel);
	    importFromSQLPanel.add(blocksField);
		
	    importFromSQLPanel.add(upperLimitLabel);
	    importFromSQLPanel.add(upperLimitField);
		
	    importFromSQLPanel.add(sendButton);
	    
	    importFromSQLContainer.setContent(importFromSQLPanel);
	    importFromSQLContainer.setAnimationEnabled(true);
	    adminContainer.add(importFromSQLContainer);

	    DisclosurePanel dumpOutActContainer = new DisclosurePanel("Export Act to Local Dump"); 
	    
	    FlowPanel dumpOutAct = new FlowPanel();
	    dumpOutAct.add(writeDB2File);
	    dumpOutAct.add(writeDB2FileButt);
	    
	    dumpOutActContainer.setContent(dumpOutAct);
	    dumpOutActContainer.setAnimationEnabled(true);
	    adminContainer.add(dumpOutActContainer);

	    DisclosurePanel reactionDiffContainer = new DisclosurePanel("Diff Reactions"); 
	    
	    FlowPanel reactionDiffs = new FlowPanel();
	    int diff_start = config.diff_start, diff_end = config.diff_end;
	    final TextBox lowReactionUUID = new TextBox(); lowReactionUUID.setText(diff_start + ""); lowReactionUUID.setVisibleLength(4);
		final TextBox highReactionUUID = new TextBox(); highReactionUUID.setText(diff_end + ""); highReactionUUID.setVisibleLength(4);
	    final Button reactionDiffsButt = new Button("Diff reactions products/substrates");
	    final CheckBox addROsToDB = new CheckBox("Persist"); addROsToDB.setValue(false);
	    reactionDiffs.add(lowReactionUUID);
	    reactionDiffs.add(highReactionUUID);
	    reactionDiffs.add(addROsToDB);
	    reactionDiffs.add(reactionDiffsButt);
	    
	    reactionDiffContainer.setContent(reactionDiffs);
	    reactionDiffContainer.setAnimationEnabled(true);
	    adminContainer.add(reactionDiffContainer);

		// The pathway synthesis interface:
	    Label targetCmpdL = new Label("Target compound: ");
	    final TextBox targetCmpd = new TextBox();
	    targetCmpd.setText("butan-1-ol"); // TODO: read this from the config
	    synthContainer.add(targetCmpdL);
	    synthContainer.add(targetCmpd);
	    final Button findPathway = new Button("Find Pathway");
	    synthContainer.add(findPathway);
	    final Button findPathwaySpeculate = new Button("Find Abstract Pathway");
	    synthContainer.add(findPathwaySpeculate);
	    final FlexTable pathwayOut = new FlexTable();
	    
	    // attempting to fix the bug when the content is vertically too much for the tabpanel to display inside the flextable
	    final ScrollPanel scrollbars = new ScrollPanel();
	    scrollbars.add(pathwayOut);
	    scrollbars.setHeight("100%");
	    synthContainer.add(scrollbars);
	    
	    
	     /** Instead of just a textbox for synonym entry, we should be using a SuggestBox
	     * See this code examples from the GWT showcase:
    		MultiWordSuggestOracle oracle = new MultiWordSuggestOracle(); // oracle that knows suggestions
    		String[] words = suggestionWords();
    		for (int i = 0; i < words.length; ++i)
      			oracle.add(words[i]);
    		final SuggestBox suggestBox = new SuggestBox(oracle);
    		*/
	     
		// The synonym resolution tab:
	    Label commonNameL = new Label("Lookup chemical: ");
	    final TextBox commonName = new TextBox();
	    commonName.setText("butanol");
	    nameCanonicalizerContainer.add(commonNameL);
	    nameCanonicalizerContainer.add(commonName);
	    final Button nameCanonicalizerButt = new Button("Find Canonical Name (testwords: Shift-Click)");
	    nameCanonicalizerContainer.add(nameCanonicalizerButt);
	    Label orgNameL = new Label("Lookup organism: ");
	    final TextBox organismName = new TextBox();
	    organismName.setText("Ralstonia eutropha");
	    nameCanonicalizerContainer.add(orgNameL);
	    nameCanonicalizerContainer.add(organismName);
	    final Button organismNameLookupButt = new Button("Lookup Organism");
	    nameCanonicalizerContainer.add(organismNameLookupButt);
	    final FlexTable canonicalNames = new FlexTable();
	    
	    // attempting to fix the bug when the content is vertically too much for the tabpanel to display inside the flextable
	    final ScrollPanel canonScrollbar = new ScrollPanel();
	    canonScrollbar.add(canonicalNames);
	    canonScrollbar.setHeight("100%");
	    nameCanonicalizerContainer.add(canonScrollbar);
	    
	    // The machine learning front end interface:
	    final Label rawActQueryL = new Label("Script: ");
	    final ListBox scriptSelectBox = new ListBox(false);
	    for (Parameters.AnalyticsScripts script : Parameters.AnalyticsScripts.values()) {
	    	scriptSelectBox.addItem(script.name());
	    }
	    queryContainer.add(rawActQueryL);
	    queryContainer.add(scriptSelectBox);
	    final Button execAnalytics = new Button("Execute");
	    queryContainer.add(execAnalytics);
	    final FlexTable analyticsOut = new FlexTable();
	    queryContainer.add(analyticsOut);
	    
	    tabPanel.add(adminContainer, "Admin");
	    tabPanel.add(synthContainer, "Synthesizer");
	    tabPanel.add(nameCanonicalizerContainer, "Common Names");
	    tabPanel.add(queryContainer, "Act Analytics");

	    tabPanel.selectTab(1);
	    
	    RootLayoutPanel rp = RootLayoutPanel.get();
	    rp.add(tabPanel); // rp.add(errorLabel); -- adding the error label kills the event handlers for the tab panel

		// Focus the cursor on the name field when the app loads
		actHostField.setFocus(true);
		actHostField.selectAll();

		// Create the popup dialog box
		final DialogBox dialogBox = new DialogBox();
		dialogBox.setText("Remote Procedure Call");
		dialogBox.setAnimationEnabled(true);
		final Button closeButton = new Button("Close");
		// We can set the id of a widget by accessing its Element
		closeButton.getElement().setId("closeButton");
		final HTML serverResponseLabel = new HTML();
		VerticalPanel dialogVPanel = new VerticalPanel();
		dialogVPanel.addStyleName("dialogVPanel");
		dialogVPanel.add(new HTML("<br><b>Server replies:</b>"));
		dialogVPanel.add(serverResponseLabel);
		dialogVPanel.setHorizontalAlignment(VerticalPanel.ALIGN_RIGHT);
		dialogVPanel.add(closeButton);
		dialogBox.setWidget(dialogVPanel);

		// Add a handler to close the DialogBox
		closeButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				dialogBox.hide();
				sendButton.setEnabled(true);
				findPathway.setEnabled(true);
				findPathwaySpeculate.setEnabled(true);
				execAnalytics.setEnabled(true);
				nameCanonicalizerButt.setEnabled(true);
				organismNameLookupButt.setEnabled(true);
				reactionDiffsButt.setEnabled(true);
				writeDB2FileButt.setEnabled(true);
				// sendButton.setFocus(true); -- since we have multiple tabs, it does not make sense to focus on the first one
			}
		});

		// handler for dumping out Act to a named local TSV file
		writeDB2FileButt.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent ev) {
					String outputFile = writeDB2File.getText();
					String actHost = actHostField.getText();
					String actDB = actDBField.getText();
					int actPort = Integer.parseInt(actPortField.getText());

					// Then, we send the input to the server.
					sendButton.setEnabled(false);
					serverResponseLabel.setText("");
					actAdminServer.dumpAct2File(
							actHost, actPort, actDB, outputFile,
							new AsyncCallback<String>() {
								public void onFailure(Throwable caught) {
									// Show the RPC error message to the user
									dialogBox.setText("Remote Procedure Call - Failure");
									serverResponseLabel.addStyleName("serverResponseLabelError");
									serverResponseLabel.setHTML(SERVER_ERROR);
									dialogBox.center();
									closeButton.setFocus(true);
								}

								public void onSuccess(String result) {
									dialogBox.setText("Remote Procedure Call");
									serverResponseLabel.removeStyleName("serverResponseLabelError");
									serverResponseLabel.setHTML(result);
									dialogBox.center();
									closeButton.setFocus(true);
								}
							});
				}
		});

		// handler for initiating reaction diffing between products and substrates
		reactionDiffsButt.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent ev) {
					Long lowUUID = Long.parseLong(lowReactionUUID.getText());
					Long highUUID = Long.parseLong(highReactionUUID.getText());
					boolean addToDB = addROsToDB.getValue();
					String actHost = actHostField.getText();
					String actDB = actDBField.getText();
					int actPort = Integer.parseInt(actPortField.getText());

					// Then, we send the input to the server.
					sendButton.setEnabled(false);
					serverResponseLabel.setText("");
					actAdminServer.diffReactions(
							actHost, actPort, actDB, lowUUID, highUUID, null, addToDB,
							new AsyncCallback<String>() {
								public void onFailure(Throwable caught) {
									// Show the RPC error message to the user
									dialogBox.setText("Remote Procedure Call - Failure");
									serverResponseLabel.addStyleName("serverResponseLabelError");
									serverResponseLabel.setHTML(SERVER_ERROR);
									dialogBox.center();
									closeButton.setFocus(true);
								}

								public void onSuccess(String result) {
									dialogBox.setText("Remote Procedure Call");
									serverResponseLabel.removeStyleName("serverResponseLabelError");
									serverResponseLabel.setHTML(result);
									dialogBox.center();
									closeButton.setFocus(true);
								}
							});
				}
		});
		
		// Create a handler for the import Button
		class ImportSQL2ActHandler implements ClickHandler, KeyUpHandler {
			/**
			 * Fired when the user clicks on the sendButton.
			 */
			public void onClick(ClickEvent event) {
				sendNameToServer();
			}

			/**
			 * Fired when the user types in the nameField.
			 */
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					sendNameToServer();
				}
			}

			/**
			 * Send the name from the nameField to the server and wait for a response.
			 */
			private void sendNameToServer() {
				// First, we validate the input.
				errorLabel.setText("");
				String actHost = actHostField.getText();
				String actDB = actDBField.getText();
				String sqlHost = sqlHostField.getText();
				int actPort = Integer.parseInt(actPortField.getText());
				long reactionHorizontalBlks = Long.parseLong(blocksField.getText());
				long upperLimit = Long.parseLong(upperLimitField.getText());

				// Then, we send the input to the server.
				sendButton.setEnabled(false);
				serverResponseLabel.setText("");
				actAdminServer.populateActEnzymesFromSQL(
						actHost, actPort, actDB, sqlHost, reactionHorizontalBlks, upperLimit, 
						new AsyncCallback<String>() {
							public void onFailure(Throwable caught) {
								// Show the RPC error message to the user
								dialogBox
										.setText("Remote Procedure Call - Failure");
								serverResponseLabel
										.addStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(SERVER_ERROR);
								dialogBox.center();
								closeButton.setFocus(true);
							}

							public void onSuccess(String result) {
								dialogBox.setText("Remote Procedure Call");
								serverResponseLabel
										.removeStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(result);
								dialogBox.center();
								closeButton.setFocus(true);
							}
						});
			}
		}

		// Add a handler to send the name to the server
		sendButton.addClickHandler(new ImportSQL2ActHandler());
		
		// Create a (super) handler for the synthesize pathway button
		abstract class SynthesizePathwayHandler implements ClickHandler, KeyUpHandler {
			/**
			 * Fired when the user clicks on the sendButton.
			 */
			public void onClick(ClickEvent event) {
				askForPathway();
			}

			/**
			 * Fired when the user types in the nameField.
			 */
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					askForPathway();
				}
			}

			/**
			 * Send the name from the nameField to the server and wait for a response.
			 */
			private void askForPathway() {
				// First, we validate the input.
				errorLabel.setText("");
				String target = targetCmpd.getText();
				String actHost = actHostField.getText();
				String actDB = actDBField.getText();
				int actPort = Integer.parseInt(actPortField.getText());

				// Then, we send the input to the server.
				serverResponseLabel.setText("");
				pathwayOut.clear();
				int numOps = 10; // number of operators to extract from DB to expand using; assuming abstract expansion...
				int maxChems = 1000; // threshold of number of compounds to stop expansion at; assuming abstract expansion...
				int numPaths = 50; // at least 100 paths should appear in the graph before we threshold search...
				actAdminServer.findPathway(
						actHost, actPort, actDB, 
						null /* optional source chemical */, target, null /* targetSMILES */,null /*targetCommonNames*/,
						-1 /*num similar if targetSMILES search*/, numOps, maxChems, numPaths,
						-1 /* do not do any ro instantiation on dfs search */, null /* no augmented network */,
						true /* addNativeSources */, isConcrete()  /* findConcrete */, false /* useFnGrpAbstraction */, null /* ignored chemicals */,
						false/*weighted*/, new AsyncCallback<List<Path>>() {
							public void onFailure(Throwable caught) {
								// Show the RPC error message to the user
								dialogBox
										.setText("Remote Procedure Call - Failure");
								serverResponseLabel
										.addStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(SERVER_ERROR);
								dialogBox.center();
								closeButton.setFocus(true);
							}

							public void onSuccess(List<Path> result) {
								dialogBox.setText("Remote Procedure Call");
								serverResponseLabel
										.removeStyleName("serverResponseLabelError");
								// serverResponseLabel.setHTML(result);
								
								int col;
								int curRow = 0;
								int pathNum = 0;
								for(Path path : result) {
									pathwayOut.setWidget(curRow++, col = 0, new HTML("ToFix: Now return Path instead of ReactionDetailed."));
									/* 
									for (int step = 0; step < path.size(); step++) {
										ReactionDetailed r = path.get(step);
										pathwayOut.setWidget(curRow, col = 0, new HTML(getReactionSummary(r)));
										pathwayOut.setWidget(curRow, col = 1, new HTML(getReactantDisplay(r.getSubstrates(), r.getSubstrateURLs())));
										pathwayOut.setWidget(curRow, col = 2, new HTML("&rarr;"));
										pathwayOut.setWidget(curRow, col = 3, new HTML(getReactantDisplay(r.getProducts(), r.getProductURLs())));
										curRow++;
										pathwayOut.setWidget(curRow, 0, new HTML("Structures: "));
										pathwayOut.setWidget(curRow, col = 1, new HTML(getImagesDisplay(r.getSubstrateImages())));
										pathwayOut.setWidget(curRow, col = 3, new HTML(getImagesDisplay(r.getProductImages())));
										
										curRow++;
									}
									*/
									
									pathwayOut.setWidget(curRow, col = 0, new HTML("Path #" + pathNum));
									curRow++;
									pathNum++;
								}
								// TODO: display the reaction path
								
								dialogBox.center();
								closeButton.setFocus(true);
							}
							
							private String getImagesDisplay(String[] images) {
								String html = "<table><tr>";
								for(String s : images) {
									html += "<td>";
									html += "<img src='"+s+"'>";
									html += "</td>";
								}
								
								html += "</tr></table>";
								return html;
							}
							
							private String getReactionSummary(ReactionDetailed r) {
								return "<b>EC" + r.getECNum() + "</b>" + r.getReactionName();
							}

							private String getReactantDisplay(Long[] rIDs, String[] rURLs) {
								String[] join = new String[rIDs.length];
								for (int i =0; i<rIDs.length; i++)
									join[i] = "<a href=" + rURLs[i] + ">" + rIDs[i] + "</a>";
								return Arrays.toString(join);
							}
						});
			}

			abstract boolean isConcrete();
		}
		
		class SynthesizePathwayHandlerConcrete extends SynthesizePathwayHandler {
			@Override
			boolean isConcrete() { findPathway.setEnabled(false); return true; }
		}
		
		class SynthesizePathwayHandlerAbstract extends SynthesizePathwayHandler {
			@Override
			boolean isConcrete() { findPathwaySpeculate.setEnabled(false); return false; }
		}

		// Add a handler to send the name to the server
		SynthesizePathwayHandlerConcrete synthHandler = new SynthesizePathwayHandlerConcrete();
		findPathway.addClickHandler(synthHandler);
		targetCmpd.addKeyUpHandler(synthHandler);
		SynthesizePathwayHandlerAbstract synthHandlerAbs = new SynthesizePathwayHandlerAbstract();
		findPathwaySpeculate.addClickHandler(synthHandlerAbs);
		
		// Create a handler for the analytics script execute button
		class ExecAnalyticsHandler implements ClickHandler, KeyUpHandler {
			/**
			 * Fired when the user clicks on the sendButton.
			 */
			public void onClick(ClickEvent event) {
				execAnalytics();
			}

			/**
			 * Fired when the user types in the nameField.
			 */
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					execAnalytics();
				}
			}

			/**
			 * Send the name from the nameField to the server and wait for a response.
			 */
			private void execAnalytics() {
				// First, we validate the input.
				errorLabel.setText("");
				String script = scriptSelectBox.getItemText(scriptSelectBox.getSelectedIndex());
				String actHost = actHostField.getText();
				String actDB = actDBField.getText();
				int actPort = Integer.parseInt(actPortField.getText());

				// Then, we send the input to the server.
				execAnalytics.setEnabled(false);
				serverResponseLabel.setText("");
				analyticsOut.clear();
				actAdminServer.execAnalyticsScript(
						actHost, actPort, actDB, Parameters.AnalyticsScripts.valueOf(script),
						new AsyncCallback<List<ReactionDetailed>>() {
							public void onFailure(Throwable caught) {
								// Show the RPC error message to the user
								dialogBox
										.setText("Remote Procedure Call - Failure");
								serverResponseLabel
										.addStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(SERVER_ERROR);
								dialogBox.center();
								closeButton.setFocus(true);
							}

							public void onSuccess(List<ReactionDetailed> result) {
								dialogBox.setText("Remote Procedure Call");
								serverResponseLabel
										.removeStyleName("serverResponseLabelError");
								// serverResponseLabel.setHTML(result);
								
								int col;
								for (int step = 0; step < result.size(); step++) {
									ReactionDetailed r = result.get(step);
									analyticsOut.setWidget(step, col = 0, new HTML(getReactionSummary(r)));
									analyticsOut.setWidget(step, col = 1, new HTML(getReactantDisplay(r.getSubstrates(), r.getSubstrateURLs())));
									analyticsOut.setWidget(step, col = 2, new HTML("&rarr;"));
									analyticsOut.setWidget(step, col = 3, new HTML(getReactantDisplay(r.getProducts(), r.getProductURLs())));
								}
								
								dialogBox.center();
								closeButton.setFocus(true);
							}

							private String getReactionSummary(ReactionDetailed r) {
								return "<b>EC" + r.getECNum() + "</b>" + r.getReactionName();
							}

							private String getReactantDisplay(Long[] rIDs, String[] rURLs) {
								String[] join = new String[rIDs.length];
								for (int i =0; i<rIDs.length; i++)
									join[i] = "<a href=" + rURLs[i] + ">" + rIDs[i] + "</a>";
								return Arrays.toString(join);
							}
						});
			}
		}

		// Add a handler to send the name to the server
		ExecAnalyticsHandler execAnalyticsHandler = new ExecAnalyticsHandler();
		execAnalytics.addClickHandler(execAnalyticsHandler);
		
		// Create a handler for the canonical names button
		class CanonicalizerHandler implements ClickHandler, KeyUpHandler {
			// String[] onShiftWhichSet = GenePubmedCaseStudy.ChangWords;
			String[] onShiftWhichSet = GenePubmedCaseStudy.GeneEntries25Words;
			// String[] onShiftWhichSet = GenePubmedCaseStudy.PubmedEntries25Words;
			
			public void onClick(ClickEvent event) {
				if (event.isShiftKeyDown()) {
					// if the control key is down we want to test a bunch of common names
					canonicalizeTestList();
					return;
				}
				getMainName(); 
			}
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					if (event.isShiftKeyDown()) {
						// if the control key is down we want to test a bunch of common names
						canonicalizeTestList();
						return;
					}
					getMainName();
				}
			}
			private void canonicalizeTestList() {
				getMainName(Arrays.asList(onShiftWhichSet));
			}
			private void getMainName(final List<String> commonNames) {
				errorLabel.setText("");
				String actHost = actHostField.getText();
				String actDB = actDBField.getText();
				int actPort = Integer.parseInt(actPortField.getText());

				// Then, we send the input to the server.
				nameCanonicalizerButt.setEnabled(false);
				serverResponseLabel.setText("");
				canonicalNames.clear();
				actAdminServer.canonicalizeAll(
						actHost, actPort, actDB, commonNames,
						new AsyncCallback<HashMap<String, List<Chemical>>>() {
							public void onFailure(Throwable caught) {
								// Show the RPC error message to the user
								dialogBox.setText("Remote Procedure Call - Failure");
								serverResponseLabel.addStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(SERVER_ERROR);
								dialogBox.center();
								closeButton.setFocus(true);
							}

							public void onSuccess(HashMap<String, List<Chemical>> result) {
								dialogBox.setText("Remote Procedure Call");
								serverResponseLabel.removeStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML("<b>Num matched:</b> " + result.size());
								
								int col;
								canonicalNames.setWidget(0, col = 0, new HTML("<b>Common Name</b>"));
								canonicalNames.setWidget(0, col = 1, new HTML("<b>Canonical Name (only first shown)</b>"));
								canonicalNames.setWidget(0, col = 2, new HTML("<b>Pubchem</b>"));
								canonicalNames.setWidget(0, col = 3, new HTML("<b>Smiles</b>"));
								canonicalNames.setWidget(0, col = 4, new HTML("<b>Match Type</b>"));
								int row = 0;
								for (String s : commonNames) {
									List<Chemical> matches = result.get(s);
									row++;
									// only print a single match; if any
									for (int step = 0; step < matches.size() && step < 1; step++) {
										Chemical c = matches.get(step);
										canonicalNames.setWidget(row, col = 0, new HTML(s));
										canonicalNames.setWidget(row, col = 1, new HTML(c.getCanon()));
										canonicalNames.setWidget(row, col = 2, new HTML(getPubchemLink(c.getPubchemID())));
										canonicalNames.setWidget(row, col = 3, new HTML(c.getSmiles()));
										canonicalNames.setWidget(row, col = 4, new HTML(c.getUuid() < 0 ? "SpellAid": "Exact"));
									}
								}
								
								dialogBox.center();
								closeButton.setFocus(true);
							}

							private String getPubchemLink(Long id) {
								return "<a href=http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + id + ">" + id + "</a>"; 
							}
						});
			}
			private void getMainName() {
				errorLabel.setText("");
				String synonym = commonName.getText();
				String actHost = actHostField.getText();
				String actDB = actDBField.getText();
				int actPort = Integer.parseInt(actPortField.getText());

				// Then, we send the input to the server.
				nameCanonicalizerButt.setEnabled(false);
				serverResponseLabel.setText("");
				canonicalNames.clear();
				actAdminServer.canonicalizeName(
						actHost, actPort, actDB, synonym,
						new AsyncCallback<List<Chemical>>() {
							public void onFailure(Throwable caught) {
								// Show the RPC error message to the user
								dialogBox.setText("Remote Procedure Call - Failure");
								serverResponseLabel.addStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(SERVER_ERROR);
								dialogBox.center();
								closeButton.setFocus(true);
							}

							public void onSuccess(List<Chemical> result) {
								dialogBox.setText("Remote Procedure Call");
								serverResponseLabel.removeStyleName("serverResponseLabelError");
								// serverResponseLabel.setHTML(result);
								
								int col;
								canonicalNames.setWidget(0, col = 0, new HTML("<b>Canonical Name</b>"));
								canonicalNames.setWidget(0, col = 1, new HTML("<b>Pubchem</b>"));
								canonicalNames.setWidget(0, col = 2, new HTML("<b>Smiles</b>"));
								canonicalNames.setWidget(0, col = 3, new HTML("<b>Match Type</b>"));
								for (int step = 0; step < result.size(); step++) {
									Chemical c = result.get(step);
									canonicalNames.setWidget(step, col = 0, new HTML(c.getCanon()));
									canonicalNames.setWidget(step, col = 1, new HTML(getPubchemLink(c.getPubchemID())));
									canonicalNames.setWidget(step, col = 2, new HTML(c.getSmiles()));
									canonicalNames.setWidget(step, col = 3, new HTML(c.getUuid() < 0 ? "SpellAid": "Exact"));
								}
								
								dialogBox.center();
								closeButton.setFocus(true);
							}

							private String getPubchemLink(Long id) {
								return "<a href=http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + id + ">" + id + "</a>"; 
							}
						});
			}
		}

		// Add a handler to send the name to the server
		CanonicalizerHandler canonicalizerHandler = new CanonicalizerHandler();
		commonName.addKeyUpHandler(canonicalizerHandler);
		nameCanonicalizerButt.addClickHandler(canonicalizerHandler);
		
		
		// Create a handler for the organism lookup button
		class OrganismLookupHandler implements ClickHandler, KeyUpHandler {
			public void onClick(ClickEvent event) { lookupOrganism(); }
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					lookupOrganism();
				}
			}
			private void lookupOrganism() {
				errorLabel.setText("");
				String organism = organismName.getText();
				String actHost = actHostField.getText();
				String actDB = actDBField.getText();
				int actPort = Integer.parseInt(actPortField.getText());

				// Then, we send the input to the server.
				nameCanonicalizerButt.setEnabled(false);
				serverResponseLabel.setText("");
				canonicalNames.clear();
				actAdminServer.lookupOrganism(
						actHost, actPort, actDB, organism,
						new AsyncCallback<List<Organism>>() {
							public void onFailure(Throwable caught) {
								// Show the RPC error message to the user
								dialogBox.setText("Remote Procedure Call - Failure");
								serverResponseLabel.addStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(SERVER_ERROR);
								dialogBox.center();
								closeButton.setFocus(true);
							}

							public void onSuccess(List<Organism> result) {
								dialogBox.setText("Remote Procedure Call");
								serverResponseLabel.removeStyleName("serverResponseLabelError");
								// serverResponseLabel.setHTML(result);
								
								int col;
								canonicalNames.setWidget(0, col = 0, new HTML("<b>Organism Name</b>"));
								canonicalNames.setWidget(0, col = 1, new HTML("<b>Internal ID</b>"));
								canonicalNames.setWidget(0, col = 2, new HTML("<b>External ID</b>"));
								for (int step = 0; step < result.size(); step++) {
									Organism c = result.get(step);
									canonicalNames.setWidget(step, col = 0, new HTML(c.getName()));
									canonicalNames.setWidget(step, col = 1, new HTML("ID: " + c.getUUID()));
									canonicalNames.setWidget(step, col = 2, new HTML(getNCBI_TaxonomyURL(c.getNCBIid())));
								}
								
								dialogBox.center();
								closeButton.setFocus(true);
							}

							private String getNCBI_TaxonomyURL(Long ncbiID) {
								return "http://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=" + ncbiID;
							}
						});
			}
		}

		// Add a handler to send the organism name to the server
		OrganismLookupHandler organismLookupHandler = new OrganismLookupHandler();
		organismName.addKeyUpHandler(organismLookupHandler);
		nameCanonicalizerButt.addClickHandler(organismLookupHandler);
	}
}
