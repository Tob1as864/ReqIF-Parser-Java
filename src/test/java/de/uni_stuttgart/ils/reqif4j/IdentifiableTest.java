package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFParseException;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * IDENTIFIER, LONG-NAME, DESC, LAST-CHANGE and ALTERNATIVE-ID are read and
 * written for every identifiable ReqIF element, so they survive a round trip.
 */
class IdentifiableTest {

	@Test
	void identifiablePropertiesAreReadForEveryKindOfElement(@TempDir Path tempDir) throws Exception {

		ReqIFCoreContent content = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF);

		Datatype string = content.getDatatype("dt-string");
		assertEquals("String", string.getName());
		assertEquals("Plain text", string.getDesc());
		assertEquals("2026-07-01T08:00:00Z", string.getLastChange());
		assertEquals("alt-dt-string", string.getAlternativeID(), "nested ALTERNATIVE-ID form");

		Identifiable draft = ((DatatypeEnumeration) content.getDatatype("dt-enum")).getEnumValues().get("ev-draft");
		assertEquals("Not reviewed yet", draft.getDesc());
		assertEquals("2026-07-01T08:00:00Z", draft.getLastChange());

		SpecType requirement = content.getSpecType("st-req");
		assertEquals("A single requirement", requirement.getDesc());
		assertEquals("2026-07-02T08:00:00Z", requirement.getAttributeDefinition("ad-id").getLastChange());

		Identifiable boot = content.getSpecObject("so-1");
		assertEquals("Boot", boot.getName(), "spec objects have a LONG-NAME too");
		assertEquals("First requirement", boot.getDesc());
		assertEquals("2026-07-03T09:15:00Z", boot.getLastChange());
		assertEquals("REQ-1", boot.getAlternativeID());

		Identifiable withoutName = content.getSpecObject("so-2");
		assertNull(withoutName.getName());
		assertNull(withoutName.getDesc());

		assertEquals("so-2 refines so-1", content.getSpecRelation("sr-1").getName());
		assertEquals("Trace link", content.getSpecRelation("sr-1").getDesc());

		Specification specification = content.getSpecification("spec-1");
		assertEquals("Top level", specification.getDesc());
		SpecHierarchy chapter = specification.getChildren().get(0);
		assertEquals("1 Boot", chapter.getName());
		assertEquals("Chapter", chapter.getDesc());
		assertEquals("2026-07-05T11:00:00Z", chapter.getLastChange());

		assertEquals("All refinements", content.getRelationGroup("rg-1").getDesc());
	}

	@Test
	void roundTripPreservesTheIdentifiablePropertiesOfAllElements(@TempDir Path tempDir) throws Exception {

		ReqIF original = new ReqIF(TestFixtures.write(tempDir, "in.reqif", TestFixtures.SCHEMA_VALID_REQIF).toString());
		String written = new ReqIFWriter().toXml(original.getReqIFDocument());
		ReqIFCoreContent roundTripped = parse(tempDir, written);

		Map<String, List<String>> before = identifiables(original.getReqIFCoreContent());
		assertEquals(28, before.size(), "every identifiable of the fixture is collected");
		assertEquals(before, identifiables(roundTripped));
	}

	@Test
	void alternativeIDIsWrittenInTheNestedSchemaForm(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", TestFixtures.SCHEMA_VALID_REQIF).toString());

		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());

		assertTrue(xml.contains("<ALTERNATIVE-ID><ALTERNATIVE-ID IDENTIFIER=\"REQ-1\"/></ALTERNATIVE-ID>"), xml);
	}

	@Test
	void missingOptionalPropertiesAreNotInvented(@TempDir Path tempDir) throws Exception {

		// LONG-NAME is optional in ReqIF; reading it used to throw a
		// NullPointerException for datatypes, spec types, attribute definitions
		// and specifications
		String withoutLongNames = TestFixtures.SCHEMA_VALID_REQIF
				.replace(" LONG-NAME=\"Bool\"", "")
				.replace(" LONG-NAME=\"Requirement\"", "")
				.replace(" LONG-NAME=\"Safety relevant\"", "")
				.replace(" LONG-NAME=\"System Spec\"", "");
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", withoutLongNames).toString());
		ReqIFCoreContent content = reqif.getReqIFCoreContent();

		assertNull(content.getDatatype("dt-bool").getName());
		assertNull(content.getSpecType("st-req").getName());
		assertNull(content.getSpecType("st-req").getAttributeDefinition("ad-safety").getName());
		assertNull(content.getSpecification("spec-1").getName());

		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());
		assertTrue(xml.contains("<DATATYPE-DEFINITION-BOOLEAN IDENTIFIER=\"dt-bool\" LAST-CHANGE=\"2026-07-01T08:00:00Z\"/>"), xml);
		assertFalse(xml.contains("LONG-NAME=\"\""), "an absent LONG-NAME must not be written empty");
		assertFalse(xml.contains("DESC=\"\""), "an absent DESC must not be written empty");
	}

	@Test
	void valuesOfUnnamedAttributeDefinitionsDoNotOverwriteEachOther(@TempDir Path tempDir) throws Exception {

		// values are kept by attribute LONG-NAME; without one, the definition's
		// IDENTIFIER is the key, so the two values stay apart
		String unnamed = TestFixtures.SCHEMA_VALID_REQIF
				.replace(" LONG-NAME=\"ReqIF.ForeignID\"", "")
				.replace(" LONG-NAME=\"Safety relevant\"", "");
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", unnamed).toString());

		assertEquals("REQ-1", reqif.getReqIFCoreContent().getSpecObject("so-1").getAttribute("ad-id"));
		assertEquals(true, reqif.getReqIFCoreContent().getSpecObject("so-1").getAttribute("ad-safety"));

		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-STRING THE-VALUE=\"REQ-1\">"), xml);
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-BOOLEAN THE-VALUE=\"true\">"), xml);
	}

	@Test
	void elementWithoutIdentifierIsSkippedAndReported(@TempDir Path tempDir) throws Exception {

		String withoutIdentifier = TestFixtures.SCHEMA_VALID_REQIF
				.replace("<SPECIFICATION-TYPE IDENTIFIER=\"st-spec\"", "<SPECIFICATION-TYPE");
		Path file = TestFixtures.write(tempDir, "in.reqif", withoutIdentifier);

		ReqIF reqif = new ReqIF(file.toString());

		assertEquals("SPECIFICATION-TYPE without IDENTIFIER; the spec type is skipped",
				reqif.getReqIFDocument().getParseIssues().get(0).getMessage());
		assertNull(reqif.getReqIFCoreContent().getSpecType("st-spec"));
		// the specification referencing it is kept, with its type reference
		assertEquals("st-spec", reqif.getReqIFCoreContent().getSpecification("spec-1").getSpecTypeID());
	}

	@Test
	void modelConstructorsRejectAnElementWithoutIdentifier() throws Exception {

		org.w3c.dom.Element withoutIdentifier = de.uni_stuttgart.ils.reqif4j.util.SecureXml.newDocumentBuilder()
				.parse(new org.xml.sax.InputSource(new java.io.StringReader("<SPEC-OBJECT-TYPE LONG-NAME=\"x\"/>")))
				.getDocumentElement();

		ReqIFParseException failure = assertThrows(ReqIFParseException.class,
				() -> new de.uni_stuttgart.ils.reqif4j.specification.SpecObjectType(withoutIdentifier, java.util.Map.of()));

		assertEquals("SPEC-OBJECT-TYPE without IDENTIFIER", failure.getMessage());
	}

	@Test
	void generatedElementsCarryDescAndLastChange() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr").title("T"))
				.stringDatatype("dt-string", "String", 100)
				.specObjectType("st-req", "Requirement", t -> t.stringAttribute("ad-name", "Name", "dt-string"))
				.specObject("so-1", "st-req", o -> o.set("ad-name", "x"))
				.build();
		document.getCoreContent().getSpecObject("so-1")
				.setDesc("generated")
				.setLastChange("2026-10-11T12:00:00Z")
				.setAlternativeID("ALT-1");

		String xml = new ReqIFWriter().toXml(document);

		assertTrue(xml.contains("<SPEC-OBJECT DESC=\"generated\" IDENTIFIER=\"so-1\" LAST-CHANGE=\"2026-10-11T12:00:00Z\">"), xml);
		assertTrue(xml.contains("<ALTERNATIVE-ID IDENTIFIER=\"ALT-1\"/>"), xml);
	}

	@Test
	void writerFollowsTheSchemaOrderAndSkipsEmptyXhtml(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", TestFixtures.SCHEMA_VALID_REQIF).toString());

		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());

		// REQ-IF-CONTENT is an xsd:sequence ending with the relation groups
		assertTrue(xml.indexOf("<SPECIFICATIONS>") < xml.indexOf("<SPEC-RELATION-GROUPS>"), xml);
		// so-2 has no ReqIF.Text; an empty THE-VALUE would be schema-invalid
		assertFalse(xml.contains("<THE-VALUE/>"), xml);
	}

	private static ReqIFCoreContent parse(Path dir, String content) throws Exception {
		return new ReqIF(TestFixtures.write(dir, "doc-" + System.nanoTime() + ".reqif", content).toString())
				.getReqIFCoreContent();
	}

	/**
	 * @return every identifiable element of the content, by kind and id, with
	 *         its LONG-NAME, DESC, LAST-CHANGE and ALTERNATIVE-ID
	 */
	private static Map<String, List<String>> identifiables(ReqIFCoreContent content) {

		Map<String, List<String>> all = new TreeMap<String, List<String>>();
		for (Datatype datatype : content.getDatatypes().values()) {
			put(all, "datatype", datatype);
			if (datatype instanceof DatatypeEnumeration) {
				((DatatypeEnumeration) datatype).getEnumValues().values().forEach(value -> put(all, "enum-value", value));
			}
		}
		for (SpecType specType : content.getSpecTypes().values()) {
			put(all, "spec-type", specType);
			for (AttributeDefinition definition : specType.getAttributeDefinitions().values()) {
				put(all, "attribute-definition", definition);
			}
		}
		content.getSpecObjects().values().forEach(specObject -> put(all, "spec-object", specObject));
		content.getSpecRelation().values().forEach(relation -> put(all, "spec-relation", relation));
		for (Specification specification : content.getSpecifications().values()) {
			put(all, "specification", specification);
			specification.getAllSpecHierarchies().forEach(hierarchy -> put(all, "spec-hierarchy", hierarchy));
		}
		content.getRelationGroups().values().forEach(group -> put(all, "relation-group", group));
		return all;
	}

	private static void put(Map<String, List<String>> all, String kind, Identifiable identifiable) {
		all.put(kind + ":" + identifiable.getID(), Arrays.asList(identifiable.getName(), identifiable.getDesc(),
				identifiable.getLastChange(), identifiable.getAlternativeID()));
	}
}
