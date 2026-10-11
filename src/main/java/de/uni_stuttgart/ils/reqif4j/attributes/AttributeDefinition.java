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
	
	
	
	
	public Datatype getDataType() {
		return this.type;
	}
	
	public String getDefaultValue() {
		return this.defaultValue;
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
				// an XHTML default carries its markup in a THE-VALUE element; it is
				// kept as rendered XHTML, like the value of an XHTML attribute
				String markup = (String) new AttributeValueXHTML(attDefVal, this).getValue();
				this.defaultValue = markup.isEmpty() ? null : markup;
			}
		}
	}

}
