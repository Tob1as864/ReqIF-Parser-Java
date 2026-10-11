package de.uni_stuttgart.ils.reqif4j.reqif;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import de.uni_stuttgart.ils.reqif4j.specification.TypeClassifier;
import de.uni_stuttgart.ils.reqif4j.util.SecureXml;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class ReqIFDocument {


	private Document reqifDocument;

	protected String filePath;
	private String fileName;
	private TypeClassifier typeClassifier = TypeClassifier.defaultClassifier();

	private ReqIFHeader header;
	private ReqIFCoreContent content;
	private final java.util.List<org.w3c.dom.Node> toolExtensions = new java.util.ArrayList<>();
	private String lang;
	private String schemaLocation;


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

	/**
	 * @return the problems found while reading the document - skipped
	 *         elements, unresolved references, identifiers used twice - in the
	 *         order they were found; empty for a document that was not read
	 *         from a file. {@code ReqIFValidator} reports them as warnings.
	 */
	public java.util.List<ParseIssue> getParseIssues() {
		return this.content == null ? java.util.List.of() : this.content.getParseIssues();
	}

	/**
	 * @return the document language (xml:lang of REQ-IF), e.g. "de", or null
	 *         if the document declares none
	 */
	public String getLang() {
		return this.lang;
	}

	/**
	 * @return xsi:schemaLocation of REQ-IF: pairs of namespace and schema file
	 *         that tell a validator where to find the schemas, or null
	 */
	public String getSchemaLocation() {
		return this.schemaLocation;
	}

	public ReqIFDocument setSchemaLocation(String schemaLocation) {
		this.schemaLocation = schemaLocation;
		return this;
	}

	public ReqIFDocument setLang(String lang) {
		this.lang = lang;
		return this;
	}

	/**
	 * Tool-specific extensions of the document. They are not interpreted, only
	 * kept as their original nodes so they survive a round trip.
	 *
	 * @return the TOOL-EXTENSIONS nodes, empty if the document declares none
	 */
	public java.util.List<org.w3c.dom.Node> getToolExtensions() {
		return this.toolExtensions;
	}


	/**
	 * Creates a document from an object model, for documents that are generated
	 * instead of parsed.
	 */
	public ReqIFDocument(ReqIFHeader header, ReqIFCoreContent content) {

		this.header = header;
		this.content = content;
		this.fileName = header == null || header.getTitle().isEmpty() ? "generated.reqif" : header.getTitle() + ".reqif";
		this.filePath = this.fileName;
	}

	public ReqIFDocument(String filePath) throws FileNotFoundException {
		this(filePath, TypeClassifier.defaultClassifier());
	}

	public ReqIFDocument(String filePath, TypeClassifier typeClassifier) throws FileNotFoundException {

		this.filePath = filePath;
		this.fileName = extractFileName(filePath);
		setTypeClassifier(typeClassifier);

		try {
			this.reqifDocument = SecureXml.newDocumentBuilder().parse(this.filePath);
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
			this.reqifDocument = SecureXml.newDocumentBuilder().parse(is);
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
			this.reqifDocument = SecureXml.newDocumentBuilder().parse(is);
			readDocument();

		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new ReqIFParseException("Failed to parse ReqIF document " + fileName + " in " + zipFilePath, e);
		}
	}

	private void setTypeClassifier(TypeClassifier typeClassifier) {
		this.typeClassifier = typeClassifier == null ? TypeClassifier.defaultClassifier() : typeClassifier;
	}


	private void readDocument() {

		// Elements are matched by local name, so a document that puts the ReqIF
		// elements into a prefixed namespace is read just like one using the
		// default namespace.
		Element theHeader = XmlUtils.firstDescendantByLocalName(this.reqifDocument, ReqIFConst.THE_HEADER);
		if (theHeader != null && theHeader.hasChildNodes()) {
			this.header = new ReqIFHeader(theHeader);
		}
		Element coreContent = XmlUtils.firstDescendantByLocalName(this.reqifDocument, ReqIFConst.CORE_CONTENT);
		if (coreContent == null) {
			throw new ReqIFParseException("Document contains no " + ReqIFConst.CORE_CONTENT + " element: " + this.fileName);
		}
		this.content = new ReqIFCoreContent(coreContent, this.typeClassifier);

		Element root = this.reqifDocument.getDocumentElement();
		if (root.hasAttributeNS(XMLConstants.XML_NS_URI, "lang")) {
			this.lang = root.getAttributeNS(XMLConstants.XML_NS_URI, "lang");
		}
		if (root.hasAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation")) {
			this.schemaLocation = root.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation");
		}

		// Tool extensions are kept verbatim; the parser does not interpret them.
		// Only the TOOL-EXTENSIONS of REQ-IF itself count: a vendor extension may
		// well contain an element of that name, and the schema allows one only.
		for (Element child : XmlUtils.childElements(root)) {
			if (ReqIFConst.TOOL_EXTENSIONS.equals(XmlUtils.localName(child))) {
				this.toolExtensions.add(child);
			}
		}
	}

	private static String extractFileName(String path) {
		int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return path.substring(lastSeparator + 1);
	}

}
