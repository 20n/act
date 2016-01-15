package com.twentyn.patentExtractor;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.io.Reader;

public interface PatentProcessor {
  void processPatentText(File patentFile, Reader patentTextReader, int patentTextLength)
      throws IOException, ParserConfigurationException,
      SAXException, TransformerConfigurationException,
      TransformerException, XPathExpressionException;
}
