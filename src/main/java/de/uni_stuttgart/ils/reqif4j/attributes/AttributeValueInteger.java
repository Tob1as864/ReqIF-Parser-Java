package de.uni_stuttgart.ils.reqif4j.attributes;

public class AttributeValueInteger extends AttributeValue {
	
	
	public AttributeValueInteger(String value, AttributeDefinition type) {
		super(value, type);
		if(value == null){
			this.value = 0;
		}else {
			this.value = Integer.parseInt(value);
		}
	}

	@Override
	public Object getValue() {
		return (int)this.value;
	}
	
}
