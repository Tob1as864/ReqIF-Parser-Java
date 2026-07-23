package de.uni_stuttgart.ils.reqif4j.reqif;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

public class ReqIF extends ReqIFFile {


	public ReqIFDocument getReqIFDocument() {
		return this.reqifDocuments.get(this.name);
	}

	public ReqIFHeader getReqIFHeader() {
		return this.reqifDocuments.get(this.name).getHeader();
	}

	public ReqIFCoreContent getReqIFCoreContent() {
		return this.reqifDocuments.get(this.name).getCoreContent();
	}




	public ReqIF(String filePath) throws FileNotFoundException {

		this.path = filePath;
		this.name = extractFileName(filePath);

		this.reqifDocuments.put(this.name, new ReqIFDocument(filePath));
		this.numberOfReqIFDocuments = 1;

		this.picturesIS = new HashMap<String, InputStream>();
		// Pictures conventionally live in a sibling folder named after the file
		// (extension stripped). Only the extension is removed; dots elsewhere in
		// the path no longer truncate it.
		File picturesFolder = new File(removeExtension(this.path));
		if(picturesFolder.exists() && picturesFolder.isDirectory()) {

			File[] pictures = picturesFolder.listFiles();
			if(pictures != null) {
				for(int picture = 0; picture < pictures.length; picture++) {

					if( pictures[picture].getName().endsWith("png")	||
						pictures[picture].getName().endsWith("jpg")	||
						pictures[picture].getName().endsWith("jpeg")		) {

						InputStream pictureIS = new BufferedInputStream(new FileInputStream(pictures[picture]));
						this.picturesIS.put(pictures[picture].getName(), pictureIS);
					}
				}
			}
		}
		this.picturesInReqIFDocument.put(this.name, this.picturesIS);
	}

}
