package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.ReqIFImplementationGuideClassifier;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;

/**
 * The {@link ReqIFImplementationGuideClassifier} classifies by the
 * standardized attribute names of the ReqIF Implementation Guide
 * (ReqIF.ChapterName / ReqIF.Text), independent of the spec type name.
 */
class ImplementationGuideClassifierTest {

	/**
	 * Fixture modeled after a DOORS-style export: a single spec object type
	 * named "Object Type" (which the LONG-NAME heuristic could not classify)
	 * with the standard Implementation Guide attributes. Three objects: a
	 * heading (ChapterName set), a requirement (Text set) and an empty one.
	 */
	private static final String GUIDE_FIXTURE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<REQ-IF xmlns="http://www.omg.org/spec/ReqIF/20110401/reqif.xsd">
			  <CORE-CONTENT>
			    <REQ-IF-CONTENT>
			      <DATATYPES>
			        <DATATYPE-DEFINITION-STRING IDENTIFIER="dt-string" LONG-NAME="String" MAX-LENGTH="4096"/>
			      </DATATYPES>
			      <SPEC-TYPES>
			        <SPEC-OBJECT-TYPE IDENTIFIER="st-obj" LONG-NAME="Object Type">
			          <SPEC-ATTRIBUTES>
			            <ATTRIBUTE-DEFINITION-STRING IDENTIFIER="ad-chapter" LONG-NAME="ReqIF.ChapterName">
			              <TYPE><DATATYPE-DEFINITION-STRING-REF>dt-string</DATATYPE-DEFINITION-STRING-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-STRING>
			            <ATTRIBUTE-DEFINITION-STRING IDENTIFIER="ad-text" LONG-NAME="ReqIF.Text">
			              <TYPE><DATATYPE-DEFINITION-STRING-REF>dt-string</DATATYPE-DEFINITION-STRING-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-STRING>
			          </SPEC-ATTRIBUTES>
			        </SPEC-OBJECT-TYPE>
			      </SPEC-TYPES>
			      <SPEC-OBJECTS>
			        <SPEC-OBJECT IDENTIFIER="so-heading">
			          <TYPE><SPEC-OBJECT-TYPE-REF>st-obj</SPEC-OBJECT-TYPE-REF></TYPE>
			          <VALUES>
			            <ATTRIBUTE-VALUE-STRING THE-VALUE="1. Introduction">
			              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>ad-chapter</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-STRING>
			          </VALUES>
			        </SPEC-OBJECT>
			        <SPEC-OBJECT IDENTIFIER="so-req">
			          <TYPE><SPEC-OBJECT-TYPE-REF>st-obj</SPEC-OBJECT-TYPE-REF></TYPE>
			          <VALUES>
			            <ATTRIBUTE-VALUE-STRING THE-VALUE="The system shall boot within 5 seconds.">
			              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>ad-text</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-STRING>
			          </VALUES>
			        </SPEC-OBJECT>
			        <SPEC-OBJECT IDENTIFIER="so-empty">
			          <TYPE><SPEC-OBJECT-TYPE-REF>st-obj</SPEC-OBJECT-TYPE-REF></TYPE>
			        </SPEC-OBJECT>
			      </SPEC-OBJECTS>
			      <SPEC-RELATIONS/>
			      <SPECIFICATIONS/>
			    </REQ-IF-CONTENT>
			  </CORE-CONTENT>
			</REQ-IF>
			""";

	@Test
	void classifiesHeadingByChapterNameAttribute(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(
				TestFixtures.write(tempDir, "guide.reqif", GUIDE_FIXTURE).toString(),
				new ReqIFImplementationGuideClassifier());

		SpecObject heading = reqif.getReqIFCoreContent().getSpecObject("so-heading");
		assertEquals(ReqIFConst.HEADLINE, heading.getType());
		assertTrue(heading.isHeadline());
		assertFalse(heading.isReq());
	}

	@Test
	void classifiesRequirementByTextAttribute(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(
				TestFixtures.write(tempDir, "guide.reqif", GUIDE_FIXTURE).toString(),
				new ReqIFImplementationGuideClassifier());

		SpecObject req = reqif.getReqIFCoreContent().getSpecObject("so-req");
		assertEquals(ReqIFConst.REQ, req.getType());
		assertTrue(req.isReq());
		assertFalse(req.isHeadline());
	}

	@Test
	void classifiesEmptyObjectAsText(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(
				TestFixtures.write(tempDir, "guide.reqif", GUIDE_FIXTURE).toString(),
				new ReqIFImplementationGuideClassifier());

		SpecObject empty = reqif.getReqIFCoreContent().getSpecObject("so-empty");
		assertEquals(ReqIFConst.TEXT, empty.getType());
		assertTrue(empty.isText());
	}

	@Test
	void defaultLongNameHeuristicCannotClassifyThisProfile(@TempDir Path tempDir) throws Exception {
		// "Object Type" contains none of req/sub/headline -> everything is TEXT
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "guide.reqif", GUIDE_FIXTURE).toString());

		assertEquals(ReqIFConst.TEXT, reqif.getReqIFCoreContent().getSpecObject("so-heading").getType(),
				"the default heuristic misses headings that are only identifiable by attribute");
		assertEquals(ReqIFConst.TEXT, reqif.getReqIFCoreContent().getSpecObject("so-req").getType());
	}

	@Test
	void customAttributeNamesAreSupported(@TempDir Path tempDir) throws Exception {
		String fixture = GUIDE_FIXTURE
				.replace("ReqIF.ChapterName", "Heading")
				.replace("ReqIF.Text", "Body");

		ReqIF reqif = new ReqIF(
				TestFixtures.write(tempDir, "guide.reqif", fixture).toString(),
				new ReqIFImplementationGuideClassifier("Heading", "Body"));

		assertEquals(ReqIFConst.HEADLINE, reqif.getReqIFCoreContent().getSpecObject("so-heading").getType());
		assertEquals(ReqIFConst.REQ, reqif.getReqIFCoreContent().getSpecObject("so-req").getType());
	}
}
