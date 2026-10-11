package de.uni_stuttgart.ils.reqif4j.attributes;

public class AttributeValueBoolean extends AttributeValue {
	
	
	public AttributeValueBoolean(String value, AttributeDefinition type) {
		super(value, type);
		
		// xsd:boolean: "true" or "1" (lower case "true" only, but older versions
		// accepted any case)
		String trimmed = value == null ? "" : value.trim();
		if(trimmed.equalsIgnoreCase("true") || trimmed.equals("1")) {
			this.value = true;
		}else{
			this.value = false;
		}
	}
	
	@Override
	public Object getValue() {
		return (boolean)this.value;
	}
	
}
