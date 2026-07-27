package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueDate;
import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFHeader;
import de.uni_stuttgart.ils.reqif4j.specification.ExceptionSpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;

/**
 * Section 5 of the analysis: field shadowing in the datatype classes, the
 * unparsed DATE value, the tool-specific "_Template" hack in the header, the
 * unreadable ExceptionSpecObject message and the commented-out getComment().
 */
class CodeQualityFixesTest {

	private Path tempDir;
	private ReqIF reqif;

	@BeforeEach
	void parseFixture(@TempDir Path tempDir) throws Exception {
		this.tempDir = tempDir;
		this.reqif = parse(TestFixtures.REQIF_FIXTURE);
	}

	private ReqIF parse(String content) throws Exception {
		return new ReqIF(TestFixtures.write(tempDir, "test.reqif", content).toString());
	}

	/** @return the DATE attribute definition of the fixture's specification type */
	private AttributeDefinition dateDefinition() {
		return reqif.getReqIFCoreContent().getSpecType("st-spec").getAttributeDefinition("ad-review");
	}

	@Test
	void datatypesReportIdAndNameAfterRemovingShadowedFields() {
		Datatype bool = reqif.getReqIFCoreContent().getDatatype("dt-bool");
		assertEquals("dt-bool", bool.getID());
		assertEquals("Bool", bool.getName());

		Datatype xhtml = reqif.getReqIFCoreContent().getDatatype("dt-xhtml");
		assertEquals("dt-xhtml", xhtml.getID());
		assertEquals("XhtmlType", xhtml.getName());
	}

	@Test
	void dateValueIsParsedInAdditionToTheRawString() {
		Specification spec = reqif.getReqIFCoreContent().getSpecification("spec-1");
		AttributeValueDate reviewDate = (AttributeValueDate) spec.getAttributes().get("ReviewDate");

		assertEquals("2026-01-01", reviewDate.getValue(), "the raw string stays available");
		assertEquals(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), reviewDate.getDateTime());
		assertEquals("2026-01-01", reviewDate.getDate().toString());
	}

	@Test
	void dateValueAcceptsFullTimestamps() {
		AttributeDefinition definition = dateDefinition();

		assertEquals(OffsetDateTime.of(2026, 1, 1, 10, 30, 0, 0, ZoneOffset.ofHours(2)),
				new AttributeValueDate("2026-01-01T10:30:00+02:00", definition).getDateTime());
		assertEquals(OffsetDateTime.of(2026, 1, 1, 10, 30, 0, 0, ZoneOffset.UTC),
				new AttributeValueDate("2026-01-01T10:30:00", definition).getDateTime(),
				"a timestamp without offset is read as UTC");
	}

	@Test
	void unparseableDateValuesYieldNullInsteadOfThrowing() {
		AttributeDefinition definition = dateDefinition();

		assertNull(new AttributeValueDate("not a date", definition).getDateTime());
		assertNull(new AttributeValueDate(null, definition).getDateTime());
		assertNull(new AttributeValueDate("", definition).getDateTime());
		assertNull(new AttributeValueDate("not a date", definition).getDate());
	}

	@Test
	void headerReturnsTheTitleAsWritten() throws Exception {
		ReqIFHeader header = parse(TestFixtures.REQIF_FIXTURE
				.replace("<TITLE>TestDoc</TITLE>", "<TITLE>TestDoc_Template</TITLE>")).getReqIFHeader();

		assertEquals("TestDoc_Template", header.getTitle(),
				"the generic parser must not strip a tool-specific suffix");
	}

	@Test
	void headerExposesTheComment() {
		ReqIFHeader header = reqif.getReqIFHeader();

		assertEquals("Created by: Tester", header.getComment());
		assertEquals("Tester", header.getAuthor(), "the author is still derived from the comment");
	}

	@Test
	void headerWithoutCommentReturnsEmptyString() throws Exception {
		ReqIFHeader header = parse(TestFixtures.REQIF_FIXTURE
				.replace("<COMMENT>Created by: Tester</COMMENT>", "")).getReqIFHeader();

		assertEquals("", header.getComment());
	}

	@Test
	void exceptionMessageSeparatesTheFields() {
		AttributeDefinition definition = reqif.getReqIFCoreContent()
				.getSpecType("st-req").getAttributeDefinition("ad-title");
		assertNotNull(definition);

		String message = new ExceptionSpecObject("Broken\n", definition).getMessage();

		assertTrue(message.contains("ID: ad-title\n"), "fields must be separated by line breaks: " + message);
		assertTrue(message.contains("Name: Title\n"), "fields must be separated by line breaks: " + message);
		assertTrue(message.contains("Type: STRING"),
				"the datatype must be readable, not an object dump: " + message);
	}

	@Test
	void exceptionMessageToleratesAMissingDefinition() {
		assertEquals("Attribute definition not existing\n",
				new ExceptionSpecObject("Attribute definition not existing\n", null).getMessage());
	}
}
