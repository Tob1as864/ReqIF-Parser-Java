package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelation;

/**
 * Bug: SpecRelation overwrote the inherited {@code type} field — which carries
 * a content category (REQ/HEADLINE/TEXT) — with the raw
 * SPEC-RELATION-TYPE-REF id, so the inherited category methods returned
 * nonsense. The relation's own attribute values were not parsed at all.
 */
class SpecRelationTest {

	private SpecRelation relation;

	@BeforeEach
	void parseFixture(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		relation = reqif.getReqIFCoreContent().getSpecRelation("sr-1");
		assertNotNull(relation, "the fixture's spec relation must be parsed");
	}

	@Test
	void sourceAndTargetAreParsed() {
		// regression guard for the original DOORS relationship reader
		assertEquals("so-1", relation.getSourceObjID());
		assertEquals("so-2", relation.getTargetObjID());
	}

	@Test
	void inheritedTypeIsUndefinedInsteadOfTheRelationTypeRef() {
		assertEquals(ReqIFConst.UNDEFINED, relation.getType(),
				"a relation has no content category; the type ref must not leak into type");
	}

	@Test
	void relationTypeIsExposedSeparately() {
		assertEquals("st-rel", relation.getRelationTypeRef());
		assertEquals("satisfies", relation.getRelationTypeName());
	}

	@Test
	void structuralKindIsStillAvailable() {
		assertEquals(ReqIFConst.SPEC_RELATION_TYPE, relation.getSpecType(),
				"the information 'this is a relation' lives in getSpecType()");
	}

	@Test
	void contentCategoryMethodsAreAllFalse() {
		assertFalse(relation.isReq());
		assertFalse(relation.isSubReq());
		assertFalse(relation.isHeadline());
		assertFalse(relation.isText(), "a relation is not text (formerly reported true)");
	}

	@Test
	void relationAttributeValuesAreParsed() {
		assertEquals("derived during review", relation.getAttribute("LinkComment"),
				"attribute values of a relation were formerly ignored");
	}
}
