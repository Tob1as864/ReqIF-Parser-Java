package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;
import de.uni_stuttgart.ils.reqif4j.specification.TypeClassifier;

/**
 * The requirement/headline/text classification is based on a LONG-NAME
 * substring heuristic that only fits certain tool profiles. It is now a
 * pluggable strategy: the default keeps the historic behavior, custom
 * classifiers support other naming conventions (e.g. German profiles).
 */
class TypeClassifierTest {

	/** Fixture variant with a boolean "IsReq" flag set on so-1. */
	private static String fixtureWithReqFlag() {
		return TestFixtures.REQIF_FIXTURE
				.replace("<ATTRIBUTE-DEFINITION-STRING IDENTIFIER=\"ad-title\"",
						"<ATTRIBUTE-DEFINITION-BOOLEAN IDENTIFIER=\"ad-isreq\" LONG-NAME=\"IsReq\">"
								+ "<TYPE><DATATYPE-DEFINITION-BOOLEAN-REF>dt-bool</DATATYPE-DEFINITION-BOOLEAN-REF></TYPE>"
								+ "</ATTRIBUTE-DEFINITION-BOOLEAN>"
								+ "<ATTRIBUTE-DEFINITION-STRING IDENTIFIER=\"ad-title\"")
				.replace("<ATTRIBUTE-VALUE-STRING THE-VALUE=\"First requirement\">",
						"<ATTRIBUTE-VALUE-BOOLEAN THE-VALUE=\"true\">"
								+ "<DEFINITION><ATTRIBUTE-DEFINITION-BOOLEAN-REF>ad-isreq</ATTRIBUTE-DEFINITION-BOOLEAN-REF></DEFINITION>"
								+ "</ATTRIBUTE-VALUE-BOOLEAN>"
								+ "<ATTRIBUTE-VALUE-STRING THE-VALUE=\"First requirement\">");
	}

	/** Fixture variant with a German spec type name the heuristic cannot match. */
	private static String germanFixture() {
		return TestFixtures.REQIF_FIXTURE.replace("Requirement Type", "Anforderungstyp");
	}

	@Test
	void defaultHeuristicClassifiesByLongNameSubstring(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");

		// "Requirement Type" contains "req" -> classified as requirement type
		assertEquals(ReqIFConst.REQ, so1.getType());
		// ... but without a boolean req flag attribute it does not count as one
		assertFalse(so1.isReq());
		assertTrue(so1.isText());
	}

	@Test
	void defaultHeuristicEvaluatesBooleanReqFlag(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "test.reqif", fixtureWithReqFlag()).toString());

		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");
		assertTrue(so1.isReq(), "boolean flag attribute containing 'req' must mark the object as requirement");
		assertFalse(so1.isText());

		// so-2 has no value for the flag; the default (false) applies
		assertFalse(reqif.getReqIFCoreContent().getSpecObject("so-2").isReq());
	}

	@Test
	void germanProfileIsMisclassifiedByDefaultHeuristic(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "test.reqif", germanFixture()).toString());
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");

		// documents the limitation that motivates the strategy interface
		assertEquals(ReqIFConst.TEXT, so1.getType());
		assertFalse(so1.isReq());
	}

	@Test
	void customClassifierSupportsOtherNamingConventions(@TempDir Path tempDir) throws Exception {
		TypeClassifier germanClassifier = new TypeClassifier() {

			@Override
			public String classifySpecType(SpecType specType) {
				String name = specType.getName() == null ? "" : specType.getName().toLowerCase();
				if (name.contains("anforderung")) {
					return ReqIFConst.REQ;
				}
				if (name.contains("überschrift")) {
					return ReqIFConst.HEADLINE;
				}
				return ReqIFConst.TEXT;
			}

			@Override
			public boolean isRequirement(SpecObject specObject) {
				return ReqIFConst.REQ.equals(specObject.getType());
			}

			@Override
			public boolean isSubRequirement(SpecObject specObject) {
				return false;
			}
		};

		Path file = TestFixtures.write(tempDir, "test.reqif", germanFixture());
		ReqIF reqif = new ReqIF(file.toString(), germanClassifier);
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");

		assertEquals(ReqIFConst.REQ, so1.getType(), "custom classifier must control the type classification");
		assertTrue(so1.isReq(), "custom classifier must control isReq");
		assertFalse(so1.isText());
	}

	@Test
	void nullClassifierFallsBackToDefault(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString(), null);

		assertEquals(ReqIFConst.REQ, reqif.getReqIFCoreContent().getSpecObject("so-1").getType());
	}
}
