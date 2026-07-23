package de.uni_stuttgart.ils.reqif4j.datatypes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class DatatypeEnumeration extends Datatype {


	private Map<String, DatatypeEnumerationValue> enumValues = new LinkedHashMap<String, DatatypeEnumerationValue>();


	/**
	 * @return all enum values of this datatype, keyed by IDENTIFIER
	 */
	public Map<String, DatatypeEnumerationValue> getEnumValues() {
		return this.enumValues;
	}

	/**
	 * @return the LONG-NAME of the enum value with the given id, or null if
	 *         unknown
	 */
	public String getEnumValueName(String id) {
		DatatypeEnumerationValue value = this.enumValues.get(id);
		return value == null ? null : value.getName();
	}

	/**
	 * @return the KEY of the enum value with the given id, or null if unknown
	 */
	public String getEnumValueKey(String id) {
		DatatypeEnumerationValue value = this.enumValues.get(id);
		return value == null ? null : value.getKey();
	}

	/**
	 * @return the OTHER-CONTENT of the enum value with the given id, or null if
	 *         unknown
	 */
	public String getEnumValueOtherContent(String id) {
		DatatypeEnumerationValue value = this.enumValues.get(id);
		return value == null ? null : value.getOtherContent();
	}


	public DatatypeEnumeration(String id, String name, Node enumeration) {
		super(id, name, ReqIFConst.ENUMERATION);

		// Iterate ENUM-VALUE elements independent of whitespace text nodes,
		// namespace prefixes and optional attributes.
		for (Element enumValue : XmlUtils.descendantsByLocalName(enumeration, ReqIFConst.ENUM_VALUE)) {

			String identifier = XmlUtils.attribute(enumValue, ReqIFConst.IDENTIFIER);
			if (identifier == null) {
				continue;
			}
			String longName = XmlUtils.attribute(enumValue, ReqIFConst.LONG_NAME);

			Element embeddedValue = XmlUtils.firstDescendantByLocalName(enumValue, ReqIFConst.EMBEDDED_VALUE);
			String key = embeddedValue == null ? "" : orEmpty(XmlUtils.attribute(embeddedValue, ReqIFConst.KEY));
			String otherContent = embeddedValue == null ? "" : orEmpty(XmlUtils.attribute(embeddedValue, ReqIFConst.OTHER_CONTENT));

			enumValues.put(identifier, new DatatypeEnumerationValue(identifier, orEmpty(longName), key, otherContent));
		}
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

}
