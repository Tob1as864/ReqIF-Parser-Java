package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * The ReqIF elements themselves may sit in a prefixed namespace
 * ({@code <rif:REQ-IF>}) instead of the default one. Such a document used to
 * fail with "Document contains no CORE-CONTENT", because elements were looked
 * up by their qualified name.
 */
class PrefixedNamespaceTest {

	/** The shared fixture, with every ReqIF element moved to a "rif" prefix. */
	private static String prefixedFixture() {

		return TestFixtures.REQIF_FIXTURE
				.replace("<REQ-IF xmlns=\"http://www.omg.org/spec/ReqIF/20110401/reqif.xsd\"",
						"<rif:REQ-IF xmlns:rif=\"http://www.omg.org/spec/ReqIF/20110401/reqif.xsd\"")
				.replace("</REQ-IF>", "</rif:REQ-IF>")
				// prefix all remaining ReqIF elements (upper case names), but
				// leave the xhtml ones and the xml declaration alone
				.replaceAll("<(/?)(?!rif:|xhtml:|myTool:|\\?|!)([A-Z][A-Z0-9-]*)", "<$1rif:$2");
	}

	private ReqIF prefixed;

	@BeforeEach
	void parsePrefixedDocument(@TempDir Path tempDir) throws Exception {
		prefixed = new ReqIF(TestFixtures.write(tempDir, "prefixed.reqif", prefixedFixture()).toString());
	}


	@Test
	void headerIsRead() {
		assertEquals("header-1", prefixed.getReqIFHeader().getID());
		assertEquals("TestDoc", prefixed.getReqIFHeader().getTitle());
		assertEquals("reqif4j", prefixed.getReqIFHeader().getToolID());
		assertEquals("2026-07-23T10:00:00Z", prefixed.getReqIFHeader().getCreationTime());
		assertEquals("Tester", prefixed.getReqIFHeader().getAuthor());
	}

	@Test
	void datatypesAreRead() {
		assertEquals(prefixedIdsOf(), List.copyOf(prefixed.getReqIFCoreContent().getDatatypes().keySet()));
		assertEquals(ReqIFConst.ENUMERATION, prefixed.getReqIFCoreContent().getDatatype("dt-enum").getType());
		assertEquals("alt-dt-string", prefixed.getReqIFCoreContent().getDatatype("dt-string").getAlternativeID());
	}

	private static List<String> prefixedIdsOf() {
		return List.of("dt-string", "dt-bool", "dt-int", "dt-int-unbounded", "dt-string-unbounded",
				"dt-date", "dt-xhtml", "dt-custom", "dt-enum");
	}

	@Test
	void specTypesAndTheirKindsAreRead() {
		assertEquals(ReqIFConst.SPEC_OBJECT_TYPE, prefixed.getReqIFCoreContent().getSpecType("st-req").getType());
		assertEquals(ReqIFConst.SPEC_RELATION_TYPE, prefixed.getReqIFCoreContent().getSpecType("st-rel").getType());
		assertEquals(ReqIFConst.RELATION_GROUP_TYPE,
				prefixed.getReqIFCoreContent().getSpecType("st-relgroup").getType());
		assertEquals(ReqIFConst.SPECIFICATION_TYPE,
				prefixed.getReqIFCoreContent().getSpecType("st-spec").getType());
	}

	@Test
	void specObjectValuesAreRead() {
		SpecObject so1 = prefixed.getReqIFCoreContent().getSpecObject("so-1");

		assertEquals("First requirement", so1.getAttribute("Title"));
		assertEquals(5, so1.getAttribute("Priority"));

		AttributeValueEnumeration colors = (AttributeValueEnumeration) so1.getAttributes().get("Colors");
		assertEquals(List.of("Red", "Green"), colors.getValues());
	}

	@Test
	void xhtmlContentIsRead() {
		String description = (String) prefixed.getReqIFCoreContent().getSpecObject("so-1").getAttribute("Description");

		assertTrue(description.contains("span content"), description);
		assertTrue(description.contains("files/image.png"), description);
	}

	@Test
	void relationsAndGroupsAreRead() {
		assertEquals("so-1", prefixed.getReqIFCoreContent().getSpecRelation("sr-1").getSourceObjID());
		assertEquals("so-2", prefixed.getReqIFCoreContent().getSpecRelation("sr-1").getTargetObjID());

		assertNotNull(prefixed.getReqIFCoreContent().getRelationGroup("rg-1"));
		assertEquals(List.of("sr-1"),
				prefixed.getReqIFCoreContent().getRelationGroup("rg-1").getSpecRelationRefs());
	}

	@Test
	void specificationHierarchyIsRead() {
		Specification spec = prefixed.getReqIFCoreContent().getSpecification("spec-1");

		assertEquals("Main Spec", spec.getName());
		List<SpecHierarchy> children = spec.getChildren();
		assertEquals(1, children.size());
		assertEquals("so-1", children.get(0).getSpecObjectID());
		assertEquals("so-2", children.get(0).getChildren().get(0).getSpecObjectID());
	}

	@Test
	void toolExtensionsAreRead() {
		assertEquals(1, prefixed.getReqIFDocument().getToolExtensions().size());
	}

	@Test
	void prefixedAndDefaultNamespaceYieldTheSameContent(@TempDir Path tempDir) throws Exception {
		ReqIF plain = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());

		// The writer always emits the ReqIF elements in the default namespace,
		// so the whole core content must render identically. Tool extensions are
		// excluded here: they are copied verbatim and therefore keep the prefix
		// of the source document (see the test below).
		assertEquals(coreContentOf(new ReqIFWriter().toXml(plain.getReqIFDocument())),
				coreContentOf(new ReqIFWriter().toXml(prefixed.getReqIFDocument())),
				"a prefixed document must produce the same content as the default-namespace one");
	}

	@Test
	void toolExtensionsKeepThePrefixOfTheSourceDocument() {
		String xml = new ReqIFWriter().toXml(prefixed.getReqIFDocument());

		assertTrue(xml.contains("rif:TOOL-EXTENSIONS"),
				"tool extensions are not interpreted, so they are copied unchanged: " + xml);
		assertTrue(xml.contains("view=\"table\""), "their content must survive: " + xml);
	}

	/** @return everything up to and including the closing CORE-CONTENT tag */
	private static String coreContentOf(String xml) {
		return xml.substring(0, xml.indexOf("</CORE-CONTENT>") + "</CORE-CONTENT>".length());
	}
}
