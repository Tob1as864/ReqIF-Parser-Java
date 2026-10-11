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
 * files they reference - images, embedded OLE objects, PDFs or anything else.
 *
 * Attachments are stored under the path the XHTML {@code object} elements
 * reference (forward slashes), so a written archive resolves them the same
 * way a tool-generated one does:
 *
 * <pre>
 * Map&lt;String, byte[]&gt; attachments = Map.of("files/diagram.png", pngBytes);
 * new ReqIFzWriter().write(document, "spec.reqif", attachments, Path.of("spec.reqifz"));
 * </pre>
 *
 * An archive that was read can be re-packed; every attachment is copied from
 * the files {@link ReqIFz} extracted, without loading them into memory:
 *
 * <pre>
 * try (ReqIFz source = new ReqIFz("in.reqifz")) {
 *     new ReqIFzWriter().write(source, Path.of("out.reqifz"));
 * }
 * </pre>
 */
public class ReqIFzWriter {

	/** The content of one archive entry. */
	private interface Content {
		void writeTo(OutputStream out) throws IOException;
	}

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
	 * Writes a single document without attachments.
	 */
	public void write(ReqIFDocument document, String entryName, Path archive) throws IOException {
		write(document, entryName, null, archive);
	}

	/**
	 * Writes a single document together with its attachments.
	 *
	 * @param entryName   name of the ReqIF entry inside the archive,
	 *                    e.g. {@code spec.reqif}
	 * @param attachments file contents keyed by the archive path referenced
	 *                    from the XHTML object elements, e.g.
	 *                    {@code files/diagram.png}; may be null
	 */
	public void write(ReqIFDocument document, String entryName, Map<String, byte[]> attachments, Path archive)
			throws IOException {

		Map<String, ReqIFDocument> documents = new LinkedHashMap<String, ReqIFDocument>();
		documents.put(normalizeEntryName(entryName), document);
		write(documents, attachments, archive);
	}

	/**
	 * Writes several documents and their attachments into one archive.
	 */
	public void write(Map<String, ReqIFDocument> documents, Map<String, byte[]> attachments, Path archive)
			throws IOException {

		Map<String, Content> contents = new LinkedHashMap<String, Content>();
		if (attachments != null) {
			for (Map.Entry<String, byte[]> attachment : attachments.entrySet()) {
				byte[] bytes = attachment.getValue();
				contents.put(attachment.getKey(), out -> {
					if (bytes != null) {
						out.write(bytes);
					}
				});
			}
		}
		writeArchive(documents, contents, archive);
	}

	/**
	 * Re-packs an archive that was read: the documents are serialized from the
	 * object model (so modifications are included) while every other entry -
	 * images, OLE objects, PDFs, ... - is copied unchanged from the extracted
	 * files.
	 */
	public void write(ReqIFz source, Path archive) throws IOException {

		Map<String, Content> contents = new LinkedHashMap<String, Content>();
		for (Map.Entry<String, File> attachment : source.getAttachmentFiles().entrySet()) {
			Path file = attachment.getValue().toPath();
			contents.put(attachment.getKey(), out -> Files.copy(file, out));
		}
		writeArchive(source.getReqIFDocuments(), contents, archive);
	}


	private void writeArchive(Map<String, ReqIFDocument> documents, Map<String, Content> attachments, Path archive)
			throws IOException {

		if (documents == null || documents.isEmpty()) {
			throw new ReqIFWriteException("A .reqifz archive must contain at least one ReqIF document");
		}

		// Collect and check the names first: a zip cannot hold the same entry
		// twice, and the raw ZipException would not say which name clashed.
		// Documents are serialized up front, so a failure leaves no half
		// written archive behind.
		Map<String, Content> entries = new LinkedHashMap<String, Content>();
		for (Map.Entry<String, ReqIFDocument> document : documents.entrySet()) {
			byte[] bytes = documentBytes(document.getValue());
			addEntry(entries, normalizeEntryName(document.getKey()), out -> out.write(bytes));
		}
		for (Map.Entry<String, Content> attachment : attachments.entrySet()) {
			addEntry(entries, normalizeEntryName(attachment.getKey()), attachment.getValue());
		}

		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
			for (Map.Entry<String, Content> entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				entry.getValue().writeTo(zip);
				zip.closeEntry();
			}
		}
	}

	private static void addEntry(Map<String, Content> entries, String name, Content content) {

		if (entries.containsKey(name)) {
			throw new ReqIFWriteException("Archive entry '" + name + "' is added twice; "
					+ "document and attachment names must be unique within the archive");
		}
		entries.put(name, content);
	}

	private byte[] documentBytes(ReqIFDocument document) throws IOException {

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		this.documentWriter.write(document, (OutputStream) bytes);
		return bytes.toByteArray();
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
