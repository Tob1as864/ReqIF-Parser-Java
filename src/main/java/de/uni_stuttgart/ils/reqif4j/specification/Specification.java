package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinitionEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueBoolean;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueDate;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueDouble;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueInteger;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueString;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class Specification {
	
	
	private String id;
	private String name;
	private SpecType type;
	private int numberOfSpecObjects;
	private int sectionCounter = 0;
	private Map<String, AttributeValue> attributeValues = new HashMap<String, AttributeValue>();
	private Map<String, SpecHierarchy> children = new LinkedHashMap<String, SpecHierarchy>();
	private List<SpecHierarchy> allSpecHierarchies = new ArrayList<SpecHierarchy>();
	
	
	
	
	public String getID() {
		return this.id;
	}
	
	/**
	 * @return the value of an attribute named "Description", or null if the
	 *         specification has no such attribute
	 */
	public String getDescription() {
		AttributeValue description = this.attributeValues.get("Description");
		return description == null ? null : (String) description.getValue();
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getSpecType() {
		return this.type.getType();
	}
	
	public String getSpecTypeName() {
		return this.type.getName();
	}
	
	public Map<String, AttributeValue> getAttributes() {
		return this.attributeValues;
	}
	
	public Object getAttribute(String attributeName) {
		AttributeValue attributeValue = this.attributeValues.get(attributeName);
		return attributeValue == null ? null : attributeValue.getValue();
	}
	
	public int getNumberOfSpecObjects() {
		return this.numberOfSpecObjects;
	}
	
	public int getNumberOfSections() {
		return this.sectionCounter;
	}

	public List<SpecHierarchy> getChildren() {

        return new ArrayList<SpecHierarchy>(this.children.values());
	}
	
	public List<SpecObject> getLvlOneSpecHierarchies() {
		
		List<SpecObject> children = new ArrayList<SpecObject>();
		for(SpecHierarchy specHierarchy: this.children.values()) {
			children.add(specHierarchy.getSpecObject());
		}
		
		return children;
	}
	
	public List<SpecHierarchy> getAllSpecHierarchies() {
		return this.allSpecHierarchies;
	}
	
	
	
	
	public Specification(Node specification, SpecType specType, Map<String, SpecObject> specObjects) {
		
		this.id = specification.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
		this.name = specification.getAttributes().getNamedItem(ReqIFConst.LONG_NAME).getTextContent();
		this.type = specType;
		
		if(			((Element)specification).getElementsByTagName(ReqIFConst.VALUES).getLength() > 0
				&&	((Element)specification).getElementsByTagName(ReqIFConst.VALUES).item(0).getChildNodes().getLength() > 0		) {
			
			NodeList attributeValues = ((Element)specification).getElementsByTagName(ReqIFConst.VALUES).item(0).getChildNodes();
			for(int attval = 0; attval < attributeValues.getLength(); attval++) {
				
				Node attribute = attributeValues.item(attval);
				String attValNodeName = attribute.getNodeName();
				if(!attValNodeName.equals(ReqIFConst._TEXT)) {

					String attributeDefinitionRef = XmlUtils.firstChildElement(
							XmlUtils.firstChildElementByLocalName(attribute, ReqIFConst.DEFINITION)).getTextContent().trim();
					String attributeDefinitionName = specType.getAttributeDefinitions().get(attributeDefinitionRef).getName();
					String attributeValue;
					AttributeDefinition attributeDefinition = specType.getAttributeDefinition(attributeDefinitionRef);
					
					switch(attValNodeName.substring(attValNodeName.lastIndexOf("-")+1)) {
					
						case ReqIFConst.BOOLEAN:		if(attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE) !=null) {
															attributeValue = attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE).getTextContent();
														}else{
															attributeValue = "";
														}
														this.attributeValues.put(attributeDefinitionName, new AttributeValueBoolean(attributeValue, attributeDefinition));
														break;
							
						case ReqIFConst.INTEGER:		if(attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE) !=null) {
															attributeValue = attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE).getTextContent();
														}else{
															attributeValue = "";
														}
														this.attributeValues.put(attributeDefinitionName, new AttributeValueInteger(attributeValue, attributeDefinition));
														break;
							
						case ReqIFConst.STRING:			if(attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE) !=null) {
															attributeValue = attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE).getTextContent();
														}else{
															attributeValue = "";
														}
														this.attributeValues.put(attributeDefinitionName, new AttributeValueString(attributeValue, attributeDefinition));
														break;
							
						case ReqIFConst.ENUMERATION:	// Multiselect: read ALL ENUM-VALUE-REF children, not just the first
														List<String> enumRefs = new ArrayList<String>();
														List<String> enumNames = new ArrayList<String>();
														for(Element ref : XmlUtils.descendantsByLocalName(attribute, ReqIFConst.ENUM_VALUE_REF)) {
															String refID = ref.getTextContent().trim();
															enumRefs.add(refID);
															enumNames.add(specType.getEnumValueName(refID));
														}
														this.attributeValues.put(attributeDefinitionName, new AttributeValueEnumeration(enumNames, enumRefs, attributeDefinition));
														break;
							
						case ReqIFConst.XHTML:			this.attributeValues.put(attributeDefinitionName, new AttributeValueXHTML(attribute, attributeDefinition));
														break;

						// DATE and REAL values were formerly ignored on specifications
						case ReqIFConst.DATE:			if(attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE) !=null) {
															attributeValue = attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE).getTextContent();
														}else{
															attributeValue = "";
														}
														this.attributeValues.put(attributeDefinitionName, new AttributeValueDate(attributeValue, attributeDefinition));
														break;

						case ReqIFConst.REAL:			if(attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE) !=null) {
															attributeValue = attribute.getAttributes().getNamedItem(ReqIFConst.THE_VALUE).getTextContent();
														}else{
															attributeValue = "";
														}
														this.attributeValues.put(attributeDefinitionName, new AttributeValueDouble(attributeValue, attributeDefinition));
														break;

						default:						break;
					}
				}
			}
		}
		
		if(this.attributeValues.size() < specType.getAttributeDefinitions().size()) {
			
			for(AttributeDefinition attributeDefinition: specType.getAttributeDefinitions().values()) {
				
				if(!this.attributeValues.containsKey(attributeDefinition.getName())) {

					if(attributeDefinition.getDataType() == null) {
						throw new ExceptionSpecObject("Attribute definition not existing in ReqIF parser\n", attributeDefinition);
					}

					switch(attributeDefinition.getDataType().getType()) {
					
						case ReqIFConst.BOOLEAN:		this.attributeValues.put(attributeDefinition.getName(), new AttributeValueBoolean(attributeDefinition.getDefaultValue(), attributeDefinition));
														break;
						
						case ReqIFConst.INTEGER:		this.attributeValues.put(attributeDefinition.getName(), new AttributeValueInteger(attributeDefinition.getDefaultValue(), attributeDefinition));
														break;
						
						case ReqIFConst.STRING:			this.attributeValues.put(attributeDefinition.getName(), new AttributeValueString(attributeDefinition.getDefaultValue(), attributeDefinition));
														break;
						
						case ReqIFConst.ENUMERATION:	if(attributeDefinition instanceof AttributeDefinitionEnumeration) {
															AttributeDefinitionEnumeration enumDefinition = (AttributeDefinitionEnumeration) attributeDefinition;
															this.attributeValues.put(attributeDefinition.getName(), new AttributeValueEnumeration(enumDefinition.getDefaultValues(), enumDefinition.getDefaultValueRefs(), attributeDefinition));
														}else{
															this.attributeValues.put(attributeDefinition.getName(), new AttributeValueEnumeration(attributeDefinition.getDefaultValue(), attributeDefinition));
														}
														break;

						case ReqIFConst.XHTML:			this.attributeValues.put(attributeDefinition.getName(), new AttributeValueXHTML(attributeDefinition.getDefaultValue(), attributeDefinition));
														break;

						case ReqIFConst.DATE:			this.attributeValues.put(attributeDefinition.getName(), new AttributeValueDate(attributeDefinition.getDefaultValue(), attributeDefinition));
														break;

						case ReqIFConst.DOUBLE:			this.attributeValues.put(attributeDefinition.getName(), new AttributeValueDouble(attributeDefinition.getDefaultValue(), attributeDefinition));
														break;

						default:						break;
					}
				}
			}
		}
		
		if(			((Element)specification).getElementsByTagName(ReqIFConst.CHILDREN).getLength() > 0
				&&	((Element)specification).getElementsByTagName(ReqIFConst.CHILDREN).item(0).getChildNodes().getLength() > 0		) {
			
			NodeList children = ((Element)specification).getElementsByTagName(ReqIFConst.CHILDREN).item(0).getChildNodes();
			for(int child = 0; child < children.getLength(); child++) {
				
				Node specHierarchy = children.item(child);
				if(!specHierarchy.getNodeName().equals(ReqIFConst._TEXT)) {
					
					String specHierarchyID = specHierarchy.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
					
					this.children.put(specHierarchyID, new SpecHierarchy(1, ++this.sectionCounter, specHierarchy, specObjects));
				}
			}
		}
		
		List<SpecHierarchy> allChildren = new ArrayList<SpecHierarchy>();
		for(SpecHierarchy specHierarchy: this.children.values()) {
			allChildren.add(specHierarchy);
			for(SpecHierarchy child: specHierarchy.getAllChildren()) {
				allChildren.add(child);
			}
		}
		this.allSpecHierarchies.addAll(allChildren);
		this.numberOfSpecObjects = allChildren.size();
	}

}
