package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.Collection;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues;
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

    /**
     * Creates a relation from plain values, for documents that are generated
     * instead of parsed.
     */
    public SpecRelation(String id, SpecType specType, String sourceObjID, String targetObjID,
            Collection<AttributeValue> attributeValues) {
        super(id, specType, attributeValues);

        this.sourceObjID = sourceObjID;
        this.targetObjID = targetObjID;
        this.relationTypeRef = specType == null ? null : specType.getID();
        // a relation has no content category
        this.type = ReqIFConst.UNDEFINED;
    }

    public SpecRelation(Node specRelation, SpecType specType) {
        this(specRelation, specType, ParseIssues.collecting());
    }

    /**
     * @param specType the resolved relation type; if null, the relation is
     *                 kept with its unresolved type reference, but its values
     *                 cannot be interpreted and are reported and skipped
     * @param issues   receives the problems found
     */
    public SpecRelation(Node specRelation, SpecType specType, ParseIssues issues) {
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

        readAttributeValues(specRelation, specType, issues);
    }

    /**
     * @return the IDENTIFIER of the relation type - also if the reference
     *         could not be resolved - or null if it has none
     */
    @Override
    public String getSpecTypeID() {
        return this.specType != null ? this.specType.getID() : this.relationTypeRef;
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

    // A relation has no content category, whatever its values.
    @Override
    protected void valuesChanged() {
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
