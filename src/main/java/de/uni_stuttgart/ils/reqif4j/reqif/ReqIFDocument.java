package de.uni_stuttgart.ils.reqif4j.reqif;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

		// Tool extensions are kept verbatim; the parser does not interpret them.
		this.toolExtensions.addAll(XmlUtils.descendantsByLocalName(this.reqifDocument, ReqIFConst.TOOL_EXTENSIONS));
	}

	private static String extractFileName(String path) {
		int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return path.substring(lastSeparator + 1);
	}

}
