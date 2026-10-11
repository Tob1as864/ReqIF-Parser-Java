package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.LinkedHashMap;
import java.util.Map;

import de.uni_stuttgart.ils.reqif4j.attributes.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues;
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
		this(specType, dataTypes, ParseIssues.collecting());
	}

	/**
	 * @param issues receives attribute definitions that cannot be read or are
	 *               declared twice
	 */
	public SpecType(Node specType, Map<String, Datatype> dataTypes, ParseIssues issues) {
		super(specType);

		this.sourceElementName = XmlUtils.localName(specType);
		this.type = ReqIFConst.UNDEFINED;

		//Doors relationship definitionen habe keine ChildNodes
		Node specAttributes = XmlUtils.firstChildElementByLocalName(specType, ReqIFConst.SPEC_ATTRIBUTES);
		for(Node attributeDefinition: XmlUtils.childElements(specAttributes)) {

			AttributeDefinition definition = issues.readOrSkip("attribute definition",
					() -> readAttributeDefinition(attributeDefinition, dataTypes));
			if(definition != null) {
				if(definition.getDataType() == null) {
					issues.report(definition.getID(), "Attribute definition references a datatype that is not declared "
							+ "in DATATYPES; it and its values cannot be written");
				}
				issues.put(this.attributeDefinitions, definition, "Attribute definition");
			}
		}
	}

	private static AttributeDefinition readAttributeDefinition(Node attributeDefinition, Map<String, Datatype> dataTypes) {

		String attDefNodeName = XmlUtils.localName(attributeDefinition);
		switch(attDefNodeName.substring(attDefNodeName.lastIndexOf("-")+1)) {

			case ReqIFConst.BOOLEAN:		return new AttributeDefinitionBoolean(attributeDefinition, dataTypes);
			case ReqIFConst.INTEGER:		return new AttributeDefinitionInteger(attributeDefinition, dataTypes);
			case ReqIFConst.STRING:			return new AttributeDefinitionString(attributeDefinition, dataTypes);
			case ReqIFConst.ENUMERATION:	return new AttributeDefinitionEnumeration(attributeDefinition, dataTypes);
			case ReqIFConst.XHTML:			return new AttributeDefinitionXHTML(attributeDefinition, dataTypes);
			case ReqIFConst.DATE:			return new AttributeDefinitionDate(attributeDefinition, dataTypes);
			// REAL is read as DOUBLE
			case ReqIFConst.DOUBLE:
			case ReqIFConst.REAL:			return new AttributeDefinitionDouble(attributeDefinition, dataTypes);
			default:						return new AttributeDefinition(attributeDefinition, dataTypes);
		}
	}

}
