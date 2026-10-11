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
	 * @return the LONG-NAME of the enum value with the given id, "" if the
	 *         value has no LONG-NAME, or null if the id is unknown
	 */
	public String getEnumValueName(String id) {
		DatatypeEnumerationValue value = this.enumValues.get(id);
		if (value == null) {
			return null;
		}
		return value.getName() == null ? "" : value.getName();
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


	/**
	 * Creates an enumeration datatype from plain values, for documents that are
	 * generated instead of parsed.
	 */
	public DatatypeEnumeration(String id, String name, java.util.List<DatatypeEnumerationValue> values) {
		super(id, name, ReqIFConst.ENUMERATION);

		if (values != null) {
			for (DatatypeEnumerationValue value : values) {
				this.enumValues.put(value.getID(), value);
			}
		}
	}

	public DatatypeEnumeration(String id, String name, Node enumeration) {
		super(id, name, ReqIFConst.ENUMERATION);

		// Iterate ENUM-VALUE elements independent of whitespace text nodes,
		// namespace prefixes and optional attributes.
		for (Element enumValue : XmlUtils.descendantsByLocalName(enumeration, ReqIFConst.ENUM_VALUE)) {

			if (XmlUtils.attribute(enumValue, ReqIFConst.IDENTIFIER) == null) {
				continue;
			}
			DatatypeEnumerationValue value = new DatatypeEnumerationValue(enumValue);
			enumValues.put(value.getID(), value);
		}
	}

}
