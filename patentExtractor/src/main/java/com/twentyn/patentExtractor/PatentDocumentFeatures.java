package com.twentyn.patentExtractor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import nu.xom.converters.DOMConverter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.ac.cam.ch.wwmm.chemicaltagger.ChemistryPOSTagger;
import uk.ac.cam.ch.wwmm.chemicaltagger.ChemistrySentenceParser;
import uk.ac.cam.ch.wwmm.chemicaltagger.POSContainer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class PatentDocumentFeatures {
    public static final Logger LOGGER = LoggerFactory.getLogger(PatentDocumentFeatures.class);

    public static final String SENTENCE_PATH = "//MOLECULE//ancestor::Sentence";
    public static final String SENTENCE_DOC_HEADER = "molecule-sentence";
    public static final String MOLECULE_PATH = "//MOLECULE";

    // TODO: nullable/nonnull annotations
    private static  Document runTagger(DocumentBuilder docBuilder, ChemistryPOSTagger tagger, List<String> textContent)
            throws ParserConfigurationException, XPathExpressionException {
        String text = StringUtils.join(textContent, "\n");
        POSContainer container = tagger.runTaggers(text);
        ChemistrySentenceParser parser = new ChemistrySentenceParser(container);
        parser.parseTags();
        nu.xom.Document xomDoc = parser.makeXMLDocument();
        DOMImplementation domImpl = docBuilder.getDOMImplementation();
        return DOMConverter.convert(xomDoc, domImpl);
    }

    // TODO: nullable/non-null annotations?
    private static List<Document> findSentences(DocumentBuilder docBuilder, Document doc)
            throws ParserConfigurationException, XPathExpressionException {
        if (doc != null) {
            // TODO: is there a more efficient yet still safe way to do this?
            XPath xpath = Util.getXPathFactory().newXPath();
            // TODO: get rid of this inline xpath compilation, run during setup.
            NodeList nodes =
                    (NodeList) xpath.evaluate(SENTENCE_PATH, doc, XPathConstants.NODESET);

            List<Document> docList = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);

                // With help from:
                // http://examples.javacodegeeks.com/core-java/xml/dom/copy-nodes-subtree-from-one-dom-document-to-another/
                org.w3c.dom.Document newDoc = docBuilder.newDocument();
                Element rootElement = newDoc.createElement(SENTENCE_DOC_HEADER);

                Node newNode = newDoc.importNode(n, true);
                rootElement.appendChild(newNode);
                newDoc.appendChild(rootElement);
                docList.add(newDoc);
            }
            return docList;
        } else {
            // TODO: log here.
            return new ArrayList<>(0);
        }
    }

    /* Node.getTextContent() returns the concatenation of all text content without any delimitation between nodes.
     * This function recursively traverses the document structure, appending (naively) text content to a string joiner
     * as it goes. */
    private static List<String> appendTextContent(List<String> textList, Node n) {
        if (n.getNodeType() == Node.TEXT_NODE) {
            textList.add(n.getTextContent());
        } else {
            n.getChildNodes();
            NodeList childNodes = n.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
                Node childNode = childNodes.item(j);
                textList = appendTextContent(textList, childNode);
            }
        }
        return textList;
    }

    private static Map<String, Integer> extractMoleculeCounts(Document doc)
            throws ParserConfigurationException, XPathExpressionException{
        HashMap<String, Integer> moleculeCounts = new HashMap<>();
        if (doc != null) {
            /* This uses //MOLECULE instead of //MOLECULE//text(), as the latter finds all text for all molecules
             * instead of text for each molecule.  We could also do a secondary traversal of each MOLECULE fragment,
             * but running XPath queries over XPath results is a major pain.  Instead, we'll grab the MOLECULE nodes
             * and recursively extract the text content one molecule at a time. */
            XPath xpath = Util.getXPathFactory().newXPath();
            NodeList nodes =
                    (NodeList) xpath.evaluate(MOLECULE_PATH, doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                List<String> nameList = appendTextContent(new LinkedList<String>(), nodes.item(i));
                String moleculeName = StringUtils.join(nameList, " ");
                Integer count = moleculeCounts.get(moleculeName);
                if (count == null) {
                    count = 0;
                }
                moleculeCounts.put(moleculeName, count + 1);
            }
        }
        return moleculeCounts;
    }

    private static String docToString(Transformer transformer, Document doc) throws TransformerException {
        StringWriter stringWriter = new StringWriter();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(stringWriter);
        transformer.transform(source, result);
        return stringWriter.toString();
    }

    // TODO: prolly belongs in a factory.
    public static PatentDocumentFeatures extractPatentDocumentFeatures(
            ChemistryPOSTagger posTagger, PatentDocument patentDocument)
            throws ParserConfigurationException, XPathExpressionException, TransformerException, IOException {
        DocumentBuilder docBuilder = Util.mkDocBuilderFactory().newDocumentBuilder();

        Document claimsDoc = runTagger(docBuilder, posTagger, patentDocument.getClaimsText());
        Document textDoc = runTagger(docBuilder, posTagger, patentDocument.getTextContent());
        //System.out.println(Util.documentToString(claimsDoc));
        //System.out.println(new String(Util.compressXMLDocument(claimsDoc), "UTF-8"));
        //System.exit(0);

        List<Document> claimsTags = findSentences(docBuilder, claimsDoc);
        List<Document> textTags = findSentences(docBuilder, textDoc);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        //transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        List<String> claimsSentences = new ArrayList<>(claimsTags.size());
        for (Document doc : claimsTags) {
            claimsSentences.add(docToString(transformer, doc));
        }

        List<String> textSentences = new ArrayList<>(textTags.size());
        for (Document doc : textTags) {
            textSentences.add(docToString(transformer, doc));
        }

        Map<String, Integer> claimsMoleculeCounts = extractMoleculeCounts(claimsDoc);
        Map<String, Integer> textMoleculeCounts = extractMoleculeCounts(textDoc);

        return new PatentDocumentFeatures(patentDocument,
                claimsDoc, textDoc,
                claimsSentences, textSentences,
                claimsMoleculeCounts, textMoleculeCounts);
    }


    @JsonProperty("patent_document")
    protected PatentDocument patentDocument;
    // TODO: why are JsonSerialize and JsonDeserialize ignored in this situation (hence they're commented out.)?
    @JsonProperty("claims_compressed_tags")
    // @JsonSerialize(contentUsing = Util.DocumentSerializer.class)
    // @JsonDeserialize(contentUsing = Util.DocumentDeserializer.class)
    protected Document claimsDocument;
    @JsonProperty("text_compressed_tags")
    // @JsonSerialize(contentUsing = Util.DocumentSerializer.class)
    // @JsonDeserialize(contentUsing = Util.DocumentDeserializer.class)
    protected Document textDocument;
    @JsonProperty("claims_sentences")
    protected List<String> claimsSentences;
    @JsonProperty("text_sentences")
    protected List<String> textSentences;
    @JsonProperty("claims_molecule_counts")
    protected Map<String, Integer> claimsMoleculeCounts;
    @JsonProperty("text_molecule_counts")
    protected Map<String, Integer> textMoleculeCounts;

    public PatentDocumentFeatures(PatentDocument patentDocument, Document claimsDocument, Document textDocument,
                                  List<String> claimsSentences, List<String> textSentences,
                                  Map<String, Integer> claimsMoleculeCounts, Map<String, Integer> textMoleculeCounts) {
        this.patentDocument = patentDocument;
        this.claimsDocument = claimsDocument;
        this.textDocument = textDocument;
        this.claimsSentences = claimsSentences;
        this.textSentences = textSentences;
        this.claimsMoleculeCounts = claimsMoleculeCounts;
        this.textMoleculeCounts = textMoleculeCounts;
    }

    public PatentDocument getPatentDocument() {
        return patentDocument;
    }

    public Document getClaimsDocument() {
        return claimsDocument;
    }

    public Document getTextDocument() {
        return textDocument;
    }

    public List<String> getClaimsSentences() {
        return claimsSentences;
    }

    public List<String> getTextSentences() {
        return textSentences;
    }

    public Map<String, Integer> getClaimsMoleculeCounts() {
        return claimsMoleculeCounts;
    }

    public Map<String, Integer> getTextMoleculeCounts() {
        return textMoleculeCounts;
    }
}
