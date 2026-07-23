package de.uni_stuttgart.ils.reqif4j.attributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Value of an enumeration attribute. ReqIF enumeration attributes may be
 * multi-valued (multiselect), so the value is a list of enum value names.
 *
 * {@link #getValue()} returns all selected names joined with {@code ", "} for
 * backwards compatibility with consumers that expect a single string.
 */
public class AttributeValueEnumeration extends AttributeValue {

	private final List<String> values;
	private final List<String> valueRefs;

	/**
	 * @param values    resolved LONG-NAMEs of all selected enum values
	 * @param valueRefs IDENTIFIERs of all selected enum values
	 */
	public AttributeValueEnumeration(List<String> values, List<String> valueRefs, AttributeDefinition type) {
		super(type);

		this.values = values == null ? new ArrayList<String>() : new ArrayList<String>(values);
		this.valueRefs = valueRefs == null ? new ArrayList<String>() : new ArrayList<String>(valueRefs);
		this.value = String.join(", ", this.values);
	}

	/**
	 * Single-value convenience constructor.
	 */
	public AttributeValueEnumeration(String value, AttributeDefinition type) {
		super(value, type);

		this.values = new ArrayList<String>();
		if (value != null && !value.isEmpty()) {
			this.values.add(value);
		}
		this.valueRefs = new ArrayList<String>();
		this.value = String.join(", ", this.values);
	}

	/**
	 * @return the resolved names of all selected enum values (empty list if the
	 *         attribute has no value)
	 */
	public List<String> getValues() {
		return Collections.unmodifiableList(this.values);
	}

	/**
	 * @return the IDENTIFIERs of all selected enum values
	 */
	public List<String> getValueRefs() {
		return Collections.unmodifiableList(this.valueRefs);
	}

	/**
	 * @return all selected enum value names joined with ", " (never null)
	 */
	@Override
	public Object getValue() {
		return String.join(", ", this.values);
	}

}
