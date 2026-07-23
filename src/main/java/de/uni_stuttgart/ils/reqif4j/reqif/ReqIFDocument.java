package de.uni_stuttgart.ils.reqif4j.reqif;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import de.uni_stuttgart.ils.reqif4j.specification.TypeClassifier;

public class ReqIFDocument {


	private Document reqifDocument;

	protected String filePath;
	private String fileName;
	private TypeClassifier typeClassifier = TypeClassifier.defaultClassifier();

	private ReqIFHeader header;
	private ReqIFCoreContent content;


	public String getFilePath() {
		return this.filePath;
	}

	public String getFileName() {
		return this.fileName;
	}

	public ReqIFHeader getHeader() {
		return this.header;
	}

	public ReqIFCoreContent getCoreContent() {
		return this.content;
	}


	public ReqIFDocument(String filePath) throws FileNotFoundException {
		this(filePath, TypeClassifier.defaultClassifier());
	}

	public ReqIFDocument(String filePath, TypeClassifier typeClassifier) throws FileNotFoundException {

		this.filePath = filePath;
		this.fileName = extractFileName(filePath);
		setTypeClassifier(typeClassifier);

		try {
			this.reqifDocument = newDocumentBuilder().parse(this.filePath);
			readDocument();

		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new ReqIFParseException("Failed to parse ReqIF document " + filePath, e);
		}
	}

	public ReqIFDocument(InputStream is, String filePath) throws FileNotFoundException {
		this(is, filePath, TypeClassifier.defaultClassifier());
	}

	public ReqIFDocument(InputStream is, String filePath, TypeClassifier typeClassifier) throws FileNotFoundException {

		this.filePath = filePath;
		this.fileName = extractFileName(filePath);
		setTypeClassifier(typeClassifier);

		try {
			this.reqifDocument = newDocumentBuilder().parse(is);
			readDocument();

		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new ReqIFParseException("Failed to parse ReqIF document " + filePath, e);
		}
	}

	public ReqIFDocument(InputStream is, String zipFilePath, String fileName) {
		this(is, zipFilePath, fileName, TypeClassifier.defaultClassifier());
	}

	public ReqIFDocument(InputStream is, String zipFilePath, String fileName, TypeClassifier typeClassifier) {

		this.filePath = zipFilePath;
		this.fileName = fileName;
		setTypeClassifier(typeClassifier);

		try {
			this.reqifDocument = newDocumentBuilder().parse(is);
			readDocument();

		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new ReqIFParseException("Failed to parse ReqIF document " + fileName + " in " + zipFilePath, e);
		}
	}

	private void setTypeClassifier(TypeClassifier typeClassifier) {
		this.typeClassifier = typeClassifier == null ? TypeClassifier.defaultClassifier() : typeClassifier;
	}


	/**
	 * Creates a namespace-aware, XXE-hardened document builder. Namespace
	 * awareness is required so XHTML content with namespace prefixes
	 * (e.g. {@code xhtml:div}) can be matched by local name.
	 */
	private static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);

		// Harden against XXE / entity expansion attacks
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

		return factory.newDocumentBuilder();
	}

	private void readDocument() {

		if (this.reqifDocument.getElementsByTagName(ReqIFConst.THE_HEADER).getLength() > 0
				&& this.reqifDocument.getElementsByTagName(ReqIFConst.THE_HEADER).item(0).hasChildNodes()) {
			this.header = new ReqIFHeader((Element) this.reqifDocument.getElementsByTagName(ReqIFConst.THE_HEADER).item(0));
		}
		if (this.reqifDocument.getElementsByTagName(ReqIFConst.CORE_CONTENT).getLength() == 0) {
			throw new ReqIFParseException("Document contains no " + ReqIFConst.CORE_CONTENT + " element: " + this.fileName);
		}
		this.content = new ReqIFCoreContent((Element) this.reqifDocument.getElementsByTagName(ReqIFConst.CORE_CONTENT).item(0), this.typeClassifier);
	}

	private static String extractFileName(String path) {
		int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return path.substring(lastSeparator + 1);
	}

}
