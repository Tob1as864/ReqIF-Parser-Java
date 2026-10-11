package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

/**
 * Reads the VALUES of an element with attributes - spec objects, spec
 * relations and specifications share the same structure - and fills in the
 * default values of attribute definitions that carry no explicit value.
 */
final class AttributeValueReader {

	private AttributeValueReader() {
	}

	/**
	 * @param element         the SPEC-OBJECT, SPEC-RELATION or SPECIFICATION
	 * @param specType        the element's resolved spec type
	 * @param attributeValues receives the values, keyed by {@link #key}
	 */
	static void read(Node element, SpecType specType, Map<String, AttributeValue> attributeValues) {

		// VALUES is located by local name so prefixed ReqIF namespaces work too;
		// only this element's own VALUES is taken, not a nested one.
		Element valuesElement = XmlUtils.firstChildElementByLocalName(element, ReqIFConst.VALUES);
		if(valuesElement != null) {

			for(Element attribute: XmlUtils.childElements(valuesElement)) {

				String attValNodeName = XmlUtils.localName(attribute);
				String attributeDefinitionRef = XmlUtils.firstChildElement(
						XmlUtils.firstChildElementByLocalName(attribute, ReqIFConst.DEFINITION)).getTextContent().trim();
				AttributeDefinition attributeDefinition = specType.getAttributeDefinition(attributeDefinitionRef);
				String attributeDefinitionName = key(attributeDefinition);

				switch(attValNodeName.substring(attValNodeName.lastIndexOf("-")+1)) {

					case ReqIFConst.BOOLEAN:
						attributeValues.put(attributeDefinitionName, new AttributeValueBoolean(theValue(attribute), attributeDefinition));
						break;

					case ReqIFConst.INTEGER:
						attributeValues.put(attributeDefinitionName, new AttributeValueInteger(theValue(attribute), attributeDefinition));
						break;

					case ReqIFConst.STRING:
						attributeValues.put(attributeDefinitionName, new AttributeValueString(theValue(attribute), attributeDefinition));
						break;

					case ReqIFConst.ENUMERATION:
						// Multiselect: read ALL ENUM-VALUE-REF children, not just the first
						List<String> enumRefs = new ArrayList<String>();
						List<String> enumNames = new ArrayList<String>();
						for(Element ref : XmlUtils.descendantsByLocalName(attribute, ReqIFConst.ENUM_VALUE_REF)) {
							String refID = ref.getTextContent().trim();
							enumRefs.add(refID);
							enumNames.add(specType.getEnumValueName(refID));
						}
						attributeValues.put(attributeDefinitionName, new AttributeValueEnumeration(enumNames, enumRefs, attributeDefinition));
						break;

					case ReqIFConst.XHTML:
						attributeValues.put(attributeDefinitionName, new AttributeValueXHTML(attribute, attributeDefinition));
						break;

					case ReqIFConst.DATE:
						attributeValues.put(attributeDefinitionName, new AttributeValueDate(theValue(attribute), attributeDefinition));
						break;

					case ReqIFConst.REAL:
						attributeValues.put(attributeDefinitionName, new AttributeValueDouble(theValue(attribute), attributeDefinition));
						break;

					// Values of datatype kinds the parser does not model are kept
					// generically instead of being dropped, so they survive a
					// round trip.
					default:
						attributeValues.put(attributeDefinitionName,
								new AttributeValue(theValue(attribute), attributeDefinition)
										.setSourceElementName(attValNodeName));
						break;
				}
			}
		}

		if(attributeValues.size() < specType.getAttributeDefinitions().size()) {

			for(AttributeDefinition attributeDefinition: specType.getAttributeDefinitions().values()) {

				String key = key(attributeDefinition);
				if(!attributeValues.containsKey(key)) {

					Datatype datatype = attributeDefinition.getDataType();

					if(datatype == null) {
						throw new ExceptionSpecObject("Attribute definition not existing in ReqIF parser\n", attributeDefinition);
					}

					String defaultValue = attributeDefinition.getDefaultValue();
					switch(datatype.getType()) {

						case ReqIFConst.BOOLEAN:
							attributeValues.put(key, new AttributeValueBoolean(defaultValue, attributeDefinition));
							break;

						case ReqIFConst.INTEGER:
							attributeValues.put(key, new AttributeValueInteger(defaultValue, attributeDefinition));
							break;

						case ReqIFConst.STRING:
							attributeValues.put(key, new AttributeValueString(defaultValue, attributeDefinition));
							break;

						case ReqIFConst.ENUMERATION:
							if(attributeDefinition instanceof AttributeDefinitionEnumeration) {
								AttributeDefinitionEnumeration enumDefinition = (AttributeDefinitionEnumeration) attributeDefinition;
								attributeValues.put(key, new AttributeValueEnumeration(enumDefinition.getDefaultValues(), enumDefinition.getDefaultValueRefs(), attributeDefinition));
							}else{
								attributeValues.put(key, new AttributeValueEnumeration(defaultValue, attributeDefinition));
							}
							break;

						case ReqIFConst.XHTML:
							attributeValues.put(key, new AttributeValueXHTML(defaultValue, attributeDefinition));
							break;

						case ReqIFConst.DATE:
							attributeValues.put(key, new AttributeValueDate(defaultValue, attributeDefinition));
							break;

						// the parser normalizes REAL datatypes to DOUBLE
						case ReqIFConst.DOUBLE:
							attributeValues.put(key, new AttributeValueDouble(defaultValue, attributeDefinition));
							break;

						default:
							break;
					}
				}
			}
		}
	}

	/**
	 * Attribute values are kept by the LONG-NAME of their definition. LONG-NAME
	 * is optional, though; definitions without one are keyed by their
	 * IDENTIFIER instead, so their values do not overwrite each other.
	 */
	static String key(AttributeDefinition attributeDefinition) {
		return attributeDefinition.getName() != null ? attributeDefinition.getName() : attributeDefinition.getID();
	}

	/**
	 * @return the THE-VALUE attribute, or "" if the value element has none
	 */
	private static String theValue(Element attribute) {
		return XmlUtils.attributeOrEmpty(attribute, ReqIFConst.THE_VALUE);
	}
}
