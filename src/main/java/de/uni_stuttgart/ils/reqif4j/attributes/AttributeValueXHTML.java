package de.uni_stuttgart.ils.reqif4j.attributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.util.XhtmlParser;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElement;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementDiv;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementObject;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementText;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLNode;

/**
 * XHTML attribute value. The content is available in two representations:
 * the node tree ({@link #getDivValue()}) and a flat token list
 * ({@link #getElementList()}).
 *
 * The token list is derived from the node tree, so both representations always
 * agree. Its grammar is:
 *
 * <pre>
 * element type   content tokens
 * ------------   ----------------------------------------------------------
 * "P" / "H"      "TXT" text | "VAR" name [guid] | "BR" | "OBJ" path
 * "TBL"          "TR" ( ("TH"|"TC") text ("OBJ" path)* )*
 * "L"            "LE" ( inline | "L" ... "/L" | "TBL" ... )*
 * "OBJ"          path
 * </pre>
 *
 * Header cells are reported as {@code "TH"}, data cells as {@code "TC"}; the
 * cell text contains the complete cell content, and images inside a cell are
 * appended as {@code "OBJ"} pairs. Nested lists are wrapped in balanced
 * {@code "L"} / {@code "/L"} markers. Ordered lists ({@code ol}) are treated
 * like unordered ones.
 */
public class AttributeValueXHTML extends AttributeValue {

	// element type tokens
	private static final String T_PARAGRAPH = "P";
	private static final String T_TABLE = "TBL";
	private static final String T_LIST = "L";
	private static final String T_HEADING = "H";
	private static final String T_OBJECT = "OBJ";

	// content tokens
	private static final String T_TEXT = "TXT";
	private static final String T_VAR = "VAR";
	private static final String T_BREAK = "BR";
	private static final String T_ROW = "TR";
	private static final String T_HEADER_CELL = "TH";
	private static final String T_CELL = "TC";
	private static final String T_LIST_ENTRY = "LE";
	private static final String T_LIST_END = "/L";

	private static final String VARIABLE_NAME_MISSING = "VARIABLE_NAME_MISSING";

	XHTMLElementDiv divValue;

	@Override
	public Object getValue() {
		if(divValue == null){
			return "";
		}
		return divValue.toString();
	}

	public AttributeValueXHTML(Node xhtmlContent, AttributeDefinition type) {
		super(type);

		// The div may carry a namespace prefix (xhtml:div, reqif-xhtml:div, ...),
		// so it has to be located by local name.
		Node div = XmlUtils.firstDescendantByLocalName(xhtmlContent, XHTML.DIV);
		this.divValue = div == null ? null : new XHTMLElementDiv(div);
		this.value = deconstructXHTML(this.divValue);
	}

	/**
	 * Creates an XHTML value from markup, for documents that are generated
	 * instead of parsed. The markup is parsed so the node tree and the token
	 * list are available just like for a parsed value; it may be given with or
	 * without a surrounding div.
	 */
	public AttributeValueXHTML(String value, AttributeDefinition type) {
		super(value, type);

		Node div = value == null || value.isBlank() ? null : XhtmlParser.parseDiv(value);
		this.divValue = div == null ? null : new XHTMLElementDiv(div);
		this.value = deconstructXHTML(this.divValue);
	}

	public XHTMLElementDiv getDivValue() {
		return this.divValue;
	}

	/**
	 * @return the deconstructed element list (P/TBL/L/H/OBJ tokens with their
	 *         content), never null
	 */
	public AttributeValueXHTMLElementList getElementList() {
		if(this.value instanceof AttributeValueXHTMLElementList) {
			return (AttributeValueXHTMLElementList) this.value;
		}
		return new AttributeValueXHTMLElementList();
	}




	/**
	 * Builds the token list from the already parsed node tree, so it shares the
	 * tree's namespace handling and never re-interprets the raw DOM.
	 */
	private AttributeValueXHTMLElementList deconstructXHTML(XHTMLElementDiv div) {

		AttributeValueXHTMLElementList xhtmlElementList = new AttributeValueXHTMLElementList();
		if(div == null) {
			return xhtmlElementList;
		}

		for(XHTMLNode child: childrenOf(div)) {
			String elementType = elementToken(child.getTagName());
			if(elementType != null) {
				xhtmlElementList.add(elementType, deconstructElement(elementType, child));
			}
		}
		return xhtmlElementList;
	}

	private static String elementToken(String tagName) {

		switch(tagName.replaceAll("[0-9]", "")) {
			case XHTML.P:		return T_PARAGRAPH;
			case XHTML.TBL:		return T_TABLE;
			case XHTML.UL:
			case XHTML.OL:		return T_LIST;
			case XHTML.H:		return T_HEADING;
			case XHTML.OBJECT:	return T_OBJECT;
			default:			return null;
		}
	}

	private static List<String> deconstructElement(String elementType, XHTMLNode element) {

		List<String> content = new ArrayList<String>();

		if(elementType.equals(T_PARAGRAPH) || elementType.equals(T_HEADING)) {
			for(XHTMLNode child: childrenOf(element)) {
				appendInline(child, content);
			}

		}else if(elementType.equals(T_TABLE)) {
			content.addAll(table(element));

		}else if(elementType.equals(T_LIST)) {
			content.addAll(list(element, false));

		}else if(elementType.equals(T_OBJECT)) {
			String data = objectData(element);
			if(data != null) {
				content.add(data);
			}
		}

		return content;
	}

	/**
	 * Emits the inline tokens of a single node (text, span, var, br, object).
	 */
	private static void appendInline(XHTMLNode node, List<String> content) {

		String tagName = node.getTagName();

		if(tagName.equals(XHTML.TEXT)) {
			String text = node instanceof XHTMLElementText
					? ((XHTMLElementText) node).getTextContent()
					: textOf(node);
			if(!text.isEmpty()) {
				content.add(T_TEXT);
				content.add(text);
			}

		}else if(tagName.equals(XHTML.SPAN)) {
			String text = textOf(node);
			if(!text.isEmpty()) {
				content.add(T_TEXT);
				content.add(text);
			}

		}else if(tagName.equals(XHTML.VAR)) {
			content.add(T_VAR);
			String text = textOf(node);
			if(text.isEmpty()) {
				content.add(VARIABLE_NAME_MISSING);
			}else{
				content.add(text);
				String guid = XmlUtils.attribute(node.getNode(), "GUID");
				if(guid != null) {
					content.add(guid);
				}
			}

		}else if(tagName.equals(XHTML.BR)) {
			content.add(T_BREAK);

		}else if(tagName.equals(XHTML.OBJECT)) {
			String data = objectData(node);
			if(data != null) {
				content.add(T_OBJECT);
				content.add(data);
			}
		}
	}

	/**
	 * Deconstructs a table. Rows are found both directly below the table and
	 * inside thead/tbody/tfoot sections; caption and colgroup are skipped.
	 */
	private static List<String> table(XHTMLNode table) {

		List<String> content = new ArrayList<String>();

		for(XHTMLNode section: childrenOf(table)) {
			String tagName = section.getTagName();

			if(tagName.equals(XHTML.TR)) {
				content.addAll(row(section));

			}else if(tagName.equals(XHTML.THEAD) || tagName.equals(XHTML.TBODY) || tagName.equals("tfoot")) {
				for(XHTMLNode row: childrenOf(section)) {
					if(row.getTagName().equals(XHTML.TR)) {
						content.addAll(row(row));
					}
				}
			}
		}

		return content;
	}

	private static List<String> row(XHTMLNode row) {

		List<String> content = new ArrayList<String>();
		content.add(T_ROW);

		for(XHTMLNode cell: childrenOf(row)) {
			String tagName = cell.getTagName();

			if(tagName.equals(XHTML.TH) || tagName.equals(XHTML.TD)) {
				content.add(tagName.equals(XHTML.TH) ? T_HEADER_CELL : T_CELL);
				content.add(cellText(cell));

				// images inside the cell would otherwise be lost
				for(XHTMLNode image: objectsIn(cell)) {
					content.add(T_OBJECT);
					content.add(objectData(image));
				}
			}
		}

		return content;
	}

	/**
	 * @return the complete text of a cell; the texts of several child elements
	 *         are joined with a blank (the former implementation read only the
	 *         second child node and dropped the rest)
	 */
	private static String cellText(XHTMLNode cell) {

		List<XHTMLNode> children = childrenOf(cell);
		if(children.isEmpty()) {
			return textOf(cell);
		}

		StringBuilder text = new StringBuilder();
		for(XHTMLNode child: children) {
			String childText = textOf(child);
			if(!childText.isEmpty()) {
				if(text.length() > 0) {
					text.append(' ');
				}
				text.append(childText);
			}
		}
		return text.toString();
	}

	/**
	 * Deconstructs a list. Nested lists are wrapped in balanced "L" / "/L"
	 * markers; the outermost list needs none because its element type already
	 * says "L".
	 */
	private static List<String> list(XHTMLNode listNode, boolean nested) {

		List<String> content = new ArrayList<String>();
		if(nested) {
			content.add(T_LIST);
		}

		for(XHTMLNode listEntry: childrenOf(listNode)) {

			if(!listEntry.getTagName().equals(XHTML.LI)) {
				continue;
			}
			content.add(T_LIST_ENTRY);

			for(XHTMLNode child: childrenOf(listEntry)) {
				String tagName = child.getTagName();

				if(tagName.equals(XHTML.UL) || tagName.equals(XHTML.OL)) {
					content.addAll(list(child, true));

				}else if(tagName.equals(XHTML.TBL)) {
					content.add(T_TABLE);
					content.addAll(table(child));

				}else{
					appendInline(child, content);
				}
			}
		}

		if(nested) {
			content.add(T_LIST_END);
		}

		return content;
	}




	private static List<XHTMLNode> childrenOf(XHTMLNode node) {

		if(node instanceof XHTMLElement) {
			List<XHTMLNode> children = ((XHTMLElement) node).getChildren();
			return children == null ? Collections.<XHTMLNode>emptyList() : children;
		}
		return Collections.<XHTMLNode>emptyList();
	}

	private static List<XHTMLNode> objectsIn(XHTMLNode node) {

		List<XHTMLNode> objects = new ArrayList<XHTMLNode>();
		for(XHTMLNode child: childrenOf(node)) {
			if(child.getTagName().equals(XHTML.OBJECT)) {
				objects.add(child);
			}
			objects.addAll(objectsIn(child));
		}
		return objects;
	}

	private static String objectData(XHTMLNode node) {
		return node instanceof XHTMLElementObject ? ((XHTMLElementObject) node).getData() : null;
	}

	private static String textOf(XHTMLNode node) {
		return node.getNode() == null ? "" : node.getNode().getTextContent().trim();
	}

}
