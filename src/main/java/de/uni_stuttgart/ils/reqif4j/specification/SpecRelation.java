package de.uni_stuttgart.ils.reqif4j.specification;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class SpecRelation extends SpecObject{
    private String sourceObjID;
    private String targetObjID;

    public SpecRelation(Node specRelation, SpecType specType) {
        super(specRelation);
        this.specType = specType;
        Element eSpecRelation = (Element)specRelation;
        // Get target and source node
        Element sourceNode = (Element) eSpecRelation.getElementsByTagName(ReqIFConst.SOURCE).item(0);
        Element targetNode = (Element) eSpecRelation.getElementsByTagName(ReqIFConst.TARGET).item(0);
        sourceObjID = sourceNode.getElementsByTagName(ReqIFConst.SPEC_OBJECT_REF).item(0).getTextContent();
        targetObjID = targetNode.getElementsByTagName(ReqIFConst.SPEC_OBJECT_REF).item(0).getTextContent();
        // Get relationship type
        Element typeNode = (Element) eSpecRelation.getElementsByTagName(ReqIFConst.TYPE).item(0);
        this.type = typeNode.getElementsByTagName(ReqIFConst.SPEC_RELATION_TYPE_REF).item(0).getTextContent();
    }

    public String getSourceObjID() {
        return sourceObjID;
    }

    public String getTargetObjID() {
        return targetObjID;
    }
}
