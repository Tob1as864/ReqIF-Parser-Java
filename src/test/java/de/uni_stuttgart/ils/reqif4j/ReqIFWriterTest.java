package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeInteger;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelation;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * The writer serializes the object model back to ReqIF XML. Round-tripping
 * (parse -> write -> parse) must preserve the content, which is the basis for
 * generating documents rather than only reading them.
 */
class ReqIFWriterTest {

	private Path tempDir;
	private ReqIF original;
	private ReqIF roundTripped;

	@BeforeEach
	void roundTrip(@TempDir Path tempDir) throws Exception {
		this.tempDir = tempDir;
		this.original = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		this.roundTripped = writeAndReadBack(this.original);
	}

	private ReqIF writeAndReadBack(ReqIF reqif) throws Exception {
		Path out = tempDir.resolve("written.reqif");
		new ReqIFWriter().write(reqif.getReqIFDocument(), out);
		return new ReqIF(out.toString());
	}

	@Test
	void writtenDocumentIsReadableAgain() throws Exception {
		Path out = tempDir.resolve("readable.reqif");
		new ReqIFWriter().write(original.getReqIFDocument(), out);

		assertTrue(Files.size(out) > 0, "the writer must produce output");
		assertNotNull(new ReqIF(out.toString()).getReqIFCoreContent());
	}

	@Test
	void headerSurvivesTheRoundTrip() {
		assertEquals(original.getReqIFHeader().getID(), roundTripped.getReqIFHeader().getID());
		assertEquals(original.getReqIFHeader().getTitle(), roundTripped.getReqIFHeader().getTitle());
		assertEquals(original.getReqIFHeader().getToolID(), roundTripped.getReqIFHeader().getToolID());
		assertEquals(original.getReqIFHeader().getSourceToolID(), roundTripped.getReqIFHeader().getSourceToolID());
		assertEquals(original.getReqIFHeader().getReqIFVersion(), roundTripped.getReqIFHeader().getReqIFVersion());
		assertEquals(original.getReqIFHeader().getComment(), roundTripped.getReqIFHeader().getComment());
		assertEquals(original.getReqIFHeader().getCreationTime(), roundTripped.getReqIFHeader().getCreationTime(),
				"the raw CREATION-TIME must be written, not the reformatted date");
	}

	@Test
	void datatypesSurviveTheRoundTrip() {
		assertEquals(original.getReqIFCoreContent().getDatatypes().keySet(),
				roundTripped.getReqIFCoreContent().getDatatypes().keySet());

		DatatypeInteger integer = (DatatypeInteger) roundTripped.getReqIFCoreContent().getDatatype("dt-int");
		assertEquals(0L, integer.getMin());
		assertEquals(Long.MAX_VALUE, integer.getMax());

		// a datatype kind the parser does not model keeps its element name
		assertEquals(ReqIFConst.UNDEFINED,
				roundTripped.getReqIFCoreContent().getDatatype("dt-custom").getType());
	}

	@Test
	void enumerationValuesSurviveTheRoundTrip() {
		DatatypeEnumeration colors = (DatatypeEnumeration) roundTripped.getReqIFCoreContent().getDatatype("dt-enum");

		assertEquals(List.of("ev-red", "ev-green", "ev-blue"), List.copyOf(colors.getEnumValues().keySet()));
		assertEquals("Red", colors.getEnumValueName("ev-red"));
		assertEquals("1", colors.getEnumValueKey("ev-red"));
		assertEquals("#ff0000", colors.getEnumValueOtherContent("ev-red"));
	}

	@Test
	void multiselectEnumValuesSurviveTheRoundTrip() {
		SpecObject so1 = roundTripped.getReqIFCoreContent().getSpecObject("so-1");
		AttributeValueEnumeration colors = (AttributeValueEnumeration) so1.getAttributes().get("Colors");

		assertEquals(List.of("ev-red", "ev-green"), colors.getValueRefs());
		assertEquals(List.of("Red", "Green"), colors.getValues());
	}

	@Test
	void enumDefaultValueSurvivesTheRoundTrip() {
		SpecObject so2 = roundTripped.getReqIFCoreContent().getSpecObject("so-2");
		AttributeValueEnumeration colors = (AttributeValueEnumeration) so2.getAttributes().get("Colors");

		assertEquals(List.of("Blue"), colors.getValues(),
				"the DEFAULT-VALUE enum reference must be written back");
	}

	@Test
	void scalarAttributeValuesSurviveTheRoundTrip() {
		SpecObject so1 = roundTripped.getReqIFCoreContent().getSpecObject("so-1");

		assertEquals("First requirement", so1.getAttribute("Title"));
		assertEquals(5, so1.getAttribute("Priority"));
	}

	@Test
	void xhtmlContentSurvivesTheRoundTrip() {
		String before = (String) original.getReqIFCoreContent().getSpecObject("so-1").getAttribute("Description");
		String after = (String) roundTripped.getReqIFCoreContent().getSpecObject("so-1").getAttribute("Description");

		assertEquals(before, after, "XHTML content including image, table and lists must be preserved");
		assertTrue(after.contains("files/image.png"));
		assertTrue(after.contains("<th>Header A</th>"));
	}

	@Test
	void specRelationsSurviveTheRoundTrip() {
		SpecRelation relation = roundTripped.getReqIFCoreContent().getSpecRelation("sr-1");

		assertNotNull(relation);
		assertEquals("so-1", relation.getSourceObjID());
		assertEquals("so-2", relation.getTargetObjID());
		assertEquals("st-rel", relation.getRelationTypeRef());
		assertEquals("derived during review", relation.getAttribute("LinkComment"),
				"relation attribute values must be written too");
	}

	@Test
	void specificationHierarchySurvivesTheRoundTrip() {
		Specification spec = roundTripped.getReqIFCoreContent().getSpecification("spec-1");

		assertEquals("Main Spec", spec.getName());
		assertEquals("2026-01-01", spec.getAttribute("ReviewDate"));

		List<SpecHierarchy> children = spec.getChildren();
		assertEquals(1, children.size());
		assertEquals("sh-1", children.get(0).getSpecHierarchyID());
		assertEquals("so-1", children.get(0).getSpecObjectID());

		List<SpecHierarchy> nested = children.get(0).getChildren();
		assertEquals(1, nested.size(), "nested spec hierarchies must be written");
		assertEquals("sh-2", nested.get(0).getSpecHierarchyID());
		assertEquals("so-2", nested.get(0).getSpecObjectID());
	}

	@Test
	void indentingIsOffByDefaultBecauseItAltersXhtmlContent() throws Exception {
		Path indented = tempDir.resolve("indented.reqif");
		new ReqIFWriter().setIndent(true).write(original.getReqIFDocument(), indented);

		String withIndent = (String) new ReqIF(indented.toString()).getReqIFCoreContent()
				.getSpecObject("so-1").getAttribute("Description");
		String withoutIndent = (String) roundTripped.getReqIFCoreContent()
				.getSpecObject("so-1").getAttribute("Description");

		assertEquals(withoutIndent,
				(String) original.getReqIFCoreContent().getSpecObject("so-1").getAttribute("Description"),
				"the default (no indent) must preserve XHTML exactly");
		assertTrue(withIndent.contains("<p> Text with"),
				"indenting injects whitespace into mixed content - this is why it is opt-in: " + withIndent);
	}

	@Test
	void writingIsStableAcrossRepeatedRoundTrips() throws Exception {
		String once = new ReqIFWriter().toXml(original.getReqIFDocument());
		String twice = new ReqIFWriter().toXml(roundTripped.getReqIFDocument());

		assertEquals(once, twice, "writing an already round-tripped document must be idempotent");
	}
}
