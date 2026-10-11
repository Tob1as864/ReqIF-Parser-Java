package de.uni_stuttgart.ils.reqif4j.reqif;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.uni_stuttgart.ils.reqif4j.specification.TypeClassifier;

/**
 * A .reqifz archive: one or more ReqIF documents plus the files they
 * reference - images, embedded OLE objects, PDFs or anything else.
 *
 * The archive is extracted into a temporary directory, which {@link #close()}
 * deletes again; use try-with-resources. Every entry that is not a ReqIF
 * document is kept as an attachment, so re-packing the archive with
 * {@code ReqIFzWriter} loses nothing.
 */
public class ReqIFz extends ReqIFFile {

    private static final String[] PICTURE_EXTENSIONS = {
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg", ".tif", ".tiff", ".webp", ".emf", ".wmf" };

    private Path extractionDirectory;
    private final Map<String, File> attachmentFiles = new LinkedHashMap<>();
    private final Map<String, File> pictureFiles = new LinkedHashMap<>();
    private final Map<String, File> reqifFiles = new LinkedHashMap<>();

    /**
     * @return every entry that is not a ReqIF document, keyed by its archive
     *         entry name (forward slashes, as referenced by xhtml object data
     *         attributes), in archive order. Valid until {@link #close()}.
     */
    public Map<String, File> getAttachmentFiles() {
        return this.attachmentFiles;
    }

    /**
     * @return the attachments that are pictures (png, jpg, gif, svg, ... by
     *         file extension), keyed like
     *         {@link #getAttachmentFiles()}. Unlike the input streams these can
     *         be read repeatedly. Valid until {@link #close()}.
     */
    public Map<String, File> getPictureFiles() {
        return this.pictureFiles;
    }

    /**
     * @return the extracted ReqIF files, keyed by their archive entry name.
     *         Valid until {@link #close()}.
     */
    public Map<String, File> getReqIFFiles() {
        return this.reqifFiles;
    }

    public ReqIFz(String filePath) throws IOException {
        this(filePath, TypeClassifier.defaultClassifier());
    }

    /**
     * @param typeClassifier strategy deciding how spec objects are classified
     *                       (requirement/headline/text); null uses the default
     *                       LONG-NAME heuristic
     */
    public ReqIFz(String filePath, TypeClassifier typeClassifier) throws IOException {
        this(new FileInputStream(filePath), filePath, typeClassifier);
    }

    /**
     * Reads an archive from a stream, e.g. an upload or a classpath resource.
     * The stream is closed.
     *
     * @param name the archive's name or path, used in messages and as
     *             {@link #getPath()}
     */
    public ReqIFz(InputStream archive, String name) throws IOException {
        this(archive, name, TypeClassifier.defaultClassifier());
    }

    public ReqIFz(InputStream archive, String name, TypeClassifier typeClassifier) throws IOException {

        this.path = name;
        this.name = extractFileName(name);

        try (InputStream in = archive) {
            this.extractionDirectory = Files.createTempDirectory("reqifz-");
            extract(in, typeClassifier);
        } catch (IOException | RuntimeException e) {
            try {
                close();
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    private void extract(InputStream archive, TypeClassifier typeClassifier) throws IOException {

        Path destination = this.extractionDirectory.toRealPath();

        try (ZipInputStream zis = new ZipInputStream(archive)) {
            for (ZipEntry zipEntry = zis.getNextEntry(); zipEntry != null; zipEntry = zis.getNextEntry()) {

                // zip entries use forward slashes; archives made on Windows
                // sometimes contain backslashes instead
                String entryName = zipEntry.getName().replace('\\', '/');
                Path target = destination.resolve(entryName).normalize();
                // Guard against zip-slip: entries must not escape the
                // extraction directory (e.g. via "../" path segments).
                if (!target.startsWith(destination) || target.equals(destination)) {
                    throw new IOException("Zip entry outside of extraction directory: " + entryName);
                }
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                // also for archives that list no directory entries
                Files.createDirectories(target.getParent());
                // a zip may hold an entry twice (tools append updated
                // entries); the later one wins, as when unzipping
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);

                File file = target.toFile();
                if (entryName.toLowerCase(Locale.ROOT).endsWith(".reqif")) {
                    this.numberOfReqIFDocuments++;
                    this.reqifFiles.put(entryName, file);
                    try (InputStream reqifIS = new FileInputStream(file)) {
                        this.reqifDocuments.put(entryName, new ReqIFDocument(reqifIS, this.path, entryName, typeClassifier));
                    }
                } else {
                    this.attachmentFiles.put(entryName, file);
                    if (isPicture(entryName)) {
                        this.pictureFiles.put(entryName, file);
                    }
                }
            }
        }

        // Opened only now, so a failure while extracting leaves no stream open
        // that would keep the files from being deleted. Associated with each
        // contained ReqIF document, using the same keys as reqifDocuments.
        // The map is registered before the streams are opened, so close()
        // reaches every stream also if opening one of them fails.
        Map<String, InputStream> picturesIS = new HashMap<>();
        for (String reqifDocumentName : this.reqifDocuments.keySet()) {
            this.picturesInReqIFDocument.put(reqifDocumentName, picturesIS);
        }
        if (!this.reqifDocuments.isEmpty()) {
            for (Map.Entry<String, File> picture : this.pictureFiles.entrySet()) {
                picturesIS.put(picture.getKey(), new FileInputStream(picture.getValue()));
            }
        }
    }

    /**
     * Closes the picture streams and deletes the extracted files.
     */
    @Override
    public void close() throws IOException {

        IOException failure = null;
        try {
            super.close();
        } catch (IOException e) {
            failure = e;
        }
        if (this.extractionDirectory != null && Files.exists(this.extractionDirectory)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(this.extractionDirectory)) {
                // children before their directories
                files = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            } catch (IOException | UncheckedIOException e) {
                files = List.of();
                failure = addFailure(failure, e);
            }
            // a file that cannot be deleted must not keep the others
            for (Path file : files) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    failure = addFailure(failure, e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static boolean isPicture(String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);
        for (String extension : PICTURE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static IOException addFailure(IOException failure, Exception next) {
        IOException asIO = next instanceof UncheckedIOException ? ((UncheckedIOException) next).getCause()
                : next instanceof IOException ? (IOException) next : new IOException(next);
        if (failure == null) {
            return asIO;
        }
        failure.addSuppressed(asIO);
        return failure;
    }

    public static String removeFileExtension(String filename, boolean removeAllExtensions) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }

        String extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
        return filename.replaceAll(extPattern, "");
    }
}
