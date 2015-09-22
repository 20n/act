package act.client;

import act.shared.Configuration;

import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TabLayoutPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Label;

/**
 * The AdminPanel Class that displays configuration settings and other administrator functions.
 * @author JeneLi
 *
 */

public class AdminPanel extends PopupPanel {

  final DockLayoutPanel dockLayoutPanel;
  final LayoutPanel headerPanel;
  final Button exitButton;
  final LayoutPanel footerPanel;
  final Button okayButton;
  final Button cancelButton;
  final TextBox mongoPort;
  final TextBox mongoDB;
  final TextBox mongoHost;
  final TextBox sqlHost;
  final TextBox blockSize;
  final TextBox maxUUID;
  final Button sqlToACTButton;
  final TextBox actToDump;
  final Button exportActToDump;
  final TextBox highUUID;
  final TextBox lowUUID;
  final CheckBox addROsToDB;
  final Button diffReactions;
  final Button assignRarity;
  final Button cmpdsMain;

  public AdminPanel(Configuration config) {
    super();

    dockLayoutPanel = new DockLayoutPanel(Unit.EM);
    setWidget(dockLayoutPanel);
    dockLayoutPanel.setSize("584px", "593px");

    headerPanel = new LayoutPanel();
    dockLayoutPanel.addNorth(headerPanel, 2.6);

    exitButton = new Button("X");
    headerPanel.add(exitButton);
    headerPanel.setWidgetRightWidth(exitButton, 0.0, Unit.PX, 55.0, Unit.PX);
    headerPanel.setWidgetTopHeight(exitButton, 0.0, Unit.PX, 30.0, Unit.PX);

    footerPanel = new LayoutPanel();
    dockLayoutPanel.addSouth(footerPanel, 4.5);

    okayButton = new Button("Okay");
    footerPanel.add(okayButton);
    footerPanel.setWidgetRightWidth(okayButton, 11.0, Unit.PX, 81.0, Unit.PX);
    footerPanel.setWidgetBottomHeight(okayButton, 12.0, Unit.PX, 30.0, Unit.PX);

    cancelButton = new Button("Cancel");
    footerPanel.add(cancelButton);
    footerPanel.setWidgetRightWidth(cancelButton, 98.0, Unit.PX, 81.0, Unit.PX);
    footerPanel.setWidgetBottomHeight(cancelButton, 12.0, Unit.PX, 30.0, Unit.PX);

    TabLayoutPanel bodyPanel = new TabLayoutPanel(1.5, Unit.EM);

    // Server Tab
    LayoutPanel serverPanel = new LayoutPanel();
    bodyPanel.add(serverPanel, "Server", false);

    mongoHost = new TextBox();
    serverPanel.add(mongoHost);
    serverPanel.setWidgetLeftWidth(mongoHost, 142.0, Unit.PX, 173.0, Unit.PX);
    serverPanel.setWidgetTopHeight(mongoHost, 11.0, Unit.PX, 34.0, Unit.PX);
    mongoHost.setText(config.actHost);

    mongoPort = new TextBox();
    serverPanel.add(mongoPort);
    serverPanel.setWidgetLeftWidth(mongoPort, 142.0, Unit.PX, 173.0, Unit.PX);
    serverPanel.setWidgetTopHeight(mongoPort, 48.0, Unit.PX, 34.0, Unit.PX);
    mongoPort.setText(Integer.toString(config.actPort));

    mongoDB = new TextBox();
    serverPanel.add(mongoDB);
    serverPanel.setWidgetLeftWidth(mongoDB, 142.0, Unit.PX, 173.0, Unit.PX);
    serverPanel.setWidgetTopHeight(mongoDB, 88.0, Unit.PX, 34.0, Unit.PX);
    mongoDB.setText(config.actDB);

    Label mongoHostLabel = new Label("Act Mongo Host");
    serverPanel.add(mongoHostLabel);
    serverPanel.setWidgetLeftWidth(mongoHostLabel, 24.0, Unit.PX, 112.0, Unit.PX);
    serverPanel.setWidgetTopHeight(mongoHostLabel, 11.0, Unit.PX, 18.0, Unit.PX);

    Label mongoPortLabel = new Label("Act Mongo Port");
    serverPanel.add(mongoPortLabel);
    serverPanel.setWidgetLeftWidth(mongoPortLabel, 24.0, Unit.PX, 106.0, Unit.PX);
    serverPanel.setWidgetTopHeight(mongoPortLabel, 48.0, Unit.PX, 18.0, Unit.PX);

    Label mongoDBLabel = new Label("Act Mongo DB");
    serverPanel.add(mongoDBLabel);
    serverPanel.setWidgetLeftWidth(mongoDBLabel, 24.0, Unit.PX, 106.0, Unit.PX);
    serverPanel.setWidgetTopHeight(mongoDBLabel, 88.0, Unit.PX, 18.0, Unit.PX);


    // Import Tab
    LayoutPanel importPanel = new LayoutPanel();
    bodyPanel.add(importPanel, "Import", false);
    importPanel.setSize("580px", "478px");

    Label title = new Label("Steps: ");
    importPanel.add(title);
    importPanel.setWidgetLeftWidth(title, 11.0, Unit.PX, 67.0, Unit.PX);
    importPanel.setWidgetTopHeight(title, 11.0, Unit.PX, 26.0, Unit.PX);

    Label stepOne = new Label("1. Read SQL DB and Populate MongoDB Act");
    importPanel.add(stepOne);
    importPanel.setWidgetLeftWidth(stepOne, 11.0, Unit.PX, 274.0, Unit.PX);
    importPanel.setWidgetTopHeight(stepOne, 32.0, Unit.PX, 26.0, Unit.PX);

    Label sqlHostLabel = new Label("SQL DB Host");
    importPanel.add(sqlHostLabel);
    importPanel.setWidgetLeftWidth(sqlHostLabel, 41.0, Unit.PX, 88.0, Unit.PX);
    importPanel.setWidgetTopHeight(sqlHostLabel, 53.0, Unit.PX, 15.0, Unit.PX);

    sqlHost = new TextBox();
    importPanel.add(sqlHost);
    importPanel.setWidgetLeftWidth(sqlHost, 237.0, Unit.PX, 127.0, Unit.PX);
    importPanel.setWidgetTopHeight(sqlHost, 53.0, Unit.PX, 26.0, Unit.PX);
    sqlHost.setText("hz.cs.berkeley.edu");

    Label blockSizeLabel = new Label("Block size for querying SQL DB");
    importPanel.add(blockSizeLabel);
    importPanel.setWidgetLeftWidth(blockSizeLabel, 41.0, Unit.PX, 190.0, Unit.PX);
    importPanel.setWidgetTopHeight(blockSizeLabel, 85.0, Unit.PX, 26.0, Unit.PX);

    blockSize = new TextBox();
    importPanel.add(blockSize);
    importPanel.setWidgetLeftWidth(blockSize, 237.0, Unit.PX, 127.0, Unit.PX);
    importPanel.setWidgetTopHeight(blockSize, 85.0, Unit.PX, 26.0, Unit.PX);
    blockSize.setText("50");

    Label maxUUIDLabel = new Label("Max UUID to populate");
    importPanel.add(maxUUIDLabel);
    importPanel.setWidgetLeftWidth(maxUUIDLabel, 41.0, Unit.PX, 150.0, Unit.PX);
    importPanel.setWidgetTopHeight(maxUUIDLabel, 117.0, Unit.PX, 26.0, Unit.PX);

    maxUUID = new TextBox();
    importPanel.add(maxUUID);
    importPanel.setWidgetLeftWidth(maxUUID, 237.0, Unit.PX, 127.0, Unit.PX);
    importPanel.setWidgetTopHeight(maxUUID, 117.0, Unit.PX, 26.0, Unit.PX);
    maxUUID.setText("75000");

    sqlToACTButton = new Button("Import Act from SQL");
    importPanel.add(sqlToACTButton);
    importPanel.setWidgetLeftWidth(sqlToACTButton, 187.0, Unit.PX, 177.0, Unit.PX);
    importPanel.setWidgetTopHeight(sqlToACTButton, 149.0, Unit.PX, 26.0, Unit.PX);

    Label rarityLabel = new Label("Add rarity measures to reaction substrates/products: ");
    importPanel.add(rarityLabel);
    importPanel.setWidgetLeftWidth(rarityLabel, 11.0, Unit.PX, 314.0, Unit.PX);
    importPanel.setWidgetTopHeight(rarityLabel, 181.0, Unit.PX, 26.0, Unit.PX);

    Label stepOne2 = new Label("1. Export Act to Local Dump");
    importPanel.add(stepOne2);
    importPanel.setWidgetLeftWidth(stepOne2, 11.0, Unit.PX, 190.0, Unit.PX);
    importPanel.setWidgetTopHeight(stepOne2, 201.0, Unit.PX, 26.0, Unit.PX);

    actToDump = new TextBox();
    importPanel.add(actToDump);
    importPanel.setWidgetLeftWidth(actToDump, 21.0, Unit.PX, 118.0, Unit.PX);
    importPanel.setWidgetTopHeight(actToDump, 222.0, Unit.PX, 26.0, Unit.PX);
    actToDump.setText("act-dump.tsv");

    exportActToDump = new Button("Dump from Act to File");
    importPanel.add(exportActToDump);
    importPanel.setWidgetLeftWidth(exportActToDump, 145.0, Unit.PX, 190.0, Unit.PX);
    importPanel.setWidgetTopHeight(exportActToDump, 222.0, Unit.PX, 26.0, Unit.PX);

    Label stepTwo = new Label("2. scp Dump to hz");
    importPanel.add(stepTwo);
    importPanel.setWidgetLeftWidth(stepTwo, 11.0, Unit.PX, 145.0, Unit.PX);
    importPanel.setWidgetTopHeight(stepTwo, 254.0, Unit.PX, 19.0, Unit.PX);

    Label stepThree = new Label("3. Assign Rarity");
    assignRarity = new Button("Assign Rarity");
    importPanel.add(stepThree);
    importPanel.setWidgetLeftWidth(stepThree, 11.0, Unit.PX, 250.0, Unit.PX);
    importPanel.setWidgetTopHeight(stepThree, 279.0, Unit.PX, 19.0, Unit.PX);

    importPanel.add(assignRarity);
    importPanel.setWidgetLeftWidth(assignRarity, 135.0, Unit.PX, 150.0, Unit.PX);
    importPanel.setWidgetTopHeight(assignRarity, 279.0, Unit.PX, 26.0, Unit.PX);

    Label stepFour = new Label("4. Augment db.chemicals with graph; reactions with diff operators");
    importPanel.add(stepFour);
    importPanel.setWidgetLeftWidth(stepFour, 11.0, Unit.PX, 448.0, Unit.PX);
    importPanel.setWidgetTopHeight(stepFour, 311.0, Unit.PX, 19.0, Unit.PX);

    lowUUID = new TextBox();
    lowUUID.setText(Integer.toString(config.diff_start));
    highUUID = new TextBox();
    highUUID.setText(Integer.toString(config.diff_end));

    importPanel.add(lowUUID);
    importPanel.setWidgetLeftWidth(lowUUID, 21.0, Unit.PX, 99.0, Unit.PX);
    importPanel.setWidgetTopHeight(lowUUID, 336.0, Unit.PX, 26.0, Unit.PX);

    importPanel.add(highUUID);
    importPanel.setWidgetLeftWidth(highUUID, 135.0, Unit.PX, 99.0, Unit.PX);
    importPanel.setWidgetTopHeight(highUUID, 336.0, Unit.PX, 26.0, Unit.PX);

    addROsToDB = new CheckBox("Persist");
    addROsToDB.setValue(false);
    diffReactions = new Button("Diff Reaction Products/Substrates");
    importPanel.add(addROsToDB);
    importPanel.setWidgetLeftWidth(addROsToDB, 237.0, Unit.PX, 67.0, Unit.PX);
    importPanel.setWidgetTopHeight(addROsToDB, 343.0, Unit.PX, 19.0, Unit.PX);

    importPanel.add(diffReactions);
    importPanel.setWidgetLeftWidth(diffReactions, 310.0, Unit.PX, 234.0, Unit.PX);
    importPanel.setWidgetTopHeight(diffReactions, 336.0, Unit.PX, 26.0, Unit.PX);

    // TODO Analytics Tab
    LayoutPanel analyticPanel = new LayoutPanel();
    bodyPanel.add(analyticPanel, "Analytics", false);

    cmpdsMain = new Button("CmpdsMain");
    analyticPanel.add(cmpdsMain);
    analyticPanel.setWidgetLeftWidth(cmpdsMain, 41.4, Unit.PCT, 100.0, Unit.PX);
    analyticPanel.setWidgetTopHeight(cmpdsMain, 9.8, Unit.PCT, 30.0, Unit.PX);


    dockLayoutPanel.add(bodyPanel);


  }
}
