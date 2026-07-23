package de.uni_stuttgart.ils.reqif4j.reqif;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReqIFFile implements Closeable {


	protected String path;
	protected String name;
	protected int numberOfReqIFDocuments;
	protected Map<String, InputStream> picturesIS;
	protected Map<String, Map<String, InputStream>> picturesInReqIFDocument = new LinkedHashMap<String, Map<String, InputStream>>();
	protected Map<String, ReqIFDocument> reqifDocuments = new LinkedHashMap<String, ReqIFDocument>();




	public String getPath() {
		return this.path;
	}

	public String getName() {
		return this.name;
	}

	public int getReqIFDocumentsCount() {
		return this.numberOfReqIFDocuments;
	}

	public Map<String, ReqIFDocument> getReqIFDocuments() {
		return this.reqifDocuments;
	}

	public List<ReqIFDocument> getReqIFDocumentsList() {
		List<ReqIFDocument> reqIFDocuments= new ArrayList<ReqIFDocument>();
		reqIFDocuments.addAll(this.reqifDocuments.values());
		return reqIFDocuments;
	}

	/**
	 * Returns the pictures belonging to the given ReqIF document. The document
	 * name may be passed with or without its file extension.
	 */
	public Map<String, InputStream> getPicturesInputStreams(String reqifDocumentName) {

		Map<String, InputStream> pictures = this.picturesInReqIFDocument.get(reqifDocumentName);
		if(pictures != null) {
			return pictures;
		}
		// Fallback: match ignoring the file extension (legacy behavior of this
		// method stripped the extension while the map was keyed with it).
		for(Map.Entry<String, Map<String, InputStream>> entry: this.picturesInReqIFDocument.entrySet()) {
			if(removeExtension(entry.getKey()).equals(removeExtension(reqifDocumentName))) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Returns the input stream of a picture referenced by a ReqIF document.
	 * The picture name is matched against the archive entry path (with forward
	 * slashes, as referenced by xhtml object data attributes) and, as a
	 * fallback, against the plain file name.
	 */
	public InputStream getPictureInputStream(String reqifDocumentName, String pictureFileName) {

		Map<String, InputStream> pictures = getPicturesInputStreams(reqifDocumentName);
		if(pictures == null || pictureFileName == null) {
			return null;
		}
		InputStream picture = pictures.get(pictureFileName);
		if(picture != null) {
			return picture;
		}
		String normalized = pictureFileName.replace('\\', '/');
		picture = pictures.get(normalized);
		if(picture != null) {
			return picture;
		}
		String baseName = normalized.substring(normalized.lastIndexOf('/') + 1);
		for(Map.Entry<String, InputStream> entry: pictures.entrySet()) {
			String entryBaseName = entry.getKey().substring(entry.getKey().replace('\\', '/').lastIndexOf('/') + 1);
			if(entryBaseName.equals(baseName)) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Closes all picture input streams held by this file.
	 */
	@Override
	public void close() throws IOException {
		IOException firstFailure = null;
		for(Map<String, InputStream> pictures: this.picturesInReqIFDocument.values()) {
			for(InputStream picture: pictures.values()) {
				try {
					picture.close();
				} catch (IOException e) {
					if(firstFailure == null) {
						firstFailure = e;
					}
				}
			}
		}
		if(firstFailure != null) {
			throw firstFailure;
		}
	}

	/**
	 * Removes the last file extension of a name or path without mangling dots
	 * in directory names.
	 */
	protected static String removeExtension(String fileName) {
		if(fileName == null) {
			return null;
		}
		int lastSeparator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
		int lastDot = fileName.lastIndexOf('.');
		return lastDot > lastSeparator ? fileName.substring(0, lastDot) : fileName;
	}

	/**
	 * @return the file name portion of a path, handling both separator styles.
	 */
	protected static String extractFileName(String path) {
		int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return path.substring(lastSeparator + 1);
	}

}
