package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueString;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.specification.ReqIFImplementationGuideClassifier;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * Values are kept as the document has them - by attribute definition, in
 * document order - and defaults are not turned into values on the way out.
 */
class ExplicitValuesTest {

	@Test
	void explicitValuesAreKeptByDefinitionInDocumentOrder(@TempDir Path tempDir) throws Exception {

		SpecObject boot = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF).getReqIFCoreContent().getSpecObject("so-1");

		assertEquals(List.of("ad-id", "ad-text", "ad-prio", "ad-safety", "ad-due", "ad-status", "ad-review", "ad-effort"),
				new ArrayList<>(boot.getValues().keySet()));
		assertEquals("REQ-1", boot.getValue("ad-id").getValue());
	}

	@Test
	void defaultsAreEffectiveButNotExplicit(@TempDir Path tempDir) throws Exception {

		SpecObject withoutPriority = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF).getReqIFCoreContent().getSpecObject("so-2");

		assertNull(withoutPriority.getValue("ad-prio"), "so-2 has no own Priority value");
		assertEquals(3, withoutPriority.getAttribute("Priority"), "the definition's default applies");
		assertEquals(List.of("ad-id", "ad-safety"), new ArrayList<>(withoutPriority.getValues().keySet()));
	}

	@Test
	void defaultsAreNotWrittenAsValues(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF);

		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());

		// so-2 carries exactly its two own values, not the Priority default 3
		// or invented ones such as false for unset booleans
		String so2 = xml.substring(xml.indexOf("IDENTIFIER=\"so-2\""), xml.indexOf("</SPEC-OBJECT>", xml.indexOf("IDENTIFIER=\"so-2\"")));
		assertEquals(List.of("ad-id", "ad-safety"), definitionRefs(so2));

		// re-read, the default still applies through the definition
		ReqIF roundTripped = parse(tempDir, xml);
		assertEquals(3, roundTripped.getReqIFCoreContent().getSpecObject("so-2").getAttribute("Priority"));
	}

	@Test
	void valuesAreWrittenInDocumentOrder(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF);

		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());

		String so1 = xml.substring(xml.indexOf("IDENTIFIER=\"so-1\""), xml.indexOf("</SPEC-OBJECT>"));
		assertEquals(List.of("ad-id", "ad-text", "ad-prio", "ad-safety", "ad-due", "ad-status", "ad-review", "ad-effort"),
				definitionRefs(so1));
	}

	@Test
	void valuesOfDefinitionsSharingALongNameAreAllKept(@TempDir Path tempDir) throws Exception {

		// DOORS exports regularly reuse attribute names; keyed by name, one of
		// the two values used to be lost
		String sameName = TestFixtures.SCHEMA_VALID_REQIF.replace("LONG-NAME=\"Safety relevant\"", "LONG-NAME=\"ReqIF.ForeignID\"");
		ReqIF reqif = parse(tempDir, sameName);
		SpecObject boot = reqif.getReqIFCoreContent().getSpecObject("so-1");

		assertEquals("REQ-1", boot.getValue("ad-id").getValue());
		assertEquals(true, boot.getValue("ad-safety").getValue());

		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-STRING THE-VALUE=\"REQ-1\">"), xml);
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-BOOLEAN THE-VALUE=\"true\">"), xml);
	}

	@Test
	void theNameViewIsReadOnly(@TempDir Path tempDir) throws Exception {

		SpecObject boot = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF).getReqIFCoreContent().getSpecObject("so-1");
		AttributeValue any = boot.getValue("ad-id");

		// a change through this view would not be written, so it is refused
		assertThrows(UnsupportedOperationException.class, () -> boot.getAttributes().put("x", any));
		assertThrows(UnsupportedOperationException.class, () -> boot.getValues().remove("ad-id"));
		assertFalse(boot.getAttributes().isEmpty());
	}

	@Test
	void anExplicitValueIsNotHiddenByADefaultOfTheSameName(@TempDir Path tempDir) throws Exception {

		// ad-prio (default 3, no value on so-1 once its value is removed) and
		// ad-id share a name; so-1's explicit ad-id value must win
		String sameName = TestFixtures.SCHEMA_VALID_REQIF.replace("LONG-NAME=\"Priority\"", "LONG-NAME=\"ReqIF.ForeignID\"");
		SpecObject boot = parse(tempDir, sameName).getReqIFCoreContent().getSpecObject("so-1");
		boot.removeValue("ad-prio");

		assertEquals("REQ-1", boot.getAttribute("ReqIF.ForeignID"));
	}

	@Test
	void anEmptyEnumerationSelectionSurvivesARoundTrip(@TempDir Path tempDir) throws Exception {

		// "nothing selected" on so-1, while the definition defaults to Draft
		String withDefault = TestFixtures.SCHEMA_VALID_REQIF
				.replace("<VALUES><ENUM-VALUE-REF>ev-draft</ENUM-VALUE-REF></VALUES>", "<VALUES/>")
				.replace("LONG-NAME=\"Review state\" MULTI-VALUED=\"false\">",
						"LONG-NAME=\"Review state\" MULTI-VALUED=\"false\">"
						+ "<DEFAULT-VALUE><ATTRIBUTE-VALUE-ENUMERATION>"
						+ "<DEFINITION><ATTRIBUTE-DEFINITION-ENUMERATION-REF>ad-review</ATTRIBUTE-DEFINITION-ENUMERATION-REF></DEFINITION>"
						+ "<VALUES><ENUM-VALUE-REF>ev-draft</ENUM-VALUE-REF></VALUES>"
						+ "</ATTRIBUTE-VALUE-ENUMERATION></DEFAULT-VALUE>");
		ReqIF reqif = parse(tempDir, withDefault);
		assertEquals(List.of(), reviewState(reqif, "so-1"));
		assertEquals(List.of("ev-draft"), reviewState(reqif, "so-2"), "so-2 gets the default");

		ReqIF roundTripped = parse(tempDir, new ReqIFWriter().toXml(reqif.getReqIFDocument()));

		assertEquals(List.of(), reviewState(roundTripped, "so-1"), "the empty selection must not turn into the default");
		OfficialReqIFSchema.assertSchemaValid(reqif.getReqIFDocument());
	}

	@Test
	void valuesCanBeSetAndRemoved(@TempDir Path tempDir) throws Exception {

		ReqIF reqif = parse(tempDir, TestFixtures.SCHEMA_VALID_REQIF);
		SpecObject draft = reqif.getReqIFCoreContent().getSpecObject("so-2");
		AttributeValue foreignID = new AttributeValueString("REQ-2b",
				reqif.getReqIFCoreContent().getSpecType("st-req").getAttributeDefinition("ad-id"));

		draft.setValue(foreignID);
		draft.removeValue("ad-safety");

		assertEquals(List.of("ad-id"), new ArrayList<>(draft.getValues().keySet()));
		assertEquals("REQ-2b", draft.getAttribute("ReqIF.ForeignID"));
		String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());
		assertTrue(xml.contains("<ATTRIBUTE-VALUE-STRING THE-VALUE=\"REQ-2b\">"), xml);
	}

	@Test
	void theContentCategoryFollowsTheValues(@TempDir Path tempDir) throws Exception {

		// attribute-based classification: ReqIF.ChapterName makes a headline
		String chapter = TestFixtures.SCHEMA_VALID_REQIF.replace("LONG-NAME=\"ReqIF.ForeignID\"", "LONG-NAME=\"ReqIF.ChapterName\"");
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "chapter.reqif", chapter).toString(),
				new ReqIFImplementationGuideClassifier());
		SpecObject boot = reqif.getReqIFCoreContent().getSpecObject("so-1");
		assertTrue(boot.isHeadline());

		boot.removeValue("ad-id");

		assertFalse(boot.isHeadline(), "without a chapter name it is no headline any more");
	}

	private static List<String> reviewState(ReqIF reqif, String specObjectID) {
		return ((AttributeValueEnumeration) reqif.getReqIFCoreContent().getSpecObject(specObjectID)
				.getAttributes().get("Review state")).getValueRefs();
	}

	private static ReqIF parse(Path dir, String content) throws Exception {
		return new ReqIF(TestFixtures.write(dir, "doc-" + System.nanoTime() + ".reqif", content).toString());
	}

	private static List<String> definitionRefs(String xml) {
		List<String> refs = new ArrayList<>();
		Matcher matcher = Pattern.compile("<ATTRIBUTE-DEFINITION-[A-Z]+-REF>([^<]+)<").matcher(xml);
		while (matcher.find()) {
			refs.add(matcher.group(1));
		}
		return refs;
	}
}
