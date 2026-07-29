package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFz;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriteException;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFzWriter;

/**
 * Writing .reqifz archives: images travel with the document, and an archive
 * that was read can be re-packed without consuming its picture streams.
 */
class ReqIFzWriterTest {

	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

	private static List<String> entriesOf(Path archive) throws IOException {
		List<String> names = new ArrayList<>();
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			zip.stream().forEach(entry -> names.add(entry.getName()));
		}
		return names;
	}

	private static byte[] entryBytes(Path archive, String name) throws IOException {
		try (ZipFile zip = new ZipFile(archive.toFile());
				InputStream in = zip.getInputStream(new ZipEntry(name))) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			in.transferTo(bytes);
			return bytes.toByteArray();
		}
	}

	/** A .reqifz built by hand, as a tool would produce it. */
	private static Path createSourceArchive(Path dir) throws IOException {
		Path archive = dir.resolve("source.reqifz");
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive.toFile()))) {
			zos.putNextEntry(new ZipEntry("spec.reqif"));
			zos.write(TestFixtures.REQIF_FIXTURE.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry("files/image.png"));
			zos.write(PNG);
			zos.closeEntry();
		}
		return archive;
	}


	@Test
	void archiveContainsDocumentAndPictures(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		Path archive = tempDir.resolve("out.reqifz");

		new ReqIFzWriter().write(reqif.getReqIFDocument(), "spec.reqif",
				Map.of("files/image.png", PNG), archive);

		assertTrue(entriesOf(archive).contains("spec.reqif"));
		assertTrue(entriesOf(archive).contains("files/image.png"),
				"images must be stored under the path the XHTML object references");
		assertArrayEquals(PNG, entryBytes(archive, "files/image.png"));
	}

	@Test
	void writtenArchiveIsReadableByTheParser(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		Path archive = tempDir.resolve("readable.reqifz");
		new ReqIFzWriter().write(reqif.getReqIFDocument(), "spec.reqif",
				Map.of("files/image.png", PNG), archive);

		try (ReqIFz read = new ReqIFz(archive.toString())) {
			assertEquals(1, read.getReqIFDocumentsCount());
			assertEquals("First requirement", read.getReqIFDocuments().get("spec.reqif")
					.getCoreContent().getSpecObject("so-1").getAttribute("Title"));

			InputStream picture = read.getPictureInputStream("spec.reqif", "files/image.png");
			assertNotNull(picture, "the image must be resolvable by its object data URI");
		}
	}

	@Test
	void archiveCanBeRepackedWithoutConsumingTheStreams(@TempDir Path tempDir) throws Exception {
		Path source = createSourceArchive(tempDir);
		Path target = tempDir.resolve("repacked.reqifz");

		try (ReqIFz read = new ReqIFz(source.toString())) {
			new ReqIFzWriter().write(read, target);

			// the source object stays usable afterwards
			assertNotNull(read.getPictureInputStream("spec.reqif", "files/image.png"));
		}

		assertTrue(entriesOf(target).contains("spec.reqif"));
		assertArrayEquals(PNG, entryBytes(target, "files/image.png"),
				"pictures must be copied from the extracted files, not from consumed streams");

		try (ReqIFz repacked = new ReqIFz(target.toString())) {
			assertEquals("First requirement", repacked.getReqIFDocuments().get("spec.reqif")
					.getCoreContent().getSpecObject("so-1").getAttribute("Title"));
		}
	}

	@Test
	void severalDocumentsCanShareOneArchive(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());

		Map<String, de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument> documents = new LinkedHashMap<>();
		documents.put("a.reqif", reqif.getReqIFDocument());
		documents.put("sub/b.reqif", reqif.getReqIFDocument());

		Path archive = tempDir.resolve("multi.reqifz");
		new ReqIFzWriter().write(documents, null, archive);

		assertTrue(entriesOf(archive).containsAll(List.of("a.reqif", "sub/b.reqif")));

		try (ReqIFz read = new ReqIFz(archive.toString())) {
			assertEquals(2, read.getReqIFDocumentsCount());
		}
	}

	@Test
	void backslashesInEntryNamesAreNormalized(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		Path archive = tempDir.resolve("slashes.reqifz");

		new ReqIFzWriter().write(reqif.getReqIFDocument(), "spec.reqif",
				Map.of("files\\image.png", PNG), archive);

		assertTrue(entriesOf(archive).contains("files/image.png"),
				"zip entries always use forward slashes: " + entriesOf(archive));
	}

	@Test
	void invalidEntryNamesAreRejected(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		Path archive = tempDir.resolve("bad.reqifz");

		assertThrows(ReqIFWriteException.class, () -> new ReqIFzWriter()
				.write(reqif.getReqIFDocument(), "../escape.reqif", archive),
				"entry names must not escape the archive");
		assertThrows(ReqIFWriteException.class, () -> new ReqIFzWriter()
				.write(reqif.getReqIFDocument(), "", archive));
	}

	@Test
	void emptyArchiveIsRejected(@TempDir Path tempDir) {
		assertThrows(ReqIFWriteException.class, () -> new ReqIFzWriter()
				.write(Map.of(), null, tempDir.resolve("empty.reqifz")));
	}
}
