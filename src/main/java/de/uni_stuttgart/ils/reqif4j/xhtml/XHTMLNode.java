package de.uni_stuttgart.ils.reqif4j.xhtml;

import javax.xml.XMLConstants;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class XHTMLNode {

	/**
	 * Elements that must be rendered self-closing; a void element written as
	 * {@code <br></br>} is read by HTML5 parsers as two line breaks.
	 */
	private static final Set<String> VOID_ELEMENTS = new HashSet<String>(Arrays.asList(
			"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param",
			"source", "track", "wbr"));

	protected String tagName;
	protected XHTMLNode parent = null;
	protected Node node;


	/**
	 * @return the tag name of this xhtml node without any namespace prefix
	 */
	public String getTagName() {
		return this.tagName;
	}

	/**
	 * @return the parent XHTMLNode of this XHTMLNode
	 */
	public XHTMLNode getParent() {
		return this.parent;
	}



	public XHTMLNode(Node xhtmlElement) {

		this.node = xhtmlElement;
		this.tagName = XmlUtils.localName(xhtmlElement);
	}

	public XHTMLNode(Node xhtmlElement, XHTMLNode parent) {
		this.node = xhtmlElement;
		this.tagName = XmlUtils.localName(xhtmlElement);
		this.parent = parent;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('<').append(tagName).append(attributesToString());
		if(isVoidElement()) {
			sb.append("/>");
		}else{
			sb.append("></").append(tagName).append('>');
		}
		return sb.toString();
	}

	public Node getNode() {
		return node;
	}

	protected String listToString(List<XHTMLNode> list){
		StringBuilder sb = new StringBuilder();
		for(XHTMLNode node : list){
			sb.append(node.toString());
		}
		return sb.toString();
	}

	/**
	 * @return true if this element must be rendered self-closing
	 */
	protected boolean isVoidElement() {
		return VOID_ELEMENTS.contains(this.tagName.toLowerCase());
	}

	/**
	 * Renders all XML attributes of this node. Namespace declarations are
	 * skipped because tag names are emitted without their prefix.
	 */
	protected String attributesToString() {

		if(this.node == null || this.node.getAttributes() == null) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		NamedNodeMap attributes = this.node.getAttributes();
		Set<String> plainNames = new HashSet<String>();
		for(int attribute = 0; attribute < attributes.getLength(); attribute++) {
			String namespace = attributes.item(attribute).getNamespaceURI();
			if(namespace == null || namespace.isEmpty()) {
				plainNames.add(XmlUtils.localName(attributes.item(attribute)));
			}
		}
		Set<String> declaredPrefixes = new HashSet<String>();
		for(int attribute = 0; attribute < attributes.getLength(); attribute++) {

			Node attributeNode = attributes.item(attribute);
			String name = attributeNode.getNodeName();
			if(name.equals(XMLConstants.XMLNS_ATTRIBUTE) || name.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
				continue;
			}
			String namespace = attributeNode.getNamespaceURI();
			String localName = XmlUtils.localName(attributeNode);
			String attributeName;
			if(namespace == null || namespace.isEmpty()) {
				attributeName = localName;
			} else if(XMLConstants.XML_NS_URI.equals(namespace)) {
				// xml:lang and xml:space keep their prefix: "lang" next to "xml:lang"
				// would be a duplicate attribute, and the XHTML schema has no "space"
				attributeName = XMLConstants.XML_NS_PREFIX + ":" + localName;
			} else if(de.uni_stuttgart.ils.reqif4j.util.XhtmlParser.XHTML_NAMESPACE.equals(namespace)) {
				// xhtml:class means class; next to a plain class it is a duplicate
				if(plainNames.contains(localName)) {
					continue;
				}
				attributeName = localName;
			} else {
				// an attribute of another vocabulary keeps its namespace, so the
				// markup stays well-formed and nothing is lost
				String prefix = attributeNode.getPrefix() == null ? "ns" : attributeNode.getPrefix();
				if(declaredPrefixes.add(prefix)) {
					sb.append(' ').append(XMLConstants.XMLNS_ATTRIBUTE).append(':').append(prefix)
					  .append("=\"").append(escapeAttribute(namespace)).append('"');
				}
				attributeName = prefix + ":" + localName;
			}
			sb.append(' ').append(attributeName)
			  .append("=\"").append(escapeAttribute(attributeNode.getNodeValue())).append('"');
		}
		return sb.toString();
	}

	/**
	 * Escapes the characters that must not appear literally in element content.
	 */
	protected static String escapeText(String text) {
		if(text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Escapes the characters that must not appear literally in a quoted
	 * attribute value.
	 */
	protected static String escapeAttribute(String value) {
		return escapeText(value).replace("\"", "&quot;");
	}
}
