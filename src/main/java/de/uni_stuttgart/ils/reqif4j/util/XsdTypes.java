package de.uni_stuttgart.ils.reqif4j.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.xml.sax.SAXException;

/**
 * The lexical rules of the XML Schema types ReqIF uses, for checks and
 * conversions the JDK does not offer directly.
 */
public final class XsdTypes {

	/**
	 * xsd:NCName restricted to ASCII, where all editions of XML agree on the
	 * name characters.
	 */
	private static final Pattern ASCII_NC_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9._-]*");

	private static final Pattern ASCII = Pattern.compile("\\p{ASCII}*");

	/** compiled on first use; only needed for identifiers with non-ASCII characters */
	private static Schema ncNameSchema;

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
	 *
	 * The editions of XML differ in which non-ASCII characters a name may
	 * contain, and the JDK's schema validator follows an older one than the
	 * current specification. Identifiers with non-ASCII characters are
	 * therefore checked by that validator itself, so this method and
	 * schema validation always agree.
	 */
	public static boolean isNCName(String text) {

		if (text == null || text.isEmpty()) {
			return false;
		}
		if (ASCII.matcher(text).matches()) {
			return ASCII_NC_NAME.matcher(text).matches();
		}
		// the schema collapses whitespace before checking, an identifier
		// written to a file must not contain any
		if (!isXmlText(text) || text.codePoints().anyMatch(Character::isWhitespace)) {
			return false;
		}
		try {
			String document = "<n>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</n>";
			ncNameSchema().newValidator().validate(new StreamSource(new StringReader(document)));
			return true;
		} catch (SAXException notAName) {
			return false;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static synchronized Schema ncNameSchema() {

		if (ncNameSchema == null) {
			String xsd = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
					+ "<xs:element name=\"n\" type=\"xs:NCName\"/></xs:schema>";
			try {
				ncNameSchema = SecureXml.newSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI)
						.newSchema(new StreamSource(new StringReader(xsd)));
			} catch (SAXException e) {
				throw new IllegalStateException("Built-in NCName schema does not compile", e);
			}
		}
		return ncNameSchema;
	}

	/**
	 * @return true if every character of the text may appear in an XML 1.0
	 *         document; control characters (except tab, line feed, carriage
	 *         return), unpaired surrogates and U+FFFE/U+FFFF may not
	 */
	public static boolean isXmlText(String text) {

		if (text == null) {
			return true;
		}
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
				i++;
				continue;
			}
			boolean allowed = c == 0x9 || c == 0xA || c == 0xD
					|| (c >= 0x20 && c <= 0xD7FF)
					|| (c >= 0xE000 && c <= 0xFFFD);
			if (!allowed) {
				return false;
			}
		}
		return true;
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
