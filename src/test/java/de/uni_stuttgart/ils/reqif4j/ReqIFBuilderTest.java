package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuildException;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelation;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * Documents created from scratch with the builder must be writable and read
 * back identically - that is what turns the parser into a generator.
 */
class ReqIFBuilderTest {

	private Path tempDir;
	private ReqIF written;

	/** A document covering every value kind, built entirely in code. */
	private static ReqIFDocument buildDocument() {
		return ReqIFBuilder.create()
				.header(h -> h
						.id("hdr-1")
						.title("Generated Spec")
						.comment("Created by: reqif4j")
						.creationTime("2026-07-23T10:00:00Z")
						.toolID("reqif4j")
						.sourceToolID("unit test")
						.reqifVersion("1.0"))
				.stringDatatype("dt-string", "String", 4096)
				.integerDatatype("dt-int", "Integer", 0, 9223372036854775807L)
				.booleanDatatype("dt-bool", "Boolean")
				.dateDatatype("dt-date", "Date")
				.realDatatype("dt-real", "Real")
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.enumerationDatatype("dt-enum", "Color", e -> e
						.value("ev-red", "Red", "1", "#ff0000")
						.value("ev-green", "Green", "2")
						.value("ev-blue", "Blue", "3"))
				.specObjectType("st-req", "Requirement Type", t -> t
						.stringAttribute("ad-title", "ReqIF.Name", "dt-string")
						.integerAttribute("ad-prio", "Priority", "dt-int")
						.booleanAttribute("ad-done", "Done", "dt-bool")
						.dateAttribute("ad-due", "Due", "dt-date")
						.realAttribute("ad-effort", "Effort", "dt-real")
						.xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml")
						.enumerationAttribute("ad-color", "Colors", "dt-enum", true,
								List.of("ev-blue")))
				.specificationType("st-spec", "Specification Type", t -> t
						.stringAttribute("ad-owner", "Owner", "dt-string"))
				.specRelationType("st-rel", "satisfies", t -> t
						.stringAttribute("ad-comment", "LinkComment", "dt-string"))
				.specObject("so-1", "st-req", o -> o
						.set("ad-title", "First requirement")
						.set("ad-prio", 5)
						.set("ad-done", true)
						.set("ad-due", "2026-12-31")
						.set("ad-effort", 2.5)
						.setEnum("ad-color", "ev-red", "ev-green")
						.setXhtml("ad-text", "<p>The system shall boot within 5 seconds.</p>"))
				.specObject("so-2", "st-req", o -> o
						.set("ad-title", "Second requirement"))
				.specRelation("sr-1", "st-rel", "so-1", "so-2", r -> r
						.set("ad-comment", "derived during review"))
				.specification("spec-1", "Main Spec", "st-spec", s -> s
						.set("ad-owner", "Tester")
						.child("sh-1", "so-1", c -> c
								.child("sh-2", "so-2")))
				.build();
	}

	@BeforeEach
	void buildWriteAndRead(@TempDir Path tempDir) throws Exception {
		this.tempDir = tempDir;
		Path file = tempDir.resolve("generated.reqif");
		new ReqIFWriter().write(buildDocument(), file);
		this.written = new ReqIF(file.toString());
	}


	@Test
	void generatedDocumentIsReadableAgain() {
		assertNotNull(written.getReqIFHeader());
		assertNotNull(written.getReqIFCoreContent());
	}

	@Test
	void headerIsWrittenFromTheBuilder() {
		assertEquals("hdr-1", written.getReqIFHeader().getID());
		assertEquals("Generated Spec", written.getReqIFHeader().getTitle());
		assertEquals("reqif4j", written.getReqIFHeader().getToolID());
		assertEquals("unit test", written.getReqIFHeader().getSourceToolID());
		assertEquals("2026-07-23T10:00:00Z", written.getReqIFHeader().getCreationTime());
		assertEquals("23.07.2026", written.getReqIFHeader().getCreationDate(),
				"the formatted date is derived like when parsing");
		assertEquals("reqif4j", written.getReqIFHeader().getAuthor(),
				"the author is derived from the comment like when parsing");
	}

	@Test
	void datatypesAreWritten() {
		assertEquals(List.of("dt-string", "dt-int", "dt-bool", "dt-date", "dt-real", "dt-xhtml", "dt-enum"),
				List.copyOf(written.getReqIFCoreContent().getDatatypes().keySet()));

		DatatypeEnumeration colors = (DatatypeEnumeration) written.getReqIFCoreContent().getDatatype("dt-enum");
		assertEquals("Red", colors.getEnumValueName("ev-red"));
		assertEquals("#ff0000", colors.getEnumValueOtherContent("ev-red"));
	}

	@Test
	void scalarValuesOfEveryKindSurvive() {
		SpecObject so1 = written.getReqIFCoreContent().getSpecObject("so-1");

		assertEquals("First requirement", so1.getAttribute("ReqIF.Name"));
		assertEquals(5, so1.getAttribute("Priority"));
		assertEquals(true, so1.getAttribute("Done"));
		assertEquals("2026-12-31", so1.getAttribute("Due"));
		assertEquals(2.5, so1.getAttribute("Effort"));
	}

	@Test
	void multiselectEnumValuesSurvive() {
		SpecObject so1 = written.getReqIFCoreContent().getSpecObject("so-1");
		AttributeValueEnumeration colors = (AttributeValueEnumeration) so1.getAttributes().get("Colors");

		assertEquals(List.of("ev-red", "ev-green"), colors.getValueRefs());
		assertEquals(List.of("Red", "Green"), colors.getValues());
	}

	@Test
	void enumDefaultIsAppliedToObjectsWithoutAValue() {
		SpecObject so2 = written.getReqIFCoreContent().getSpecObject("so-2");
		AttributeValueEnumeration colors = (AttributeValueEnumeration) so2.getAttributes().get("Colors");

		assertEquals(List.of("Blue"), colors.getValues(),
				"the enum DEFAULT-VALUE declared in the builder must be written");
	}

	@Test
	void xhtmlValueGivenAsMarkupIsWritten() {
		String text = (String) written.getReqIFCoreContent().getSpecObject("so-1").getAttribute("ReqIF.Text");

		assertEquals("<div><p>The system shall boot within 5 seconds.</p></div>", text,
				"markup passed without a surrounding div must be wrapped");
	}

	@Test
	void relationsAndTheirAttributesAreWritten() {
		SpecRelation relation = written.getReqIFCoreContent().getSpecRelation("sr-1");

		assertNotNull(relation);
		assertEquals("so-1", relation.getSourceObjID());
		assertEquals("so-2", relation.getTargetObjID());
		assertEquals("st-rel", relation.getRelationTypeRef());
		assertEquals("satisfies", relation.getRelationTypeName());
		assertEquals("derived during review", relation.getAttribute("LinkComment"));
	}

	@Test
	void specificationHierarchyIsWritten() {
		Specification spec = written.getReqIFCoreContent().getSpecification("spec-1");

		assertEquals("Main Spec", spec.getName());
		assertEquals("Tester", spec.getAttribute("Owner"));

		List<SpecHierarchy> children = spec.getChildren();
		assertEquals(1, children.size());
		assertEquals("sh-1", children.get(0).getSpecHierarchyID());
		assertEquals("so-1", children.get(0).getSpecObjectID());

		List<SpecHierarchy> nested = children.get(0).getChildren();
		assertEquals(1, nested.size());
		assertEquals("sh-2", nested.get(0).getSpecHierarchyID());
		assertEquals("so-2", nested.get(0).getSpecObjectID());
	}

	@Test
	void classificationRunsOnGeneratedObjects() {
		// "Requirement Type" contains "req", so the default heuristic applies
		assertEquals("REQ", written.getReqIFCoreContent().getSpecObject("so-1").getType());
	}

	@Test
	void generatedDocumentRoundTripsAgain() throws Exception {
		Path second = tempDir.resolve("second.reqif");
		new ReqIFWriter().write(written.getReqIFDocument(), second);

		assertEquals(new ReqIFWriter().toXml(written.getReqIFDocument()),
				new ReqIFWriter().toXml(new ReqIF(second.toString()).getReqIFDocument()),
				"generated documents must be stable across further round trips");
	}


	@Test
	void unknownReferencesFailWhileBuilding() {
		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.specObjectType("st", "T", t -> t.stringAttribute("ad", "A", "does-not-exist")),
				"an unknown datatype must be rejected");

		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.specObject("so", "does-not-exist"),
				"an unknown spec type must be rejected");

		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.stringDatatype("dt", "S", 10)
				.specObjectType("st", "T", t -> t.stringAttribute("ad", "A", "dt"))
				.specObject("so-1", "st", o -> o.set("nope", "x")),
				"an unknown attribute definition must be rejected");
	}

	@Test
	void wrongValueKindIsRejected() {
		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.xhtmlDatatype("dt-xhtml", "X")
				.specObjectType("st", "T", t -> t.xhtmlAttribute("ad", "A", "dt-xhtml"))
				.specObject("so-1", "st", o -> o.set("ad", "plain text")),
				"an XHTML attribute must be set with setXhtml");

		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.enumerationDatatype("dt-enum", "E", e -> e.value("ev-1", "One", "1"))
				.specObjectType("st", "T", t -> t.enumerationAttribute("ad", "A", "dt-enum", false))
				.specObject("so-1", "st", o -> o.setEnum("ad", "ev-unknown")),
				"an unknown enum value must be rejected");
	}

	@Test
	void datatypeKindMismatchIsRejected() {
		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.stringDatatype("dt-string", "S", 10)
				.specObjectType("st", "T", t -> t.integerAttribute("ad", "A", "dt-string")),
				"using a string datatype for an integer attribute must be rejected");
	}

	@Test
	void writtenXmlIsWellFormedReqIF() {
		String xml = new ReqIFWriter().toXml(buildDocument());

		assertTrue(xml.contains("<REQ-IF"), xml.substring(0, Math.min(200, xml.length())));
		assertTrue(xml.contains("<DATATYPE-DEFINITION-REAL"),
				"DOUBLE must be written under its standard ReqIF name REAL");
		assertTrue(xml.contains("<SPEC-RELATION-TYPE-REF>st-rel</SPEC-RELATION-TYPE-REF>"));
	}
}
