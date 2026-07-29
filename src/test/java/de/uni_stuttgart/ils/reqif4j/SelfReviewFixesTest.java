package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuildException;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriteException;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFzWriter;

/**
 * Findings of the self review:
 * 4 - the builder silently overwrote duplicate identifiers,
 * 5 - markup starting with "div" (e.g. divider) was mangled by string surgery,
 * 6 - the XML parsers of the write path were not hardened,
 * 7 - a duplicate archive entry surfaced as a raw ZipException.
 */
class SelfReviewFixesTest {

	private static ReqIFBuilder minimal() {
		return ReqIFBuilder.create()
				.header(h -> h.id("hdr").toolID("reqif4j"))
				.stringDatatype("dt", "String", 255)
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.specObjectType("st", "Type", t -> t
						.stringAttribute("ad", "Title", "dt")
						.xhtmlAttribute("ad-text", "Text", "dt-xhtml"))
				.specificationType("st-spec", "Spec Type", t -> { });
	}


	// ---------------------------------------------------------- finding 4

	@Test
	void duplicateDatatypeIdIsRejected() {
		ReqIFBuildException failure = assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.stringDatatype("dt", "A", 10)
				.stringDatatype("dt", "B", 20));

		assertTrue(failure.getMessage().contains("already used by a datatype"), failure.getMessage());
	}

	@Test
	void duplicateSpecObjectIdIsRejected() {
		assertThrows(ReqIFBuildException.class, () -> minimal()
				.specObject("so", "st", o -> o.set("ad", "first"))
				.specObject("so", "st", o -> o.set("ad", "second")));
	}

	@Test
	void identifierClashAcrossCategoriesIsRejected() {
		assertThrows(ReqIFBuildException.class, () -> minimal().specObject("dt", "st"),
				"a spec object must not reuse a datatype identifier");
		assertThrows(ReqIFBuildException.class, () -> minimal()
				.specObject("so", "st")
				.specification("so", "Main", "st-spec", s -> { }));
	}

	@Test
	void duplicateAttributeDefinitionAndHierarchyIdsAreRejected() {
		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.stringDatatype("dt", "String", 10)
				.specObjectType("st", "Type", t -> t
						.stringAttribute("ad", "A", "dt")
						.stringAttribute("ad", "B", "dt")));

		assertThrows(ReqIFBuildException.class, () -> minimal()
				.specObject("so-1", "st")
				.specObject("so-2", "st")
				.specification("spec", "Main", "st-spec", s -> s
						.child("sh", "so-1")
						.child("sh", "so-2")));
	}

	@Test
	void duplicateEnumValueIsRejected() {
		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create()
				.enumerationDatatype("dt-enum", "Color", e -> e
						.value("ev", "Red", "1")
						.value("ev", "Blue", "2")));
	}

	@Test
	void blankIdentifierIsRejected() {
		assertThrows(ReqIFBuildException.class, () -> ReqIFBuilder.create().stringDatatype("", "A", 10));
	}

	@Test
	void distinctIdentifiersStillWork() {
		ReqIFDocument document = minimal()
				.specObject("so-1", "st", o -> o.set("ad", "first"))
				.specObject("so-2", "st", o -> o.set("ad", "second"))
				.build();

		assertEquals(2, document.getCoreContent().getSpecObjects().size());
	}


	// ---------------------------------------------------------- finding 5

	@Test
	void markupWhoseRootMerelyStartsWithDivIsNotMangled() {
		ReqIFDocument document = minimal()
				.specObject("so", "st", o -> o.setXhtml("ad-text", "<divider>x</divider>"))
				.build();

		String text = (String) document.getCoreContent().getSpecObject("so").getAttribute("Text");
		assertEquals("<div><divider>x</divider></div>", text,
				"a divider element must be wrapped, not turned into a broken div");
	}

	@Test
	void surroundingDivIsStillReused() {
		ReqIFDocument document = minimal()
				.specObject("so", "st", o -> o.setXhtml("ad-text", "<div><p>a</p></div>"))
				.build();

		assertEquals("<div><p>a</p></div>",
				document.getCoreContent().getSpecObject("so").getAttribute("Text"),
				"an existing div must not be wrapped a second time");
	}

	@Test
	void severalTopLevelElementsAreWrapped() {
		ReqIFDocument document = minimal()
				.specObject("so", "st", o -> o.setXhtml("ad-text", "<p>a</p><p>b</p>"))
				.build();

		assertEquals("<div><p>a</p><p>b</p></div>",
				document.getCoreContent().getSpecObject("so").getAttribute("Text"));
	}

	@Test
	void malformedMarkupStillFails() {
		assertThrows(IllegalArgumentException.class, () -> minimal()
				.specObject("so", "st", o -> o.setXhtml("ad-text", "<p>unclosed")));
	}


	// ---------------------------------------------------------- finding 6

	@Test
	void doctypeInAGeneratedXhtmlValueIsRejected(@TempDir Path tempDir) throws Exception {
		Path secret = tempDir.resolve("secret.txt");
		Files.writeString(secret, "TOP-SECRET-CONTENT");

		String evil = "<!DOCTYPE d [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
				+ "<div><p>&xxe;</p></div>";

		assertThrows(IllegalArgumentException.class, () -> minimal()
				.specObject("so", "st", o -> o.setXhtml("ad-text", evil)),
				"the XHTML parser must reject DOCTYPEs instead of resolving entities");
	}

	@Test
	void xxeIsNotResolvedInAParsedDocument(@TempDir Path tempDir) throws Exception {
		Path secret = tempDir.resolve("secret.txt");
		Files.writeString(secret, "TOP-SECRET-CONTENT");

		String xxe = "<?xml version=\"1.0\"?>\n"
				+ "<!DOCTYPE REQ-IF [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>\n"
				+ "<REQ-IF><CORE-CONTENT>&xxe;</CORE-CONTENT></REQ-IF>";
		Path file = TestFixtures.write(tempDir, "xxe.reqif", xxe);

		assertThrows(RuntimeException.class, () -> new ReqIF(file.toString()));
	}


	// ---------------------------------------------------------- finding 7

	@Test
	void duplicateArchiveEntryGivesAClearError(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());

		ReqIFWriteException failure = assertThrows(ReqIFWriteException.class, () -> new ReqIFzWriter()
				.write(reqif.getReqIFDocument(), "spec.reqif",
						Map.of("spec.reqif", new byte[] {1}), tempDir.resolve("dup.reqifz")));

		assertTrue(failure.getMessage().contains("spec.reqif"), failure.getMessage());
		assertTrue(failure.getMessage().contains("added twice"), failure.getMessage());
	}

	@Test
	void noArchiveIsLeftBehindOnADuplicateEntry(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		Path archive = tempDir.resolve("dup.reqifz");

		assertThrows(ReqIFWriteException.class, () -> new ReqIFzWriter()
				.write(reqif.getReqIFDocument(), "spec.reqif", Map.of("spec.reqif", new byte[] {1}), archive));

		assertFalse(Files.exists(archive),
				"the name clash must be detected before the archive file is created");
	}
}
