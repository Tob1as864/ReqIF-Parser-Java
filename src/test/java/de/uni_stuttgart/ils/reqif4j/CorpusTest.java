package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.UninterpretedAttributeValue;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFz;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationResult;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFzWriter;

/**
 * Exports of real tools (DOORS, Polarion, ReqIF Studio, Enterprise Architect,
 * Capella, StrictDoc) and the test cases of the ReqIF Implementor Forum, see
 * src/test/resources/corpus/README.md. Many of them are not schema-valid -
 * excerpts that reference types they do not contain, duplicate identifiers -
 * which is what makes them worth testing against.
 */
class CorpusTest {

	private static final Path CORPUS = Path.of("src", "test", "resources", "corpus");
	private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);

	/** documents whose rewrite differs from the original, and why */
	private static final Map<String, String> NOT_LOSSLESS = Map.of(
			"StrictDoc/01_example/sample.reqif",
			"lacks the required LAST-CHANGE and MAX-LENGTH, which the writer fills in",
			"org.eclipse.rmf/01_specRelationTest/sample.reqif",
			"uses the namespace of a ReqIF draft (20101201); it is written in the ReqIF 1.2 namespace",
			"org.eclipse.rmf/02_sample/sample.reqif",
			"uses the namespace of a ReqIF draft (20101201); it is written in the ReqIF 1.2 namespace");

	private static List<Path> files(String extension) throws IOException {
		try (Stream<Path> files = Files.walk(CORPUS)) {
			return files.filter(file -> file.toString().endsWith(extension)).sorted().collect(Collectors.toList());
		}
	}

	private static String name(Path file) {
		return CORPUS.relativize(file).toString().replace('\\', '/');
	}

	private static String write(ReqIFDocument document) {
		return new ReqIFWriter().setClock(FIXED).toXml(document);
	}

	private static ReqIFDocument read(Path dir, String name, String xml) throws IOException {
		return new ReqIF(TestFixtures.write(dir, name, xml).toString()).getReqIFDocument();
	}

	@TestFactory
	Stream<DynamicTest> everyDocumentIsWrittenBackAsRead(@TempDir Path dir) throws IOException {
		return files(".reqif").stream().map(file -> DynamicTest.dynamicTest(name(file), () -> {

			String original = Files.readString(file, StandardCharsets.UTF_8);
			String written = write(new ReqIF(file.toString()).getReqIFDocument());

			String reason = NOT_LOSSLESS.get(name(file));
			if (reason == null) {
				assertEquals(CanonicalXml.of(original), CanonicalXml.of(written), "not lossless");
			} else {
				// listed: once it becomes lossless, the entry must go
				assertNotEquals(CanonicalXml.of(original), CanonicalXml.of(written), reason);
			}
			// writing what was written changes nothing
			assertEquals(written, write(read(dir, "again.reqif", written)), "not stable");
		}));
	}

	/**
	 * The validator's contract: a document without errors is written
	 * schema-valid; and it raises no errors for a schema-valid file.
	 */
	@TestFactory
	Stream<DynamicTest> theValidatorAgreesWithTheSchema(@TempDir Path dir) throws IOException {
		return files(".reqif").stream().map(file -> DynamicTest.dynamicTest(name(file), () -> {

			Path xsd = OfficialReqIFSchema.reqifXsd();
			ReqIFValidator validator = new ReqIFValidator();
			ReqIFDocument document = new ReqIF(file.toString()).getReqIFDocument();
			ValidationResult model = validator.validate(document);
			boolean inputValid = validator.validateAgainstSchema(file, xsd).isValid();
			ValidationResult output = validator.validateAgainstSchema(TestFixtures.write(dir, "out.reqif", write(document)), xsd);

			if (model.isValid()) {
				assertTrue(output.isValid(), "no errors reported, but the output is not schema-valid:\n" + output);
			}
			if (inputValid) {
				assertTrue(model.isValid(), "errors reported for a schema-valid file:\n" + model);
			}
		}));
	}

	@TestFactory
	Stream<DynamicTest> everyArchiveIsRepackedWithAllItsEntries(@TempDir Path dir) throws IOException {
		return files(".reqifz").stream().map(file -> DynamicTest.dynamicTest(name(file), () -> {

			Path repacked = dir.resolve(file.getParent().getFileName() + ".reqifz");
			try (ReqIFz archive = new ReqIFz(file.toString())) {
				new ReqIFzWriter(new ReqIFWriter().setClock(FIXED)).write(archive, repacked);
			}
			Map<String, String> before = entries(file);
			Map<String, String> after = entries(repacked);
			assertEquals(before.keySet(), after.keySet());
			for (Map.Entry<String, String> entry : before.entrySet()) {
				String other = after.get(entry.getKey());
				if (entry.getKey().endsWith(".reqif")) {
					assertEquals(CanonicalXml.of(entry.getValue()), CanonicalXml.of(other), entry.getKey());
				} else {
					assertEquals(entry.getValue(), other, entry.getKey());
				}
			}
		}));
	}

	/** archive entries, documents as text and attachments as their bytes in hex */
	private static Map<String, String> entries(Path archive) throws IOException {
		Map<String, String> entries = new TreeMap<String, String>();
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
				if (entry.isDirectory()) {
					continue;
				}
				try (InputStream in = zip.getInputStream(entry)) {
					byte[] bytes = in.readAllBytes();
					entries.put(entry.getName(), entry.getName().endsWith(".reqif")
							? new String(bytes, StandardCharsets.UTF_8)
							: java.util.HexFormat.of().formatHex(bytes));
				}
			}
		}
		return entries;
	}

	@Test
	void relationsMayConnectTheDocumentsOfAnArchive() throws IOException {

		// ReqIF Implementor Forum TC1700: the relation of one document connects
		// the objects of the other (GLOBAL-REF)
		try (ReqIFz archive = new ReqIFz(CORPUS.resolve("ci.eclipse.org/TC1700/sample.reqifz").toString())) {
			ReqIFDocument relations = archive.getReqIFDocuments().values().stream()
					.filter(document -> !document.getCoreContent().getSpecRelation().isEmpty()).findFirst().get();

			assertFalse(new ReqIFValidator().validate(relations).isValid(), "on its own, the objects are missing");
			assertTrue(new ReqIFValidator().validate(archive).isValid());
		}
	}

	@Test
	void valuesThatDoNotFitTheirDefinitionAreKeptAsWritten() throws IOException {

		// Polarion writes some ReqIF.Text values as ATTRIBUTE-VALUE-STRING,
		// although the attribute is XHTML
		ReqIFDocument document = new ReqIF(CORPUS.resolve("Polarion/02_example/sample.reqif").toString()).getReqIFDocument();
		SpecObject object = document.getCoreContent().getSpecObjects().values().stream()
				.filter(o -> o.getValue("rmf-a6468304-cb04-41a2-b714-f2531c8acc4b") instanceof UninterpretedAttributeValue)
				.findFirst().get();

		assertEquals("ATTRIBUTE-VALUE-STRING",
				object.getValue("rmf-a6468304-cb04-41a2-b714-f2531c8acc4b").getSourceElementName());
		assertTrue(new ReqIFValidator().validate(document).toString()
				.contains("ATTRIBUTE-VALUE-STRING for attribute 'ReqIF.Text'"), new ReqIFValidator().validate(document).toString());
	}

	@Test
	void anExcerptKeepsTheValuesOfTypesItDoesNotContain() throws IOException {

		// a DOORS export cut out of a module: the object's type is missing
		ReqIFDocument document = new ReqIF(CORPUS.resolve("Doors/03_example_from_a_user/sample.reqif").toString())
				.getReqIFDocument();
		SpecObject object = document.getCoreContent().getSpecObjects().values().iterator().next();

		assertEquals(12, object.getValues().size());
		assertTrue(object.getValues().values().stream().allMatch(value -> value instanceof UninterpretedAttributeValue));
		assertTrue(write(document).contains(">demo</reqif-xhtml:div>"));
		assertEquals("demo", object.getValues().values().iterator().next().getValue(), "the text of THE-VALUE");
	}

	@Test
	void aDraftNamespaceIsWrittenAsReqIF12() throws IOException {

		String written = write(new ReqIF(CORPUS.resolve("org.eclipse.rmf/02_sample/sample.reqif").toString())
				.getReqIFDocument());

		assertTrue(written.contains("xsi:schemaLocation=\"http://www.omg.org/spec/ReqIF/20110401/reqif.xsd reqif.xsd "
				+ "http://www.w3.org/1999/xhtml driver.xsd\""), written);
		assertFalse(written.contains("20101201"), written);
	}
}
