package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationIssue;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationResult;

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
	void parsedDocumentIsValidatedWithoutGoingOnline(@TempDir Path tempDir) throws Exception {

		// The writer does not produce schema-valid output yet (LAST-CHANGE is not
		// written); what matters here is that the schema with its http imports
		// loads at all, which it did not before.
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "doc.reqif", TestFixtures.SCHEMA_VALID_REQIF).toString());

		ValidationResult result = new ReqIFValidator()
				.validateAgainstSchema(reqif.getReqIFDocument(), OfficialReqIFSchema.reqifXsd());

		List<String> loadFailures = result.getIssues().stream()
				.map(ValidationIssue::getMessage)
				.filter(message -> message.startsWith("Schema could not be loaded"))
				.collect(Collectors.toList());
		assertTrue(loadFailures.isEmpty(), loadFailures.toString());
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
