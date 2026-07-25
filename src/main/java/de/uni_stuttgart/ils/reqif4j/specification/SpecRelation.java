package de.uni_stuttgart.ils.reqif4j.specification;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A relation (link) between two spec objects.
 *
 * The inherited {@code type} field carries a <em>content</em> category
 * (REQ/SUB-REQ/HEADLINE/TEXT) which does not apply to a relation, so it is set
 * to {@link ReqIFConst#UNDEFINED}. The structural information "this is a
 * relation" is available via {@link #getSpecType()} ("SPEC-RELATION-TYPE"),
 * the relation's own type via {@link #getRelationTypeRef()} and
 * {@link #getRelationTypeName()}.
 */
public class SpecRelation extends SpecObject {

    private String sourceObjID;
    private String targetObjID;
    private String relationTypeRef;

    public SpecRelation(Node specRelation, SpecType specType) {
        super(specRelation);
        this.specType = specType;

        // Get target and source node
        Element sourceNode = XmlUtils.firstChildElementByLocalName(specRelation, ReqIFConst.SOURCE);
        Element targetNode = XmlUtils.firstChildElementByLocalName(specRelation, ReqIFConst.TARGET);
        this.sourceObjID = specObjectRef(sourceNode);
        this.targetObjID = specObjectRef(targetNode);

        // Get relationship type reference
        Element typeNode = XmlUtils.firstChildElementByLocalName(specRelation, ReqIFConst.TYPE);
        Element typeRef = XmlUtils.firstDescendantByLocalName(typeNode, ReqIFConst.SPEC_RELATION_TYPE_REF);
        this.relationTypeRef = typeRef == null ? null : typeRef.getTextContent().trim();

        // A relation has no content category; the relation type lives in
        // relationTypeRef, not in the inherited type field.
        this.type = ReqIFConst.UNDEFINED;

        if (specType != null) {
            readAttributeValues(specRelation, specType);
        }
    }

    public String getSourceObjID() {
        return this.sourceObjID;
    }

    public String getTargetObjID() {
        return this.targetObjID;
    }

    /**
     * @return the IDENTIFIER of the SPEC-RELATION-TYPE this relation refers to
     *         (SPEC-RELATION-TYPE-REF), or null if the document declares none
     */
    public String getRelationTypeRef() {
        return this.relationTypeRef;
    }

    /**
     * @return the LONG-NAME of the relation type (e.g. "satisfies"), or null if
     *         the reference could not be resolved
     */
    public String getRelationTypeName() {
        return this.specType == null ? null : this.specType.getName();
    }

    // A relation is none of the content categories.

    @Override
    public boolean isReq() {
        return false;
    }

    @Override
    public boolean isSubReq() {
        return false;
    }

    @Override
    public boolean isHeadline() {
        return false;
    }

    @Override
    public boolean isText() {
        return false;
    }

    private static String specObjectRef(Element sourceOrTarget) {
        Element ref = XmlUtils.firstDescendantByLocalName(sourceOrTarget, ReqIFConst.SPEC_OBJECT_REF);
        return ref == null ? null : ref.getTextContent().trim();
    }
}
