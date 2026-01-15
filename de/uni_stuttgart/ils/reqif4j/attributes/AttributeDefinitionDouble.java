package de.uni_stuttgart.ils.reqif4j.attributes;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import org.w3c.dom.Node;

import java.util.Map;

public class AttributeDefinitionDouble extends AttributeDefinition {

    public AttributeDefinitionDouble(Node attributeDefinition, Map<String, Datatype> dataTypes) {
        super(attributeDefinition, dataTypes);


    }
}
