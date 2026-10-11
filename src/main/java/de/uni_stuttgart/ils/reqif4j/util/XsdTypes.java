package de.uni_stuttgart.ils.reqif4j.util;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The lexical rules of the XML Schema types ReqIF uses, for checks and
 * conversions the JDK does not offer directly.
 */
public final class XsdTypes {

	/**
	 * xsd:NCName (the lexical space of xsd:ID), following the name characters
	 * of XML 1.0 fifth edition without the colon. The JDK's schema validator
	 * uses the older, stricter character tables, so a few exotic characters
	 * pass here but not there; ASCII and common letters agree.
	 */
	private static final Pattern NC_NAME = Pattern.compile(
			"[A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF"
			+ "\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD"
			+ "\\x{10000}-\\x{EFFFF}]"
			+ "[A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02FF\\u0370-\\u037D\\u037F-\\u1FFF"
			+ "\\u200C-\\u200D\\u2070-\\u218F\\u2C00-\\u2FEF\\u3001-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFFD"
			+ "\\x{10000}-\\x{EFFFF}\\-.0-9\\u00B7\\u0300-\\u036F\\u203F-\\u2040]*");

	/**
	 * xsd:dateTime: year with at least four digits, optional fraction of
	 * seconds, optional time zone. Year 0000 is excluded, as in XML Schema 1.0,
	 * which the JDK's schema validator implements.
	 */
	private static final Pattern DATE_TIME = Pattern.compile(
			"(-?)(?!0000)([1-9][0-9]{3,}|0[0-9]{3})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])"
			+ "T(([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]+)?|24:00:00(\\.0+)?)"
			+ "(Z|[+-]((0[0-9]|1[0-3]):[0-5][0-9]|14:00))?");

	private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");

	/** xsd:double in XML Schema 1.0 notation (no "+INF") */
	private static final Pattern DOUBLE = Pattern.compile(
			"[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([Ee][+-]?[0-9]+)?|-?INF|NaN");

	private static final Pattern BOOLEAN = Pattern.compile("true|false|1|0");

	private static final BigInteger FOUR = BigInteger.valueOf(4);
	private static final BigInteger HUNDRED = BigInteger.valueOf(100);
	private static final BigInteger FOUR_HUNDRED = BigInteger.valueOf(400);

	private XsdTypes() {
	}


	/**
	 * @return true if the text is a valid xsd:ID / xsd:NCName
	 */
	public static boolean isNCName(String text) {
		return text != null && NC_NAME.matcher(text).matches();
	}

	/**
	 * @return true if the text is a valid xsd:dateTime, including an existing
	 *         day of month (no 30 February)
	 */
	public static boolean isDateTime(String text) {

		if (text == null) {
			return false;
		}
		Matcher matcher = DATE_TIME.matcher(text.trim());
		if (!matcher.matches()) {
			return false;
		}
		// the pattern allows day 31 in every month; the year may have any
		// number of digits, so the leap year rule is applied on a BigInteger
		BigInteger year = new BigInteger(matcher.group(1) + matcher.group(2));
		int month = Integer.parseInt(matcher.group(3));
		int day = Integer.parseInt(matcher.group(4));
		return day <= daysIn(month, year);
	}

	private static int daysIn(int month, BigInteger year) {
		switch (month) {
			case 2:
				boolean leap = year.mod(FOUR).signum() == 0
						&& (year.mod(HUNDRED).signum() != 0 || year.mod(FOUR_HUNDRED).signum() == 0);
				return leap ? 29 : 28;
			case 4:
			case 6:
			case 9:
			case 11:
				return 30;
			default:
				return 31;
		}
	}

	/**
	 * @return true if the text is a valid xsd:integer (any number of digits)
	 */
	public static boolean isInteger(String text) {
		return text != null && INTEGER.matcher(text.trim()).matches();
	}


	/**
	 * @return true if the text is a valid xsd:double (XML Schema 1.0 notation)
	 */
	public static boolean isDouble(String text) {
		return text != null && DOUBLE.matcher(text.trim()).matches();
	}

	/**
	 * @return true if the text is a valid xsd:boolean: true, false, 1 or 0
	 */
	public static boolean isBoolean(String text) {
		return text != null && BOOLEAN.matcher(text.trim()).matches();
	}

	/**
	 * Parses xsd:double notation, which spells infinity {@code INF} /
	 * {@code -INF}; Java's {@code Infinity} is accepted as well.
	 *
	 * @throws NumberFormatException if the text is not a double
	 */
	public static double parseDouble(String text) {
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
	public static String formatDouble(double value) {
		if (value == Double.POSITIVE_INFINITY) {
			return "INF";
		}
		if (value == Double.NEGATIVE_INFINITY) {
			return "-INF";
		}
		return Double.toString(value);
	}
}
