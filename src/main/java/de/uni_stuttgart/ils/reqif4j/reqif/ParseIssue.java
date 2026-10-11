package de.uni_stuttgart.ils.reqif4j.reqif;

/**
 * A problem found while reading a document that did not stop the reading,
 * e.g. a value referencing an attribute definition the document does not
 * declare. See {@link ReqIFDocument#getParseIssues()}.
 */
public final class ParseIssue {

	private final String elementID;
	private final String message;

	public ParseIssue(String elementID, String message) {
		this.elementID = elementID;
		this.message = message;
	}

	/**
	 * @return the IDENTIFIER of the element the issue was found on, or null
	 */
	public String getElementID() {
		return this.elementID;
	}

	public String getMessage() {
		return this.message;
	}

	@Override
	public String toString() {
		return (this.elementID == null ? "" : "[" + this.elementID + "] ") + this.message;
	}
}
