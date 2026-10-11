package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.Map;

import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;

/**
 * An element that carries attribute values: spec objects, spec relations and
 * specifications, as in the ReqIF metamodel.
 *
 * Values are available in two views. {@link #getValues()} holds what the
 * element itself carries - exactly what the document contains and what is
 * written back. {@link #getAttributes()} adds the defaults of all attribute
 * definitions the element has no value for, keyed by LONG-NAME for
 * convenient lookup.
 */
public abstract class SpecElementWithAttributes extends Identifiable {

	private AttributeValues values = new AttributeValues(null);


	protected SpecElementWithAttributes(String id, String name) {
		super(id, name);
	}

	protected SpecElementWithAttributes(Node element) {
		super(element);
	}


	/**
	 * @return the values this element carries itself, by IDENTIFIER of their
	 *         attribute definition, in document order (read-only). These are
	 *         the values that are written; definition defaults are not
	 *         included.
	 */
	public Map<String, AttributeValue> getValues() {
		return this.values.explicit();
	}

	/**
	 * @return the value this element carries for the attribute definition, or
	 *         null if it has none (the definition's default then applies)
	 */
	public AttributeValue getValue(String attributeDefinitionID) {
		return this.values.explicit().get(attributeDefinitionID);
	}

	/**
	 * Sets a value of this element, replacing the one it has for the same
	 * attribute definition.
	 */
	public SpecElementWithAttributes setValue(AttributeValue value) {
		if (value == null || value.getAttributeDefinitionType() == null) {
			throw new IllegalArgumentException("A value needs an attribute definition");
		}
		this.values.put(value);
		valuesChanged();
		return this;
	}

	/**
	 * Removes the element's own value for the attribute definition, so its
	 * default applies again.
	 *
	 * @return the removed value, or null if the element had none
	 */
	public AttributeValue removeValue(String attributeDefinitionID) {
		AttributeValue removed = this.values.remove(attributeDefinitionID);
		valuesChanged();
		return removed;
	}

	/**
	 * @return the effective values by attribute LONG-NAME: the element's own
	 *         values plus the defaults of all other attribute definitions of
	 *         its type (read-only). A convenience view: LONG-NAMEs need not be
	 *         unique, so prefer {@link #getValues()} to see what the document
	 *         contains.
	 */
	public Map<String, AttributeValue> getAttributes() {
		return this.values.effectiveByName();
	}

	/**
	 * @return the effective value of the attribute with this LONG-NAME, or null
	 */
	public Object getAttribute(String attributeName) {
		AttributeValue attributeValue = getAttributes().get(attributeName);
		return attributeValue == null ? null : attributeValue.getValue();
	}


	/**
	 * Starts a new set of values for the given spec type, e.g. before reading
	 * them from a document.
	 */
	AttributeValues resetValues(SpecType specType) {
		this.values = new AttributeValues(specType);
		return this.values;
	}

	/**
	 * Called after a value was set or removed.
	 */
	protected void valuesChanged() {
	}
}
