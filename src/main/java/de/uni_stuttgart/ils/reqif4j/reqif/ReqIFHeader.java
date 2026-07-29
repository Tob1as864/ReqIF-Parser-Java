package de.uni_stuttgart.ils.reqif4j.reqif;

import org.w3c.dom.Element;

import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class ReqIFHeader {
	
	
	private String id;
	private String author = "";
	private String title = "";
	private String toolID;
	private String sourceToolID = "";
	private String reqifVersion = "";
	private String comment = "";
	private String creationDate = "";
	private String creationTime = "";
	
	
	
	
	public String getID() {
		return this.id;
	}
	
	public String getAuthor() {
		return this.author;
	}
	
	public String getTitle() {
		return this.title;
	}
	
	public String getToolID() {
		return this.toolID;
	}
	
	public String getSourceToolID() {
		return this.sourceToolID;
	}
	
	public String getReqIFVersion() {
		return this.reqifVersion;
	}
	
	/**
	 * @return the COMMENT of the header, or "" if the document declares none
	 */
	public String getComment() {
		return this.comment;
	}
	
	/**
	 * @return the creation date formatted as dd.MM.yyyy
	 */
	public String getCreationDate() {
		return this.creationDate;
	}

	/**
	 * @return the CREATION-TIME exactly as written in the document (xsd:dateTime)
	 */
	public String getCreationTime() {
		return this.creationTime;
	}
	
	
	
	
	public ReqIFHeader(Element theHeader) {

		Element reqifHeader = XmlUtils.firstDescendantByLocalName(theHeader, ReqIFConst.REQ_IF_HEADER);
		this.id = reqifHeader == null ? null : XmlUtils.attribute(reqifHeader, ReqIFConst.IDENTIFIER);
		this.toolID = textOf(theHeader, ReqIFConst.REQ_IF_TOOL_ID);

		String sourceToolID = textOf(theHeader, ReqIFConst.SOURCE_TOOL_ID);
		if(sourceToolID != null) {
			this.sourceToolID = sourceToolID;
		}
		String reqifVersion = textOf(theHeader, ReqIFConst.REQ_IF_VERSION);
		if(reqifVersion != null) {
			this.reqifVersion = reqifVersion;
		}
		String comment = textOf(theHeader, ReqIFConst.COMMENT);
		if(comment != null) {
			this.comment = comment;
			this.author = authorOf(comment);
		}
		String creationTime = textOf(theHeader, ReqIFConst.CREATION_TIME);
		if(creationTime != null) {
			this.creationTime = creationTime;
			this.creationDate = creationDateOf(creationTime);
		}
		String title = textOf(theHeader, ReqIFConst.TITLE);
		if(title != null) {
			// The title is returned as written in the document; stripping a
			// "_Template" suffix was a tool-specific hack in a generic parser.
			this.title = title;
		}
	}

	/**
	 * @return the text of the first descendant with that local name, or null.
	 *         Matching by local name keeps documents readable that put the ReqIF
	 *         elements into a prefixed namespace.
	 */
	private static String textOf(Element parent, String localName) {

		Element element = XmlUtils.firstDescendantByLocalName(parent, localName);
		return element == null ? null : element.getTextContent();
	}

	/**
	 * Creates a header from plain values, for documents that are generated
	 * instead of parsed. The author is derived from the comment and the
	 * formatted creation date from the creation time, exactly as when reading.
	 *
	 * @param creationTime xsd:dateTime, e.g. 2026-07-23T10:00:00Z
	 */
	public ReqIFHeader(String id, String title, String comment, String creationTime,
			String toolID, String sourceToolID, String reqifVersion) {

		this.id = id;
		this.title = nullToEmpty(title);
		this.comment = nullToEmpty(comment);
		this.creationTime = nullToEmpty(creationTime);
		this.toolID = toolID;
		this.sourceToolID = nullToEmpty(sourceToolID);
		this.reqifVersion = nullToEmpty(reqifVersion);

		this.author = authorOf(this.comment);
		this.creationDate = creationDateOf(this.creationTime);
	}

	/**
	 * The "Created by: " convention is tool-specific; a comment without it must
	 * not crash the parser.
	 */
	private static String authorOf(String comment) {

		int createdBy = comment == null ? -1 : comment.indexOf("Created by: ");
		return createdBy < 0 ? "" : comment.substring(createdBy + "Created by: ".length()).trim();
	}

	private static String creationDateOf(String creationTime) {

		if(creationTime == null || creationTime.isEmpty()) {
			return "";
		}
		String[] date = creationTime.split("T")[0].split("-");
		// unexpected format: keep the raw value instead of crashing
		return date.length >= 3 ? date[2] + "." + date[1] + "." + date[0] : creationTime;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
