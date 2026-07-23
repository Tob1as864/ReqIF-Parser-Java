package de.uni_stuttgart.ils.reqif4j.attributes;

public class AttributeValueInteger extends AttributeValue {
	
	
	public AttributeValueInteger(String value, AttributeDefinition type) {
		super(value, type);
		// Missing THE-VALUE reaches this constructor as null or "", both of
		// which must not crash the parser.
		if(value == null || value.isBlank()){
			this.value = 0;
		}else {
			this.value = Integer.parseInt(value.trim());
		}
	}

	@Override
	public Object getValue() {
		return (int)this.value;
	}
	
}
