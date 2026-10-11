package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.LinkedHashMap;
import java.util.Map;

import de.uni_stuttgart.ils.reqif4j.attributes.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class SpecType extends Identifiable {
	
	
	private Map<String, AttributeDefinition> attributeDefinitions = new LinkedHashMap<String, AttributeDefinition>();
	protected String type;
	protected String sourceElementName;
	
	
	
	
	/**
	 * @return the SPEC-TYPE element name this type was read from, or null when
	 *         it was not created from a document. Needed to write back spec type
	 *         kinds the parser does not model explicitly, instead of silently
	 *         turning them into a SPEC-OBJECT-TYPE.
	 */
	public String getSourceElementName() {
		return this.sourceElementName;
	}
	
	public String getType() {
		return this.type;
	}
	
	public Map<String, AttributeDefinition> getAttributeDefinitions() {
		return this.attributeDefinitions;
	}
	
	public AttributeDefinition getAttributeDefinition(String id) {
		return this.attributeDefinitions.get(id);
	}
	
	public String getEnumValueName(String id) {
		
		for(AttributeDefinition attributeDefinition: this.attributeDefinitions.values()) {
			//Falls noch keine Klasse für diese Attributsdefinition definiert ist
			if(attributeDefinition.getDataType() == null){
				continue;
			}
			if(attributeDefinition.getDataType().getClass().equals(DatatypeEnumeration.class)) {
				
				if(((DatatypeEnumeration)attributeDefinition.getDataType()).getEnumValueName(id) != null) {
					
					return ((DatatypeEnumeration)attributeDefinition.getDataType()).getEnumValueName(id);
				}
			}
		}
		return "";
	}
	
	public String getEnumValueKey(String id) {

		for(AttributeDefinition attributeDefinition: this.attributeDefinitions.values()) {
			//Falls noch keine Klasse für diese Attributsdefinition definiert ist
			if(attributeDefinition.getDataType() == null){
				continue;
			}
			if(attributeDefinition.getDataType().getClass().equals(DatatypeEnumeration.class)) {

				if(((DatatypeEnumeration)attributeDefinition.getDataType()).getEnumValueName(id) != null) {

					return ((DatatypeEnumeration)attributeDefinition.getDataType()).getEnumValueKey(id);
				}
			}
		}
		return "";
	}

	public String getEnumValueOtherContent(String id) {

		for(AttributeDefinition attributeDefinition: this.attributeDefinitions.values()) {
			//Falls noch keine Klasse für diese Attributsdefinition definiert ist
			if(attributeDefinition.getDataType() == null){
				continue;
			}
			if(attributeDefinition.getDataType().getClass().equals(DatatypeEnumeration.class)) {

				if(((DatatypeEnumeration)attributeDefinition.getDataType()).getEnumValueName(id) != null) {

					return ((DatatypeEnumeration)attributeDefinition.getDataType()).getEnumValueOtherContent(id);
				}
			}
		}
		return "";
	}
	
	
	
	
	/**
	 * Creates a spec type from plain values, for documents that are generated
	 * instead of parsed.
	 *
	 * @param kind SPEC-OBJECT-TYPE, SPECIFICATION-TYPE or SPEC-RELATION-TYPE
	 */
	public SpecType(String id, String name, String kind) {
		super(id, name);

		this.type = kind;
	}

	/**
	 * Adds an attribute definition; used when building a document.
	 */
	public SpecType addAttributeDefinition(AttributeDefinition attributeDefinition) {

		this.attributeDefinitions.put(attributeDefinition.getID(), attributeDefinition);
		return this;
	}

	public SpecType(Node specType, Map<String, Datatype> dataTypes) {
		super(specType);

		this.sourceElementName = XmlUtils.localName(specType);
		this.type = ReqIFConst.UNDEFINED;

		//Doors relationship definitionen habe keine ChildNodes
		Node specAttributes = XmlUtils.firstChildElementByLocalName(specType, ReqIFConst.SPEC_ATTRIBUTES);
		if(specAttributes != null) {

			for(Node attributeDefinition: XmlUtils.childElements(specAttributes)) {

				String attDefNodeName = XmlUtils.localName(attributeDefinition);
				{
					
					String attDefID = XmlUtils.attribute(attributeDefinition, ReqIFConst.IDENTIFIER);
					
					switch(attDefNodeName.substring(attDefNodeName.lastIndexOf("-")+1)) {
					
						case ReqIFConst.BOOLEAN:		this.attributeDefinitions.put(attDefID, new AttributeDefinitionBoolean(attributeDefinition, dataTypes));
														break;
											
						case ReqIFConst.INTEGER:		this.attributeDefinitions.put(attDefID, new AttributeDefinitionInteger(attributeDefinition, dataTypes));
														break;
											
						case ReqIFConst.STRING:			this.attributeDefinitions.put(attDefID, new AttributeDefinitionString(attributeDefinition, dataTypes));
														break;
											
						case ReqIFConst.ENUMERATION:	this.attributeDefinitions.put(attDefID, new AttributeDefinitionEnumeration(attributeDefinition, dataTypes));
														break;
											
						case ReqIFConst.XHTML:			this.attributeDefinitions.put(attDefID, new AttributeDefinitionXHTML(attributeDefinition, dataTypes));
														break;

						case ReqIFConst.DATE:			this.attributeDefinitions.put(attDefID, new AttributeDefinitionDate(attributeDefinition, dataTypes));
														break;

						case ReqIFConst.DOUBLE:			this.attributeDefinitions.put(attDefID, new AttributeDefinitionDouble(attributeDefinition, dataTypes));
														break;
						//Convert REAL to DOUBLE
						case ReqIFConst.REAL:			this.attributeDefinitions.put(attDefID, new AttributeDefinitionDouble(attributeDefinition, dataTypes));
														break;
											
						default:						this.attributeDefinitions.put(attDefID, new AttributeDefinition(attributeDefinition, dataTypes));
														break;
					}
				}
			}
		}
	}

}
