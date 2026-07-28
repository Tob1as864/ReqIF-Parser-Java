package de.uni_stuttgart.ils.reqif4j.validate;

/**
 * A single finding of {@link ReqIFValidator}.
 */
public class ValidationIssue {

	public enum Severity {
		/** The document is invalid; writing it produces a broken file. */
		ERROR,
		/** The document is usable but violates a recommendation. */
		WARNING
	}

	private final Severity severity;
	private final String elementID;
	private final String message;

	public ValidationIssue(Severity severity, String elementID, String message) {
		this.severity = severity;
		this.elementID = elementID;
		this.message = message;
	}

	public Severity getSeverity() {
		return this.severity;
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

	public boolean isError() {
		return this.severity == Severity.ERROR;
	}

	@Override
	public String toString() {
		return this.severity + (this.elementID == null ? "" : " [" + this.elementID + "]") + ": " + this.message;
	}
}
