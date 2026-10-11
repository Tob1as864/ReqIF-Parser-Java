package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;

/**
 * The attribute values of a spec object, spec relation or specification.
 *
 * Two views are kept apart:
 *
 * <ul>
 * <li><em>explicit</em> values are the ones the element carries itself -
 * exactly what the document contains and what is written back - keyed by the
 * IDENTIFIER of their attribute definition, in document order;</li>
 * <li><em>effective</em> values additionally fill in the default value of
 * every attribute definition the element has no value for. They are keyed by
 * LONG-NAME for convenient lookup and only ever derived, never written.</li>
 * </ul>
 */
final class AttributeValues {

	private final SpecType specType;
	private final Map<String, AttributeValue> explicit = new LinkedHashMap<String, AttributeValue>();
	private final Map<String, AttributeValue> explicitView = Collections.unmodifiableMap(this.explicit);

	AttributeValues(SpecType specType) {
		this.specType = specType;
	}

	SpecType specType() {
		return this.specType;
	}

	/**
	 * Adds an explicit value; a second value for the same definition replaces
	 * the first.
	 */
	void put(AttributeValue value) {
		this.explicit.put(value.getAttributeDefinitionType().getID(), value);
	}

	AttributeValue remove(String attributeDefinitionID) {
		return this.explicit.remove(attributeDefinitionID);
	}

	/**
	 * @return the explicit values by attribute definition IDENTIFIER, in
	 *         document order (read-only)
	 */
	Map<String, AttributeValue> explicit() {
		return this.explicitView;
	}

	/**
	 * @return the effective values by LONG-NAME (IDENTIFIER for definitions
	 *         without one), in spec type order: explicit values plus the
	 *         defaults of all other definitions. An explicit value always wins
	 *         over a default of the same name. Derived on every call, so it
	 *         follows later changes of the values or the spec type.
	 */
	Map<String, AttributeValue> effectiveByName() {

		Map<String, AttributeValue> effective = new LinkedHashMap<String, AttributeValue>();
		Map<String, AttributeValue> undeclared = new LinkedHashMap<String, AttributeValue>(this.explicit);
		Set<String> explicitNames = new HashSet<String>();
		for (AttributeValue value : this.explicit.values()) {
			explicitNames.add(AttributeValueReader.key(value.getAttributeDefinitionType()));
		}

		if (this.specType != null) {
			for (AttributeDefinition definition : this.specType.getAttributeDefinitions().values()) {
				String name = AttributeValueReader.key(definition);
				AttributeValue value = undeclared.remove(definition.getID());
				if (value == null && !explicitNames.contains(name)) {
					value = AttributeValueReader.defaultValue(definition);
				}
				if (value != null) {
					effective.putIfAbsent(name, value);
				}
			}
		}
		for (AttributeValue value : undeclared.values()) {
			effective.putIfAbsent(AttributeValueReader.key(value.getAttributeDefinitionType()), value);
		}
		return Collections.unmodifiableMap(effective);
	}
}
