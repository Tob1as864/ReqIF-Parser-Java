package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuildException;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeDouble;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * The writer emits every value the ReqIF schema requires, also when the model
 * does not carry it - typically for documents generated with the builder.
 */
class RequiredValuesTest {

	private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-11T12:34:56.789Z"), ZoneOffset.UTC);

	@Test
	void elementsWithoutLastChangeGetTheHeaderCreationTime() {

		ReqIFDocument document = minimalDocument(h -> h.id("hdr").creationTime("2026-07-23T10:00:00Z"));

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("<DATATYPE-DEFINITION-STRING IDENTIFIER=\"dt-string\" LAST-CHANGE=\"2026-07-23T10:00:00Z\""), xml);
		assertTrue(xml.contains("<SPEC-OBJECT IDENTIFIER=\"so-1\" LAST-CHANGE=\"2026-07-23T10:00:00Z\">"), xml);
		assertFalse(xml.contains("2026-10-11T12:34:56"), "the clock is not needed when the header has a creation time");
	}

	@Test
	void withoutCreationTimeTheTimeOfWritingIsUsedEverywhere() {

		ReqIFDocument document = minimalDocument(h -> h.id("hdr"));

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		// truncated to seconds, one timestamp for header and elements alike
		assertTrue(xml.contains("<CREATION-TIME>2026-10-11T12:34:56Z</CREATION-TIME>"), xml);
		assertTrue(xml.contains("<SPEC-OBJECT IDENTIFIER=\"so-1\" LAST-CHANGE=\"2026-10-11T12:34:56Z\">"), xml);
	}

	@Test
	void writingDoesNotChangeTheModel() {

		ReqIFDocument document = minimalDocument(h -> h.id("hdr"));

		new ReqIFWriter().setClock(FIXED).toXml(document);

		assertNull(document.getCoreContent().getSpecObject("so-1").getLastChange());
		assertEquals("", document.getHeader().getCreationTime());
	}

	@Test
	void existingLastChangeIsKept(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", TestFixtures.SCHEMA_VALID_REQIF).toString());

		String xml = new ReqIFWriter().setClock(FIXED).toXml(reqif.getReqIFDocument());

		assertTrue(xml.contains("IDENTIFIER=\"so-1\" LAST-CHANGE=\"2026-07-03T09:15:00Z\""), xml);
		assertFalse(xml.contains("2026-10-11"), xml);
	}

	@Test
	void missingHeaderFieldsGetDefaults() {

		ReqIFDocument document = minimalDocument(h -> h.id("hdr").reqifVersion(""));

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("<REQ-IF-HEADER IDENTIFIER=\"hdr\">"
				+ "<CREATION-TIME>2026-10-11T12:34:56Z</CREATION-TIME>"
				+ "<REQ-IF-TOOL-ID>reqif4j</REQ-IF-TOOL-ID>"
				+ "<REQ-IF-VERSION>1.0</REQ-IF-VERSION>"
				+ "<SOURCE-TOOL-ID>reqif4j</SOURCE-TOOL-ID>"
				+ "<TITLE/>"
				+ "</REQ-IF-HEADER>"), xml);
	}

	@Test
	void requiredEnumerationAttributesAreAlwaysWritten() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.enumerationDatatype("dt-enum", "Color", e -> e.value("ev-red", "Red", "1"))
				.specObjectType("st", "Type", t -> t.enumerationAttribute("ad-color", "Color", "dt-enum", false))
				.build();

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("<EMBEDDED-VALUE KEY=\"1\" OTHER-CONTENT=\"\"/>"), xml);
		assertTrue(xml.contains("IDENTIFIER=\"ad-color\" LAST-CHANGE=\"2026-10-11T12:34:56Z\" LONG-NAME=\"Color\" MULTI-VALUED=\"false\""), xml);
	}

	@Test
	void realDatatypeRoundTripsAccuracyAndBounds(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", TestFixtures.SCHEMA_VALID_REQIF).toString());
		DatatypeDouble real = (DatatypeDouble) reqif.getReqIFCoreContent().getDatatype("dt-real");
		assertEquals(3, real.getAccuracy());
		assertEquals(-1.5, real.getMin());
		assertEquals(1000.0, real.getMax());

		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());

		assertTrue(xml.contains("ACCURACY=\"3\" IDENTIFIER=\"dt-real\" LAST-CHANGE=\"2026-07-01T08:00:00Z\" LONG-NAME=\"Real\" MAX=\"1000.0\" MIN=\"-1.5\""), xml);
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-REAL THE-VALUE=\"2.5\">"), xml);
	}

	@Test
	void realDatatypeWithoutBoundsGetsSchemaValidDefaults() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.realDatatype("dt-real", "Real")
				.realDatatype("dt-ratio", "Ratio", 2, 0.0, Double.POSITIVE_INFINITY)
				.build();

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("ACCURACY=\"15\" IDENTIFIER=\"dt-real\""), xml);
		assertTrue(xml.contains("MAX=\"1.7976931348623157E308\" MIN=\"-1.7976931348623157E308\""), xml);
		// xsd:double spells infinity INF, not Java's "Infinity"
		assertTrue(xml.contains("ACCURACY=\"2\" IDENTIFIER=\"dt-ratio\""), xml);
		assertTrue(xml.contains("MAX=\"INF\" MIN=\"0.0\""), xml);
	}

	@Test
	void realBoundsSpelledInXsdNotationAreRead(@TempDir Path tempDir) throws Exception {

		String infinite = TestFixtures.SCHEMA_VALID_REQIF.replace("MIN=\"-1.5\" MAX=\"1000.0\"", "MIN=\"-INF\" MAX=\"INF\"");
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", infinite).toString());

		DatatypeDouble real = (DatatypeDouble) reqif.getReqIFCoreContent().getDatatype("dt-real");

		assertEquals(Double.NEGATIVE_INFINITY, real.getMin());
		assertEquals(Double.POSITIVE_INFINITY, real.getMax());
	}

	@Test
	void infiniteRealValuesAreWrittenInXsdNotationAndReadBack(@TempDir Path tempDir) throws Exception {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.realDatatype("dt-real", "Real", 2, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
				.specObjectType("st", "Type", t -> t.realAttribute("ad-effort", "Effort", "dt-real"))
				.specObject("so-1", "st", o -> o.set("ad-effort", Double.POSITIVE_INFINITY))
				.build();
		Path file = tempDir.resolve("out.reqif");
		new ReqIFWriter().setClock(FIXED).write(document, file);

		String xml = Files.readString(file);
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-REAL THE-VALUE=\"INF\">"), xml);

		// the library reads back what it writes
		ReqIF reqif = new ReqIF(file.toString());
		assertEquals(Double.POSITIVE_INFINITY, reqif.getReqIFCoreContent().getSpecObject("so-1").getAttribute("Effort"));
	}

	@Test
	void headerWithoutIdentifierGetsAFreeDefault() {

		ReqIFDocument withoutHeader = minimalDocument(h -> { });
		ReqIFDocument clashing = ReqIFBuilder.create()
				.stringDatatype(ReqIFBuilder.DEFAULT_HEADER_ID, "String", 10)
				.build();

		assertEquals("reqif4j-header", withoutHeader.getHeader().getID());
		assertEquals("reqif4j-header-2", clashing.getHeader().getID());
	}

	@Test
	void inconsistentRealDatatypeIsRefused() {

		assertThrows(ReqIFBuildException.class,
				() -> ReqIFBuilder.create().realDatatype("dt", "Real", -1, 0.0, 1.0));
		assertThrows(ReqIFBuildException.class,
				() -> ReqIFBuilder.create().realDatatype("dt", "Real", 2, 10.0, 0.0));
		assertThrows(ReqIFBuildException.class,
				() -> ReqIFBuilder.create().realDatatype("dt", "Real", 2, Double.NaN, 0.0));
	}

	@Test
	void xhtmlDefaultValueIsWrittenAsMarkup() throws Exception {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.specObjectType("st", "Type", t -> t.attribute("ad-text", "Text", "dt-xhtml", "<p>to be written</p>"))
				.build();

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("<DEFAULT-VALUE><ATTRIBUTE-VALUE-XHTML><DEFINITION>"
				+ "<ATTRIBUTE-DEFINITION-XHTML-REF>ad-text</ATTRIBUTE-DEFINITION-XHTML-REF></DEFINITION>"
				+ "<THE-VALUE><div xmlns=\"http://www.w3.org/1999/xhtml\"><p>to be written</p></div></THE-VALUE>"), xml);
		OfficialReqIFSchema.assertSchemaValid(document);
	}

	@Test
	void outputCarriesNoInternalState() {

		Document xml = new ReqIFWriter().buildDocument(minimalDocument(h -> h.id("hdr")));

		assertNull(xml.getUserData("reqif4j.defaultLastChange"));
	}

	private static ReqIFDocument minimalDocument(Consumer<ReqIFBuilder.HeaderBuilder> header) {
		return ReqIFBuilder.create()
				.header(header)
				.stringDatatype("dt-string", "String", 100)
				.specObjectType("st-req", "Requirement", t -> t.stringAttribute("ad-name", "Name", "dt-string"))
				.specificationType("st-spec", "Spec", t -> { })
				.specObject("so-1", "st-req", o -> o.set("ad-name", "x"))
				.specification("spec-1", "Spec", "st-spec", s -> s.child("sh-1", "so-1"))
				.build();
	}
}
