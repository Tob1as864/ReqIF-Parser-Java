package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementObject;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementP;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLNode;

/**
 * Bug: XHTML content with namespace prefixes (xhtml:div, xhtml:p, ...) was not
 * recognized because elements were matched against unprefixed tag names. All
 * content, including images, was silently dropped or caused an NPE when the
 * div could not be found.
 */
class XHTMLNamespaceTest {

	private ReqIF reqif;

	@BeforeEach
	void parseFixture(@TempDir Path tempDir) throws Exception {
		reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
	}

	@Test
	void xhtmlDivWithNamespacePrefixIsFound() {
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");
		String description = (String) so1.getAttribute("Description");

		assertNotNull(description);
		assertFalse(description.isEmpty(), "XHTML content must not be dropped");
		assertTrue(description.contains("Text with"), "text content must be preserved, got: " + description);
		assertTrue(description.contains("span content"), "nested span content must be preserved, got: " + description);
	}

	@Test
	void prefixedElementsAreMappedToTypedNodes() {
		SpecHierarchy sh1 = reqif.getReqIFCoreContent().getSpecificationsList().get(0).getAllSpecHierarchies().get(0);
		List<XHTMLNode> divContent = sh1.getXHTMLDivContent();

		assertNotNull(divContent, "div content must be found despite namespace prefix");
		assertTrue(divContent.stream().anyMatch(n -> n instanceof XHTMLElementP),
				"xhtml:p must be parsed as XHTMLElementP");
		assertTrue(divContent.stream().anyMatch(n -> n instanceof XHTMLElementObject),
				"xhtml:object must be parsed as XHTMLElementObject");
	}

	@Test
	void tagNamesAreExposedWithoutNamespacePrefix() {
		SpecHierarchy sh1 = reqif.getReqIFCoreContent().getSpecificationsList().get(0).getAllSpecHierarchies().get(0);

		for (XHTMLNode node : sh1.getXHTMLDivContent()) {
			assertFalse(node.getTagName().contains(":"),
					"tag name must not contain a namespace prefix: " + node.getTagName());
		}
	}
}
