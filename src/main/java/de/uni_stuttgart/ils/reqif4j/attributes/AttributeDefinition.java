package de.uni_stuttgart.ils.reqif4j.attributes;

import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class AttributeDefinition {
	
	
	private String id;
	private String name;
	private Datatype type;
	private String defaultValue;
	
	
	
	
	public String getID() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public Datatype getDataType() {
		return this.type;
	}
	
	public String getDefaultValue() {
		return this.defaultValue;
	}
	
	
	
	
	/**
	 * Creates an attribute definition from plain values, for documents that are
	 * generated instead of parsed.
	 */
	public AttributeDefinition(String id, String name, Datatype type, String defaultValue) {

		this.id = id;
		this.name = name;
		this.type = type;
		this.defaultValue = defaultValue;
	}

	public AttributeDefinition(Node attributeDefinition, Map<String, Datatype> dataTypes) {
		
		this.id = attributeDefinition.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
		this.name = attributeDefinition.getAttributes().getNamedItem(ReqIFConst.LONG_NAME).getTextContent();
		
		// Navigate by element (not by fixed child index) so both pretty-printed
		// and minified ReqIF files are handled.
		Element typeElement = XmlUtils.firstChildElementByLocalName(attributeDefinition, ReqIFConst.TYPE);
		Element typeRef = XmlUtils.firstChildElement(typeElement);
		if(typeRef != null) {
			this.type = dataTypes.get(typeRef.getTextContent().trim());
		}

		Element defVal = XmlUtils.firstChildElementByLocalName(attributeDefinition, ReqIFConst.DEFAULT_VALUE);
		if(defVal != null) {

			Node attDefVal = XmlUtils.firstChildElement(defVal);
			if(attDefVal != null && attDefVal.hasAttributes() && attDefVal.getAttributes().getNamedItem(ReqIFConst.THE_VALUE) != null) {
				this.defaultValue = attDefVal.getAttributes().getNamedItem(ReqIFConst.THE_VALUE).getTextContent();
			}
		}
	}

}
