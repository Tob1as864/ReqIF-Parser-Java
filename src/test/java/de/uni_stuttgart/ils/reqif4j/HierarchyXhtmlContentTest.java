package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.build.ValuesBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.util.XhtmlParser;

/**
 * Which XHTML value a hierarchy node shows, and how XHTML attributes are
 * rendered.
 */
class HierarchyXhtmlContentTest {

	/** ReqIF.Text is declared before ReqIF.ChapterName, which has a default */
	private static SpecHierarchy node(Consumer<ValuesBuilder> values) {
		ReqIFDocument document = ReqIFBuilder.create()
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.specObjectType("st", "Requirement", t -> t
						.xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml")
						.attribute("ad-chapter", "ReqIF.ChapterName", "dt-xhtml", "<p>TBD</p>"))
				.specificationType("st-spec", "Spec", t -> { })
				.specObject("so", "st", values)
				.specification("spec", null, "st-spec", s -> s.child("sh", "so"))
				.build();
		return document.getCoreContent().getSpecHierarchy("sh");
	}

	private static String firstBlock(SpecHierarchy node) {
		return node.getXHTMLDivContent().get(0).toString();
	}

	@Test
	void theSpecTypeOrderDecidesNotTheValueOrder() {
		SpecHierarchy node = node(o -> o.setXhtml("ad-chapter", "<p>Chapter</p>").setXhtml("ad-text", "<p>Text</p>"));
		assertTrue(firstBlock(node).contains("Text"), firstBlock(node));
	}

	@Test
	void anEmptyOwnValueIsNotReplacedByAnotherAttributesDefault() {
		SpecHierarchy node = node(o -> o.setXhtml("ad-text", ""));
		assertNull(node.getXHTMLDivContent());
		assertEquals(0, node.getXHTMLContent().size());
	}

	@Test
	void withoutOwnValuesTheDefaultIsShown() {
		SpecHierarchy node = node(o -> { });
		assertTrue(firstBlock(node).contains("TBD"), firstBlock(node));
	}

	@Test
	void attributesOfOtherVocabulariesKeepTheirNamespace() {
		ReqIFDocument document = ReqIFBuilder.create()
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.specObjectType("st", null, t -> t.xhtmlAttribute("ad-text", null, "dt-xhtml"))
				.specObject("so", "st", o -> o.setXhtml("ad-text",
						"<div xmlns:x=\"urn:x\" xmlns:h=\"http://www.w3.org/1999/xhtml\">"
								+ "<p x:mark=\"1\" h:class=\"dup\" class=\"c\">t</p></div>"))
				.build();
		String rendered = (String) ((AttributeValueXHTML) document.getCoreContent().getSpecObject("so")
				.getValues().get("ad-text")).getValue();

		assertTrue(rendered.contains("x:mark=\"1\"") && rendered.contains("xmlns:x=\"urn:x\""), rendered);
		assertTrue(rendered.contains("class=\"c\"") && !rendered.contains("dup"), rendered);
		XhtmlParser.parseDiv(rendered);   // well-formed
	}
}
