package de.uni_stuttgart.ils.reqif4j.attributes;

public class AttributeValue {
	
	
	private String name;
	protected Object value;
	private AttributeDefinition type;
	private String sourceElementName;
	
	
	
	
	public String getName() {
		return this.name;
	}
	
	public Object getValue() {
		return this.value;
	}
	
	public AttributeDefinition getAttributeDefinitionType() {
		return this.type;
	}
	
	public String getDatatype() {
		return this.type.getDataType().getType();
	}

	/**
	 * @return the ATTRIBUTE-VALUE-* element name this value was read from, or
	 *         null when it was not created from a document. Needed to write back
	 *         values of datatype kinds the parser does not model explicitly.
	 */
	public String getSourceElementName() {
		return this.sourceElementName;
	}

	public AttributeValue setSourceElementName(String sourceElementName) {
		this.sourceElementName = sourceElementName;
		return this;
	}
	
	
	
	
	public AttributeValue(String value, AttributeDefinition type) {
		
		this.name = type.getName();
		this.value = value;
		this.type = type;
	}
	
	public AttributeValue(AttributeDefinition type) {
		
		this.name = type.getName();
		this.type = type;
	}

}
