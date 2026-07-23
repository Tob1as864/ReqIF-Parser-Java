package de.uni_stuttgart.ils.reqif4j.xhtml;

import java.io.File;

import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

/**
 * An embedded object, typically an image. The {@code data} attribute is kept
 * as the original URI (with forward slashes) so it stays a valid reference in
 * generated markup and matches the picture entries of a .reqifz archive.
 */
public class XHTMLElementObject extends XHTMLElement {

	private String data;
	private String mimeType;

	/**
	 * @return the object URI as written in the data attribute (forward slashes),
	 *         or null if the object has no data attribute
	 */
	public String getData() {
		return this.data;
	}

	/**
	 * @return the data URI converted to a platform-specific file path, or null
	 */
	public String getDataAsFilePath() {
		return this.data == null ? null : this.data.replace("/", File.separator);
	}

	/**
	 * @return the MIME type of the object (type attribute), or null
	 */
	public String getMimeType() {
		return this.mimeType;
	}


	public XHTMLElementObject (Node xhtmlElement) {
		super(xhtmlElement);

		readAttributes(xhtmlElement);
	}

	public XHTMLElementObject(Node xhtmlElement, XHTMLNode parent) {
		super(xhtmlElement, parent);

		readAttributes(xhtmlElement);
	}

	private void readAttributes(Node xhtmlElement) {
		String rawData = XmlUtils.attribute(xhtmlElement, "data");
		this.data = rawData == null ? null : rawData.trim();
		this.mimeType = XmlUtils.attribute(xhtmlElement, "type");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('<').append(tagName);
		if (data != null) {
			sb.append(" data=\"").append(data).append('"');
		}
		if (mimeType != null) {
			sb.append(" type=\"").append(mimeType).append('"');
		}
		sb.append('>');
		sb.append((!children.isEmpty() ? this.listToString(children) : ""));
		sb.append("</").append(tagName).append('>');
		return sb.toString();
	}

}
