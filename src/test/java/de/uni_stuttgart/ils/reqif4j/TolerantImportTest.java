package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssue;
import de.uni_stuttgart.ils.reqif4j.attributes.UninterpretedAttributeValue;
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
		assertEquals(8, boot.getValues().size(), "the values are kept, uninterpreted");
		assertTrue(boot.getValues().values().stream().allMatch(v -> v instanceof UninterpretedAttributeValue));
		assertEquals("REQ-1", boot.getValue("ad-id").getValue(), "THE-VALUE as written");
		assertEquals(List.of("[so-1] SPEC-OBJECT references type st-gone, which is not declared; its 8 values are kept as written"),
				issues(reqif), "one report per defect");
		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());
		assertTrue(xml.contains("<SPEC-OBJECT-TYPE-REF>st-gone</SPEC-OBJECT-TYPE-REF>"));
		assertTrue(xml.contains("<ATTRIBUTE-DEFINITION-REAL-REF>ad-effort</ATTRIBUTE-DEFINITION-REAL-REF>"), xml);
	}

	@Test
	void valuesOfAnUnknownTypeAreCheckedAsWritten(@TempDir Path tempDir) throws Exception {

		// two values for one definition, one without the required THE-VALUE
		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF
				.replaceFirst("<SPEC-OBJECT-TYPE-REF>st-req</SPEC-OBJECT-TYPE-REF>", "<SPEC-OBJECT-TYPE-REF>st-gone</SPEC-OBJECT-TYPE-REF>")
				// so-1's boolean value: no THE-VALUE, and the definition of another value
				.replaceFirst("<ATTRIBUTE-VALUE-BOOLEAN THE-VALUE=\"true\">(\\s*)<DEFINITION><ATTRIBUTE-DEFINITION-BOOLEAN-REF>ad-safety",
						"<ATTRIBUTE-VALUE-BOOLEAN>$1<DEFINITION><ATTRIBUTE-DEFINITION-BOOLEAN-REF>ad-id"));
		SpecObject boot = reqif.getReqIFCoreContent().getSpecObject("so-1");

		assertTrue(issues(reqif).contains("[so-1] SPEC-OBJECT references type st-gone, which is not declared; "
				+ "its 7 values are kept as written"), issues(reqif).toString());
		assertTrue(issues(reqif).contains("[so-1] Attribute definition ad-id has two values; the later one replaces the earlier"));
		assertEquals("ev-approved", boot.getValue("ad-status").getValue(), "the enum value IDENTIFIERs");
		assertTrue(boot.getValue("ad-text").getValue().toString().startsWith("The system"), boot.getValue("ad-text").getValue().toString());
		String report = new de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator().validate(reqif.getReqIFDocument()).toString();
		assertFalse(report.contains("has no THE-VALUE"), "the type is unknown, so the values are not checked:\n" + report);
	}

	@Test
	void valueOfAnUnknownDefinitionIsKeptAsWritten(@TempDir Path tempDir) throws Exception {

		// the only value referencing ad-effort is so-1's
		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF.replace(
				"<ATTRIBUTE-DEFINITION-REAL-REF>ad-effort</ATTRIBUTE-DEFINITION-REAL-REF></DEFINITION>",
				"<ATTRIBUTE-DEFINITION-REAL-REF>ad-gone</ATTRIBUTE-DEFINITION-REAL-REF></DEFINITION>"));

		assertEquals(List.of("[so-1] Value references attribute definition ad-gone, which spec type st-req does not "
				+ "declare; the value is kept as written"), issues(reqif));
		SpecObject boot = reqif.getReqIFCoreContent().getSpecObject("so-1");
		assertEquals(8, boot.getValues().size());
		assertTrue(boot.getValue("ad-gone") instanceof UninterpretedAttributeValue);
		assertTrue(new ReqIFWriter().toXml(reqif.getReqIFDocument())
				.contains("<ATTRIBUTE-DEFINITION-REAL-REF>ad-gone</ATTRIBUTE-DEFINITION-REAL-REF>"));
		assertTrue(new de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator().validate(reqif.getReqIFDocument())
				.toString().contains("uses definition ad-gone, which does not belong to spec type st-req"));
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

		assertEquals(List.of("[ad-due] Attribute definition references a datatype that is not declared "
				+ "in DATATYPES; it and its values are kept as written"), issues(reqif));
		// both are written back as read
		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());
		assertTrue(xml.contains("<DATATYPE-DEFINITION-DATE-REF>dt-gone</DATATYPE-DEFINITION-DATE-REF>"), xml);
		assertTrue(xml.contains("<ATTRIBUTE-DEFINITION-DATE-REF>ad-due</ATTRIBUTE-DEFINITION-DATE-REF>"), xml);

		// what the model changes is written, the rest as read
		de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition due = reqif.getReqIFCoreContent()
				.getSpecType("st-req").getAttributeDefinition("ad-due");
		due.setName("Deadline");
		due.setEditable(true);
		String changed = new ReqIFWriter().toXml(reqif.getReqIFDocument());
		assertTrue(changed.contains("IDENTIFIER=\"ad-due\" IS-EDITABLE=\"true\""), changed);
		assertTrue(changed.contains("LONG-NAME=\"Deadline\""), changed);
		assertFalse(due.isModified(), "stamped like every other element");
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
