package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuildException;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.specification.RelationGroup;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * Everything the parser reads can also be built: relation groups, the common
 * element properties, the document language, tool extensions, editability
 * and simplified XHTML values.
 */
class BuilderCompletenessTest {

	private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-11T12:34:56Z"), ZoneOffset.UTC);

	private static ReqIFBuilder base() {
		return ReqIFBuilder.create()
				.header(h -> h.id("hdr").creationTime("2026-07-23T10:00:00Z"))
				.stringDatatype("dt-string", "String", 255)
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.specObjectType("st-req", "Requirement", t -> t
						.stringAttribute("ad-id", "ReqIF.ForeignID", "dt-string")
						.xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml")
						.editable("ad-id", false)
						.editable("ad-text", true))
				.specificationType("st-spec", "Specification", t -> t
						.xhtmlAttribute("ad-spec-text", "Text", "dt-xhtml"))
				.specRelationType("st-rel", "refines", t -> { })
				.relationGroupType("st-group", "Trace", t -> { })
				.specObject("so-1", "st-req", o -> o
						.set("ad-id", "R-1")
						.setXhtml("ad-text", "<div><p>Preview</p></div>",
								"<div><p>The complete <b>content</b></p></div>"))
				.specObject("so-2", "st-req", o -> o.set("ad-id", "R-2"))
				.specRelation("sr-1", "st-rel", "so-2", "so-1")
				.specification("spec-1", "System", "st-spec", s -> s
						.setXhtml("ad-spec-text", "<p>About</p>")
						.child("sh-1", "so-1", h -> h
								.tableInternal(false)
								.editable(true)
								.editableAttributes("ad-text")
								.child("sh-2", "so-2")))
				.specification("spec-2", "Software", "st-spec", s -> s.child("sh-3", "so-2"));
	}

	private static ReqIFDocument full() {
		return base()
				.relationGroup("rg-1", "System to software", "st-group", "spec-1", "spec-2", "sr-1")
				.with("so-1", e -> e.setDesc("Boot").setAlternativeID("REQ-1").setLastChange("2026-07-01T08:00:00Z"))
				.with("sh-2", e -> e.setName("Child"))
				.with("ad-id", e -> e.setDesc("Foreign key"))
				.with("rg-1", e -> e.setDesc("Trace view"))
				.lang("de")
				.toolExtension("<tool:settings xmlns:tool=\"urn:example:tool\" mode=\"strict\"/>")
				.toolExtension("<tool:view xmlns:tool=\"urn:example:tool\"><tool:column>ID</tool:column></tool:view>")
				.build();
	}

	@Test
	void everythingBuiltIsWrittenSchemaValidAndReadBack(@TempDir Path dir) throws Exception {

		ReqIFDocument document = full();
		assertTrue(new ReqIFValidator().validate(document).isValid(), new ReqIFValidator().validate(document).toString());
		OfficialReqIFSchema.assertSchemaValid(document);

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);
		ReqIFDocument read = new ReqIF(TestFixtures.write(dir, "built.reqif", xml).toString()).getReqIFDocument();
		ReqIFCoreContent content = read.getCoreContent();

		RelationGroup group = content.getRelationGroup("rg-1");
		assertEquals("st-group", group.getRelationGroupTypeRef());
		assertEquals("spec-1", group.getSourceSpecificationRef());
		assertEquals("spec-2", group.getTargetSpecificationRef());
		assertEquals(List.of("sr-1"), group.getSpecRelationRefs());
		assertEquals("Trace view", group.getDesc());

		SpecObject so1 = content.getSpecObject("so-1");
		assertEquals("Boot", so1.getDesc());
		assertEquals("REQ-1", so1.getAlternativeID());
		assertEquals("2026-07-01T08:00:00Z", so1.getLastChange(), "set explicitly");
		assertEquals("2026-07-23T10:00:00Z", content.getSpecObject("so-2").getLastChange(), "the default");
		assertEquals("Child", content.getSpecHierarchy("sh-2").getName());
		assertEquals("Foreign key", content.getSpecType("st-req").getAttributeDefinition("ad-id").getDesc());
		assertEquals(Boolean.FALSE, content.getSpecType("st-req").getAttributeDefinition("ad-id").getEditable());
		assertEquals(Boolean.TRUE, content.getSpecType("st-req").getAttributeDefinition("ad-text").getEditable());

		SpecHierarchy sh1 = content.getSpecHierarchy("sh-1");
		assertEquals(Boolean.FALSE, sh1.getTableInternal());
		assertEquals(Boolean.TRUE, sh1.getEditable());
		assertEquals(List.of("ad-text"), sh1.getEditableAttributeIDs());
		assertEquals("ATTRIBUTE-DEFINITION-XHTML-REF", sh1.getEditableAttributeRefs().get(0).getValue());

		AttributeValueXHTML text = (AttributeValueXHTML) so1.getValues().get("ad-text");
		assertEquals(Boolean.TRUE, text.getSimplified());
		assertNotNull(text.getOriginalValue());
		assertTrue(text.getOriginalValue().toString().contains("complete"), text.getOriginalValue().toString());
		assertTrue(text.getDivValue().toString().contains("Preview"));

		assertEquals("de", read.getLang());
		assertEquals(1, read.getToolExtensions().size(), "one TOOL-EXTENSIONS holds both");
		assertTrue(xml.contains("<REQ-IF-TOOL-EXTENSION><tool:settings"), xml);
		assertTrue(xml.contains("<tool:column>ID</tool:column>"), xml);
	}

	@Test
	void buildingTwiceWritesTheSame() {
		ReqIFWriter writer = new ReqIFWriter().setClock(FIXED);
		assertEquals(writer.toXml(full()), writer.toXml(full()));
	}

	@Test
	void relationGroupsReferenceWhatExists() {

		assertThrows(ReqIFBuildException.class,
				() -> base().relationGroup("rg", null, "st-rel", "spec-1", "spec-2"), "not a group type");
		assertThrows(ReqIFBuildException.class,
				() -> base().relationGroup("rg", null, "st-group", "spec-9", "spec-2"));
		assertThrows(ReqIFBuildException.class,
				() -> base().relationGroup("rg", null, "st-group", "spec-1", "spec-9"));
		assertThrows(ReqIFBuildException.class,
				() -> base().relationGroup("rg", null, "st-group", "spec-1", "spec-2", "sr-9"));
		assertThrows(ReqIFBuildException.class,
				() -> base().relationGroup("so-1", null, "st-group", "spec-1", "spec-2"), "identifier taken");

		// a group without relations is fine
		ReqIFDocument document = base().relationGroup("rg", null, "st-group", "spec-1", "spec-2").build();
		assertEquals(List.of(), document.getCoreContent().getRelationGroup("rg").getSpecRelationRefs());
	}

	@Test
	void aFailedRelationGroupLeavesItsIdentifierFree() {
		ReqIFBuilder builder = base();
		assertThrows(ReqIFBuildException.class, () -> builder.relationGroup("rg", null, "st-rel", "spec-1", "spec-2"));
		builder.relationGroup("rg", null, "st-group", "spec-1", "spec-2");
		assertNotNull(builder.build().getCoreContent().getRelationGroup("rg"));
	}

	@Test
	void emptyToolExtensionsAndSpecificationValuesAreBuilt() throws Exception {
		ReqIFDocument document = base()
				.specification("spec-3", null, "st-spec", s -> s.setXhtml("ad-spec-text", "<p>Preview</p>", "<p>All</p>"))
				.toolExtension("")
				.toolExtension("<!-- nothing yet -->")
				.build();
		OfficialReqIFSchema.assertSchemaValid(document);
		AttributeValueXHTML value = (AttributeValueXHTML) document.getCoreContent().getSpecification("spec-3")
				.getValues().get("ad-spec-text");
		assertEquals(Boolean.TRUE, value.getSimplified());
	}

	@Test
	void editableAttributesBelongToTheObjectsType(@TempDir Path dir) throws Exception {

		ReqIFDocument document = full();
		SpecHierarchy sh1 = document.getCoreContent().getSpecHierarchy("sh-1");
		assertThrows(IllegalArgumentException.class, () -> sh1.setEditableAttributes(
				List.of(document.getCoreContent().getSpecType("st-spec").getAttributeDefinition("ad-spec-text"))));
		sh1.setEditableAttributes(null);
		assertEquals(List.of(), sh1.getEditableAttributeIDs());

		// as read from a file
		String xml = new ReqIFWriter().setClock(FIXED).toXml(full());
		String otherType = xml.replace(
				"<ATTRIBUTE-DEFINITION-XHTML-REF>ad-text</ATTRIBUTE-DEFINITION-XHTML-REF></EDITABLE-ATTS>",
				"<ATTRIBUTE-DEFINITION-XHTML-REF>ad-spec-text</ATTRIBUTE-DEFINITION-XHTML-REF></EDITABLE-ATTS>");
		ReqIFDocument read = new ReqIF(TestFixtures.write(dir, "type.reqif", otherType).toString()).getReqIFDocument();
		String report = new ReqIFValidator().validate(read).toString();
		assertTrue(report.contains("not an attribute of spec type st-req"), report);
	}

	@Test
	void editableAttributeReferencesAreWrittenAsTheirDefinitionsKind(@TempDir Path dir) throws Exception {

		String xml = new ReqIFWriter().setClock(FIXED).toXml(full()).replace(
				"<ATTRIBUTE-DEFINITION-XHTML-REF>ad-text</ATTRIBUTE-DEFINITION-XHTML-REF></EDITABLE-ATTS>",
				"<ATTRIBUTE-DEFINITION-STRING-REF>ad-text</ATTRIBUTE-DEFINITION-STRING-REF></EDITABLE-ATTS>");
		ReqIFDocument wrongKind = new ReqIF(TestFixtures.write(dir, "kind.reqif", xml).toString()).getReqIFDocument();

		String written = new ReqIFWriter().setClock(FIXED).toXml(wrongKind);

		assertTrue(written.contains("<ATTRIBUTE-DEFINITION-XHTML-REF>ad-text</ATTRIBUTE-DEFINITION-XHTML-REF></EDITABLE-ATTS>"),
				written);
		OfficialReqIFSchema.assertSchemaValid(wrongKind);
	}

	@Test
	void aFailedCallLeavesItsIdentifiersFree() {
		ReqIFBuilder builder = base();
		assertThrows(ReqIFBuildException.class, () -> builder.specification("spec-x", null, "st-spec",
				s -> s.child("sh-x", "so-1", h -> h.editableAttributes("ad-none"))));
		builder.specification("spec-x", null, "st-spec", s -> s.child("sh-x", "so-1"));
		assertNotNull(builder.build().getCoreContent().getSpecHierarchy("sh-x"));
	}

	@Test
	void elementsHaveTypesOfTheirKind() {
		assertThrows(ReqIFBuildException.class, () -> base().specObject("so-x", "st-group"));
		assertThrows(ReqIFBuildException.class, () -> base().specObject("so-x", "st-spec"));
		assertThrows(ReqIFBuildException.class, () -> base().specRelation("sr-x", "st-req", "so-1", "so-2"));
		assertThrows(ReqIFBuildException.class, () -> base().specification("spec-x", null, "st-rel", s -> { }));
	}

	@Test
	void anInvalidLastChangeIsRefused() {
		ReqIFBuilder builder = base().with("so-1", e -> e.setLastChange("2026-07-01T08:00:00Z"));
		assertThrows(ReqIFBuildException.class, () -> builder.with("so-1", e -> e.setLastChange("yesterday")));
		assertEquals("2026-07-01T08:00:00Z", builder.build().getCoreContent().getSpecObject("so-1").getLastChange());
	}

	@Test
	void unknownElementsAndBadExtensionsAreRefused() {

		assertThrows(ReqIFBuildException.class, () -> base().with("nothing", e -> e.setDesc("x")));
		assertThrows(ReqIFBuildException.class, () -> base().toolExtension("<open>"));
		assertThrows(ReqIFBuildException.class, () -> base().toolExtension("<settings/>"), "no namespace");
		assertThrows(ReqIFBuildException.class, () -> base().toolExtension("vendor blob"), "text");
		assertThrows(ReqIFBuildException.class, () -> base().toolExtension(
				"<SPEC-OBJECT xmlns=\"http://www.omg.org/spec/ReqIF/20110401/reqif.xsd\"/>"), "ReqIF's namespace");
		assertThrows(ReqIFBuildException.class, () -> base().specObject("so-x", "st-req",
				o -> o.setXhtml("ad-text", "", "<p>b</p>")), "an empty simplified value");
		assertThrows(ReqIFBuildException.class, () -> base().toolExtension(
				"</REQ-IF-TOOL-EXTENSION><REQ-IF-TOOL-EXTENSION>"), "must not break out of its element");
		assertThrows(ReqIFBuildException.class, () -> base().specObjectType("st-x", null,
				t -> t.editable("ad-none", true)));
		assertThrows(ReqIFBuildException.class, () -> base().specification("spec-x", null, "st-spec",
				s -> s.child("sh-x", "so-1", h -> h.editableAttributes("ad-spec-text"))),
				"not an attribute of the object's type");
		assertThrows(ReqIFBuildException.class, () -> base().specObject("so-x", "st-req",
				o -> o.setXhtml("ad-text", "<p>a</p>", "")), "an empty original");
		assertThrows(ReqIFBuildException.class, () -> base().specObject("so-x", "st-req",
				o -> o.setXhtml("ad-id", "<p>a</p>", "<p>b</p>")), "not an XHTML attribute");
	}
}
