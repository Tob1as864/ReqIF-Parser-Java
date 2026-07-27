package de.uni_stuttgart.ils.reqif4j.xhtml;

import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.attributes.XHTML;

public class XHTMLElementText extends XHTMLLeaf {

	private String textContent;
	private String renderedText;

	/**
	 * @return the text with surrounding whitespace removed
	 */
	public String getTextContent() {
		return this.textContent;
	}



	public XHTMLElementText (Node xhtmlElement) {
		super(xhtmlElement);

		this.tagName = XHTML.TEXT;
		readText(xhtmlElement);
	}

	public XHTMLElementText(Node xhtmlElement, XHTMLNode parent) {
		super(xhtmlElement, parent);

		this.tagName = XHTML.TEXT;
		readText(xhtmlElement);
	}

	private void readText(Node xhtmlElement) {

		String raw = xhtmlElement.getTextContent();
		this.textContent = raw.trim();
		// Keep the spacing around the text for rendering; trimming it would run
		// words together across inline elements ("Siehe <a>Link</a>").
		this.renderedText = raw.replaceAll("\\s+", " ");
	}

	@Override
	public String toString() {
		return escapeText(this.renderedText);
	}

}
