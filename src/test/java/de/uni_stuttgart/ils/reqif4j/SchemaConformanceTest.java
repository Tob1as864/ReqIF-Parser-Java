package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationIssue;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationResult;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * Validation against the official OMG ReqIF schema, whose imports (xml.xsd,
 * driver.xsd, XHTML modules) are resolved from the schema's directory instead
 * of being fetched over http.
 */
class SchemaConformanceTest {

	@Test
	void validFileIsAcceptedByTheOfficialSchema(@TempDir Path tempDir) throws Exception {

		Path file = TestFixtures.write(tempDir, "valid.reqif", TestFixtures.SCHEMA_VALID_REQIF);

		OfficialReqIFSchema.assertSchemaValid(file);
	}

	@Test
	void violationsOfAFileAreReportedWithLineNumbers(@TempDir Path tempDir) throws Exception {

		String withoutLastChange = TestFixtures.SCHEMA_VALID_REQIF.replace(
				"<SPEC-OBJECT IDENTIFIER=\"so-2\" LAST-CHANGE=\"2026-07-03T09:20:00Z\">",
				"<SPEC-OBJECT IDENTIFIER=\"so-2\">");
		Path file = TestFixtures.write(tempDir, "invalid.reqif", withoutLastChange);
		int line = lineOf(withoutLastChange, "<SPEC-OBJECT IDENTIFIER=\"so-2\">");

		ValidationResult result = new ReqIFValidator()
				.validateAgainstSchema(file, OfficialReqIFSchema.reqifXsd());

		List<ValidationIssue> errors = result.getErrors();
		assertEquals(1, errors.size(), result.toString());
		assertTrue(errors.get(0).getMessage().contains("LAST-CHANGE"), result.toString());
		assertTrue(errors.get(0).getMessage().startsWith("line " + line + ":"), result.toString());
	}

	@Test
	void malformedFileIsReportedOnce(@TempDir Path tempDir) throws Exception {

		Path file = TestFixtures.write(tempDir, "truncated.reqif",
				"<REQ-IF xmlns=\"http://www.omg.org/spec/ReqIF/20110401/reqif.xsd\"><THE-HEADER>");

		ValidationResult result = new ReqIFValidator()
				.validateAgainstSchema(file, OfficialReqIFSchema.reqifXsd());

		// the parser reports a fatal error and then throws it again
		assertEquals(1, result.getErrors().size(), result.toString());
	}

	@Test
	void documentAndFileValidationReportTheSameFindings(@TempDir Path tempDir) throws Exception {

		// two objects with the same disallowed XHTML element: on a DOM source
		// the findings have no position, but they are still two findings
		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.specObjectType("st", "Type", t -> t.xhtmlAttribute("ad-text", "Text", "dt-xhtml"))
				.specObject("so-1", "st", o -> o.setXhtml("ad-text", "<font>x</font>"))
				.specObject("so-2", "st", o -> o.setXhtml("ad-text", "<font>x</font>"))
				.build();
		Path file = tempDir.resolve("out.reqif");
		new ReqIFWriter().write(document, file);

		ValidationResult ofDocument = new ReqIFValidator().validateAgainstSchema(document, OfficialReqIFSchema.reqifXsd());
		ValidationResult ofFile = new ReqIFValidator().validateAgainstSchema(file, OfficialReqIFSchema.reqifXsd());

		assertFalse(ofFile.isValid());
		assertEquals(ofFile.getErrors().size(), ofDocument.getErrors().size(), ofDocument.toString());
	}

	@Test
	void roundTripOfASchemaValidDocumentStaysSchemaValid(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", TestFixtures.SCHEMA_VALID_REQIF).toString());

		// the XML the writer produces for the model ...
		OfficialReqIFSchema.assertSchemaValid(reqif.getReqIFDocument());

		// ... and the file written to disk
		Path written = tempDir.resolve("out.reqif");
		new ReqIFWriter().write(reqif.getReqIFDocument(), written);
		OfficialReqIFSchema.assertSchemaValid(written);
	}

	@Test
	void documentGeneratedWithTheBuilderIsSchemaValid() throws Exception {

		// the README example, plus the kinds whose required attributes are easy
		// to drop: REAL bounds, a single-valued enumeration, empty OTHER-CONTENT
		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr-1").title("My Spec").toolID("reqif4j")
						.creationTime("2026-07-23T10:00:00Z"))
				.stringDatatype("dt-string", "String", 4096)
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.realDatatype("dt-real", "Real")
				.enumerationDatatype("dt-enum", "Color", e -> e
						.value("ev-red", "Red", "1", "#ff0000")
						.value("ev-blue", "Blue", "2"))
				.specObjectType("st-req", "Requirement Type", t -> t
						.stringAttribute("ad-title", "ReqIF.Name", "dt-string")
						.xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml")
						.realAttribute("ad-effort", "Effort", "dt-real")
						.enumerationAttribute("ad-color", "Colors", "dt-enum", true, List.of("ev-blue"))
						.enumerationAttribute("ad-main", "Main color", "dt-enum", false))
				.specificationType("st-spec", "Specification Type", t -> { })
				.specRelationType("st-rel", "satisfies", t -> { })
				.specObject("so-1", "st-req", o -> o
						.set("ad-title", "First requirement")
						.set("ad-effort", 2.5)
						.setEnum("ad-color", "ev-red", "ev-blue")
						.setEnum("ad-main", "ev-red")
						.setXhtml("ad-text", "<p>The system shall boot.</p>"))
				.specObject("so-2", "st-req")
				.specRelation("sr-1", "st-rel", "so-1", "so-2")
				.specification("spec-1", "Main Spec", "st-spec", s -> s
						.child("sh-1", "so-1", c -> c.child("sh-2", "so-2")))
				.build();

		OfficialReqIFSchema.assertSchemaValid(document);
	}

	@Test
	void missingImportsAreNamedInsteadOfFetched(@TempDir Path tempDir) throws Exception {

		// only reqif.xsd, without xml.xsd / driver.xsd next to it
		Path schema = Files.copy(OfficialReqIFSchema.reqifXsd(), tempDir.resolve("reqif.xsd"));
		Path file = TestFixtures.write(tempDir, "valid.reqif", TestFixtures.SCHEMA_VALID_REQIF);

		ValidationResult result = new ReqIFValidator().validateAgainstSchema(file, schema);

		assertFalse(result.isValid());
		String message = result.getErrors().get(0).getMessage();
		assertTrue(message.startsWith("Schema could not be loaded"), message);
		assertTrue(message.contains("xml.xsd (http://www.w3.org/2001/xml.xsd)"), message);
		assertTrue(message.contains("place them next to reqif.xsd"), message);
	}

	private static int lineOf(String text, String fragment) {
		String before = text.substring(0, text.indexOf(fragment));
		return (int) before.chars().filter(c -> c == '\n').count() + 1;
	}
}
