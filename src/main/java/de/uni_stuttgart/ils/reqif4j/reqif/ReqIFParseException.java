package de.uni_stuttgart.ils.reqif4j.reqif;

/**
 * Thrown when a ReqIF document cannot be parsed. Replaces the former
 * {@code System.exit(1)} behavior, which terminated the host JVM on any
 * malformed input.
 */
public class ReqIFParseException extends RuntimeException {

	public ReqIFParseException(String message, Throwable cause) {
		super(message, cause);
	}

	public ReqIFParseException(String message) {
		super(message);
	}
}
