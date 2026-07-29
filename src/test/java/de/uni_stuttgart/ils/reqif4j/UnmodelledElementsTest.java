package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * ReqIF kinds this parser does not model explicitly must survive a round trip
 * instead of being dropped (attribute definitions and values) or silently
 * turned into something else (spec types written as SPEC-OBJECT-TYPE).
 */
class UnmodelledElementsTest {

	/** Fixture with a custom datatype, an attribute of it, and a custom spec type. */
	private static String fixtureWithUnmodelledKinds() {
		return TestFixtures.REQIF_FIXTURE
				.replace("<ATTRIBUTE-DEFINITION-STRING IDENTIFIER=\"ad-title\" LONG-NAME=\"Title\">",
						"<ATTRIBUTE-DEFINITION-CUSTOM IDENTIFIER=\"ad-custom\" LONG-NAME=\"Custom\">"
								+ "<TYPE><DATATYPE-DEFINITION-CUSTOM-REF>dt-custom</DATATYPE-DEFINITION-CUSTOM-REF></TYPE>"
								+ "</ATTRIBUTE-DEFINITION-CUSTOM>"
								+ "<ATTRIBUTE-DEFINITION-STRING IDENTIFIER=\"ad-title\" LONG-NAME=\"Title\">")
				.replace("<ATTRIBUTE-VALUE-STRING THE-VALUE=\"First requirement\">",
						"<ATTRIBUTE-VALUE-CUSTOM THE-VALUE=\"custom payload\">"
								+ "<DEFINITION><ATTRIBUTE-DEFINITION-CUSTOM-REF>ad-custom</ATTRIBUTE-DEFINITION-CUSTOM-REF></DEFINITION>"
								+ "</ATTRIBUTE-VALUE-CUSTOM>"
								+ "<ATTRIBUTE-VALUE-STRING THE-VALUE=\"First requirement\">")
				.replace("<SPECIFICATION-TYPE IDENTIFIER=\"st-spec\"",
						"<SOME-FUTURE-TYPE IDENTIFIER=\"st-future\" LONG-NAME=\"Future\"><SPEC-ATTRIBUTES/></SOME-FUTURE-TYPE>"
								+ "<SPECIFICATION-TYPE IDENTIFIER=\"st-spec\"");
	}

	private ReqIF roundTrip(Path tempDir, String fixture) throws Exception {
		ReqIF original = new ReqIF(TestFixtures.write(tempDir, "in.reqif", fixture).toString());
		Path out = tempDir.resolve("out.reqif");
		new ReqIFWriter().write(original.getReqIFDocument(), out);
		return new ReqIF(out.toString());
	}


	@Test
	void attributeDefinitionOfAnUnmodelledDatatypeSurvives(@TempDir Path tempDir) throws Exception {
		ReqIF written = roundTrip(tempDir, fixtureWithUnmodelledKinds());

		assertTrue(written.getReqIFCoreContent().getSpecType("st-req").getAttributeDefinitions()
						.containsKey("ad-custom"),
				"the definition was formerly dropped because its datatype kind is not modelled");
		assertEquals("Custom", written.getReqIFCoreContent().getSpecType("st-req")
				.getAttributeDefinition("ad-custom").getName());
	}

	@Test
	void attributeValueOfAnUnmodelledDatatypeSurvives(@TempDir Path tempDir) throws Exception {
		ReqIF original = new ReqIF(
				TestFixtures.write(tempDir, "in.reqif", fixtureWithUnmodelledKinds()).toString());

		assertEquals("custom payload", original.getReqIFCoreContent().getSpecObject("so-1")
						.getAttribute("Custom"),
				"the value was formerly not even parsed");

		ReqIF written = roundTrip(tempDir, fixtureWithUnmodelledKinds());
		assertEquals("custom payload", written.getReqIFCoreContent().getSpecObject("so-1")
				.getAttribute("Custom"));
	}

	@Test
	void unmodelledElementNamesAreWrittenUnchanged(@TempDir Path tempDir) throws Exception {
		ReqIF original = new ReqIF(
				TestFixtures.write(tempDir, "in.reqif", fixtureWithUnmodelledKinds()).toString());
		String xml = new ReqIFWriter().toXml(original.getReqIFDocument());

		assertTrue(xml.contains("<ATTRIBUTE-DEFINITION-CUSTOM"), xml);
		assertTrue(xml.contains("<DATATYPE-DEFINITION-CUSTOM-REF>dt-custom</DATATYPE-DEFINITION-CUSTOM-REF>"), xml);
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-CUSTOM"), xml);
		assertTrue(xml.contains("<ATTRIBUTE-DEFINITION-CUSTOM-REF>ad-custom</ATTRIBUTE-DEFINITION-CUSTOM-REF>"), xml);
	}

	@Test
	void unmodelledSpecTypeKeepsItsElementName(@TempDir Path tempDir) throws Exception {
		ReqIF original = new ReqIF(
				TestFixtures.write(tempDir, "in.reqif", fixtureWithUnmodelledKinds()).toString());
		String xml = new ReqIFWriter().toXml(original.getReqIFDocument());

		assertTrue(xml.contains("<SOME-FUTURE-TYPE IDENTIFIER=\"st-future\""),
				"the original element name must be kept: " + xml);
		assertFalse(xml.contains("SPEC-OBJECT-TYPE IDENTIFIER=\"st-future\""),
				"it must not be silently rewritten as a spec object type");
	}

	@Test
	void unmodelledSpecTypeStillReadableAfterRoundTrip(@TempDir Path tempDir) throws Exception {
		ReqIF written = roundTrip(tempDir, fixtureWithUnmodelledKinds());

		assertNotNull(written.getReqIFCoreContent().getSpecType("st-future"));
		assertEquals(ReqIFConst.UNDEFINED, written.getReqIFCoreContent().getSpecType("st-future").getType());
		assertEquals("Future", written.getReqIFCoreContent().getSpecType("st-future").getName());
	}

	@Test
	void knownKindsAreUnaffected(@TempDir Path tempDir) throws Exception {
		ReqIF written = roundTrip(tempDir, fixtureWithUnmodelledKinds());

		assertEquals(ReqIFConst.SPEC_OBJECT_TYPE, written.getReqIFCoreContent().getSpecType("st-req").getType());
		assertEquals(ReqIFConst.SPECIFICATION_TYPE, written.getReqIFCoreContent().getSpecType("st-spec").getType());
		assertEquals(ReqIFConst.RELATION_GROUP_TYPE,
				written.getReqIFCoreContent().getSpecType("st-relgroup").getType());
		assertEquals("First requirement", written.getReqIFCoreContent().getSpecObject("so-1").getAttribute("Title"));
	}

	@Test
	void roundTripStaysIdempotentWithUnmodelledKinds(@TempDir Path tempDir) throws Exception {
		ReqIF original = new ReqIF(
				TestFixtures.write(tempDir, "in.reqif", fixtureWithUnmodelledKinds()).toString());
		ReqIF written = roundTrip(tempDir, fixtureWithUnmodelledKinds());

		assertEquals(new ReqIFWriter().toXml(original.getReqIFDocument()),
				new ReqIFWriter().toXml(written.getReqIFDocument()));
	}
}
