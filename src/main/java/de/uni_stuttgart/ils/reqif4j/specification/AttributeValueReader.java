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
import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import de.uni_stuttgart.ils.reqif4j.util.XsdTypes;

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
	 * A value whose definition cannot be found is reported and skipped. A
	 * value that does not fit its datatype (e.g. THE-VALUE="abc" for an
	 * integer) is kept as written, as a plain {@link AttributeValue}, so it
	 * is written back unchanged and the validator can report it.
	 *
	 * @param element  the SPEC-OBJECT, SPEC-RELATION or SPECIFICATION
	 * @param specType the element's resolved spec type
	 * @param values   receives the values
	 * @param issues   receives the problems found
	 */
	static void read(Node element, SpecType specType, AttributeValues values, ParseIssues issues) {

		// VALUES is located by local name so prefixed ReqIF namespaces work too;
		// only this element's own VALUES is taken, not a nested one.
		Element valuesElement = XmlUtils.firstChildElementByLocalName(element, ReqIFConst.VALUES);
		if(valuesElement == null) {
			return;
		}
		String ownerID = XmlUtils.attribute(element, ReqIFConst.IDENTIFIER);

		for(Element attribute: XmlUtils.childElements(valuesElement)) {

			String attValNodeName = XmlUtils.localName(attribute);
			Element definitionRef = XmlUtils.firstChildElement(
					XmlUtils.firstChildElementByLocalName(attribute, ReqIFConst.DEFINITION));
			if(definitionRef == null) {
				issues.report(ownerID, attValNodeName + " has no DEFINITION; the value is skipped");
				continue;
			}
			String attributeDefinitionRef = definitionRef.getTextContent().trim();
			AttributeDefinition attributeDefinition = specType.getAttributeDefinition(attributeDefinitionRef);
			if(attributeDefinition == null) {
				issues.report(ownerID, "Value references attribute definition " + attributeDefinitionRef
						+ ", which spec type " + specType.getID() + " does not declare; the value is skipped");
				continue;
			}
			if(values.explicit().containsKey(attributeDefinitionRef)) {
				issues.report(ownerID, "Attribute definition " + attributeDefinitionRef
						+ " has two values; the later one replaces the earlier");
			}

			String theValue = theValue(attribute);
			String kind = attValNodeName.substring(attValNodeName.lastIndexOf("-")+1);
			if(THE_VALUE_KINDS.contains(kind) && XmlUtils.attribute(attribute, ReqIFConst.THE_VALUE) == null) {
				// read as the neutral value (false, 0, ""), as earlier versions did
				issues.report(ownerID, attValNodeName + " for " + attributeDefinitionRef
						+ " has no THE-VALUE; it is read as empty");
			}
			switch(kind) {

				case ReqIFConst.BOOLEAN:
					// any case is accepted, as earlier versions did; other text is kept
					values.put(theValue.isEmpty() || BOOLEAN_TEXT.contains(theValue.trim().toLowerCase(java.util.Locale.ROOT))
							? new AttributeValueBoolean(theValue, attributeDefinition)
							: asWritten(theValue, attributeDefinition, attValNodeName));
					break;

				case ReqIFConst.INTEGER:
					values.put(theValue.isEmpty() || XsdTypes.isInteger(theValue)
							? new AttributeValueInteger(theValue, attributeDefinition)
							: asWritten(theValue, attributeDefinition, attValNodeName));
					break;

				case ReqIFConst.STRING:
					values.put(new AttributeValueString(theValue, attributeDefinition));
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
					values.put(new AttributeValueDate(theValue, attributeDefinition));
					break;

				case ReqIFConst.REAL:
					values.put(theValue.isEmpty() || XsdTypes.isDouble(theValue)
							? new AttributeValueDouble(theValue, attributeDefinition)
							: asWritten(theValue, attributeDefinition, attValNodeName));
					break;

				// Values of datatype kinds the parser does not model are kept
				// generically instead of being dropped, so they survive a
				// round trip.
				default:
					values.put(asWritten(theValue, attributeDefinition, attValNodeName));
					break;
			}
		}
	}

	private static final java.util.Set<String> BOOLEAN_TEXT = java.util.Set.of("true", "false", "1", "0");

	/** the value kinds that carry their value in a required THE-VALUE attribute */
	private static final java.util.Set<String> THE_VALUE_KINDS = java.util.Set.of(
			ReqIFConst.BOOLEAN, ReqIFConst.INTEGER, ReqIFConst.STRING, ReqIFConst.DATE, ReqIFConst.REAL);

	/**
	 * @return the value exactly as written, for kinds the parser does not model
	 *         and for text that does not fit the datatype
	 */
	private static AttributeValue asWritten(String text, AttributeDefinition definition, String elementName) {
		return new AttributeValue(text, definition).setSourceElementName(elementName);
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

			// a default that does not fit its datatype is kept as written, like a
			// value; the validator reports it
			case ReqIFConst.BOOLEAN:
				return defaultValue == null || BOOLEAN_TEXT.contains(defaultValue.trim().toLowerCase(java.util.Locale.ROOT))
						? new AttributeValueBoolean(defaultValue, attributeDefinition)
						: new AttributeValue(defaultValue, attributeDefinition);

			case ReqIFConst.INTEGER:
				return defaultValue == null || XsdTypes.isInteger(defaultValue)
						? new AttributeValueInteger(defaultValue, attributeDefinition)
						: new AttributeValue(defaultValue, attributeDefinition);

			case ReqIFConst.STRING:
				return new AttributeValueString(defaultValue, attributeDefinition);

			case ReqIFConst.ENUMERATION:
				if(attributeDefinition instanceof AttributeDefinitionEnumeration) {
					AttributeDefinitionEnumeration enumDefinition = (AttributeDefinitionEnumeration) attributeDefinition;
					return new AttributeValueEnumeration(enumDefinition.getDefaultValues(), enumDefinition.getDefaultValueRefs(), attributeDefinition);
				}
				return new AttributeValueEnumeration(defaultValue, attributeDefinition);

			case ReqIFConst.XHTML:
				return attributeDefinition.getDefaultXhtmlValue() != null
						? attributeDefinition.getDefaultXhtmlValue()
						: new AttributeValueXHTML(defaultValue, attributeDefinition);

			case ReqIFConst.DATE:
				return new AttributeValueDate(defaultValue, attributeDefinition);

			// the parser normalizes REAL datatypes to DOUBLE
			case ReqIFConst.DOUBLE:
			case ReqIFConst.REAL:
				return defaultValue == null || XsdTypes.isDouble(defaultValue)
						? new AttributeValueDouble(defaultValue, attributeDefinition)
						: new AttributeValue(defaultValue, attributeDefinition);

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
