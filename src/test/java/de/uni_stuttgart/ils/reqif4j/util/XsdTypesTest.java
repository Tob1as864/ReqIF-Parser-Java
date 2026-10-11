package de.uni_stuttgart.ils.reqif4j.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class XsdTypesTest {

	@Test
	void ncNames() {
		for (String valid : new String[] { "so-1", "_x", "REQ.1", "Anforderung-\u00E4", "a\u00B7b", "_1", "\u03A9mega" }) {
			assertTrue(XsdTypes.isNCName(valid), valid);
		}
		for (String invalid : new String[] { "", "1-req", "-a", ".a", "a b", "a:b", "a/b", "a#b", "\u00E4 b", "\u00E4\u0001" }) {
			assertFalse(XsdTypes.isNCName(invalid), invalid);
		}
		assertFalse(XsdTypes.isNCName(null));
	}

	@Test
	void nonAsciiNamesFollowTheJdkSchemaValidator() {
		// Glagolitic was added to Unicode after the name tables the JDK's schema
		// validator uses; XML 1.0 fifth edition would allow it
		assertFalse(XsdTypes.isNCName("\u2C00x"));
	}

	@Test
	void xmlText() {
		assertTrue(XsdTypes.isXmlText("tab\tnewline\n\u00E4\uD83D\uDE00"));
		assertTrue(XsdTypes.isXmlText(null));
		assertFalse(XsdTypes.isXmlText("a\u0001b"));
		assertFalse(XsdTypes.isXmlText("lone \uD800 surrogate"));
		assertFalse(XsdTypes.isXmlText("\uFFFE"));
	}

	@Test
	void dateTimes() {
		for (String valid : new String[] { "2026-07-23T10:00:00Z", "2026-07-23T10:00:00", "2026-07-23T10:00:00.123+02:00",
				"2024-02-29T00:00:00Z", "2000-02-29T00:00:00Z", "2026-07-23T24:00:00Z", "12026-01-01T00:00:00Z",
				"-0044-03-15T12:00:00Z", "1000000000-02-29T00:00:00Z", "30000000000-12-31T00:00:00Z" }) {
			assertTrue(XsdTypes.isDateTime(valid), valid);
		}
		for (String invalid : new String[] { "", "2026-01-01", "2026-07-23 10:00:00", "2026-13-01T00:00:00Z",
				"2026-02-30T00:00:00Z", "2025-02-29T00:00:00Z", "2026-07-23T10:00Z", "2026-07-23T10:00:00+15:00",
				"26-07-23T10:00:00Z", "yesterday", "0000-01-01T00:00:00Z", "1900-02-29T00:00:00Z",
				"2026-04-31T00:00:00Z", "1000000001-02-29T00:00:00Z" }) {
			assertFalse(XsdTypes.isDateTime(invalid), invalid);
		}
	}

	@Test
	void integers() {
		assertTrue(XsdTypes.isInteger("42"));
		assertTrue(XsdTypes.isInteger("-7"));
		assertTrue(XsdTypes.isInteger("+0012"));
		assertTrue(XsdTypes.isInteger("123456789012345678901234567890"));
		assertFalse(XsdTypes.isInteger(""));
		assertFalse(XsdTypes.isInteger("1.0"));
		assertFalse(XsdTypes.isInteger("abc"));
		assertFalse(XsdTypes.isInteger(null));
	}

	@Test
	void doublesAndBooleansLexically() {
		for (String valid : new String[] { "1", "-1.5", "+.5", "1.", "1e3", "1.5E-3", "INF", "-INF", "NaN" }) {
			assertTrue(XsdTypes.isDouble(valid), valid);
		}
		for (String invalid : new String[] { "", "Infinity", "+INF", "1.5d", "2f", "0x1p3", "1,5", "e3" }) {
			assertFalse(XsdTypes.isDouble(invalid), invalid);
		}
		for (String valid : new String[] { "true", "false", "1", "0" }) {
			assertTrue(XsdTypes.isBoolean(valid), valid);
		}
		for (String invalid : new String[] { "", "yes", "TRUE", "2" }) {
			assertFalse(XsdTypes.isBoolean(invalid), invalid);
		}
	}

	@Test
	void doublesInXsdNotation() {
		assertEquals(Double.POSITIVE_INFINITY, XsdTypes.parseDouble("INF"));
		assertEquals(Double.NEGATIVE_INFINITY, XsdTypes.parseDouble(" -INF "));
		assertEquals(Double.POSITIVE_INFINITY, XsdTypes.parseDouble("Infinity"));
		assertEquals(1.5e3, XsdTypes.parseDouble("1.5E3"));
		assertEquals("INF", XsdTypes.formatDouble(Double.POSITIVE_INFINITY));
		assertEquals("-INF", XsdTypes.formatDouble(Double.NEGATIVE_INFINITY));
		assertEquals("NaN", XsdTypes.formatDouble(Double.NaN));
		assertEquals("2.5", XsdTypes.formatDouble(2.5));
	}
}
