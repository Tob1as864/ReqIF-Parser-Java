package de.uni_stuttgart.ils.reqif4j.attributes;

public class AttributeValueDouble extends AttributeValue{

    public AttributeValueDouble(String value, AttributeDefinition type) {
        super(value, type);
        if(value == null){
            this.value = 0.0;
        }else {
            this.value = Double.parseDouble(value);
        }
    }

    @Override
    public Object getValue() {
        return (double)this.value;
    }
}
