package de.uni_stuttgart.ils.reqif4j.write;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFz;

/**
 * Writes a .reqifz archive: one or more ReqIF documents together with the
 * images they reference.
 *
 * Image entries are stored under the path the XHTML {@code object} elements
 * reference (forward slashes), so a written archive resolves its images the
 * same way a tool-generated one does:
 *
 * <pre>
 * Map&lt;String, byte[]&gt; pictures = Map.of("files/diagram.png", pngBytes);
 * new ReqIFzWriter().write(document, "spec.reqif", pictures, Path.of("spec.reqifz"));
 * </pre>
 *
 * An archive that was read can be re-packed without consuming its picture
 * streams, because {@link ReqIFz} keeps the extracted files:
 *
 * <pre>
 * try (ReqIFz source = new ReqIFz("in.reqifz")) {
 *     new ReqIFzWriter().write(source, Path.of("out.reqifz"));
 * }
 * </pre>
 */
public class ReqIFzWriter {

	private final ReqIFWriter documentWriter;

	public ReqIFzWriter() {
		this(new ReqIFWriter());
	}

	/**
	 * @param documentWriter writer used for the contained ReqIF documents
	 */
	public ReqIFzWriter(ReqIFWriter documentWriter) {
		this.documentWriter = documentWriter;
	}


	/**
	 * Writes a single document without images.
	 */
	public void write(ReqIFDocument document, String entryName, Path archive) throws IOException {
		write(document, entryName, null, archive);
	}

	/**
	 * Writes a single document together with its images.
	 *
	 * @param entryName name of the ReqIF entry inside the archive,
	 *                  e.g. {@code spec.reqif}
	 * @param pictures  image data keyed by the archive path referenced from the
	 *                  XHTML object elements, e.g. {@code files/diagram.png};
	 *                  may be null
	 */
	public void write(ReqIFDocument document, String entryName, Map<String, byte[]> pictures, Path archive)
			throws IOException {

		Map<String, ReqIFDocument> documents = new LinkedHashMap<String, ReqIFDocument>();
		documents.put(normalizeEntryName(entryName), document);
		write(documents, pictures, archive);
	}

	/**
	 * Writes several documents and their images into one archive.
	 */
	public void write(Map<String, ReqIFDocument> documents, Map<String, byte[]> pictures, Path archive)
			throws IOException {

		if (documents == null || documents.isEmpty()) {
			throw new ReqIFWriteException("A .reqifz archive must contain at least one ReqIF document");
		}

		// Collect and check the names first: a zip cannot hold the same entry
		// twice, and the raw ZipException would not say which name clashed.
		Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
		for (Map.Entry<String, ReqIFDocument> document : documents.entrySet()) {
			addEntry(entries, normalizeEntryName(document.getKey()), documentBytes(document.getValue()));
		}
		if (pictures != null) {
			for (Map.Entry<String, byte[]> picture : pictures.entrySet()) {
				addEntry(entries, normalizeEntryName(picture.getKey()), picture.getValue());
			}
		}

		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				writeEntry(zip, entry.getKey(), entry.getValue());
			}
		}
	}

	/**
	 * Re-packs an archive that was read: the documents are serialized from the
	 * object model (so modifications are included) while the images are copied
	 * from the extracted files.
	 */
	public void write(ReqIFz source, Path archive) throws IOException {

		Map<String, byte[]> pictures = new LinkedHashMap<String, byte[]>();
		for (Map.Entry<String, File> picture : source.getPictureFiles().entrySet()) {
			pictures.put(picture.getKey(), Files.readAllBytes(picture.getValue().toPath()));
		}
		write(source.getReqIFDocuments(), pictures, archive);
	}


	private static void addEntry(Map<String, byte[]> entries, String name, byte[] content) {

		if (entries.containsKey(name)) {
			throw new ReqIFWriteException("Archive entry '" + name + "' is added twice; "
					+ "document and picture names must be unique within the archive");
		}
		entries.put(name, content);
	}

	private byte[] documentBytes(ReqIFDocument document) throws IOException {

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		this.documentWriter.write(document, (OutputStream) bytes);
		return bytes.toByteArray();
	}

	private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {

		zip.putNextEntry(new ZipEntry(name));
		if (content != null) {
			zip.write(content);
		}
		zip.closeEntry();
	}

	/**
	 * Zip entries always use forward slashes, independent of the platform the
	 * archive is written on.
	 */
	private static String normalizeEntryName(String name) {

		if (name == null || name.isBlank()) {
			throw new ReqIFWriteException("Archive entry name must not be empty");
		}
		String normalized = name.replace('\\', '/');
		if (normalized.startsWith("/") || normalized.contains("../")) {
			throw new ReqIFWriteException("Archive entry name must be a relative path without '..': " + name);
		}
		return normalized;
	}
}
