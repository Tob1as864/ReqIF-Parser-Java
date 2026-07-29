package de.uni_stuttgart.ils.reqif4j.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Parses the XHTML markup of an attribute value into a {@code div} element in
 * the XHTML namespace.
 *
 * The markup may be given with or without a surrounding div. It is always
 * parsed inside a synthetic root, and the result is only reused as the div when
 * it really is one - matching on the string {@code "<div"} would mangle an
 * element such as {@code <divider>}.
 */
public final class XhtmlParser {

	public static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";

	private static final String SYNTHETIC_ROOT = "reqif4j-xhtml-root";
	private static final String DIV = "div";

	private XhtmlParser() {
	}

	/**
	 * @param markup XHTML fragment, with or without a surrounding div
	 * @return a div element containing the markup, never null
	 * @throws IllegalArgumentException if the markup is not well-formed
	 */
	public static Element parseDiv(String markup) {

		String fragment = markup == null ? "" : markup.trim();
		String document = "<" + SYNTHETIC_ROOT + " xmlns=\"" + XHTML_NAMESPACE + "\">" + fragment
				+ "</" + SYNTHETIC_ROOT + ">";

		Element root;
		try {
			Document parsed = SecureXml.newDocumentBuilder()
					.parse(new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)));
			root = parsed.getDocumentElement();

		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new IllegalArgumentException("XHTML value is not well-formed: " + markup, e);
		}

		Element onlyChild = onlyElementChild(root);
		if (onlyChild != null && DIV.equals(XmlUtils.localName(onlyChild))) {
			return onlyChild;
		}

		// no surrounding div (or more than one top level element): wrap it
		Element div = root.getOwnerDocument().createElementNS(XHTML_NAMESPACE, DIV);
		while (root.hasChildNodes()) {
			div.appendChild(root.getFirstChild());
		}
		return div;
	}

	/**
	 * @return the single element child of the node, or null if it has none or
	 *         more than one (text next to it is ignored only when blank)
	 */
	private static Element onlyElementChild(Element parent) {

		Element found = null;
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {

			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (found != null) {
					return null;
				}
				found = (Element) child;

			} else if (child.getNodeType() == Node.TEXT_NODE && !child.getTextContent().isBlank()) {
				// text outside the element means it cannot be the surrounding div
				return null;
			}
		}
		return found;
	}
}
