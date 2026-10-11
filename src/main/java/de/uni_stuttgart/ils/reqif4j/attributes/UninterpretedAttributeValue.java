package de.uni_stuttgart.ils.reqif4j.attributes;

import org.w3c.dom.Element;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

/**
 * A value the parser could not interpret, kept as the ATTRIBUTE-VALUE-*
 * element it was read from and written back unchanged: a value of an element
 * whose spec type is not declared, a value whose attribute definition is not
 * declared, or a value whose kind does not match its definition (e.g. an
 * ATTRIBUTE-VALUE-STRING for an XHTML attribute).
 *
 * Exports cut out of a larger document often reference types they do not
 * contain; their values are kept this way instead of being lost.
 */
public class UninterpretedAttributeValue extends AttributeValue {

	private final Element element;
	private final String reason;

	/**
	 * @param element    the ATTRIBUTE-VALUE-* element
	 * @param definition the attribute definition, or one standing in for an
	 *                   undeclared definition (IDENTIFIER only)
	 * @param reason     why the value could not be interpreted
	 */
	public UninterpretedAttributeValue(Element element, AttributeDefinition definition, String reason) {
		super(theValue(element), definition);
		this.element = element;
		this.reason = reason;
		setSourceElementName(XmlUtils.localName(element));
	}

	/**
	 * @return the ATTRIBUTE-VALUE-* element exactly as read
	 */
	public Element getElement() {
		return this.element;
	}

	/**
	 * @return why the value could not be interpreted
	 */
	public String getReason() {
		return this.reason;
	}

	/**
	 * @return the THE-VALUE attribute; for an XHTML value the text of its
	 *         THE-VALUE element, for an enumeration value the referenced enum
	 *         value IDENTIFIERs separated by ", "; "" if there is none
	 */
	private static String theValue(Element element) {
		String theValue = XmlUtils.attribute(element, ReqIFConst.THE_VALUE);
		if (theValue != null) {
			return theValue;
		}
		Element content = XmlUtils.firstChildElementByLocalName(element, ReqIFConst.THE_VALUE);
		if (content != null) {
			return content.getTextContent().trim();
		}
		java.util.List<String> refs = new java.util.ArrayList<String>();
		for (Element ref : XmlUtils.descendantsByLocalName(element, ReqIFConst.ENUM_VALUE_REF)) {
			refs.add(ref.getTextContent().trim());
		}
		return String.join(", ", refs);
	}
}
