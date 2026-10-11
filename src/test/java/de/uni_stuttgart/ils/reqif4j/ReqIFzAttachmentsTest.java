package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFz;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFzWriter;

/**
 * A .reqifz archive keeps every file next to its documents - not only
 * pictures - and extracting it leaves nothing behind.
 */
class ReqIFzAttachmentsTest {

	private static final Map<String, byte[]> ATTACHMENTS = new LinkedHashMap<>();
	static {
		ATTACHMENTS.put("files/diagram.ole", new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0 });
		ATTACHMENTS.put("files/diagram.png", new byte[] { (byte) 0x89, 'P', 'N', 'G' });
		ATTACHMENTS.put("files/logo.GIF", "GIF89a".getBytes(StandardCharsets.US_ASCII));
		ATTACHMENTS.put("files/sub/manual.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
		ATTACHMENTS.put("files/photo.JPG", new byte[] { (byte) 0xFF, (byte) 0xD8 });
	}

	@Test
	void everyEntryIsKeptAsAnAttachment(@TempDir Path tempDir) throws Exception {

		try (ReqIFz reqifz = new ReqIFz(archive(tempDir).toString())) {

			assertEquals(List.of("spec.reqif"), List.copyOf(reqifz.getReqIFDocuments().keySet()));
			assertEquals(List.copyOf(ATTACHMENTS.keySet()), List.copyOf(reqifz.getAttachmentFiles().keySet()));
			assertEquals(List.of("files/diagram.png", "files/logo.GIF", "files/photo.JPG"),
					List.copyOf(reqifz.getPictureFiles().keySet()), "pictures are recognized case-insensitively");
			for (Map.Entry<String, File> attachment : reqifz.getAttachmentFiles().entrySet()) {
				assertArrayEquals(ATTACHMENTS.get(attachment.getKey()), Files.readAllBytes(attachment.getValue().toPath()));
			}
		}
	}

	@Test
	void repackingKeepsEveryAttachment(@TempDir Path tempDir) throws Exception {

		Path repacked = tempDir.resolve("out.reqifz");
		try (ReqIFz reqifz = new ReqIFz(archive(tempDir).toString())) {
			new ReqIFzWriter().write(reqifz, repacked);
		}

		try (ZipFile zip = new ZipFile(repacked.toFile())) {
			for (Map.Entry<String, byte[]> attachment : ATTACHMENTS.entrySet()) {
				ZipEntry entry = zip.getEntry(attachment.getKey());
				assertTrue(entry != null, attachment.getKey() + " is lost");
				try (InputStream in = zip.getInputStream(entry)) {
					assertArrayEquals(attachment.getValue(), in.readAllBytes());
				}
			}
			assertTrue(zip.getEntry("spec.reqif") != null);
		}
	}

	@Test
	void extractingLeavesNothingBehind(@TempDir Path tempDir) throws Exception {

		Path archive = archive(tempDir);
		File extracted;
		try (ReqIFz reqifz = new ReqIFz(archive.toString())) {
			extracted = reqifz.getAttachmentFiles().get("files/diagram.ole");
			assertTrue(extracted.isFile());
		}

		assertFalse(extracted.exists(), "close() deletes the extracted files");
		try (var siblings = Files.list(tempDir)) {
			assertEquals(List.of(archive), siblings.toList(), "no directory is created next to the archive");
		}
	}

	@Test
	void archiveCanBeReadFromAStream(@TempDir Path tempDir) throws Exception {

		try (ReqIFz reqifz = new ReqIFz(Files.newInputStream(archive(tempDir)), "upload.reqifz")) {
			assertEquals("upload.reqifz", reqifz.getName());
			assertEquals(1, reqifz.getReqIFDocumentsCount());
			assertEquals(ATTACHMENTS.size(), reqifz.getAttachmentFiles().size());
		}
	}

	@Test
	void documentEntriesAreRecognizedCaseInsensitively(@TempDir Path tempDir) throws Exception {

		Path archive = tempDir.resolve("upper.reqifz");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
			put(zip, "SPEC.REQIF", TestFixtures.SCHEMA_VALID_REQIF.getBytes(StandardCharsets.UTF_8));
			put(zip, "notes.notreqif", new byte[] { 1 });
		}

		try (ReqIFz reqifz = new ReqIFz(archive.toString())) {
			assertEquals(List.of("SPEC.REQIF"), List.copyOf(reqifz.getReqIFDocuments().keySet()));
			assertEquals(List.of("notes.notreqif"), List.copyOf(reqifz.getAttachmentFiles().keySet()));
		}
	}

	@Test
	void duplicateAndBackslashEntriesDoNotBreakReading(@TempDir Path tempDir) throws Exception {

		Path archive = tempDir.resolve("odd.reqifz");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
			put(zip, "spec.reqif", TestFixtures.SCHEMA_VALID_REQIF.getBytes(StandardCharsets.UTF_8));
			put(zip, "files\\old.png", new byte[] { 1 });
			// some tools append an updated entry under the same name
			put(zip, "files/old.png", new byte[] { 2 });
		}

		try (ReqIFz reqifz = new ReqIFz(archive.toString())) {
			assertEquals(List.of("files/old.png"), List.copyOf(reqifz.getAttachmentFiles().keySet()));
			assertArrayEquals(new byte[] { 2 }, Files.readAllBytes(reqifz.getAttachmentFiles().get("files/old.png").toPath()),
					"the later entry wins, as when unzipping");
		}
	}

	private static Path archive(Path dir) throws IOException {
		Path archive = dir.resolve("in.reqifz");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
			put(zip, "spec.reqif", TestFixtures.SCHEMA_VALID_REQIF.getBytes(StandardCharsets.UTF_8));
			for (Map.Entry<String, byte[]> attachment : ATTACHMENTS.entrySet()) {
				put(zip, attachment.getKey(), attachment.getValue());
			}
		}
		return archive;
	}

	private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content);
		zip.closeEntry();
	}
}
