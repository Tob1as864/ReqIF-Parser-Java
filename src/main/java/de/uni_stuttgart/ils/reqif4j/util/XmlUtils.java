package de.uni_stuttgart.ils.reqif4j.util;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
	 * @return all direct child nodes of type ELEMENT_NODE.
	 */
	public static List<Element> childElements(Node parent) {
		List<Element> elements = new ArrayList<Element>();
		if (parent == null) {
			return elements;
		}
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
				elements.add((Element) children.item(i));
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
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
				return (Element) children.item(i);
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
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
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
		collectDescendants(root, localName, result);
		return result;
	}

	/**
	 * @return the first descendant element (document order) with the given local
	 *         name, or null.
	 */
	public static Element firstDescendantByLocalName(Node root, String localName) {
		List<Element> descendants = descendantsByLocalName(root, localName);
		return descendants.isEmpty() ? null : descendants.get(0);
	}

	private static void collectDescendants(Node node, String localName, List<Element> result) {
		if (node == null) {
			return;
		}
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (localName(child).equals(localName)) {
					result.add((Element) child);
				}
				collectDescendants(child, localName, result);
			}
		}
	}

	/**
	 * Reads the optional ALTERNATIVE-ID of an identifiable element.
	 *
	 * @return the IDENTIFIER of the ALTERNATIVE-ID child, or null if the element
	 *         declares none
	 */
	public static String alternativeID(Node identifiable) {

		Element alternativeID = firstChildElementByLocalName(identifiable, "ALTERNATIVE-ID");
		return alternativeID == null ? null : attribute(alternativeID, "IDENTIFIER");
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
}
