package de.uni_stuttgart.ils.reqif4j.reqif;

import org.w3c.dom.Element;

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
		
		this.id = theHeader.getElementsByTagName(ReqIFConst.REQ_IF_HEADER).item(0).getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
		this.toolID = theHeader.getElementsByTagName(ReqIFConst.REQ_IF_TOOL_ID).item(0).getTextContent();
		
		if(theHeader.getElementsByTagName(ReqIFConst.SOURCE_TOOL_ID).getLength() > 0) {
			this.sourceToolID = theHeader.getElementsByTagName(ReqIFConst.SOURCE_TOOL_ID).item(0).getTextContent();
		}
		if(theHeader.getElementsByTagName(ReqIFConst.REQ_IF_VERSION).getLength() > 0) {
			this.reqifVersion = theHeader.getElementsByTagName(ReqIFConst.REQ_IF_VERSION).item(0).getTextContent();
		}
		if(theHeader.getElementsByTagName(ReqIFConst.COMMENT).getLength() > 0) {
			this.comment = theHeader.getElementsByTagName(ReqIFConst.COMMENT).item(0).getTextContent();
			this.author = authorOf(this.comment);
		}
		if(theHeader.getElementsByTagName(ReqIFConst.CREATION_TIME).getLength() > 0) {
			this.creationTime = theHeader.getElementsByTagName(ReqIFConst.CREATION_TIME).item(0).getTextContent();
			this.creationDate = creationDateOf(this.creationTime);
		}
		if(theHeader.getElementsByTagName(ReqIFConst.TITLE).getLength() > 0) {
			// The title is returned as written in the document; stripping a
			// "_Template" suffix was a tool-specific hack in a generic parser.
			this.title = theHeader.getElementsByTagName(ReqIFConst.TITLE).item(0).getTextContent();
		}
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
