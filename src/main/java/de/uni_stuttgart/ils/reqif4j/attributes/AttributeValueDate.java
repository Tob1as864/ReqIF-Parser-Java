package de.uni_stuttgart.ils.reqif4j.attributes;

public class AttributeValueDate extends AttributeValue{
    public AttributeValueDate(String value, AttributeDefinition type) {
        super(value, type);


    }

    @Override
    public Object getValue() {
        return (String)this.value;
    }
}
