package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.LinkedHashMap;
import java.util.Map;

import de.uni_stuttgart.ils.reqif4j.attributes.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class SpecType {
	
	
	private Map<String, AttributeDefinition> attributeDefinitions = new LinkedHashMap<String, AttributeDefinition>();
	private String id;
	protected String name;
	protected String type;
	protected String alternativeID;
	protected String sourceElementName;
	
	
	
	
	public String getID() {
		return this.id;
	}

	/**
	 * @return the IDENTIFIER of the optional ALTERNATIVE-ID, or null
	 */
	public String getAlternativeID() {
		return this.alternativeID;
	}

	/**
	 * @return the SPEC-TYPE element name this type was read from, or null when
	 *         it was not created from a document. Needed to write back spec type
	 *         kinds the parser does not model explicitly, instead of silently
	 *         turning them into a SPEC-OBJECT-TYPE.
	 */
	public String getSourceElementName() {
		return this.sourceElementName;
	}
	
	public String getName() {
		return this.name;
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

		this.id = id;
		this.name = name;
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
		
		this.id = specType.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
		this.name = specType.getAttributes().getNamedItem(ReqIFConst.LONG_NAME).getTextContent();
		this.alternativeID = XmlUtils.alternativeID(specType);
		this.sourceElementName = XmlUtils.localName(specType);
		this.type = ReqIFConst.UNDEFINED;

		//Doors relationship definitionen habe keine ChildNodes
		Node specAttributes = XmlUtils.firstChildElementByLocalName(specType, ReqIFConst.SPEC_ATTRIBUTES);
		if(specAttributes != null) {

			NodeList attributeDefinitions = specAttributes.getChildNodes();

			for(int specatt = 0; specatt < attributeDefinitions.getLength(); specatt++) {
				
				Node attributeDefinition = attributeDefinitions.item(specatt);
				String attDefNodeName = attributeDefinition.getNodeName();
				if(!attDefNodeName.equals(ReqIFConst._TEXT)) {
					
					String attDefID = attributeDefinition.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
					
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
