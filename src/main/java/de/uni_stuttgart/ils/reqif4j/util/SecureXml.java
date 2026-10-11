package de.uni_stuttgart.ils.reqif4j.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Factories for XML components with the same hardening applied everywhere.
 *
 * Every place that parses XML in this library must go through here; a parser
 * created with the plain JDK defaults resolves DOCTYPEs and external entities
 * (XXE).
 */
public final class SecureXml {

	private SecureXml() {
	}

	/**
	 * @return a namespace-aware document builder that rejects DOCTYPEs and does
	 *         not resolve external entities
	 */
	public static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
		synchronized (SecureXml.class) {
			if (factory == null) {
				factory = newDocumentBuilderFactory();
			}
			// a factory is not required to be thread-safe
			return factory.newDocumentBuilder();
		}
	}

	/**
	 * Looking up and configuring a factory costs more than parsing a small
	 * XHTML value, so one is kept; the builders it creates are not shared.
	 */
	private static DocumentBuilderFactory factory;

	private static DocumentBuilderFactory newDocumentBuilderFactory() throws ParserConfigurationException {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);

		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		try {
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		} catch (IllegalArgumentException ignored) {
			// parser implementation does not support these attributes
		}
		return factory;
	}

	/**
	 * @return a namespace-aware SAX reader with the same hardening as
	 *         {@link #newDocumentBuilder()}. Unlike a DOM it keeps line numbers,
	 *         which makes schema violations of a file traceable.
	 */
	public static XMLReader newXMLReader() throws ParserConfigurationException, SAXException {

		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setXIncludeAware(false);

		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

		XMLReader reader = factory.newSAXParser().getXMLReader();
		try {
			reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		} catch (SAXException ignored) {
			// parser implementation does not support these properties
		}
		return reader;
	}

	/**
	 * @return an identity transformer with secure processing enabled
	 */
	public static Transformer newTransformer() throws TransformerConfigurationException {

		TransformerFactory factory = TransformerFactory.newInstance();
		try {
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
		} catch (IllegalArgumentException | TransformerConfigurationException ignored) {
			// implementation does not support these settings
		}
		return factory.newTransformer();
	}

	/**
	 * @param schemaLanguage e.g. {@link XMLConstants#W3C_XML_SCHEMA_NS_URI}
	 * @return a schema factory with secure processing enabled. Local schema
	 *         files may still be resolved, because an XSD legitimately imports
	 *         other XSDs from the same directory.
	 */
	public static SchemaFactory newSchemaFactory(String schemaLanguage) {

		SchemaFactory factory = SchemaFactory.newInstance(schemaLanguage);
		try {
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
		} catch (Exception ignored) {
			// implementation does not support these settings
		}
		return factory;
	}
}
