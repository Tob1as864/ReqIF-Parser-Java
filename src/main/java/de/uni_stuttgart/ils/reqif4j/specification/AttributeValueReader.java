package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.List;

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
 * relations and specifications share the same structure - and creates the
 * values that stand in for absent ones from the attribute definition defaults.
 */
final class AttributeValueReader {

	private AttributeValueReader() {
	}

	/**
	 * Reads the explicit values, the ones the element itself carries.
	 *
	 * @param element  the SPEC-OBJECT, SPEC-RELATION or SPECIFICATION
	 * @param specType the element's resolved spec type
	 * @param values   receives the values
	 */
	static void read(Node element, SpecType specType, AttributeValues values) {

		// VALUES is located by local name so prefixed ReqIF namespaces work too;
		// only this element's own VALUES is taken, not a nested one.
		Element valuesElement = XmlUtils.firstChildElementByLocalName(element, ReqIFConst.VALUES);
		if(valuesElement == null) {
			return;
		}

		for(Element attribute: XmlUtils.childElements(valuesElement)) {

			String attValNodeName = XmlUtils.localName(attribute);
			String attributeDefinitionRef = XmlUtils.firstChildElement(
					XmlUtils.firstChildElementByLocalName(attribute, ReqIFConst.DEFINITION)).getTextContent().trim();
			AttributeDefinition attributeDefinition = specType.getAttributeDefinition(attributeDefinitionRef);

			switch(attValNodeName.substring(attValNodeName.lastIndexOf("-")+1)) {

				case ReqIFConst.BOOLEAN:
					values.put(new AttributeValueBoolean(theValue(attribute), attributeDefinition));
					break;

				case ReqIFConst.INTEGER:
					values.put(new AttributeValueInteger(theValue(attribute), attributeDefinition));
					break;

				case ReqIFConst.STRING:
					values.put(new AttributeValueString(theValue(attribute), attributeDefinition));
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
					values.put(new AttributeValueEnumeration(enumNames, enumRefs, attributeDefinition));
					break;

				case ReqIFConst.XHTML:
					values.put(new AttributeValueXHTML(attribute, attributeDefinition));
					break;

				case ReqIFConst.DATE:
					values.put(new AttributeValueDate(theValue(attribute), attributeDefinition));
					break;

				case ReqIFConst.REAL:
					values.put(new AttributeValueDouble(theValue(attribute), attributeDefinition));
					break;

				// Values of datatype kinds the parser does not model are kept
				// generically instead of being dropped, so they survive a
				// round trip.
				default:
					values.put(new AttributeValue(theValue(attribute), attributeDefinition)
							.setSourceElementName(attValNodeName));
					break;
			}
		}
	}

	/**
	 * @return the value that stands in for an absent one: the definition's
	 *         default, or the neutral value of its datatype (false, 0, ...) as
	 *         earlier versions reported it; null for datatypes without one
	 */
	static AttributeValue defaultValue(AttributeDefinition attributeDefinition) {

		Datatype datatype = attributeDefinition.getDataType();
		if(datatype == null) {
			return null;
		}
		String defaultValue = attributeDefinition.getDefaultValue();
		switch(datatype.getType()) {

			case ReqIFConst.BOOLEAN:
				return new AttributeValueBoolean(defaultValue, attributeDefinition);

			case ReqIFConst.INTEGER:
				return new AttributeValueInteger(defaultValue, attributeDefinition);

			case ReqIFConst.STRING:
				return new AttributeValueString(defaultValue, attributeDefinition);

			case ReqIFConst.ENUMERATION:
				if(attributeDefinition instanceof AttributeDefinitionEnumeration) {
					AttributeDefinitionEnumeration enumDefinition = (AttributeDefinitionEnumeration) attributeDefinition;
					return new AttributeValueEnumeration(enumDefinition.getDefaultValues(), enumDefinition.getDefaultValueRefs(), attributeDefinition);
				}
				return new AttributeValueEnumeration(defaultValue, attributeDefinition);

			case ReqIFConst.XHTML:
				return new AttributeValueXHTML(defaultValue, attributeDefinition);

			case ReqIFConst.DATE:
				return new AttributeValueDate(defaultValue, attributeDefinition);

			// the parser normalizes REAL datatypes to DOUBLE
			case ReqIFConst.DOUBLE:
			case ReqIFConst.REAL:
				return new AttributeValueDouble(defaultValue, attributeDefinition);

			default:
				return null;
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
