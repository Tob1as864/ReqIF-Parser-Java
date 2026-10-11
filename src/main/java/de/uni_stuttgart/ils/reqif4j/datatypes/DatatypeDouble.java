package de.uni_stuttgart.ils.reqif4j.datatypes;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XsdDouble;

/**
 * A DATATYPE-DEFINITION-REAL. The parser normalizes the ReqIF kind REAL to the
 * category {@link ReqIFConst#DOUBLE}; it is written as REAL again.
 *
 * ACCURACY, MIN and MAX are required by the ReqIF schema. A document that
 * omits them, or a datatype created without them, gets the defaults below,
 * so it is always written schema-valid.
 */
public class DatatypeDouble extends Datatype {

	/** Significant decimal digits a double can hold. */
	public static final int DEFAULT_ACCURACY = 15;
	public static final double DEFAULT_MIN = -Double.MAX_VALUE;
	public static final double DEFAULT_MAX = Double.MAX_VALUE;

	private final int accuracy;
	private final double min;
	private final double max;


	/**
	 * Creates a datatype with the default accuracy and the full double range.
	 */
	public DatatypeDouble(String id, String name) {
		this(id, name, DEFAULT_ACCURACY, DEFAULT_MIN, DEFAULT_MAX);
	}

	public DatatypeDouble(String id, String name, int accuracy, double min, double max) {
		super(id, name, ReqIFConst.DOUBLE);

		this.accuracy = accuracy;
		this.min = min;
		this.max = max;
	}

	/**
	 * Reads the attributes as written in a document; missing or unparseable
	 * values fall back to the defaults.
	 */
	public DatatypeDouble(String id, String name, String accuracy, String min, String max) {
		this(id, name, parseAccuracy(accuracy), parseBound(min, DEFAULT_MIN), parseBound(max, DEFAULT_MAX));
	}


	/**
	 * @return the ACCURACY, the maximum precision of the values
	 */
	public int getAccuracy() {
		return this.accuracy;
	}

	public double getMin() {
		return this.min;
	}

	public double getMax() {
		return this.max;
	}


	private static int parseAccuracy(String value) {
		if (value == null || value.isBlank()) {
			return DEFAULT_ACCURACY;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return DEFAULT_ACCURACY;
		}
	}

	/**
	 * Accepts the xsd:double spellings of infinity (INF, -INF) as well.
	 */
	private static double parseBound(String value, double fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return XsdDouble.parse(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
