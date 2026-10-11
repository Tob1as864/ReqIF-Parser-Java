package de.uni_stuttgart.ils.reqif4j.attributes;

import java.util.ArrayList;
import java.util.List;

import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

/**
 * Creates attribute values of the class that fits the attribute's datatype,
 * so callers need not know AttributeValueBoolean, AttributeValueInteger, ...
 * Used by the builder and by the setters of the model elements.
 */
public final class AttributeValueFactory {

	private AttributeValueFactory() {
	}

	/**
	 * @param value a Boolean, Number, String or anything whose toString() is
	 *              the value's text; null for an empty value
	 * @throws IllegalArgumentException for enumeration and XHTML attributes,
	 *         which have their own factory methods
	 */
	public static AttributeValue of(AttributeDefinition definition, Object value) {

		String category = categoryOf(definition);
		String text = value == null ? null : String.valueOf(value);

		if (ReqIFConst.ENUMERATION.equals(category)) {
			throw new IllegalArgumentException("Use setEnum(...) for the enumeration attribute " + definition.getID());
		}
		if (ReqIFConst.XHTML.equals(category)) {
			throw new IllegalArgumentException("Use setXhtml(...) for the XHTML attribute " + definition.getID());
		}
		if (category == null) {
			return new AttributeValue(text, definition);
		}
		try {
			switch (category) {
				case ReqIFConst.BOOLEAN:	return new AttributeValueBoolean(text, definition);
				case ReqIFConst.INTEGER:	return new AttributeValueInteger(text, definition);
				case ReqIFConst.STRING:		return new AttributeValueString(text, definition);
				case ReqIFConst.DATE:		return new AttributeValueDate(text, definition);
				case ReqIFConst.DOUBLE:
				case ReqIFConst.REAL:		return new AttributeValueDouble(text, definition);
				default:					return new AttributeValue(text, definition);
			}
		} catch (IllegalArgumentException e) {
			// e.g. a NumberFormatException, which does not say which attribute
			throw new IllegalArgumentException("'" + text + "' is not a valid " + category
					+ " value for attribute " + definition.getID(), e);
		}
	}

	/**
	 * @param markup XHTML, with or without a surrounding div
	 * @throws IllegalArgumentException if the attribute is not an XHTML one or
	 *         the markup is not well-formed
	 */
	public static AttributeValueXHTML xhtml(AttributeDefinition definition, String markup) {
		requireCategory(definition, ReqIFConst.XHTML);
		return new AttributeValueXHTML(markup, definition);
	}

	/**
	 * @param enumValueIDs the IDENTIFIERs of the selected enum values; none for
	 *                     an empty selection
	 * @throws IllegalArgumentException if the attribute is not an enumeration
	 *         one or an enum value does not exist
	 */
	public static AttributeValueEnumeration enumeration(AttributeDefinition definition, List<String> enumValueIDs) {

		requireCategory(definition, ReqIFConst.ENUMERATION);
		if (enumValueIDs == null || enumValueIDs.contains(null)) {
			throw new IllegalArgumentException("The enum values for attribute " + definition.getID()
					+ " must not be null; pass none for an empty selection");
		}
		DatatypeEnumeration datatype = (DatatypeEnumeration) definition.getDataType();
		List<String> names = new ArrayList<String>();
		for (String enumValueID : enumValueIDs) {
			String name = datatype.getEnumValueName(enumValueID);
			if (name == null) {
				throw new IllegalArgumentException("Unknown enum value " + enumValueID
						+ " for attribute " + definition.getID());
			}
			names.add(name);
		}
		return new AttributeValueEnumeration(names, new ArrayList<String>(enumValueIDs), definition);
	}

	private static String categoryOf(AttributeDefinition definition) {
		return definition.getDataType() == null ? null : definition.getDataType().getType();
	}

	private static void requireCategory(AttributeDefinition definition, String expected) {
		String category = categoryOf(definition);
		if (!expected.equals(category)
				|| ReqIFConst.ENUMERATION.equals(expected) && !(definition.getDataType() instanceof DatatypeEnumeration)) {
			throw new IllegalArgumentException("Attribute " + definition.getID() + " is of kind " + category
					+ ", but " + expected + " was expected");
		}
	}
}
