package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueFactory;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

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
		markModified();
		valuesChanged();
		return this;
	}

	/**
	 * Sets a scalar value - boolean, integer, real, string or date - of the
	 * attribute definition with this IDENTIFIER, from e.g. a Boolean, a
	 * Number or a String.
	 *
	 * @throws IllegalArgumentException if the element's spec type has no such
	 *         attribute definition, or it is an enumeration or XHTML one
	 */
	public SpecElementWithAttributes set(String attributeDefinitionID, Object value) {
		return setValue(AttributeValueFactory.of(definition(attributeDefinitionID), value));
	}

	/**
	 * Sets an XHTML value from markup, with or without a surrounding div.
	 */
	public SpecElementWithAttributes setXhtml(String attributeDefinitionID, String markup) {
		return setValue(AttributeValueFactory.xhtml(definition(attributeDefinitionID), markup));
	}

	/**
	 * Sets an XHTML value that is a simplified version of the complete
	 * content, e.g. the preview image of an embedded OLE object.
	 *
	 * @param originalMarkup the complete content (THE-ORIGINAL-VALUE)
	 */
	public SpecElementWithAttributes setXhtml(String attributeDefinitionID, String markup, String originalMarkup) {
		return setValue(AttributeValueFactory.xhtml(definition(attributeDefinitionID), markup, originalMarkup));
	}

	/**
	 * Selects enum values by their IDENTIFIER; none for an empty selection.
	 */
	public SpecElementWithAttributes setEnum(String attributeDefinitionID, String... enumValueIDs) {
		return setValue(AttributeValueFactory.enumeration(definition(attributeDefinitionID),
				enumValueIDs == null ? null : java.util.Arrays.asList(enumValueIDs)));
	}

	/**
	 * Removes the element's own value for the attribute definition, so its
	 * default applies again.
	 *
	 * @return the removed value, or null if the element had none
	 */
	public AttributeValue removeValue(String attributeDefinitionID) {
		AttributeValue removed = this.values.remove(attributeDefinitionID);
		if (removed != null) {
			markModified();
			valuesChanged();
		}
		return removed;
	}

	/**
	 * @return the element's spec type, or null if its type reference could
	 *         not be resolved
	 */
	public SpecType getResolvedSpecType() {
		return this.values.specType();
	}

	private AttributeDefinition definition(String attributeDefinitionID) {
		SpecType specType = this.values.specType();
		AttributeDefinition definition = specType == null ? null : specType.getAttributeDefinition(attributeDefinitionID);
		if (definition == null) {
			throw new IllegalArgumentException((specType == null ? "The type of " + getID() + " is unknown"
					: "Spec type " + specType.getID() + " has no attribute definition " + attributeDefinitionID));
		}
		return definition;
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

	/**
	 * Reads the element's values. Without a resolved spec type there are no
	 * attribute definitions to interpret them by; that is reported once,
	 * together with the number of values skipped.
	 *
	 * @param typeRef the unresolved type reference, for the report
	 */
	void readValues(Node element, SpecType specType, String typeRef, ParseIssues issues) {

		AttributeValues values = resetValues(specType);
		if (specType != null) {
			AttributeValueReader.read(element, specType, values, issues);
			return;
		}
		String problem = XmlUtils.localName(element)
				+ (typeRef == null ? " has no type reference" : " references type " + typeRef + ", which is not declared");
		int count = AttributeValueReader.readUninterpreted(element, problem, values, issues);
		issues.report(getID(), problem
				+ (count == 0 ? "" : "; its " + count + " value" + (count == 1 ? " is" : "s are") + " kept as written"));
	}

	/**
	 * @return the IDENTIFIER in TYPE/{refName}, or null
	 */
	static String typeRef(Node element, String refName) {
		Element type = XmlUtils.firstChildElementByLocalName(element, ReqIFConst.TYPE);
		Element ref = XmlUtils.firstChildElementByLocalName(type, refName);
		return ref == null ? null : ref.getTextContent().trim();
	}
}
