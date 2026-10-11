package de.uni_stuttgart.ils.reqif4j.util;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Helper methods for namespace- and whitespace-independent DOM navigation.
 *
 * ReqIF files may serialize XHTML content with a namespace prefix
 * (e.g. {@code xhtml:div} or {@code reqif-xhtml:div}) and may or may not be
 * pretty-printed. These helpers therefore match elements by local name and
 * never rely on fixed child indices.
 */
public final class XmlUtils {

	private XmlUtils() {
	}

	/**
	 * @return the local name of the node (tag name without namespace prefix),
	 *         never null. Falls back to the node name for non-namespace-aware
	 *         documents and special nodes such as {@code #text}.
	 */
	public static String localName(Node node) {
		if (node == null) {
			return "";
		}
		String localName = node.getLocalName();
		if (localName != null) {
			return localName;
		}
		String nodeName = node.getNodeName();
		int colon = nodeName.indexOf(':');
		return colon >= 0 ? nodeName.substring(colon + 1) : nodeName;
	}

	/**
	 * @return all direct child nodes of type ELEMENT_NODE. Like all methods
	 *         here, it walks the children with getFirstChild/getNextSibling:
	 *         a NodeList's item(i) can cost as much as the list is long when
	 *         lists are walked nested, which made reading large documents
	 *         quadratic.
	 */
	public static List<Element> childElements(Node parent) {
		List<Element> elements = new ArrayList<Element>();
		if (parent == null) {
			return elements;
		}
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				elements.add((Element) child);
			}
		}
		return elements;
	}

	/**
	 * @return the first direct child of type ELEMENT_NODE, or null.
	 */
	public static Element firstChildElement(Node parent) {
		if (parent == null) {
			return null;
		}
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				return (Element) child;
			}
		}
		return null;
	}

	/**
	 * @return the first direct child element with the given local name, or null.
	 */
	public static Element firstChildElementByLocalName(Node parent, String localName) {
		if (parent == null) {
			return null;
		}
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE && localName(child).equals(localName)) {
				return (Element) child;
			}
		}
		return null;
	}

	/**
	 * @return all descendant elements (document order) with the given local name.
	 */
	public static List<Element> descendantsByLocalName(Node root, String localName) {
		List<Element> result = new ArrayList<Element>();
		collectDescendants(root, localName, result, false);
		return result;
	}

	/**
	 * @return the first descendant element (document order) with the given local
	 *         name, or null.
	 */
	public static Element firstDescendantByLocalName(Node root, String localName) {
		List<Element> first = new ArrayList<Element>(1);
		collectDescendants(root, localName, first, true);
		return first.isEmpty() ? null : first.get(0);
	}

	/**
	 * @param firstOnly stop at the first match
	 * @return whether to stop
	 */
	private static boolean collectDescendants(Node node, String localName, List<Element> result, boolean firstOnly) {
		if (node == null) {
			return false;
		}
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (localName(child).equals(localName)) {
					result.add((Element) child);
					if (firstOnly) {
						return true;
					}
				}
				if (collectDescendants(child, localName, result, firstOnly)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Reads the optional ALTERNATIVE-ID of an identifiable element.
	 *
	 * The schema wraps the alternative id twice:
	 * {@code <ALTERNATIVE-ID><ALTERNATIVE-ID IDENTIFIER="..."/></ALTERNATIVE-ID>}.
	 * The flat form {@code <ALTERNATIVE-ID IDENTIFIER="..."/>} is not
	 * schema-valid but is accepted as well, because earlier versions of this
	 * library wrote it.
	 *
	 * @return the IDENTIFIER of the alternative id, or null if the element
	 *         declares none
	 */
	public static String alternativeID(Node identifiable) {

		// literals instead of ReqIFConst: this package must not depend on the
		// model packages, which depend on it
		Element wrapper = firstChildElementByLocalName(identifiable, "ALTERNATIVE-ID");
		if (wrapper == null) {
			return null;
		}
		Element nested = firstChildElementByLocalName(wrapper, "ALTERNATIVE-ID");
		return attribute(nested != null ? nested : wrapper, "IDENTIFIER");
	}

	/**
	 * @return the text content of the named attribute, or null if the node has no
	 *         such attribute.
	 */
	public static String attribute(Node node, String attributeName) {
		if (node == null || node.getAttributes() == null) {
			return null;
		}
		Node attribute = node.getAttributes().getNamedItem(attributeName);
		return attribute == null ? null : attribute.getTextContent();
	}

	/**
	 * @return the text content of the named attribute, or "" if the node has
	 *         no such attribute.
	 */
	public static String attributeOrEmpty(Node node, String attributeName) {
		String value = attribute(node, attributeName);
		return value == null ? "" : value;
	}

	/**
	 * @return the xsd:boolean attribute ("true"/"1" is true, "false"/"0"
	 *         false), or null if the node has no such attribute or its value
	 *         is not an xsd:boolean - an unknown value must not turn into
	 *         false and be written back as such
	 */
	public static Boolean booleanAttribute(Node node, String attributeName) {
		String value = attribute(node, attributeName);
		if (value == null) {
			return null;
		}
		switch (value.trim()) {
			case "true":
			case "1":
				return Boolean.TRUE;
			case "false":
			case "0":
				return Boolean.FALSE;
			default:
				return null;
		}
	}
}
