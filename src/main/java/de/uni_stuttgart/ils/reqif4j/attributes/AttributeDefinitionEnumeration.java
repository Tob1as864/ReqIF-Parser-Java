package de.uni_stuttgart.ils.reqif4j.attributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class AttributeDefinitionEnumeration extends AttributeDefinition {

	private final List<String> defaultValueRefs = new ArrayList<String>();
	private final List<String> defaultValues = new ArrayList<String>();
	private final boolean multiValued;

	/**
	 * Creates an enumeration attribute definition from plain values, for
	 * documents that are generated instead of parsed.
	 *
	 * @param defaultValueRefs IDENTIFIERs of the default enum values
	 */
	public AttributeDefinitionEnumeration(String id, String name, Datatype type, boolean multiValued,
			List<String> defaultValueRefs) {
		super(id, name, type, null);

		this.multiValued = multiValued;
		if (defaultValueRefs != null) {
			for (String ref : defaultValueRefs) {
				this.defaultValueRefs.add(ref);
				this.defaultValues.add(resolveName(ref));
			}
		}
	}

	public AttributeDefinitionEnumeration(Node attributeDefinition, Map<String, Datatype> dataTypes) {
		super(attributeDefinition, dataTypes);

		this.multiValued = "true".equalsIgnoreCase(XmlUtils.attribute(attributeDefinition, ReqIFConst.MULTI_VALUED));

		// Enumeration defaults are ENUM-VALUE-REF elements, not a THE-VALUE
		// attribute, so the base class cannot read them.
		Element defaultValue = XmlUtils.firstChildElementByLocalName(attributeDefinition, ReqIFConst.DEFAULT_VALUE);
		if (defaultValue != null) {
			for (Element ref : XmlUtils.descendantsByLocalName(defaultValue, ReqIFConst.ENUM_VALUE_REF)) {
				String refID = ref.getTextContent().trim();
				this.defaultValueRefs.add(refID);
				this.defaultValues.add(resolveName(refID));
			}
		}
	}

	private String resolveName(String enumValueID) {
		if (getDataType() instanceof DatatypeEnumeration) {
			String name = ((DatatypeEnumeration) getDataType()).getEnumValueName(enumValueID);
			if (name != null) {
				return name;
			}
		}
		return enumValueID;
	}

	/**
	 * @return true if this attribute may carry more than one enum value
	 *         (MULTI-VALUED="true")
	 */
	public boolean isMultiValued() {
		return this.multiValued;
	}

	/**
	 * @return resolved names of the default enum values (empty if none)
	 */
	public List<String> getDefaultValues() {
		return Collections.unmodifiableList(this.defaultValues);
	}

	/**
	 * @return IDENTIFIERs of the default enum values (empty if none)
	 */
	public List<String> getDefaultValueRefs() {
		return Collections.unmodifiableList(this.defaultValueRefs);
	}

	/**
	 * @return the default enum value names joined with ", ", or null if the
	 *         definition declares no default
	 */
	@Override
	public String getDefaultValue() {
		return this.defaultValues.isEmpty() ? null : String.join(", ", this.defaultValues);
	}

}
