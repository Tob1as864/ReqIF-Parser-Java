package de.uni_stuttgart.ils.reqif4j.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueFactory;
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
		return add(() -> AttributeValueFactory.of(definition, value));
	}

	/**
	 * Sets an XHTML value from markup, with or without a surrounding div.
	 */
	public ValuesBuilder setXhtml(String attributeDefinitionID, String markup) {
		AttributeDefinition definition = requireDefinition(attributeDefinitionID);
		return add(() -> AttributeValueFactory.xhtml(definition, markup));
	}

	/**
	 * Sets an XHTML value that is a simplified version of the complete
	 * content, e.g. the preview image of an embedded OLE object; the value is
	 * marked IS-SIMPLIFIED.
	 *
	 * @param originalMarkup the complete content (THE-ORIGINAL-VALUE)
	 */
	public ValuesBuilder setXhtml(String attributeDefinitionID, String markup, String originalMarkup) {
		AttributeDefinition definition = requireDefinition(attributeDefinitionID);
		return add(() -> AttributeValueFactory.xhtml(definition, markup, originalMarkup));
	}

	/**
	 * Sets one or more enum values by their IDENTIFIER (multiselect).
	 */
	public ValuesBuilder setEnum(String attributeDefinitionID, String... enumValueIDs) {
		return setEnum(attributeDefinitionID, enumValueIDs == null ? null : Arrays.asList(enumValueIDs));
	}

	public ValuesBuilder setEnum(String attributeDefinitionID, List<String> enumValueIDs) {
		AttributeDefinition definition = requireDefinition(attributeDefinitionID);
		return add(() -> AttributeValueFactory.enumeration(definition, enumValueIDs));
	}


	private ValuesBuilder add(Supplier<AttributeValue> value) {
		try {
			this.values.add(value.get());
		} catch (IllegalArgumentException e) {
			throw new ReqIFBuildException(e.getMessage(), e);
		}
		return this;
	}

	Collection<AttributeValue> build() {
		return this.values;
	}


	private AttributeDefinition requireDefinition(String attributeDefinitionID) {

		AttributeDefinition definition = this.specType.getAttributeDefinition(attributeDefinitionID);
		if (definition == null) {
			throw new ReqIFBuildException("Spec type " + this.specType.getID()
					+ " has no attribute definition " + attributeDefinitionID);
		}
		return definition;
	}
}
