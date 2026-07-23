package de.uni_stuttgart.ils.reqif4j.reqif;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ReqIFz extends ReqIFFile {

    private static final String EXTRACTION_SUFFIX = "_unzipped";

    public ReqIFz(String filePath) throws IOException {

        this.path = filePath;
        this.name = extractFileName(filePath);

        File destDir = new File(removeExtension(new File(filePath).getAbsolutePath()) + EXTRACTION_SUFFIX);
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Failed to create extraction directory " + destDir);
        }
        String canonicalDestDir = destDir.getCanonicalPath();

        byte[] buffer = new byte[8192];
        Map<String, InputStream> picturesIS = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(filePath))) {
            ZipEntry zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());

                // Guard against zip-slip: entries must not escape the
                // extraction directory (e.g. via "../" path segments).
                if (!newFile.getCanonicalPath().startsWith(canonicalDestDir + File.separator)) {
                    throw new IOException("Zip entry outside of extraction directory: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // Fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // Write file content
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }

                    // Process reqif files and associated images
                    if (zipEntry.getName().endsWith("reqif")) {
                        this.numberOfReqIFDocuments++;
                        try (InputStream reqifIS = new FileInputStream(newFile)) {
                            this.reqifDocuments.put(zipEntry.getName(), new ReqIFDocument(reqifIS, filePath, zipEntry.getName()));
                        }
                    } else if (zipEntry.getName().endsWith("png") || zipEntry.getName().endsWith("jpeg") || zipEntry.getName().endsWith("jpg")) {
                        // Keyed by the archive entry path (forward slashes), which is
                        // what xhtml object data attributes reference.
                        picturesIS.put(zipEntry.getName(), new FileInputStream(newFile));
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }

        // Associate the archive's pictures with each contained ReqIF document,
        // using the same keys as reqifDocuments (full entry name).
        for (String reqifDocumentName : this.reqifDocuments.keySet()) {
            this.picturesInReqIFDocument.put(reqifDocumentName, picturesIS);
        }
    }

    public static String removeFileExtension(String filename, boolean removeAllExtensions) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }

        String extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
        return filename.replaceAll(extPattern, "");
    }
}
