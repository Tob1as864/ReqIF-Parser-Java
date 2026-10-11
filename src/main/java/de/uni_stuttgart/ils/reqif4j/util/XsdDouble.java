package de.uni_stuttgart.ils.reqif4j.util;

/**
 * Conversion between Java doubles and the xsd:double notation ReqIF uses for
 * REAL bounds and values. The two differ only for infinity: xsd:double spells
 * it {@code INF} / {@code -INF}, Java {@code Infinity} / {@code -Infinity}.
 * Both spellings are read; xsd notation is written.
 */
public final class XsdDouble {

	private XsdDouble() {
	}

	/**
	 * @return the parsed value
	 * @throws NumberFormatException if the text is neither xsd nor Java notation
	 */
	public static double parse(String text) {
		String trimmed = text.trim();
		switch (trimmed) {
			case "INF":
			case "+INF":	return Double.POSITIVE_INFINITY;
			case "-INF":	return Double.NEGATIVE_INFINITY;
			default:		return Double.parseDouble(trimmed);
		}
	}

	/**
	 * @return the value in xsd:double notation
	 */
	public static String format(double value) {
		if (value == Double.POSITIVE_INFINITY) {
			return "INF";
		}
		if (value == Double.NEGATIVE_INFINITY) {
			return "-INF";
		}
		return Double.toString(value);
	}
}
