package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTMLElementList;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;

/**
 * Bugs in the XHTML deconstruction: table cells were read via a fixed child
 * index (losing content or reading the wrong child), header cells were
 * indistinguishable from data cells, images inside cells were dropped, list
 * detection required a namespace prefix, the "/L" markers were unbalanced and
 * ordered lists were ignored entirely.
 *
 * The token list is now derived from the node tree, so both representations
 * agree.
 */
class XHTMLDeconstructionTest {

	private AttributeValueXHTMLElementList elements;

	@BeforeEach
	void parseFixture(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		AttributeValueXHTML description = (AttributeValueXHTML) reqif.getReqIFCoreContent()
				.getSpecObject("so-1").getAttributes().get("Description");
		elements = description.getElementList();
		assertNotNull(elements);
	}

	/** @return the content token list of the first element of the given type */
	private List<String> contentOf(String elementType) {
		for (int i = 0; i < elements.size(); i++) {
			if (elements.getElementType(i).equals(elementType)) {
				return elements.getElementContentList(i);
			}
		}
		return null;
	}

	private List<String> allContentOf(String elementType) {
		List<String> all = new ArrayList<>();
		for (int i = 0; i < elements.size(); i++) {
			if (elements.getElementType(i).equals(elementType)) {
				all.addAll(elements.getElementContentList(i));
			}
		}
		return all;
	}

	@Test
	void paragraphContentIsExtracted() {
		List<String> paragraph = contentOf("P");

		assertNotNull(paragraph, "the paragraph must appear in the element list");
		assertEquals(List.of("TXT", "Text with", "TXT", "span content"), paragraph);
	}

	@Test
	void headerCellsAreDistinguishableFromDataCells() {
		List<String> table = contentOf("TBL");

		assertNotNull(table, "the table must appear in the element list");
		assertEquals(List.of("TR", "TH", "Header A", "TH", "Header B"), table.subList(0, 5),
				"header cells must be reported as TH");
		assertTrue(table.contains("TC"), "data cells must be reported as TC");
	}

	@Test
	void cellWithSeveralParagraphsKeepsAllContent() {
		List<String> table = contentOf("TBL");
		int cell = table.indexOf("TC");

		assertEquals("First para Second para", table.get(cell + 1),
				"a cell with two paragraphs must not lose the second one");
	}

	@Test
	void imageInsideCellIsPreserved() {
		List<String> table = contentOf("TBL");

		assertTrue(table.contains("files/cell.png"),
				"an image inside a table cell must not be dropped, got: " + table);
	}

	@Test
	void nestedListsUseBalancedMarkers() {
		List<String> list = contentOf("L");

		assertNotNull(list, "the unordered list must appear in the element list");
		assertEquals(List.of("LE", "TXT", "Outer item", "L", "LE", "TXT", "Inner item", "/L"), list);

		long opening = list.stream().filter("L"::equals).count();
		long closing = list.stream().filter("/L"::equals).count();
		assertEquals(opening, closing, "L and /L markers must be balanced");
	}

	@Test
	void orderedListsAreNotIgnored() {
		assertTrue(allContentOf("L").contains("Numbered item"),
				"ordered lists (ol) must be deconstructed too");
	}

	@Test
	void topLevelImageIsStillExtracted() {
		assertEquals(List.of("files/image.png"), contentOf("OBJ"));
	}

	@Test
	void minifiedXmlYieldsTheSameTokens(@TempDir Path tempDir) throws Exception {
		ReqIF minified = new ReqIF(TestFixtures
				.write(tempDir, "min.reqif", TestFixtures.minified(TestFixtures.REQIF_FIXTURE)).toString());
		AttributeValueXHTML description = (AttributeValueXHTML) minified.getReqIFCoreContent()
				.getSpecObject("so-1").getAttributes().get("Description");
		List<String> table = null;
		AttributeValueXHTMLElementList minifiedElements = description.getElementList();
		for (int i = 0; i < minifiedElements.size(); i++) {
			if (minifiedElements.getElementType(i).equals("TBL")) {
				table = minifiedElements.getElementContentList(i);
			}
		}

		assertNotNull(table);
		assertEquals("First para Second para", table.get(table.indexOf("TC") + 1),
				"minified XML must not shift the cell content");
	}
}
