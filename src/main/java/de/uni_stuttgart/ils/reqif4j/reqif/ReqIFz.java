package de.uni_stuttgart.ils.reqif4j.reqif;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ReqIFz extends ReqIFFile {
    private String extendFilename = "_unzipped";

    public ReqIFz(String filePath) throws IOException {
        File destDir = new File(filePath);
        String pathWithoutFileExtension = removeFileExtension(destDir.getAbsolutePath(), true);
        destDir = new File(pathWithoutFileExtension + extendFilename);
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Failed to create extraction directory " + destDir);
        }

        byte[] buffer = new byte[1024];
        Map<String, InputStream> picturesIS = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(filePath))) {
            ZipEntry zipEntry = zis.getNextEntry();

            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());
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
                        String reqifBaseName = zipEntry.getName().split("\\.")[0];
                        this.reqifDocuments.put(zipEntry.getName(), new ReqIFDocument(new FileInputStream(newFile), filePath, zipEntry.getName()));
                    } else if (zipEntry.getName().endsWith("png") || zipEntry.getName().endsWith("jpeg") || zipEntry.getName().endsWith("jpg")) {
                        picturesIS.put(zipEntry.getName(), new FileInputStream(newFile));
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }

        // Assuming you want to associate pictures with ReqIF documents
        for (String reqifBaseName : this.reqifDocuments.keySet()) {
            this.picturesInReqIFDocument.put(reqifBaseName, picturesIS);
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