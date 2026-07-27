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
	
	public String getCreationDate() {
		return this.creationDate;
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
			// The "Created by: " convention is tool-specific; a comment without
			// it must not crash the parser.
			this.comment = theHeader.getElementsByTagName(ReqIFConst.COMMENT).item(0).getTextContent();
			int createdBy = this.comment.indexOf("Created by: ");
			if(createdBy >= 0) {
				this.author = this.comment.substring(createdBy + "Created by: ".length()).trim();
			}
		}
		if(theHeader.getElementsByTagName(ReqIFConst.CREATION_TIME).getLength() > 0) {
			String creationTime = theHeader.getElementsByTagName(ReqIFConst.CREATION_TIME).item(0).getTextContent();
			String[] date = creationTime.split("T")[0].split("-");
			if(date.length >= 3) {
				this.creationDate = date[2] + "." + date[1] + "." + date[0];
			}else{
				// unexpected format: keep the raw value instead of crashing
				this.creationDate = creationTime;
			}
		}
		if(theHeader.getElementsByTagName(ReqIFConst.TITLE).getLength() > 0) {
			// The title is returned as written in the document; stripping a
			// "_Template" suffix was a tool-specific hack in a generic parser.
			this.title = theHeader.getElementsByTagName(ReqIFConst.TITLE).item(0).getTextContent();
		}
	}

}
