package de.uni_stuttgart.ils.reqif4j.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueBoolean;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueDate;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueDouble;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueInteger;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueString;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;

/**
 * Collects the attribute values of a spec object, relation or specification.
 * The attribute definition must belong to the given spec type, and enum value
 * references must exist; otherwise a {@link ReqIFBuildException} is thrown.
 */
public class ValuesBuilder {

	private final SpecType specType;
	private final List<AttributeValue> values = new ArrayList<AttributeValue>();

	ValuesBuilder(SpecType specType) {
		this.specType = specType;
	}


	/**
	 * Sets a scalar value; the value class is chosen from the attribute's
	 * datatype.
	 */
	public ValuesBuilder set(String attributeDefinitionID, Object value) {

		AttributeDefinition definition = requireDefinition(attributeDefinitionID);
		String category = definition.getDataType() == null ? null : definition.getDataType().getType();
		String text = value == null ? null : String.valueOf(value);

		if (ReqIFConst.ENUMERATION.equals(category)) {
			throw new ReqIFBuildException("Use setEnum(...) for the enumeration attribute " + attributeDefinitionID);
		}
		if (ReqIFConst.XHTML.equals(category)) {
			throw new ReqIFBuildException("Use setXhtml(...) for the XHTML attribute " + attributeDefinitionID);
		}

		this.values.add(scalarValue(category, text, definition));
		return this;
	}

	/**
	 * Sets an XHTML value from markup, with or without a surrounding div.
	 */
	public ValuesBuilder setXhtml(String attributeDefinitionID, String markup) {

		AttributeDefinition definition = requireDefinition(attributeDefinitionID);
		requireCategory(definition, ReqIFConst.XHTML, attributeDefinitionID);

		this.values.add(new AttributeValueXHTML(markup, definition));
		return this;
	}

	/**
	 * Sets one or more enum values by their IDENTIFIER (multiselect).
	 */
	public ValuesBuilder setEnum(String attributeDefinitionID, String... enumValueIDs) {
		return setEnum(attributeDefinitionID, Arrays.asList(enumValueIDs));
	}

	public ValuesBuilder setEnum(String attributeDefinitionID, List<String> enumValueIDs) {

		AttributeDefinition definition = requireDefinition(attributeDefinitionID);
		requireCategory(definition, ReqIFConst.ENUMERATION, attributeDefinitionID);

		DatatypeEnumeration datatype = (DatatypeEnumeration) definition.getDataType();
		List<String> names = new ArrayList<String>();
		for (String enumValueID : enumValueIDs) {
			String name = datatype.getEnumValueName(enumValueID);
			if (name == null) {
				throw new ReqIFBuildException("Unknown enum value " + enumValueID
						+ " for attribute " + attributeDefinitionID);
			}
			names.add(name);
		}

		this.values.add(new AttributeValueEnumeration(names, new ArrayList<String>(enumValueIDs), definition));
		return this;
	}


	Collection<AttributeValue> build() {
		return this.values;
	}


	private AttributeValue scalarValue(String category, String text, AttributeDefinition definition) {

		if (category == null) {
			return new AttributeValue(text, definition);
		}
		switch (category) {
			case ReqIFConst.BOOLEAN:	return new AttributeValueBoolean(text, definition);
			case ReqIFConst.INTEGER:	return new AttributeValueInteger(text, definition);
			case ReqIFConst.STRING:		return new AttributeValueString(text, definition);
			case ReqIFConst.DATE:		return new AttributeValueDate(text, definition);
			case ReqIFConst.DOUBLE:
			case ReqIFConst.REAL:		return new AttributeValueDouble(text, definition);
			default:					return new AttributeValue(text, definition);
		}
	}

	private AttributeDefinition requireDefinition(String attributeDefinitionID) {

		AttributeDefinition definition = this.specType.getAttributeDefinition(attributeDefinitionID);
		if (definition == null) {
			throw new ReqIFBuildException("Spec type " + this.specType.getID()
					+ " has no attribute definition " + attributeDefinitionID);
		}
		return definition;
	}

	private static void requireCategory(AttributeDefinition definition, String expected, String attributeDefinitionID) {

		String category = definition.getDataType() == null ? null : definition.getDataType().getType();
		if (!expected.equals(category)) {
			throw new ReqIFBuildException("Attribute " + attributeDefinitionID + " is of kind " + category
					+ ", but " + expected + " was expected");
		}
	}
}
