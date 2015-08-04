package com.twentyn.patentExtractor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Util {
    public enum DocumentType {
        PATENT,
        SEQUENCE,
        UNKNOWN,
    }

    private static final HashMap<String, DocumentType> NODE_NAME_TO_DOC_TYPE = new HashMap<String, DocumentType>() {{
        put("us-patent-grant", DocumentType.PATENT);
        put("sequence-cwu", DocumentType.SEQUENCE);
    }};

    private static final ThreadLocal<XPathFactory> XPATH_FACTORY = new ThreadLocal<XPathFactory>() {
        @Override
        protected XPathFactory initialValue() {
            return XPathFactory.newInstance();
        }
    };

    private static final ThreadLocal<TransformerFactory> TRANSFORMER_FACTORY = new ThreadLocal<TransformerFactory>() {
        @Override
        protected TransformerFactory initialValue() {
            return TransformerFactory.newInstance();
        }
    };

    public static XPathFactory getXPathFactory() {
        return XPATH_FACTORY.get();
    }

    public static TransformerFactory getTransformerFactory() {
        return TRANSFORMER_FACTORY.get();
    }

    public static DocumentType identifyDocType(Document dom) throws XPathExpressionException {
        XPath xpath = null;
        xpath = getXPathFactory().newXPath();
        for (Map.Entry<String, DocumentType> entry : NODE_NAME_TO_DOC_TYPE.entrySet()) {
            Node top = (Node) xpath.evaluate("/" + entry.getKey(), dom, XPathConstants.NODE);
            if (top != null) {
                return entry.getValue();
            }
        }
        return DocumentType.UNKNOWN;
    }

    public static byte[] compressXMLDocument(Document doc) throws
            IOException, TransformerConfigurationException, TransformerException {
        Transformer transformer = getTransformerFactory().newTransformer();
        // The OutputKeys.INDENT configuration key determins whether the output is indented.

        DOMSource w3DomSource = new DOMSource(doc);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Writer w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                new Base64OutputStream(baos, true, 0, new byte[] {'\n'}))));
        StreamResult sResult = new StreamResult(w);
        transformer.transform(w3DomSource, sResult);
        w.close();
        return baos.toByteArray();
    }

    public static Document decompressXMLDocument(byte[] bytes) throws
            IOException, ParserConfigurationException, SAXException,
            TransformerConfigurationException, TransformerException {
        // With help from http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        InputStream s = new GZIPInputStream(new Base64InputStream(bais));
        DocumentBuilder documentBuilder = mkDocBuilderFactory().newDocumentBuilder();
        Document doc = documentBuilder.parse(s);
        s.close();
        return doc;
    }

    public static DocumentBuilderFactory mkDocBuilderFactory() throws ParserConfigurationException {
        /* Try to load the document.  Note that the factory must be configured within the context of a method call
         * for exception handling.  TODO: can we work around this w/ dependency injection? */
        // from http://stackoverflow.com/questions/155101/make-documentbuilder-parse-ignore-dtd-references
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);
        docFactory.setNamespaceAware(true);
        docFactory.setFeature("http://xml.org/sax/features/namespaces", false);
        docFactory.setFeature("http://xml.org/sax/features/validation", false);
        docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return docFactory;
    }

    public static String documentToString(Document doc)
            throws ParserConfigurationException, TransformerConfigurationException, TransformerException {
        StringWriter stringWriter = new StringWriter();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(stringWriter);
        Transformer transformer = getTransformerFactory().newTransformer();
        transformer.transform(source, result);
        return stringWriter.toString();
    }

    public static Document nodeToDocument(DocumentBuilder docBuilder, String documentContainer, Node n) {
        // With help from:
        // http://examples.javacodegeeks.com/core-java/xml/dom/copy-nodes-subtree-from-one-dom-document-to-another/
        org.w3c.dom.Document newDoc = docBuilder.newDocument();
        Element rootElement = newDoc.createElement(documentContainer);
        Node newNode = newDoc.importNode(n, true);
        rootElement.appendChild(newNode);
        newDoc.appendChild(rootElement);
        return newDoc;
    }

    public static class DocumentSerializer extends JsonSerializer<Document> {
        @Override
        public void serialize(Document document, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                throws IOException, JsonProcessingException {
            System.out.println("In document serializer.serialize");
            byte[] compressedDoc;
            try {
                compressedDoc = compressXMLDocument(document);
                System.out.println(new String(compressedDoc, "UTF-8"));
            } catch (TransformerException e) {
                throw new IOException("Caught TransformerException when compressing document", e);
            }
            jsonGenerator.writeString(new String(compressedDoc, "UTF-8"));
        }
    }

    public static class DocumentDeserializer extends JsonDeserializer<Document> {
        @Override
        public Document deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException, JsonProcessingException {
            byte[] compressedDoc = jsonParser.getText().getBytes("UTF-8");
            Document doc;
            try {
                doc = decompressXMLDocument(compressedDoc);
            } catch (ParserConfigurationException e) {
                throw new IOException("Caught ParserConfigurationException when compressing document", e);
            } catch (SAXException e) {
                throw new IOException("Caught SAXException when compressing document", e);
            } catch (TransformerConfigurationException e) {
                throw new IOException("Caught TransformerConfigurationException when compressing document", e);
            } catch (TransformerException e) {
                throw new IOException("Caught TransformerException when compressing document", e);
            }
            return doc;
        }
    }
}
