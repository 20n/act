package com.twentyn.patentExtractor;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class PatentDocument {

    public static final Logger LOGGER = LogManager.getLogger(PatentDocument.class);

    // See http://www.uspto.gov/learning-and-resources/xml-resources.
    public static final String DTD2013 = "v4.4 2013-05-16";
    public static final String DTD2004 = "v40 2004-12-02";

    public static final String PATH_DTD_VERSION = "/us-patent-grant/@dtd-version";
    public static final String[] PATHS_TEXT = {
            "//description",
            "//invention-title",
            "//abstract",
    };
    public static final String PATH_CLAIMS = "//claims";

    public static final String
            PATH_KEY_FILE_ID = "fileId",
            PATH_KEY_TITLE = "title",
            PATH_KEY_DATE = "date",
            PATH_KEY_MAIN_CLASSIFICATION = "classification",
            PATH_KEY_FURTHER_CLASSIFICATIONS = "further_classifications",
            PATH_KEY_SEARCHED_CLASSIFICATIONS = "referenced_classifications";

    // TODO: is there a type-safe way of building an object from XPath with a map of functions?
    public static final HashMap<String, String> PATHS_2013 = new HashMap<String, String>() {{
        put(PATH_KEY_FILE_ID, "/us-patent-grant/@file");
        put(PATH_KEY_TITLE, "/us-patent-grant/us-bibliographic-data-grant/invention-title/text()");
        put(PATH_KEY_DATE, "/us-patent-grant/@date-publ");
        put(PATH_KEY_MAIN_CLASSIFICATION,
                "/us-patent-grant/us-bibliographic-data-grant/classification-national/main-classification/text()");
        put(PATH_KEY_FURTHER_CLASSIFICATIONS,
                "/us-patent-grant/us-bibliographic-data-grant/classification-national/further-classification");
        put(PATH_KEY_SEARCHED_CLASSIFICATIONS,
                "/us-patent-grant/us-bibliographic-data-grant/us-field-of-classification-search/classification-national[./country/text()='US']/main-classification");
    }};

    public static final HashMap<String, String> PATHS_2004 = new HashMap<String, String>() {{
        put(PATH_KEY_FILE_ID, "/us-patent-grant/@file");
        put(PATH_KEY_TITLE, "/us-patent-grant/us-bibliographic-data-grant/invention-title/text()");
        put(PATH_KEY_DATE, "/us-patent-grant/@date-publ");
        put(PATH_KEY_MAIN_CLASSIFICATION,
                "/us-patent-grant/us-bibliographic-data-grant/classification-national/main-classification/text()");
        put(PATH_KEY_FURTHER_CLASSIFICATIONS,
                "/us-patent-grant/us-bibliographic-data-grant/classification-national/further-classification");
        put(PATH_KEY_SEARCHED_CLASSIFICATIONS,
                "/us-patent-grant/us-bibliographic-data-grant/field-of-search/classification-national[./country/text()='US']/main-classification");
    }};

    // TODO: handle 2005 schema as well.

    public static final HashMap<String, HashMap<String, String>> VERSION_MAP =
            new HashMap<String, HashMap<String, String>>() {{
                put(DTD2013, PATHS_2013);
                put(DTD2004, PATHS_2004);
            }};

    private static final Pattern GZIP_PATTERN = Pattern.compile("\\.gz$");

    private static List<String> getRelevantDocumentText(DocumentBuilder docBuilder, String[] paths,
                                                        XPath xpath, Document doc)
            throws ParserConfigurationException, TransformerConfigurationException,
            TransformerException, XPathExpressionException {
        List<String> allTextList = new ArrayList<>(0);
        for (String path : paths) {
            XPathExpression exp = xpath.compile(path);
            NodeList textNodes = (NodeList) exp.evaluate(doc, XPathConstants.NODESET);
            if (textNodes != null) {
                for (int i = 0; i < textNodes.getLength(); i++) {
                    Node n = textNodes.item(i);
                    /* This extremely around-the-horn approach to handling text content is due to the mix of HTML and
                     * XML in the patent body.  We use Jsoup to parse the HTML entities we find in the body, and use
                     * its extremely convenient NodeVisitor API to recursively traverse the document and extract the
                     * text content in reasonable chunks.
                     */
                    Document contentsDoc = Util.nodeToDocument(docBuilder, "body", n);
                    String docText = Util.documentToString(contentsDoc);
                    // With help from http://stackoverflow.com/questions/832620/stripping-html-tags-in-java
                    org.jsoup.nodes.Document htmlDoc = Jsoup.parse(docText);
                    HtmlVisitor visitor = new HtmlVisitor();
                    NodeTraversor traversor = new NodeTraversor(visitor);
                    traversor.traverse(htmlDoc);
                    List<String> textSegments = visitor.getTextContent();
                    allTextList.addAll(textSegments);
                }
            }
        }

        return allTextList;
    };

    private static class HtmlVisitor implements NodeVisitor {
        // Based on https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/examples/HtmlToPlainText.java
        private static final HashSet<String> SEGMENTING_NODES = new HashSet<String>() {{
            addAll(Arrays.asList(
                    "p", "h1", "h2", "h3", "h4", "h5", "h6", "dt", "dd", "tr", "li", "body", // HTML entities
                    "row", "claim" // patent-specific entities
                    ));
        }};
        private static final Pattern SPACE_PATTERN = Pattern.compile("^\\s+$");

        private StringBuilder segmentBuilder = new StringBuilder();
        private List<String> textSegments = new LinkedList<>();

        @Override
        public void head(org.jsoup.nodes.Node node, int i) {
            // This borrows a page from HtmlToPlainText's book.
            if (node instanceof TextNode) {
                String text = ((TextNode) node).text();
                if (text != null && text.length() > 0) {
                    segmentBuilder.append(((TextNode) node).text());
                }
            }
        }

        @Override
        public void tail(org.jsoup.nodes.Node node, int i) {
            String nodeName = node.nodeName();
            if (nodeName.equals("a")) {
                // Same as Jsoup's HtmlToPlainText.
                segmentBuilder.append(String.format(" <%s>", node.absUrl("href")));
            } else if (SEGMENTING_NODES.contains(nodeName) && segmentBuilder.length() > 0) {
                String segmentText = segmentBuilder.toString();
                // Ignore blank lines, as we'll be tagging each line separately.
                if (!SPACE_PATTERN.matcher(segmentText).matches()) {
                    textSegments.add(segmentText);
                }
                // TODO: is it better to drop the old one than clear the existing?
                segmentBuilder = new StringBuilder();
            }
        }

        public List<String> getTextContent() {
            return this.textSegments;
        }
    }

    /**
     * Converts an XML file into a patent document object, extracting relevant fields from the patent XML.
     * @param inputPath A path to the file to be read.
     * @return Returns A patent object if the XML can be read, or null otherwise.
     * @throws IOException Thrown on file I/O errors.
     * @throws ParserConfigurationException Thrown when the XML parser cannot be configured correctly.
     * @throws SAXException Thrown on XML parser errors.
     * @throws XPathExpressionException Thrown when XPath fails to handle queries against the specified document.
     */
    // TODO: logging?
    // TODO: are @nullable and @non-null annotations still a thing?
    // TODO: prolly belongs in a factory.
    public static PatentDocument patentDocumentFromXMLFile(File inputPath)
            throws IOException, ParserConfigurationException,
            SAXException, TransformerConfigurationException,
            TransformerException, XPathExpressionException {
        InputStream iStream = null;

        iStream = new BufferedInputStream(new FileInputStream(inputPath));
        if (GZIP_PATTERN.matcher(inputPath.getName()).find()) {
            iStream = new GZIPInputStream(iStream);
        }
        return patentDocumentFromStream(iStream);
    }

    public static PatentDocument patentDocumentFromString(String text)
            throws IOException, ParserConfigurationException,
            SAXException, TransformerConfigurationException,
            TransformerException, XPathExpressionException {
        StringReader stringReader = new StringReader(text);
        return patentDocumentFromStream(new ReaderInputStream(stringReader));
    }

    private static PatentDocument patentDocumentFromStream(InputStream iStream)
            throws IOException, ParserConfigurationException,
            SAXException, TransformerConfigurationException,
            TransformerException, XPathExpressionException {

        // Create XPath objects for validating that this document is actually a patent.
        XPath xpath = Util.getXPathFactory().newXPath();
        XPathExpression versionXPath = xpath.compile(PATH_DTD_VERSION);

        DocumentBuilderFactory docFactory = Util.mkDocBuilderFactory();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(iStream);

        Util.DocumentType docType = Util.identifyDocType(doc);
        if (docType != Util.DocumentType.PATENT) {
            LOGGER.warn("Found unexpected document type: " + docType);
            return null;
        }

        // Yes this is in fact the way suggested by the XPath API.
        String version = (String) versionXPath.evaluate(doc, XPathConstants.STRING);
        if (version == null || !VERSION_MAP.containsKey(version)) {
            LOGGER.warn("Unrecognized patent DTD version: " + version);
            return null;
        }

        HashMap<String, String> paths = VERSION_MAP.get(version);

        /* Create XPath objects for extracting the fields of interest based on the version information.
         * TODO: extract these into some sharable, thread-safe place, maybe via dependency injection.
         */
        XPathExpression idXPath = xpath.compile(paths.get(PATH_KEY_FILE_ID));
        XPathExpression dateXPath = xpath.compile(paths.get(PATH_KEY_DATE));
        XPathExpression titleXPath = xpath.compile(paths.get(PATH_KEY_TITLE));
        XPathExpression classificationXPath = xpath.compile(paths.get(PATH_KEY_MAIN_CLASSIFICATION));
        XPathExpression furtherClassificationsXPath = xpath.compile(paths.get(PATH_KEY_FURTHER_CLASSIFICATIONS));
        XPathExpression searchedClassificationsXPath = xpath.compile(paths.get(PATH_KEY_SEARCHED_CLASSIFICATIONS));

        String fileId = (String) idXPath.evaluate(doc, XPathConstants.STRING);
        String date = (String) dateXPath.evaluate(doc, XPathConstants.STRING);
        String title = (String) titleXPath.evaluate(doc, XPathConstants.STRING);
        String classification = (String) classificationXPath.evaluate(doc, XPathConstants.STRING);
        NodeList furtherClassificationNodes =
                (NodeList) furtherClassificationsXPath.evaluate(doc, XPathConstants.NODESET);
        ArrayList<String> furtherClassifications = null;
        if (furtherClassificationNodes!= null) {
            furtherClassifications = new ArrayList<>(furtherClassificationNodes.getLength());
            for (int i = 0; i < furtherClassificationNodes.getLength(); i++) {
                Node n = furtherClassificationNodes.item(i);
                String txt = n.getTextContent();
                if (txt != null) {
                    furtherClassifications.add(i, txt);
                }
            }
        } else {
            furtherClassifications = new ArrayList<>(0);
        }

        NodeList otherClassificationNodes =
                (NodeList) searchedClassificationsXPath.evaluate(doc, XPathConstants.NODESET);
        ArrayList<String> otherClassifications = null;
        if (otherClassificationNodes != null) {
            otherClassifications = new ArrayList<>(otherClassificationNodes.getLength());
            for (int i = 0; i < otherClassificationNodes.getLength(); i++) {
                Node n = otherClassificationNodes.item(i);
                String txt = n.getTextContent();
                if (txt != null) {
                    otherClassifications.add(i, txt);
                }
            }
        } else {
            otherClassifications = new ArrayList<>(0);
        }

        // Extract text content for salient document paths.
        List<String> allTextList = getRelevantDocumentText(docBuilder, PATHS_TEXT, xpath, doc);
        List<String> claimsTextList = getRelevantDocumentText(docBuilder, new String[]{PATH_CLAIMS}, xpath, doc);

        return new PatentDocument(fileId, date, title, classification,
                furtherClassifications, otherClassifications, allTextList, claimsTextList);
    }

    @JsonProperty("file_id")
    protected String fileId;
    @JsonProperty("grant_date")
    protected String grantDate;
    @JsonProperty("title")
    protected String title;
    @JsonProperty("primary_classification")
    protected String mainClassification;
    @JsonProperty("further_classification")
    protected List<String> furtherClassifications;
    @JsonProperty("searched_classifications")
    protected List<String> searchedClassifications;
    @JsonProperty("text_content")
    protected List<String> textContent;
    @JsonProperty("claims")
    protected List<String> claimsText;

    // TODO: this could probably use a builder if it gets more complicated.

    protected PatentDocument(String fileId, String grantDate, String title, String mainClassification,
                             List<String> furtherClassifications, List<String> searchedClassifications,
                             List<String> textContent, List<String> claimsText) {
        this.fileId = fileId;
        this.grantDate = grantDate;
        this.title = title;
        this.mainClassification = mainClassification;
        this.furtherClassifications = furtherClassifications;
        this.searchedClassifications = searchedClassifications;
        this.textContent = textContent;
        this.claimsText = claimsText;
    }

    public String getFileId() {
        return fileId;
    }

    public String getGrantDate() {
        return grantDate;
    }

    public String getTitle() {
        return title;
    }

    public String getMainClassification() {
        return mainClassification;
    }

    public List<String> getFurtherClassifications() {
        return furtherClassifications;
    }

    public List<String> getSearchedClassifications() {
        return searchedClassifications;
    }

    public List<String> getTextContent() {
        return textContent;
    }

    public List<String> getClaimsText() {
        return claimsText;
    }
}
