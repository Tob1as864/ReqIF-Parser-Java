package de.uni_stuttgart.ils.reqif4j.build;

/**
 * Thrown when a ReqIF document cannot be assembled, e.g. because an unknown
 * identifier is referenced. An {@link IllegalArgumentException}: the builder
 * was called with arguments that do not fit together.
 */
public class ReqIFBuildException extends IllegalArgumentException {

	public ReqIFBuildException(String message) {
		super(message);
	}

	public ReqIFBuildException(String message, Throwable cause) {
		super(message, cause);
	}
}
