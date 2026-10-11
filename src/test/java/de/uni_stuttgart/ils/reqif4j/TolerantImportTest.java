package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssue;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationResult;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * A document with broken parts is still read: what cannot be used is skipped
 * and reported, what does not fit its datatype is kept as written, and the
 * validator turns the reports into errors.
 */
class TolerantImportTest {

	@Test
	void unknownSpecTypeKeepsTheObjectAndItsReference(@TempDir Path tempDir) throws Exception {

		// so-1 is the first object referencing st-req
		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF.replaceFirst(
				"<SPEC-OBJECT-TYPE-REF>st-req</SPEC-OBJECT-TYPE-REF>", "<SPEC-OBJECT-TYPE-REF>st-gone</SPEC-OBJECT-TYPE-REF>"));
		SpecObject boot = reqif.getReqIFCoreContent().getSpecObject("so-1");

		assertNotNull(boot, "the object is kept");
		assertEquals("st-gone", boot.getSpecTypeID());
		assertNull(boot.getSpecTypeName());
		assertTrue(boot.getValues().isEmpty());
		assertEquals(List.of("[so-1] SPEC-OBJECT references type st-gone, which is not declared; its 8 values are skipped"),
				issues(reqif), "one report per defect");
		assertTrue(new ReqIFWriter().toXml(reqif.getReqIFDocument()).contains("<SPEC-OBJECT-TYPE-REF>st-gone</SPEC-OBJECT-TYPE-REF>"));
	}

	@Test
	void valueOfAnUnknownDefinitionIsSkipped(@TempDir Path tempDir) throws Exception {

		// the only value referencing ad-effort is so-1's
		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF.replace(
				"<ATTRIBUTE-DEFINITION-REAL-REF>ad-effort</ATTRIBUTE-DEFINITION-REAL-REF></DEFINITION>",
				"<ATTRIBUTE-DEFINITION-REAL-REF>ad-gone</ATTRIBUTE-DEFINITION-REAL-REF></DEFINITION>"));

		assertEquals(List.of("[so-1] Value references attribute definition ad-gone, which spec type st-req does not "
				+ "declare; the value is skipped"), issues(reqif));
		assertEquals(7, reqif.getReqIFCoreContent().getSpecObject("so-1").getValues().size(), "the other values are kept");
	}

	@Test
	void valuesThatDoNotFitTheirDatatypeAreKeptAsWritten(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF
				.replace("<ATTRIBUTE-VALUE-INTEGER THE-VALUE=\"1\">", "<ATTRIBUTE-VALUE-INTEGER THE-VALUE=\"one\">")
				.replace("<ATTRIBUTE-VALUE-REAL THE-VALUE=\"2.5\">", "<ATTRIBUTE-VALUE-REAL THE-VALUE=\"1.5d\">")
				.replace("<ATTRIBUTE-VALUE-BOOLEAN THE-VALUE=\"true\">", "<ATTRIBUTE-VALUE-BOOLEAN THE-VALUE=\"yes\">"));
		SpecObject boot = reqif.getReqIFCoreContent().getSpecObject("so-1");

		// no NumberFormatException; the text survives for the user to fix
		assertEquals("one", boot.getValue("ad-prio").getValue());
		assertEquals("1.5d", boot.getValue("ad-effort").getValue(), "Java accepts 1.5d, xsd:double does not");
		assertEquals("yes", boot.getValue("ad-safety").getValue());
		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-INTEGER THE-VALUE=\"one\">"), xml);

		ValidationResult result = new ReqIFValidator().validate(reqif.getReqIFDocument());
		assertTrue(messages(result).containsAll(List.of(
				"Attribute 'Priority': 'one' is not an integer",
				"Attribute 'Effort': '1.5d' is not an xsd:double",
				"Attribute 'Safety relevant': 'yes' is not an xsd:boolean (true, false, 1 or 0)")), result.toString());
	}

	@Test
	void identifiersUsedTwiceAreReported(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF
				.replace("<SPEC-OBJECT IDENTIFIER=\"so-2\"", "<SPEC-OBJECT IDENTIFIER=\"so-1\"")
				.replace("<ENUM-VALUE IDENTIFIER=\"ev-approved\"", "<ENUM-VALUE IDENTIFIER=\"ev-draft\""));

		assertEquals(List.of(
				"[ev-draft] Enum value ev-draft is declared twice; the later one replaces the earlier",
				"[so-1] Spec object so-1 is declared twice; the later one replaces the earlier"),
				issues(reqif).stream().filter(issue -> issue.contains("twice")).collect(Collectors.toList()));
	}

	@Test
	void elementsWithoutIdentifierAreSkipped(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF
				.replace("<SPEC-HIERARCHY IDENTIFIER=\"sh-2\"", "<SPEC-HIERARCHY")
				.replace("<RELATION-GROUP IDENTIFIER=\"rg-1\"", "<RELATION-GROUP")
				.replace("<ENUM-VALUE IDENTIFIER=\"ev-approved\"", "<ENUM-VALUE"));
		ReqIFCoreContent content = reqif.getReqIFCoreContent();

		assertTrue(issues(reqif).containsAll(List.of(
				"ENUM-VALUE without IDENTIFIER; the enum value is skipped",
				"SPEC-HIERARCHY without IDENTIFIER; the spec hierarchy is skipped",
				"RELATION-GROUP without IDENTIFIER; the relation group is skipped")), issues(reqif).toString());
		assertTrue(content.getRelationGroups().isEmpty());
		assertTrue(content.getSpecification("spec-1").getChildren().get(0).getChildren().isEmpty());
		assertEquals(1, ((de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration) content.getDatatype("dt-enum"))
				.getEnumValues().size());
	}

	@Test
	void missingDatatypeIsReported(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF.replace(
				"<DATATYPE-DEFINITION-DATE-REF>dt-date</DATATYPE-DEFINITION-DATE-REF>",
				"<DATATYPE-DEFINITION-DATE-REF>dt-gone</DATATYPE-DEFINITION-DATE-REF>"));

		assertTrue(issues(reqif).contains("[ad-due] Attribute definition references a datatype that is not declared "
				+ "in DATATYPES; it and its values cannot be written"), issues(reqif).toString());
	}

	@Test
	void theValidatorReportsWhatTheParserSkippedAsWarnings(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF.replace("<RELATION-GROUP IDENTIFIER=\"rg-1\"", "<RELATION-GROUP"));

		ValidationResult result = new ReqIFValidator().validate(reqif.getReqIFDocument());

		// the skipped group is gone from the model, which is valid without it
		assertTrue(result.isValid(), result.toString());
		assertEquals(List.of("WARNING: While reading: RELATION-GROUP without IDENTIFIER; the relation group is skipped"),
				result.getIssues().stream().map(Object::toString).collect(Collectors.toList()));
	}

	@Test
	void aDefaultThatDoesNotFitItsDatatypeDoesNotStopReading(@TempDir Path tempDir) throws Exception {

		// so-2 has no Priority value, so the broken default applies to it
		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF.replace(
				"<ATTRIBUTE-VALUE-INTEGER THE-VALUE=\"3\">", "<ATTRIBUTE-VALUE-INTEGER THE-VALUE=\"three\">"));

		assertEquals("three", reqif.getReqIFCoreContent().getSpecObject("so-2").getAttribute("Priority"));
		assertTrue(messages(new ReqIFValidator().validate(reqif.getReqIFDocument()))
				.contains("Default value: 'three' is not an integer"));
	}

	@Test
	void unresolvedRelationTypeIsKept(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF.replace(
				"<SPEC-RELATION-TYPE-REF>st-rel</SPEC-RELATION-TYPE-REF>", "<SPEC-RELATION-TYPE-REF>st-gone</SPEC-RELATION-TYPE-REF>"));

		assertEquals("st-gone", reqif.getReqIFCoreContent().getSpecRelation("sr-1").getSpecTypeID());
	}

	@Test
	void modelConstructorsReadTolerantlyAsBefore() throws Exception {

		org.w3c.dom.Element relation = de.uni_stuttgart.ils.reqif4j.util.SecureXml.newDocumentBuilder()
				.parse(new org.xml.sax.InputSource(new java.io.StringReader(
						"<SPEC-RELATION IDENTIFIER=\"sr\"><VALUES><ATTRIBUTE-VALUE-STRING THE-VALUE=\"x\"/></VALUES></SPEC-RELATION>")))
				.getDocumentElement();

		// no type to interpret the value by: skipped, not an exception
		assertTrue(new de.uni_stuttgart.ils.reqif4j.specification.SpecRelation(relation, null).getValues().isEmpty());
	}

	@Test
	void aCleanDocumentHasNoIssues(@TempDir Path tempDir) throws Exception {
		assertEquals(List.of(), issues(parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF)));
		assertEquals(List.of(), issues(parse(tempDir, LosslessRoundTripTest.fullFeatureDocument())));
	}

	private static ReqIF parse(Path dir, String content) throws Exception {
		return new ReqIF(TestFixtures.write(dir, "doc-" + System.nanoTime() + ".reqif", content).toString());
	}

	private static List<String> issues(ReqIF reqif) {
		return reqif.getReqIFDocument().getParseIssues().stream().map(ParseIssue::toString).collect(Collectors.toList());
	}

	private static List<String> messages(ValidationResult result) {
		return result.getIssues().stream().map(issue -> issue.getMessage()).collect(Collectors.toList());
	}
}
