package de.uni_stuttgart.ils.reqif4j.build;

/**
 * Thrown when a ReqIF document cannot be assembled, e.g. because an unknown
 * identifier is referenced.
 */
public class ReqIFBuildException extends RuntimeException {

	public ReqIFBuildException(String message) {
		super(message);
	}

	public ReqIFBuildException(String message, Throwable cause) {
		super(message, cause);
	}
}
