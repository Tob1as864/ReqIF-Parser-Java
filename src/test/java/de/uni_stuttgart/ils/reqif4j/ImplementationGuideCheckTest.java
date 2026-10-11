package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFz;
import de.uni_stuttgart.ils.reqif4j.validate.ImplementationGuideCheck;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationIssue;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationResult;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFzWriter;

class ImplementationGuideCheckTest {

	private static final String OLE_WITH_PREVIEW = "<div><object data=\"files/drawing.ole\" type=\"application/oleobject\">"
			+ "<object data=\"files/drawing.png\" type=\"image/png\">Drawing</object></object></div>";

	/** an object with a ReqIF.Text value of this markup */
	private static ReqIFDocument document(String markup) {
		return ReqIFBuilder.create()
				.stringDatatype("dt-string", "String", 255)
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.dateDatatype("dt-date", "Date")
				.specObjectType("st", "Requirement", t -> t
						.xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml")
						.stringAttribute("ad-name", "ReqIF.Name", "dt-string")
						.dateAttribute("ad-created", "ReqIF.ForeignCreatedOn", "dt-date")
						.stringAttribute("ad-own", "Own attribute", "dt-string"))
				.specObject("so-1", "st", o -> o.setXhtml("ad-text", markup))
				.build();
	}

	private static List<String> messages(ValidationResult result) {
		return result.getIssues().stream().map(ValidationIssue::getMessage).collect(Collectors.toList());
	}

	@Test
	void theReferenceFilesOfTheImplementorForumFollowTheGuide() throws IOException {
		try (Stream<Path> files = Files.walk(Path.of("src", "test", "resources", "corpus", "ci.eclipse.org"))) {
			for (Path file : files.filter(f -> f.toString().endsWith(".reqif")).collect(Collectors.toList())) {
				ValidationResult result = new ImplementationGuideCheck().check(new ReqIF(file.toString()).getReqIFDocument());
				assertTrue(result.getIssues().isEmpty(), file + ":\n" + result);
			}
		}
	}

	@Test
	void standardAttributesHaveTheExpectedDatatype() {

		ReqIFDocument document = ReqIFBuilder.create()
				.stringDatatype("dt-string", "String", 255)
				.specObjectType("st", "Requirement", t -> t
						.stringAttribute("ad-text", "ReqIF.Text", "dt-string")
						.stringAttribute("ad-created", "ReqIF.ForeignCreatedOn", "dt-string")
						.stringAttribute("ad-name", "ReqIF.Name", "dt-string")
						.stringAttribute("ad-own", "Own attribute", "dt-string"))
				.build();

		assertEquals(List.of(
				"The standard attribute ReqIF.Text is of kind STRING; tools expect XHTML",
				"The standard attribute ReqIF.ForeignCreatedOn is of kind STRING; tools expect DATE"),
				messages(new ImplementationGuideCheck().check(document)));
		assertTrue(new ImplementationGuideCheck().check(document(OLE_WITH_PREVIEW)).getIssues().isEmpty());
	}

	@Test
	void objectsStateTheirTypeAndComeWithAPreview() {

		ValidationResult result = new ImplementationGuideCheck().check(document("<div>"
				+ "<object data=\"files/drawing.ole\" type=\"application/oleobject\">Drawing</object>"
				+ "<object data=\"files/photo.png\">Photo</object>"
				+ "<object data=\"http://example.org/diagram.png\" type=\"image/png\">Diagram</object>"
				+ "</div>"));

		assertEquals(List.of(
				"Object files/drawing.ole of type application/oleobject has no PNG image as preview (a nested object)",
				"Object files/photo.png has no MIME type (type attribute)",
				"Object http://example.org/diagram.png is referenced by an absolute location; "
						+ "objects reference files of the archive by a relative path"),
				messages(result));
		assertTrue(result.isValid(), "conventions are warnings");
	}

	@Test
	void theFilesObjectsReferenceAreInTheArchive(@TempDir Path dir) throws IOException {

		Path archive = dir.resolve("spec.reqifz");
		new ReqIFzWriter().write(document(OLE_WITH_PREVIEW), "spec.reqif",
				Map.of("files/drawing.png", new byte[] { 1, 2, 3 }), archive);

		try (ReqIFz read = new ReqIFz(archive.toString())) {
			ValidationResult result = new ImplementationGuideCheck().check(read);

			assertEquals(List.of("spec.reqif: Object files/drawing.ole is not in the archive"), messages(result));
			assertTrue(!result.isValid());
		}
	}

	@Test
	void referencesAreResolvedAgainstTheDocumentsFolder(@TempDir Path dir) throws IOException {

		Path archive = dir.resolve("spec.reqifz");
		new ReqIFzWriter().write(document("<div>"
				+ "<object data=\"files/My%20Drawing.png\" type=\"image/png\">encoded</object>"
				+ "<object data=\" ./files/logo.png\" type=\"image/png\">blank and ./</object>"
				+ "<object data=\"../shared/icon.png\" type=\"image/png\">up</object>"
				+ "<object data=\"data:image/png;base64,iVBORw0KGgo=\" type=\"image/png\">inline</object>"
				+ "</div>"), "sub/spec.reqif",
				Map.of("sub/files/My Drawing.png", new byte[] { 1 }, "sub/files/logo.png", new byte[] { 2 },
						"shared/icon.png", new byte[] { 3 }), archive);

		try (ReqIFz read = new ReqIFz(archive.toString())) {
			ValidationResult result = new ImplementationGuideCheck().check(read);
			assertTrue(result.getIssues().isEmpty(), result.toString());
		}
	}

	@Test
	void referencesDoNotLeaveTheArchive(@TempDir Path dir) throws IOException {

		Path archive = dir.resolve("spec.reqifz");
		new ReqIFzWriter().write(document("<div><object data=\"../shared/logo.png\" type=\"image/png\">up</object></div>"),
				"spec.reqif", Map.of("shared/logo.png", new byte[] { 1 }), archive);

		try (ReqIFz read = new ReqIFz(archive.toString())) {
			assertEquals(List.of("spec.reqif: Object ../shared/logo.png points outside the archive"),
					messages(new ImplementationGuideCheck().check(read)));
		}
	}

	@Test
	void eachObjectIsReportedOnceAndDefaultsAreChecked() {

		// the preview in THE-VALUE is the one nested in THE-ORIGINAL-VALUE
		ReqIFDocument document = ReqIFBuilder.create()
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.specObjectType("st", null, t -> t
						.xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml")
						.attribute("ad-logo", "Logo", "dt-xhtml",
								"<div><object data=\"files/logo.emf\" type=\"image/x-emf\">logo</object></div>"))
				.specObject("so-1", "st", o -> o.setXhtml("ad-text",
						"<div><object data=\"files/drawing.png\">preview</object></div>",
						"<div><object data=\"files/drawing.ole\" type=\"application/oleobject\">"
								+ "<object data=\"files/drawing.png\">preview</object></object></div>"))
				.build();

		assertEquals(List.of(
				"Object files/logo.emf of type image/x-emf has no PNG image as preview (a nested object)",
				"Object files/drawing.png has no MIME type (type attribute)",
				"Object files/drawing.ole of type application/oleobject has no PNG image as preview (a nested object)"),
				messages(new ImplementationGuideCheck().check(document)));
	}

	@Test
	void simplifiedValuesCarryTheirOriginal(@TempDir Path dir) throws IOException {

		ReqIFDocument simplified = ReqIFBuilder.create()
				.xhtmlDatatype("dt-xhtml", "XHTML")
				.specObjectType("st", null, t -> t.xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml"))
				.specObject("so-1", "st", o -> o.setXhtml("ad-text", "<p>preview</p>", "<p>all</p>"))
				.build();
		assertTrue(new ImplementationGuideCheck().check(simplified).getIssues().isEmpty());

		String notMarked = new ReqIFWriter().toXml(simplified).replace("IS-SIMPLIFIED=\"true\"", "IS-SIMPLIFIED=\"false\"");
		ReqIFDocument read = new ReqIF(TestFixtures.write(dir, "in.reqif", notMarked).toString()).getReqIFDocument();

		assertEquals(List.of("An XHTML value of attribute ad-text has THE-ORIGINAL-VALUE but is not marked IS-SIMPLIFIED"),
				messages(new ImplementationGuideCheck().check(read)));
	}
}
