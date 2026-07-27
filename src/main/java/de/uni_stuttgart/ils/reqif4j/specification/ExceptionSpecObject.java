package de.uni_stuttgart.ils.reqif4j.specification;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;

public class ExceptionSpecObject extends RuntimeException {

    public ExceptionSpecObject(String message, AttributeDefinition attributeDefinition) {
        super(buildMessage(message, attributeDefinition));
    }

    private static String buildMessage(String message, AttributeDefinition attributeDefinition) {

        if (attributeDefinition == null) {
            return message;
        }
        return message
                + "Attribute Definition:\n"
                + "ID: " + attributeDefinition.getID() + "\n"
                + "Name: " + attributeDefinition.getName() + "\n"
                + "Type: " + (attributeDefinition.getDataType() == null
                        ? "<unresolved>"
                        : attributeDefinition.getDataType().getType());
    }
}
