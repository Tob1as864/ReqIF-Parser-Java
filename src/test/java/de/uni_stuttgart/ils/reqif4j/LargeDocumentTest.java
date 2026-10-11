package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * Guards against work that grows with the square of the document size: a
 * specification with tens of thousands of nodes side by side is built,
 * written, read, validated and changed within a generous time limit, which
 * a quadratic step would exceed by far.
 */
class LargeDocumentTest {

	private static final int OBJECTS = 20_000;

	@Test
	void tensOfThousandsOfObjectsTakeSeconds(@TempDir Path dir) {
		assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {

			ReqIFBuilder builder = ReqIFBuilder.create()
					.stringDatatype("dt-string", "String", 255)
					.xhtmlDatatype("dt-xhtml", "XHTML")
					.specObjectType("st", "Requirement", t -> t
							.stringAttribute("ad-id", "ReqIF.ForeignID", "dt-string")
							.xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml"))
					.specificationType("st-spec", "Specification", t -> { });
			for (int i = 0; i < OBJECTS; i++) {
				String id = "so-" + i;
				builder.specObject(id, "st", o -> o.set("ad-id", id).setXhtml("ad-text", "<p>Requirement " + id + "</p>"));
			}
			builder.specification("spec", "Spec", "st-spec", s -> {
				for (int i = 0; i < OBJECTS; i++) {
					s.child("sh-" + i, "so-" + i);
				}
			});
			Path file = dir.resolve("large.reqif");
			new ReqIFWriter().write(builder.build(), file);

			ReqIFDocument read = new ReqIF(file.toString()).getReqIFDocument();
			ReqIFCoreContent content = read.getCoreContent();
			assertEquals(OBJECTS, content.getSpecification("spec").getNumberOfSpecObjects());
			assertEquals(0, new ReqIFValidator().validate(read).getErrors().size());

			// changes look nodes up by identifier
			for (int i = 0; i < 200; i++) {
				content.addSpecObject(new SpecObject("so-new-" + i, content.getSpecType("st"), java.util.List.of()));
				content.removeSpecObject("so-" + i);
			}
			content.moveSpecHierarchy("sh-" + (OBJECTS - 1), "spec", 0);
			assertEquals(OBJECTS - 200, content.getSpecification("spec").getNumberOfSpecObjects());
			new ReqIFWriter().write(read, dir.resolve("changed.reqif"));
		});
	}

	@Test
	void largeHierarchiesAreChangedNodeByNode() {
		assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {

			ReqIFBuilder builder = ReqIFBuilder.create()
					.specObjectType("st", "Requirement", t -> { })
					.specificationType("st-spec", "Specification", t -> { })
					.specObject("so-heading", "st");
			for (int i = 0; i < OBJECTS; i++) {
				builder.specObject("so-" + i, "st");
			}
			// one heading with all others below it, and one more node
			builder.specification("spec", "Spec", "st-spec", s -> s.child("sh-heading", "so-heading", h -> {
				for (int i = 0; i < OBJECTS; i++) {
					h.child("sh-" + i, "so-" + i);
				}
			}));
			ReqIFCoreContent content = builder.build().getCoreContent();

			// the heading goes, all its children move up at once
			content.removeSpecObject("so-heading");
			assertEquals(OBJECTS, content.getSpecification("spec").getNumberOfSections());

			// node by node: inserted, looked up and moved
			for (int i = 0; i < OBJECTS; i++) {
				content.addSpecHierarchy("sh-" + i, 0,
						new de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy("sh-new-" + i, 0, 0,
								content.getSpecObject("so-" + i), null));
			}
			for (int i = 0; i < OBJECTS; i++) {
				content.moveSpecHierarchy("sh-new-" + i, "spec", content.getSpecification("spec").getNumberOfSections());
			}
			assertEquals(2 * OBJECTS, content.getSpecification("spec").getNumberOfSections());
			assertEquals(2 * OBJECTS, content.getSpecification("spec").getNumberOfSpecObjects());
		});
	}
}
