package act.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.GenePubmedCaseStudy;
import act.shared.Path;
import act.shared.Reaction;
import act.shared.ReactionDetailed;
import act.shared.ReactionType;
import act.shared.helpers.T;

import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;

/**
 * This panel displays results for pathway synthesis.
 * @author JeneLi
 *
 */
public class PathwayPanel extends Composite {
	public Button researchButton;
	Button returnButton;
	private LayoutPanel pathwayPanel;
	private int colMax = 100;
	private LayoutPanel headerPanel;
	private List<Path> pathList;
	public Set<Long> ignoreList = new HashSet<Long>();
	public String researchCompound;
	private Anchor researchCompoundAnchor;
	private int pathNumber = 0;
	final static String CURVEDARROW = "<img class=\"curvedArrow\"src=\"curvedarrow.png\">";
	final static String LEFTARROW = "<div class=\"leftArrow\"> &larr; </div>";
	final static String RIGHTARROW = "<div class=\"rightArrow\"> &rarr; </div>";
	final static String DOWNARROW = "<div class=\"downArrow\"> &darr; </div>";
	final static int ROW = 0;
	final static int COLUMN = 1;
	
	final static String sampleImage = "http://ggasoftware.com/up/cano_mol1.svg";
	private ScrollPanel scrollPathwayPanel;
	private Button verifyButton;
	private ListBox organismSelection;
	
	public PathwayPanel(List<List<ReactionDetailed>> result) {
		loadUI(false);
		setPathway(new Path());
	}
	
	/**
	 * @wbp.parser.constructor
	 */
	public PathwayPanel(HashMap<String, List<Chemical>> result) {
		loadUI(false);
		this.setCanonicalizer(result);
	}
	
	public PathwayPanel(List<Path> pathList, boolean x) {
		loadUI(true);
		this.pathList = pathList;
		setPathway(pathList.get(pathNumber));
		pathNumber++;
	}
	
	public PathwayPanel(List<Path> pathList, Set<Long> ignore) {
		this(pathList, true);
		this.ignoreList = ignore;
	}
	
	private void loadNextPath() {
		System.out.println("num paths: " + pathList.size());
		System.out.println(pathNumber);
		if (pathNumber < pathList.size() - 1) {
			pathNumber++;
		}else {
			pathNumber = 0;
		}
		setPathway(pathList.get(pathNumber));
		System.out.println(pathNumber);
	}
	
	private void loadPrevPath() {
		if (pathNumber > 0) {
			pathNumber--;
		}else {
			pathNumber = pathList.size() - 1;
		}
		setPathway(pathList.get(pathNumber));
	}
	
	private void loadUI(boolean isMultiple) {
		final DockLayoutPanel dockLayoutPanel = new DockLayoutPanel(Unit.EM);
		
		headerPanel = new LayoutPanel();
		dockLayoutPanel.addNorth(headerPanel, 4.2);
		
		returnButton = new Button("<--- Back");
		headerPanel.add(returnButton);
		headerPanel.setWidgetLeftWidth(returnButton, 2.1, Unit.PCT, 81.0, Unit.PX);
		headerPanel.setWidgetTopHeight(returnButton, 22.2, Unit.PCT, 30.0, Unit.PX);
		
		Label headerLabel = new Label("Pathway");
		headerLabel.setStyleName("pathwayHeader");
		headerPanel.add(headerLabel);
		headerPanel.setWidgetLeftWidth(headerLabel, 25.0, Unit.PCT, 420.0, Unit.PX);
		headerPanel.setWidgetTopHeight(headerLabel, 11.0, Unit.PX, 43.0, Unit.PX);
		
		returnButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				removeFromParent();
			}
		});
		pathwayPanel = new LayoutPanel();
		scrollPathwayPanel = new ScrollPanel(pathwayPanel);
		dockLayoutPanel.add(scrollPathwayPanel);
		
		if (isMultiple) {
			Button nextButton = new Button("Next ---->");
			headerPanel.add(nextButton);
			headerPanel.setWidgetLeftWidth(nextButton, 70, Unit.PCT, 81, Unit.PX);
			headerPanel.setWidgetTopHeight(nextButton, 22.2, Unit.PCT, 30.0, Unit.PX);
			nextButton.addClickHandler(new ClickHandler() {
				public void onClick(ClickEvent event) {
					pathwayPanel.removeFromParent();
					pathwayPanel = new LayoutPanel();
					scrollPathwayPanel.add(pathwayPanel);
					loadNextPath();
				}
			});
			Button prevButton = new Button("<---- Previous");
			headerPanel.add(prevButton);
			headerPanel.setWidgetLeftWidth(prevButton, 15, Unit.PCT, 120, Unit.PX);
			headerPanel.setWidgetTopHeight(prevButton, 22.2, Unit.PCT, 30.0, Unit.PX);
			prevButton.addClickHandler(new ClickHandler() {
				public void onClick(ClickEvent event) {
					pathwayPanel.removeFromParent();
					pathwayPanel = new LayoutPanel();
					scrollPathwayPanel.add(pathwayPanel);
					loadPrevPath();
				}
			});
		}
		
		researchButton = new Button("Run Search Again");
		headerPanel.add(researchButton);
		headerPanel.setWidgetLeftWidth(researchButton, 83, Unit.PCT, 160, Unit.PX);
		headerPanel.setWidgetTopHeight(researchButton, 22.2, Unit.PCT, 30.0, Unit.PX);
		initWidget(dockLayoutPanel);
		setStyleName("pathwayResultPage");
	}
	
	private void setCanonicalizer(HashMap<String, List<Chemical>> result) {
		FlexTable canonicalNames = new FlexTable();
		pathwayPanel.add(canonicalNames);
		canonicalNames.setStyleName("canonicalNameTable");

		canonicalNames.setWidget(0, 0, new HTML("<b>Common Name</b>"));
		canonicalNames.setWidget(0, 1, new HTML("<b>Canonical Name (only first shown)</b>"));
		canonicalNames.setWidget(0, 2, new HTML("<b>Pubchem</b>"));
		canonicalNames.setWidget(0, 3, new HTML("<b>Smiles</b>"));
		canonicalNames.setWidget(0, 4, new HTML("<b>Match Type</b>"));
		int row = 0;
		for (String s : Arrays.asList(GenePubmedCaseStudy.GeneEntries25Words)) {
			List<Chemical> matches = result.get(s);
			row++;
			// only print a single match; if any
			for (int step = 0; step < matches.size() && step < 1; step++) {
				Chemical c = matches.get(step);
				canonicalNames.setWidget(row, 0, new HTML(s));
				canonicalNames.setWidget(row, 1, new HTML(c.getCanon()));
				canonicalNames.setWidget(row, 2, new HTML(getPubchemLink(c.getPubchemID())));
				canonicalNames.setWidget(row, 3, new HTML(c.getSmiles()));
				canonicalNames.setWidget(row, 4, new HTML(c.getUuid() < 0 ? "SpellAid": "Exact"));
			}
		}
	}
	
	private String getPubchemLink(Long id) {
		return "<a href=http://pubchem.ncbi.nlm.nih.gov/summary/summary.cgi?cid=" + id + ">" + id + "</a>"; 
	}
	
	private String getSourceURL(Path pathwayData, Chemical compound) {
		String url = "compounds/" + pathwayData.pathID + "/" + pathwayData.getCompoundList().indexOf(compound) + ".png";
		return url;
	}
	
	private String getArrow(T<Long, ReactionType, Double> triplet) {
		String arrow = null;
		if (ReactionType.CONCRETE.equals(triplet.snd())) {
			arrow = RIGHTARROW;
		}else if (ReactionType.CRO.equals(triplet.snd())) {
			if (triplet.third() > .66) {
				arrow = "<div class=\"rightArrow croHigh\"> &rarr; </div>";
			}else if (triplet.third() > .33) {
				arrow = "<div class=\"rightArrow croMed\"> &rarr; </div>";
			}else {
				arrow = "<div class=\"rightArrow croLow\"> &rarr; </div>";
			}
		}else {
			arrow = "<div class=\"rightArrow ero\"> &rarr; </div>";
		}
		return arrow;
	}
	@SuppressWarnings("deprecation")
	private void setPathway(Path pathwayData) {
		HTMLPanel[] compoundArray = new HTMLPanel[pathwayData.getCompoundList().size()];
		int column = 0;
		int row = 0;
		int compoundCount = 0;
		String arrow = RIGHTARROW;
		verifyButton = new Button("Verify Path");
		pathwayPanel.add(verifyButton);
		pathwayPanel.setWidgetLeftWidth(verifyButton, 20, Unit.PX, 80, Unit.PX);
		pathwayPanel.setWidgetTopHeight(verifyButton, 500, Unit.PX, 30, Unit.PX);
		organismSelection = new ListBox();
		organismSelection.setVisibleItemCount(1);
		organismSelection.addItem("E.Coli","562");
		organismSelection.addItem("Yeast","4932");
		pathwayPanel.add(organismSelection);
		pathwayPanel.setWidgetLeftWidth(organismSelection, 110, Unit.PX, 80, Unit.PX);
		pathwayPanel.setWidgetTopHeight(organismSelection, 500, Unit.PX, 30, Unit.PX);
		class VerifyClickHandler implements ClickHandler {
			private Path pathway;
			private ListBox org;
			public VerifyClickHandler(Path pathwayData, ListBox organism) {
				pathway = pathwayData;
				org = organism;
			}
			public void onClick(ClickEvent event) {
				displaySequence(Long.parseLong(org.getValue(org.getSelectedIndex())), pathway);
			}
		}
		verifyButton.addClickHandler(new VerifyClickHandler(pathwayData, organismSelection));
		// TODO Add link in each image to more information on compound (possible pubchem page) 
		// TODO Add link on arrows to list of genes
		for (Chemical compound : pathwayData.getCompoundList()) {
			if (compoundCount + 1 == pathwayData.getCompoundList().size()) {
				compoundArray[compoundCount] = new HTMLPanel("<img width=200 height=200 src=\"http://localhost:8080/getChemImage?smiles="+compound.getSmiles()+"\">");
				compoundArray[compoundCount].setStyleName("finalCompoundBlock");
				pathwayPanel.add(compoundArray[compoundCount]);
				pathwayPanel.setWidgetLeftWidth(compoundArray[compoundCount], getPosition(row, column)[COLUMN]-130, Unit.PX, 210, Unit.PX);
				pathwayPanel.setWidgetTopHeight(compoundArray[compoundCount], 100, Unit.PX, 210, Unit.PX);
			}else if (column < colMax) {
				T<Long, ReactionType, Double> reactionTriplet = pathwayData.getEdgeList().get(compoundCount);
				if (compoundCount == 0) {
					compoundArray[compoundCount] = new HTMLPanel(getArrow(reactionTriplet) + "<div class=\"reactionID\">" + reactionTriplet.fst() + ", " + reactionTriplet.third() +  "</div>");
					Image compoundImage = getCompoundImage(compound);
					compoundImage.setPixelSize(200,200);
					compoundImage.setStyleName("compound");
					compoundImage.addClickHandler(new IgnoreCompoundHandler(compound.getUuid(), compoundImage));
					compoundArray[compoundCount].add(compoundImage);
					System.out.println(compoundArray[compoundCount]);
					compoundArray[compoundCount].setStyleName("compoundBlock");
				}else {
					compoundArray[compoundCount] = new HTMLPanel(getArrow(reactionTriplet) + "<div class=\"reactionID\">" + reactionTriplet.fst() + ", " + reactionTriplet.third() + "</div>");
					compoundArray[compoundCount].setStyleName("compoundBlock");
					Image compoundImage = getCompoundImage(compound);
					
					compoundImage.setPixelSize(200,200);
					compoundImage.setStyleName("compound");
					compoundArray[compoundCount].add(compoundImage);
					compoundImage.addClickHandler(new IgnoreCompoundHandler(compound.getUuid(), compoundImage));
				}
				HTMLPanel secondaryCompoundBlock = getSecondaryCompounds(pathwayData.getReactants().get(compoundCount), pathwayData.getProducts().get(compoundCount), compound);
				compoundArray[compoundCount].add(secondaryCompoundBlock);
				pathwayPanel.add(compoundArray[compoundCount]);
				pathwayPanel.setWidgetLeftWidth(compoundArray[compoundCount], getPosition(row, column)[COLUMN], Unit.PX, 600, Unit.PX);
				pathwayPanel.setWidgetTopHeight(compoundArray[compoundCount], getPosition(row, column)[ROW], Unit.PX, 400, Unit.PX);
				compoundCount++;
				column++;
				// TODO Change arrow type based on ROs and add link to gene list
			}else if (column == colMax) {
				T<Long, ReactionType, Double> reactionTriplet = pathwayData.getEdgeList().get(compoundCount);
				compoundArray[compoundCount] = new HTMLPanel(DOWNARROW + "<div class=\"reactionID\">" + reactionTriplet.fst() + ", " + reactionTriplet.third() + "</div>");
				Image compoundImage = getCompoundImage(compound);
				compoundImage.setPixelSize(200,200);
				compoundImage.setStyleName("compound");
				compoundArray[compoundCount].add(compoundImage);
				HTMLPanel secondaryCompoundBlock = getSecondaryCompounds(pathwayData.getReactants().get(compoundCount), pathwayData.getProducts().get(compoundCount), compound);
				compoundImage.addClickHandler(new IgnoreCompoundHandler(compound.getUuid(), compoundImage));
				compoundArray[compoundCount].add(secondaryCompoundBlock);
				compoundArray[compoundCount].setStyleName("compoundBlock");
				pathwayPanel.add(compoundArray[compoundCount]);
				pathwayPanel.setWidgetLeftWidth(compoundArray[compoundCount], getPosition(row, column)[COLUMN], Unit.PX, 600, Unit.PX);
				pathwayPanel.setWidgetTopHeight(compoundArray[compoundCount], getPosition(row, column)[ROW], Unit.PX, 400, Unit.PX);
				compoundCount++;
				column = 0;
				row++;
				if (arrow == RIGHTARROW) {
					arrow = LEFTARROW;
				}else {
					arrow = RIGHTARROW;
				}
			}
		}
	}

	private void displaySequence(Long organism, Path path) {
//		// Top: 540 Left: 20
//		//Verifier verify = new Verifier(mongo);
		/*
		FlexTable verifyTable = new FlexTable();
		int rowCount = 0;
		for ( Reaction rxn : path.getReactionList()) {
			if (!result.getResult()) {
				verifyTable.addCell(rowCount);
				verifyTable.setHTML(rowCount, 0, rowCount + ". ");
				String contents = "<div>Substrates: ";
				for (Long cmpd : rxn.getSubstrates()) {
					contents = contents + mongo.getChemicalFromChemicalUUID(cmpd).getShortestName() + ", ";
				}
				contents = contents + "Enzyme: " + rxn.getECNum() + "<br /><ul><li>Interactions: ";
				
				for (String enz : result.getEnzymes()) {
					contents = contents + enz + ", ";
				}
				for (Long cmpd : result.getSubstrates()) {
					contents = contents + mongo.getChemicalFromChemicalUUID(cmpd).getShortestName() + ", ";
				}
				contents = contents + "</li></ul></div>";
				verifyTable.setHTML(rowCount, 1, contents);
				rowCount++;
			}
		}
		pathwayPanel.add(verifyTable);
		pathwayPanel.setWidgetTopHeight(verifyTable, 540, Unit.PX, 1000, Unit.PX);
		pathwayPanel.setWidgetLeftWidth(verifyTable, 20, Unit.PX, 1000, Unit.PX);*/
	}
	private Image getCompoundImage(Chemical compound) {
		String smiles = "";
		if (compound.getSmiles() != null)
			smiles = compound.getSmiles();
		String url = "http://localhost:8080/getChemImage?smiles="+URL.encode(smiles) +"&chemid="+compound.getUuid();
		Image compoundImage = new Image();
		compoundImage.setUrl(url);
		return compoundImage;
	}
	private class IgnoreCompoundHandler implements ClickHandler {
		private Long ignore;
		private Image image;
		public IgnoreCompoundHandler(Long compound, Image image) {
			this.ignore = compound;
			this.image = image;
		}
		public void onClick(ClickEvent event) {
			if (ignoreList.contains(ignore)) {
				ignoreList.remove(ignore);
				image.removeStyleName("ignored");
			}else {
				System.out.println("Ignoring: " + ignore);
				ignoreList.add(ignore);
				image.addStyleName("ignored");
				System.out.println("ignoring");
				for (Long i : ignoreList) {
					System.out.println(i);
				}
			}
		}
	}
	
	private class ReSearchHandler implements ClickHandler, MouseOverHandler, MouseOutHandler {
		private Chemical compound;
		private Anchor compoundAnchor;
		private PopupPanel compoundImage;
		public ReSearchHandler(Chemical compound, Anchor compoundAnchor) {
			this.compound = compound;
			this.compoundAnchor = compoundAnchor;
			this.compoundImage = new PopupPanel();
			
			
			Image image = getCompoundImage(compound);
			
			image.setSize("400px", "200px");
			this.compoundImage.add(image);
			this.compoundImage.setGlassEnabled(true);
			this.compoundImage.setSize("400px", "200px");
		}
		public void onClick(ClickEvent event) { 
			if (researchCompound != null) {
				researchCompoundAnchor.setStyleName("deactivated");
				if (researchCompoundAnchor == compoundAnchor) {
					researchCompound = null;
					researchCompoundAnchor = null;
				}else {
					researchCompound = compound.getShortestName();
					researchCompoundAnchor =  compoundAnchor;
					researchCompoundAnchor.setStyleName("activated");
				}
			}else {
				researchCompound = compound.getShortestName();
				researchCompoundAnchor =  compoundAnchor;
				researchCompoundAnchor.setStyleName("activated");
			}
			
		}
		public void onMouseOver(MouseOverEvent event) {
			RootPanel.get().add(compoundImage);
			compoundImage.center();
		}
		public void onMouseOut(MouseOutEvent event) {
			compoundImage.hide();
		}
	}
	
	@SuppressWarnings("deprecation")
	private HTMLPanel getSecondaryCompounds(Set<Chemical> reactants, Set<Chemical> products, Chemical mainCompound) {
		String productString = "<div class=\"products\"><ul>";
		for (Chemical compound : products) {
			if (!compound.isCofactor()) {
				productString = productString + "<li><a id =\"product" +compound.getUuid() + "\">"+ compound.getShortestName() + "</a></li>";
			}else {
				productString = productString + "<li>" + compound.getShortestName() + "</li>";
			}
		}
		productString = productString + "</ul></div>";
		String reactantString= "<div class=\"reactants\"><ul>";
		for (Chemical compound : reactants) {
			if (compound.getUuid() != mainCompound.getUuid() && (!compound.isCofactor())){
				reactantString = reactantString + "<li><a id =\"reactant" +compound.getUuid() + "\">"+ compound.getShortestName() + "</a></li>";
			}else if (compound.getUuid() != mainCompound.getUuid()) {
				reactantString = reactantString + "<li>" + compound.getShortestName() + "</li>";
			}
		}
		reactantString = reactantString + "</ul></div>";
		HTMLPanel secondaryCompoundBlock = new HTMLPanel(productString + reactantString + CURVEDARROW);
		secondaryCompoundBlock.setStyleName("secondaryCompoundBlock");
		for (Chemical compound : products ) {
			if (!compound.isCofactor()) {
				Anchor a= new Anchor(secondaryCompoundBlock.getElementById("product" + compound.getUuid()).getInnerHTML());
				a.setStyleName("deactivated");
				ReSearchHandler handler= new ReSearchHandler(compound, a);
				a.addClickHandler(handler);
				a.addMouseOverHandler(handler);
				a.addMouseOutHandler(handler);
				secondaryCompoundBlock.addAndReplaceElement(a, secondaryCompoundBlock.getElementById("product" + compound.getUuid()));
			}
		}
		for (Chemical compound : reactants) {
			if (!compound.isCofactor() && (compound.getUuid() != mainCompound.getUuid())) {
				Anchor a= new Anchor(secondaryCompoundBlock.getElementById("reactant" + compound.getUuid()).getInnerHTML());
				a.setStyleName("deactivated");
				ReSearchHandler handler= new ReSearchHandler(compound, a);
				a.addClickHandler(handler);
				a.addMouseOverHandler(handler);
				a.addMouseOutHandler(handler);
				secondaryCompoundBlock.addAndReplaceElement(a, secondaryCompoundBlock.getElementById("reactant" + compound.getUuid()));
			}
		}
		return secondaryCompoundBlock;
	}
	private int[] getPosition(int row, int col) {
		int[] position = new int[2];
		if (row % 2 == 0 && col == colMax) {
			position[ROW] = 400 * row;
			position[COLUMN] = 600 * col + 100;
		}else if (row % 2 == 0) {
			position[ROW] = 400 * row;
			position[COLUMN] = 600 * col + 100;
		}else {
			position[ROW] = 400 * row;
			position[COLUMN] = 600 * (colMax - col) + 100;
		}
		return position;
	}
}

