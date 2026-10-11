package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueInteger;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * The model exposes every part of the ReqIF schema that used to be dropped
 * when reading: big integers, REPOSITORY-ID, xml:lang, IS-EDITABLE,
 * IS-TABLE-INTERNAL, EDITABLE-ATTS, THE-ORIGINAL-VALUE and IS-SIMPLIFIED.
 */
class ModelCompletenessTest {

	private ReqIF reqif;
	private ReqIFCoreContent content;

	@BeforeEach
	void parse(@TempDir Path tempDir) throws Exception {
		this.reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", LosslessRoundTripTest.fullFeatureDocument()).toString());
		this.content = this.reqif.getReqIFCoreContent();
	}

	@Test
	void integersAreNotLimitedToInt() {

		AttributeValueInteger big = (AttributeValueInteger) this.content.getSpecObject("so-1").getValue("ad-count");
		assertEquals(99999999999L, big.getValue(), "a Long once the value exceeds int");
		assertEquals(new BigInteger("99999999999"), big.getBigInteger());

		assertEquals(7, this.content.getSpecObject("so-2").getAttribute("Count"), "an Integer while it fits");

		AttributeValueInteger huge = new AttributeValueInteger("123456789012345678901234567890",
				this.content.getSpecType("st-req").getAttributeDefinition("ad-count"));
		assertEquals(new BigInteger("123456789012345678901234567890"), huge.getValue());
	}

	@Test
	void documentAndHeaderMetadata() {

		assertEquals("de", this.reqif.getReqIFDocument().getLang());
		assertEquals("repo-42", this.reqif.getReqIFHeader().getRepositoryID());

		String xml = new ReqIFWriter().toXml(ReqIFBuilder.create()
				.header(h -> h.id("hdr").repositoryID("repo-7").creationTime("2026-07-23T10:00:00Z"))
				.build());
		assertTrue(xml.contains("<CREATION-TIME>2026-07-23T10:00:00Z</CREATION-TIME><REPOSITORY-ID>repo-7</REPOSITORY-ID>"), xml);
	}

	@Test
	void editabilityAndTableFlags() {

		assertEquals(false, this.content.getSpecType("st-req").getAttributeDefinition("ad-id").getEditable());
		assertEquals(true, this.content.getSpecType("st-req").getAttributeDefinition("ad-text").getEditable());
		assertNull(this.content.getSpecType("st-req").getAttributeDefinition("ad-due").getEditable());

		SpecHierarchy chapter = this.content.getSpecification("spec-1").getChildren().get(0);
		assertEquals(true, chapter.getEditable());
		assertEquals(false, chapter.getTableInternal());
		assertEquals(List.of("ad-text", "ad-status", "ad-text"), chapter.getEditableAttributeIDs(),
				"in document order, a repeated reference included");

		SpecHierarchy row = chapter.getChildren().get(0);
		assertEquals(true, row.getTableInternal());
		assertNull(row.getEditable());
		assertTrue(row.getEditableAttributeIDs().isEmpty());
	}

	@Test
	void originalAndSimplifiedXhtml() {

		AttributeValueXHTML text = (AttributeValueXHTML) this.content.getSpecObject("so-1").getValue("ad-text");

		assertEquals(true, text.getSimplified());
		assertTrue(text.getOriginalValue().toString().contains("files/diagram.ole"), text.getOriginalValue().toString());
		assertTrue(!text.getValue().toString().contains("diagram.ole"), "THE-VALUE is the simplified content");
	}

	@Test
	void xhtmlValueMadeOfASingleParagraph() {

		AttributeValueXHTML paragraph = (AttributeValueXHTML) this.content.getSpecObject("so-2").getValue("ad-text");

		assertEquals("<p>A single paragraph.</p>", paragraph.getValue());
		assertEquals(1, paragraph.getElementList().size());
		assertEquals("P", paragraph.getElementList().getElementType(0));
	}

	@Test
	void xhtmlDefaultKeepsItsFormAndOriginal() {

		AttributeValueXHTML defaultText = this.content.getSpecType("st-req").getAttributeDefinition("ad-text")
				.getDefaultXhtmlValue();

		assertEquals("<p>to be written</p>", defaultText.getValue());
		assertEquals(true, defaultText.getSimplified());
		assertTrue(defaultText.getOriginalValue().toString().contains("<b>written</b>"));
	}

	@Test
	void xhtmlDefaultWithTheOriginalValueFirst(@TempDir Path tempDir) throws Exception {

		// xsd:all: THE-ORIGINAL-VALUE may come before THE-VALUE; xml:lang must
		// survive rendering, so lang next to xml:lang is no duplicate
		String reordered = LosslessRoundTripTest.fullFeatureDocument().replace(
				"<THE-VALUE><xhtml:p>to be written</xhtml:p></THE-VALUE><THE-ORIGINAL-VALUE><xhtml:div><xhtml:p>to be <xhtml:b>written</xhtml:b></xhtml:p></xhtml:div></THE-ORIGINAL-VALUE>",
				"<THE-ORIGINAL-VALUE><xhtml:div><xhtml:p>to be <xhtml:b>written</xhtml:b></xhtml:p></xhtml:div></THE-ORIGINAL-VALUE><THE-VALUE><xhtml:p xml:lang=\"de\" lang=\"de\">to be written</xhtml:p></THE-VALUE>");
		ReqIF withOriginalFirst = new ReqIF(TestFixtures.write(tempDir, "reordered.reqif", reordered).toString());
		de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition text =
				withOriginalFirst.getReqIFCoreContent().getSpecType("st-req").getAttributeDefinition("ad-text");

		// the attribute order is the DOM's; what counts is that both survive once
		String rendered = text.getDefaultValue();
		assertTrue(rendered.matches("<p( lang=\"de\"| xml:lang=\"de\"){2}>to be written</p>")
				&& rendered.contains(" lang=") && rendered.contains("xml:lang="), rendered);
		assertTrue(new ReqIFValidator().validate(withOriginalFirst.getReqIFDocument()).isValid());
	}

	@Test
	void xhtmlContentOfAHierarchyNodePrefersTheObjectsOwnText() {

		// so-2 has its own ReqIF.Text; no other XHTML default may stand in
		SpecHierarchy row = this.content.getSpecification("spec-1").getChildren().get(0).getChildren().get(0);

		assertEquals("A single paragraph.", ((de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElement) row.getXHTMLDivContent().get(0))
				.getChildren().get(0).toString());
	}

	@Test
	void invalidBooleanFlagIsNotFlipped(@TempDir Path tempDir) throws Exception {

		// xsd:boolean is case sensitive; TRUE is not false, it is unknown
		String upperCase = LosslessRoundTripTest.fullFeatureDocument().replace("IS-EDITABLE=\"true\" IS-TABLE-INTERNAL", "IS-EDITABLE=\"TRUE\" IS-TABLE-INTERNAL");
		ReqIF withInvalid = new ReqIF(TestFixtures.write(tempDir, "invalid.reqif", upperCase).toString());

		SpecHierarchy chapter = withInvalid.getReqIFCoreContent().getSpecification("spec-1").getChildren().get(0);
		assertNull(chapter.getEditable());
		assertTrue(!new ReqIFWriter().toXml(withInvalid.getReqIFDocument()).contains("IS-EDITABLE=\"false\" IS-TABLE-INTERNAL"));
	}

	@Test
	void unresolvedHierarchyReferenceIsKept(@TempDir Path tempDir) throws Exception {

		String dangling = LosslessRoundTripTest.fullFeatureDocument()
				.replace("<OBJECT><SPEC-OBJECT-REF>so-2</SPEC-OBJECT-REF></OBJECT>", "<OBJECT><SPEC-OBJECT-REF>so-gone</SPEC-OBJECT-REF></OBJECT>");
		ReqIF withDangling = new ReqIF(TestFixtures.write(tempDir, "dangling.reqif", dangling).toString());
		SpecHierarchy row = withDangling.getReqIFCoreContent().getSpecification("spec-1").getChildren().get(0).getChildren().get(0);

		assertNull(row.getSpecObject());
		assertEquals("so-gone", row.getSpecObjectID());
		assertNull(row.getXHTMLContent());
		assertNull(row.getXHTMLDivContent());
		assertTrue(new ReqIFWriter().toXml(withDangling.getReqIFDocument()).contains("<SPEC-OBJECT-REF>so-gone</SPEC-OBJECT-REF>"));
		assertTrue(new ReqIFValidator().validate(withDangling.getReqIFDocument()).getErrors().stream()
				.anyMatch(issue -> issue.getMessage().contains("references spec object so-gone")));
	}
}
