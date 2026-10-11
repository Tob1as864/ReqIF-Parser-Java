package de.uni_stuttgart.ils.reqif4j.attributes;

import de.uni_stuttgart.ils.reqif4j.util.XsdDouble;

public class AttributeValueDouble extends AttributeValue{

    public AttributeValueDouble(String value, AttributeDefinition type) {
        super(value, type);
        // Missing THE-VALUE reaches this constructor as null or "", both of
        // which must not crash the parser.
        if(value == null || value.isBlank()){
            this.value = 0.0;
        }else {
            // xsd:double notation: INF / -INF for infinity
            this.value = XsdDouble.parse(value);
        }
    }

    @Override
    public Object getValue() {
        return (double)this.value;
    }
}
