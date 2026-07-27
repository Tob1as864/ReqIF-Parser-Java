package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;

/**
 * Bugs in the XHTML rendering ({@code getValue()} / {@code toString()}):
 * text was not escaped (making the output malformed), all attributes were
 * dropped, void elements were written as {@code <br></br>}, elements without a
 * dedicated class lost their entire content, and trimming ran words together
 * across inline elements.
 */
class XHTMLRenderingTest {

	private static final String RENDER_FIXTURE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<REQ-IF xmlns="http://www.omg.org/spec/ReqIF/20110401/reqif.xsd" xmlns:xhtml="http://www.w3.org/1999/xhtml">
			  <CORE-CONTENT><REQ-IF-CONTENT>
			    <DATATYPES><DATATYPE-DEFINITION-XHTML IDENTIFIER="dt" LONG-NAME="X"/></DATATYPES>
			    <SPEC-TYPES><SPEC-OBJECT-TYPE IDENTIFIER="st" LONG-NAME="T"><SPEC-ATTRIBUTES>
			      <ATTRIBUTE-DEFINITION-XHTML IDENTIFIER="ad" LONG-NAME="Description">
			        <TYPE><DATATYPE-DEFINITION-XHTML-REF>dt</DATATYPE-DEFINITION-XHTML-REF></TYPE>
			      </ATTRIBUTE-DEFINITION-XHTML></SPEC-ATTRIBUTES></SPEC-OBJECT-TYPE></SPEC-TYPES>
			    <SPEC-OBJECTS><SPEC-OBJECT IDENTIFIER="so-1">
			      <TYPE><SPEC-OBJECT-TYPE-REF>st</SPEC-OBJECT-TYPE-REF></TYPE>
			      <VALUES><ATTRIBUTE-VALUE-XHTML>
			        <DEFINITION><ATTRIBUTE-DEFINITION-XHTML-REF>ad</ATTRIBUTE-DEFINITION-XHTML-REF></DEFINITION>
			        <THE-VALUE><xhtml:div><xhtml:p style="color:red">a &lt; b &amp; c</xhtml:p><xhtml:p>Siehe <xhtml:a href="http://x.y">diesen Link</xhtml:a> und <xhtml:em>Betonung</xhtml:em>.</xhtml:p><xhtml:br/><xhtml:table><xhtml:tr><xhtml:td colspan="2">merged</xhtml:td></xhtml:tr></xhtml:table></xhtml:div></THE-VALUE>
			      </ATTRIBUTE-VALUE-XHTML></VALUES></SPEC-OBJECT></SPEC-OBJECTS>
			    <SPEC-RELATIONS/><SPECIFICATIONS/>
			  </REQ-IF-CONTENT></CORE-CONTENT></REQ-IF>
			""";

	private String rendered;

	@BeforeEach
	void parseFixture(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "render.reqif", RENDER_FIXTURE).toString());
		rendered = (String) reqif.getReqIFCoreContent().getSpecObject("so-1").getAttribute("Description");
	}

	@Test
	void specialCharactersAreEscaped() {
		assertTrue(rendered.contains("a &lt; b &amp; c"),
				"text must be escaped, got: " + rendered);
	}

	@Test
	void outputIsWellFormedXml() {
		// Unescaped text used to make the output unparseable.
		assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new ByteArrayInputStream(rendered.getBytes(StandardCharsets.UTF_8))),
				"the rendered XHTML must be parseable again: " + rendered);
	}

	@Test
	void attributesArePreserved() {
		assertTrue(rendered.contains("style=\"color:red\""), "style attribute lost: " + rendered);
		assertTrue(rendered.contains("colspan=\"2\""), "colspan attribute lost: " + rendered);
		assertTrue(rendered.contains("href=\"http://x.y\""), "href attribute lost: " + rendered);
	}

	@Test
	void namespaceDeclarationsAreNotEmitted() {
		assertFalse(rendered.contains("xmlns"),
				"tag names are rendered without prefix, so xmlns declarations must be skipped: " + rendered);
	}

	@Test
	void voidElementsAreSelfClosing() {
		assertTrue(rendered.contains("<br/>"), "br must be self-closing, got: " + rendered);
		assertFalse(rendered.contains("</br>"),
				"<br></br> is read as two line breaks by HTML5 parsers: " + rendered);
	}

	@Test
	void elementsWithoutDedicatedClassKeepTheirContent() {
		assertTrue(rendered.contains(">diesen Link</a>"),
				"link text must not be dropped, got: " + rendered);
		assertTrue(rendered.contains("<em>Betonung</em>"),
				"emphasized text must not be dropped, got: " + rendered);
	}

	@Test
	void inlineSpacingIsPreserved() {
		assertTrue(rendered.contains("Siehe <a"), "space before an inline element must survive: " + rendered);
		assertTrue(rendered.contains("</a> und <em>"), "spaces around inline elements must survive: " + rendered);
	}

	@Test
	void renderedMarkupMatchesExpectedShape() {
		assertEquals("<div>"
				+ "<p style=\"color:red\">a &lt; b &amp; c</p>"
				+ "<p>Siehe <a href=\"http://x.y\">diesen Link</a> und <em>Betonung</em>.</p>"
				+ "<br/>"
				+ "<table><tr><td colspan=\"2\">merged</td></tr></table>"
				+ "</div>", rendered);
	}
}
