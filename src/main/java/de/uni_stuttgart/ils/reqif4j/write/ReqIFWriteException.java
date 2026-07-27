package de.uni_stuttgart.ils.reqif4j.write;

/**
 * Thrown when a ReqIF document cannot be serialized.
 */
public class ReqIFWriteException extends RuntimeException {

	public ReqIFWriteException(String message, Throwable cause) {
		super(message, cause);
	}

	public ReqIFWriteException(String message) {
		super(message);
	}
}
