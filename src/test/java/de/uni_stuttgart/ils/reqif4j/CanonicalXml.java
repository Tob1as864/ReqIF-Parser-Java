package de.uni_stuttgart.ils.reqif4j;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.util.SecureXml;

/**
 * Renders a ReqIF document as an indented tree that is independent of
 * everything the ReqIF schema leaves open, so two documents with the same
 * content compare equal:
 *
 * <ul>
 * <li>namespace prefixes and declarations (names are compared by namespace
 * URI and local name), attribute order, and indentation;</li>
 * <li>the order of child elements that all have different names - the
 * ReqIF elements are xsd:all groups - while repeated elements (lists such as
 * VALUES or CHILDREN) keep their order;</li>
 * <li>empty elements without attributes, e.g. an empty VALUES or
 * SPEC-ATTRIBUTES, which carry no content.</li>
 * </ul>
 *
 * XHTML content is compared exactly: element order, text and whitespace.
 */
final class CanonicalXml {

	private static final String XHTML = "http://www.w3.org/1999/xhtml";
	private static final String XMLNS = "http://www.w3.org/2000/xmlns/";

	private CanonicalXml() {
	}

	static String of(String xml) throws Exception {
		Document document = SecureXml.newDocumentBuilder()
				.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		StringBuilder out = new StringBuilder();
		render(document.getDocumentElement(), 0, out);
		return out.toString();
	}

	private static void render(Element element, int depth, StringBuilder out) {

		boolean xhtml = XHTML.equals(element.getNamespaceURI());
		indent(depth, out);
		out.append('<').append(name(element));

		TreeMap<String, String> attributes = new TreeMap<String, String>();
		NamedNodeMap map = element.getAttributes();
		for (int i = 0; i < map.getLength(); i++) {
			Attr attribute = (Attr) map.item(i);
			if (!XMLNS.equals(attribute.getNamespaceURI())) {
				attributes.put(name(attribute), attribute.getValue());
			}
		}
		attributes.forEach((key, value) -> out.append(' ').append(key).append("=\"").append(value).append('"'));
		out.append(">\n");

		List<Node> children = new ArrayList<Node>();
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (xhtml || !isEmpty((Element) child)) {
					children.add(child);
				}
			} else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
				String text = child.getNodeValue();
				if (xhtml || !text.isBlank()) {
					children.add(child);
				}
			}
		}
		if (!xhtml && distinctElementNames(children)) {
			children.sort((a, b) -> name(a).compareTo(name(b)));
		}

		for (Node child : children) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				render((Element) child, depth + 1, out);
			} else {
				indent(depth + 1, out);
				String text = child.getNodeValue();
				out.append("\"").append(xhtml ? text : text.trim()).append("\"\n");
			}
		}
	}

	private static boolean isEmpty(Element element) {
		return !hasAttributes(element) && !element.hasChildNodes()
				|| !hasAttributes(element) && element.getTextContent().isBlank() && !hasElementChild(element);
	}

	/** namespace declarations are not attributes of the content */
	private static boolean hasAttributes(Element element) {
		NamedNodeMap attributes = element.getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			if (!javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attributes.item(i).getNamespaceURI())) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasElementChild(Element element) {
		for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				return true;
			}
		}
		return false;
	}

	private static boolean distinctElementNames(List<Node> children) {
		Set<String> names = new HashSet<String>();
		for (Node child : children) {
			if (child.getNodeType() != Node.ELEMENT_NODE || !names.add(name(child))) {
				return false;
			}
		}
		return true;
	}

	private static String name(Node node) {
		String namespace = node.getNamespaceURI();
		String local = node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
		return namespace == null ? local : "{" + namespace + "}" + local;
	}

	private static void indent(int depth, StringBuilder out) {
		for (int i = 0; i < depth; i++) {
			out.append("  ");
		}
	}
}
