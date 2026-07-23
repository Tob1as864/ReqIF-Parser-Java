package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTMLElementList;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeInteger;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeString;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;

/**
 * Bugs: crashes on valid ReqIF files — unknown datatypes were stored under a
 * null key, optional MIN/MAX/MAX-LENGTH attributes caused NPEs, long-range
 * bounds and empty numeric THE-VALUEs threw NumberFormatException, minified
 * XML broke fixed child-index navigation, getXHTMLContent always threw
 * ClassCastException, header parsing crashed on unexpected comment/date
 * formats, and DATE values on specifications were silently dropped.
 */
class RobustnessTest {

	private ReqIF parse(Path dir, String content) throws Exception {
		return new ReqIF(TestFixtures.write(dir, "test.reqif", content).toString());
	}

	@Test
	void minifiedXmlParsesLikePrettyPrintedXml(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = parse(tempDir, TestFixtures.minified(TestFixtures.REQIF_FIXTURE));

		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");
		assertEquals("First requirement", so1.getAttribute("Title"));
		assertEquals(5, so1.getAttribute("Priority"));
		AttributeValueEnumeration colors = (AttributeValueEnumeration) so1.getAttributes().get("Colors");
		assertEquals(List.of("Red", "Green"), colors.getValues());
		assertTrue(((String) so1.getAttribute("Description")).contains("span content"));
	}

	@Test
	void unknownDatatypeIsRegisteredUnderItsId(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = parse(tempDir, TestFixtures.REQIF_FIXTURE);

		assertNotNull(reqif.getReqIFCoreContent().getDatatype("dt-custom"),
				"unknown datatypes must be stored under their ID, not under null");
		assertEquals(ReqIFConst.UNDEFINED, reqif.getReqIFCoreContent().getDatatype("dt-custom").getType());
	}

	@Test
	void longRangeAndMissingIntegerBoundsAreAccepted(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = parse(tempDir, TestFixtures.REQIF_FIXTURE);

		DatatypeInteger bounded = (DatatypeInteger) reqif.getReqIFCoreContent().getDatatype("dt-int");
		assertEquals(0L, bounded.getMin());
		assertEquals(Long.MAX_VALUE, bounded.getMax(), "long-range MAX must not overflow");

		DatatypeInteger unbounded = (DatatypeInteger) reqif.getReqIFCoreContent().getDatatype("dt-int-unbounded");
		assertEquals(Long.MIN_VALUE, unbounded.getMin(), "missing MIN must not throw");
		assertEquals(Long.MAX_VALUE, unbounded.getMax(), "missing MAX must not throw");

		DatatypeString stringType = (DatatypeString) reqif.getReqIFCoreContent().getDatatype("dt-string-unbounded");
		assertEquals(Integer.MAX_VALUE, stringType.getMaxLength(), "missing MAX-LENGTH must not throw");
	}

	@Test
	void emptyNumericValuesDefaultToZero(@TempDir Path tempDir) throws Exception {
		String fixture = TestFixtures.REQIF_FIXTURE.replace(
				"<ATTRIBUTE-VALUE-INTEGER THE-VALUE=\"5\">", "<ATTRIBUTE-VALUE-INTEGER>");

		ReqIF reqif = parse(tempDir, fixture);
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");

		assertEquals(0, so1.getAttribute("Priority"),
				"a missing THE-VALUE on an integer attribute must parse as 0, not crash");
	}

	@Test
	void getXHTMLContentReturnsElementListWithoutClassCastException(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = parse(tempDir, TestFixtures.REQIF_FIXTURE);
		SpecHierarchy sh1 = reqif.getReqIFCoreContent().getSpecificationsList().get(0).getAllSpecHierarchies().get(0);

		AttributeValueXHTMLElementList content = sh1.getXHTMLContent();
		assertNotNull(content);
		assertTrue(content.size() > 0, "element list must contain the deconstructed XHTML content");
	}

	@Test
	void headerWithoutCreatedByCommentDoesNotCrash(@TempDir Path tempDir) throws Exception {
		String fixture = TestFixtures.REQIF_FIXTURE.replace(
				"<COMMENT>Created by: Tester</COMMENT>", "<COMMENT>just a plain comment</COMMENT>");

		ReqIF reqif = parse(tempDir, fixture);

		assertEquals("", reqif.getReqIFHeader().getAuthor(),
				"a comment without 'Created by:' must leave the author empty instead of crashing");
		assertEquals("23.07.2026", reqif.getReqIFHeader().getCreationDate());
		assertEquals("Tester", parse(tempDir, TestFixtures.REQIF_FIXTURE).getReqIFHeader().getAuthor());
	}

	@Test
	void dateValuesOnSpecificationsAreParsed(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = parse(tempDir, TestFixtures.REQIF_FIXTURE);
		Specification spec = reqif.getReqIFCoreContent().getSpecification("spec-1");

		assertEquals("2026-01-01", spec.getAttribute("ReviewDate"),
				"DATE attribute values on specifications were formerly dropped");
	}

	@Test
	void unknownAttributeNamesReturnNullInsteadOfThrowing(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = parse(tempDir, TestFixtures.REQIF_FIXTURE);

		assertNull(reqif.getReqIFCoreContent().getSpecObject("so-1").getAttribute("DoesNotExist"));
		assertNull(reqif.getReqIFCoreContent().getSpecification("spec-1").getAttribute("DoesNotExist"));
	}
}
