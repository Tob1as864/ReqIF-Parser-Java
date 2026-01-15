package de.uni_stuttgart.ils.reqif4j.specification;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;

public class ExceptionSpecObject extends RuntimeException {

    public ExceptionSpecObject(String message, AttributeDefinition attributeDefinition) {
        super(message + "Attribute Definition:\nID: " + attributeDefinition.getID() + "Name: " + attributeDefinition.getName() + "Type: " + attributeDefinition.getDataType());
    }
}
