package de.uni_stuttgart.ils.reqif4j.attributes;

import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class AttributeDefinition extends Identifiable {
	
	
	private Datatype type;
	private String defaultValue;
	private String sourceElementName;
	private Boolean editable;
	private AttributeValueXHTML defaultXhtml;
	
	
	
	
	public Datatype getDataType() {
		return this.type;
	}
	
	public String getDefaultValue() {
		return this.defaultValue;
	}

	/**
	 * @return the default of an XHTML attribute as read from the document, or
	 *         null if there is none or the definition was not read from a
	 *         document (then {@link #getDefaultValue()} holds the markup)
	 */
	public AttributeValueXHTML getDefaultXhtmlValue() {
		return this.defaultXhtml;
	}

	/**
	 * @return IS-EDITABLE: whether values of this attribute may be edited in
	 *         the receiving tool, or null if the document does not say
	 */
	public Boolean getEditable() {
		return this.editable;
	}

	public AttributeDefinition setEditable(Boolean editable) {
		this.editable = editable;
		markModified();
		return this;
	}

	/**
	 * @return the ATTRIBUTE-DEFINITION-* element name this definition was read
	 *         from, or null when it was not created from a document
	 */
	public String getSourceElementName() {
		return this.sourceElementName;
	}
	
	
	
	
	/**
	 * Creates an attribute definition from plain values, for documents that are
	 * generated instead of parsed.
	 */
	public AttributeDefinition(String id, String name, Datatype type, String defaultValue) {
		super(id, name);

		this.type = type;
		this.defaultValue = defaultValue;
	}

	public AttributeDefinition(Node attributeDefinition, Map<String, Datatype> dataTypes) {
		super(attributeDefinition);

		this.sourceElementName = XmlUtils.localName(attributeDefinition);
		this.editable = XmlUtils.booleanAttribute(attributeDefinition, ReqIFConst.IS_EDITABLE);
		
		// Navigate by element (not by fixed child index) so both pretty-printed
		// and minified ReqIF files are handled.
		Element typeElement = XmlUtils.firstChildElementByLocalName(attributeDefinition, ReqIFConst.TYPE);
		Element typeRef = XmlUtils.firstChildElement(typeElement);
		if(typeRef != null) {
			this.type = dataTypes.get(typeRef.getTextContent().trim());
		}

		Element defVal = XmlUtils.firstChildElementByLocalName(attributeDefinition, ReqIFConst.DEFAULT_VALUE);
		if(defVal != null) {

			Element attDefVal = XmlUtils.firstChildElement(defVal);
			String theValue = XmlUtils.attribute(attDefVal, ReqIFConst.THE_VALUE);
			if(theValue != null) {
				this.defaultValue = theValue;

			} else if(XmlUtils.firstChildElementByLocalName(attDefVal, ReqIFConst.THE_VALUE) != null) {
				// an XHTML default carries its markup in a THE-VALUE element. The
				// parsed value is kept, so it is written back as it was (a p
				// instead of a div, THE-ORIGINAL-VALUE); getDefaultValue() offers
				// it as rendered XHTML.
				this.defaultXhtml = new AttributeValueXHTML(attDefVal, this);
				String markup = (String) this.defaultXhtml.getValue();
				this.defaultValue = markup.isEmpty() ? null : markup;
			}
		}
	}

}
