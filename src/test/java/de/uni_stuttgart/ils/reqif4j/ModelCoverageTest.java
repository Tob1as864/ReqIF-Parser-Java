package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.RelationGroup;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * ReqIF elements the parser used to ignore: ALTERNATIVE-ID on identifiable
 * elements, relation groups and tool extensions. They must be readable and
 * survive a round trip, otherwise writing a document silently drops content.
 */
class ModelCoverageTest {

	private ReqIF original;
	private ReqIF roundTripped;

	@BeforeEach
	void roundTrip(@TempDir Path tempDir) throws Exception {
		this.original = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());

		Path out = tempDir.resolve("written.reqif");
		new ReqIFWriter().write(original.getReqIFDocument(), out);
		this.roundTripped = new ReqIF(out.toString());
	}


	@Test
	void alternativeIdsAreRead() {
		assertEquals("alt-dt-string", original.getReqIFCoreContent().getDatatype("dt-string").getAlternativeID());
		assertEquals("alt-st-req", original.getReqIFCoreContent().getSpecType("st-req").getAlternativeID());
		assertEquals("alt-so-1", original.getReqIFCoreContent().getSpecObject("so-1").getAlternativeID());
		assertEquals("alt-spec-1", original.getReqIFCoreContent().getSpecification("spec-1").getAlternativeID());
	}

	@Test
	void elementsWithoutAlternativeIdReportNull() {
		assertNull(original.getReqIFCoreContent().getSpecObject("so-2").getAlternativeID());
		assertNull(original.getReqIFCoreContent().getDatatype("dt-bool").getAlternativeID());
	}

	@Test
	void alternativeIdsSurviveTheRoundTrip() {
		assertEquals("alt-dt-string", roundTripped.getReqIFCoreContent().getDatatype("dt-string").getAlternativeID());
		assertEquals("alt-st-req", roundTripped.getReqIFCoreContent().getSpecType("st-req").getAlternativeID());
		assertEquals("alt-so-1", roundTripped.getReqIFCoreContent().getSpecObject("so-1").getAlternativeID());
		assertEquals("alt-spec-1", roundTripped.getReqIFCoreContent().getSpecification("spec-1").getAlternativeID());
		assertEquals("alt-rg-1", roundTripped.getReqIFCoreContent().getRelationGroup("rg-1").getAlternativeID());
	}

	@Test
	void relationGroupIsRead() {
		RelationGroup group = original.getReqIFCoreContent().getRelationGroup("rg-1");

		assertNotNull(group, "SPEC-RELATION-GROUPS were formerly ignored entirely");
		assertEquals("System to Software", group.getName());
		assertEquals("st-relgroup", group.getRelationGroupTypeRef());
		assertEquals("spec-1", group.getSourceSpecificationRef());
		assertEquals("spec-1", group.getTargetSpecificationRef());
		assertEquals(List.of("sr-1"), group.getSpecRelationRefs());
	}

	@Test
	void relationGroupSurvivesTheRoundTrip() {
		RelationGroup group = roundTripped.getReqIFCoreContent().getRelationGroup("rg-1");

		assertNotNull(group);
		assertEquals("System to Software", group.getName());
		assertEquals("st-relgroup", group.getRelationGroupTypeRef());
		assertEquals(List.of("sr-1"), group.getSpecRelationRefs());
	}

	@Test
	void relationGroupTypeKeepsItsKind() {
		assertEquals(ReqIFConst.RELATION_GROUP_TYPE,
				original.getReqIFCoreContent().getSpecType("st-relgroup").getType(),
				"a RELATION-GROUP-TYPE must not be mistaken for a spec object type");
		assertEquals(ReqIFConst.RELATION_GROUP_TYPE,
				roundTripped.getReqIFCoreContent().getSpecType("st-relgroup").getType(),
				"and it must be written back under the right element name");
	}

	@Test
	void toolExtensionsAreKept() {
		assertEquals(1, original.getReqIFDocument().getToolExtensions().size(),
				"TOOL-EXTENSIONS must be captured");
	}

	@Test
	void toolExtensionsSurviveTheRoundTrip() {
		assertEquals(1, roundTripped.getReqIFDocument().getToolExtensions().size(),
				"tool extensions must be written back verbatim");

		String xml = new ReqIFWriter().toXml(original.getReqIFDocument());
		assertTrue(xml.contains("REQ-IF-TOOL-EXTENSION"), "the extension element must appear in the output");
		assertTrue(xml.contains("view=\"table\""), "the extension content must be copied unchanged: " + xml);
	}

	@Test
	void writingStaysIdempotentWithTheNewElements() {
		assertEquals(new ReqIFWriter().toXml(original.getReqIFDocument()),
				new ReqIFWriter().toXml(roundTripped.getReqIFDocument()));
	}
}
