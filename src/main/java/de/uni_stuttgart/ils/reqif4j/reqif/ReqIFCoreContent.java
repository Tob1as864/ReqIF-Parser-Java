package de.uni_stuttgart.ils.reqif4j.reqif;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.uni_stuttgart.ils.reqif4j.datatypes.*;
import de.uni_stuttgart.ils.reqif4j.specification.*;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import org.w3c.dom.Element;

public class ReqIFCoreContent {


    private Map<String, Datatype> dataTypes = new LinkedHashMap<String, Datatype>();
    private Map<String, SpecType> specTypes = new LinkedHashMap<String, SpecType>();
    private Map<String, SpecObject> specObjects = new LinkedHashMap<String, SpecObject>();
    private Map<String, SpecRelation> specRelation = new LinkedHashMap<>();
    private Map<String, Specification> specifications = new LinkedHashMap<String, Specification>();
    private Map<String, RelationGroup> relationGroups = new LinkedHashMap<String, RelationGroup>();
    private ParseIssues parseIssues = ParseIssues.collecting();


    public Map<String, Datatype> getDatatypes() {
        return this.dataTypes;
    }

    public Datatype getDatatype(String id) {
        return this.dataTypes.get(id);
    }

    public Map<String, SpecType> getSpecTypes() {
        return this.specTypes;
    }

    public SpecType getSpecType(String id) {
        return this.specTypes.get(id);
    }

    public Map<String, SpecObject> getSpecObjects() {
        return this.specObjects;
    }

    public SpecObject getSpecObject(String id) {
        return this.specObjects.get(id);
    }

    public Map<String, SpecRelation> getSpecRelation() {
        return this.specRelation;
    }

    public SpecRelation getSpecRelation(String id) {
        return this.specRelation.get(id);
    }

    public Map<String, Specification> getSpecifications() {
        return this.specifications;
    }

    public Specification getSpecification(String id) {
        return this.specifications.get(id);
    }

    public Map<String, RelationGroup> getRelationGroups() {
        return this.relationGroups;
    }

    public RelationGroup getRelationGroup(String id) {
        return this.relationGroups.get(id);
    }

    public ReqIFCoreContent addRelationGroup(RelationGroup relationGroup) {
        this.relationGroups.put(relationGroup.getID(), relationGroup);
        return this;
    }

    public List<Specification> getSpecificationsList() {
        List<Specification> specifications = new ArrayList<Specification>();
        for (Specification specification : this.specifications.values()) {
            specifications.add(specification);
        }

        return specifications;
    }

    public List<SpecHierarchy> getOrderedSpecHierarchyList() {

        List<SpecHierarchy> orderedSpecHierarchies = new ArrayList<SpecHierarchy>();
        for (Specification specification : this.specifications.values()) {
            orderedSpecHierarchies.addAll(specification.getAllSpecHierarchies());
        }
        return orderedSpecHierarchies;
    }


    /**
     * Creates empty content, to be filled for documents that are generated
     * instead of parsed.
     */
    public ReqIFCoreContent() {
    }

    public ReqIFCoreContent addDatatype(Datatype datatype) {
        this.dataTypes.put(datatype.getID(), datatype);
        return this;
    }

    public ReqIFCoreContent addSpecType(SpecType specType) {
        this.specTypes.put(specType.getID(), specType);
        return this;
    }

    public ReqIFCoreContent addSpecObject(SpecObject specObject) {
        this.specObjects.put(specObject.getID(), specObject);
        return this;
    }

    public ReqIFCoreContent addSpecRelation(SpecRelation relation) {
        this.specRelation.put(relation.getID(), relation);
        return this;
    }

    public ReqIFCoreContent addSpecification(Specification specification) {
        this.specifications.put(specification.getID(), specification);
        return this;
    }


    public ReqIFCoreContent(Element coreContent) {
        this(coreContent, TypeClassifier.defaultClassifier());
    }

    public ReqIFCoreContent(Element coreContent, TypeClassifier typeClassifier) {
        this(coreContent, typeClassifier, ParseIssues.collecting());
    }

    /**
     * Reads the content of a document. Elements that cannot be read are
     * skipped and, like every other problem found, reported to {@code issues}
     * instead of making the whole document unreadable.
     */
    public ReqIFCoreContent(Element coreContent, TypeClassifier typeClassifier, ParseIssues issues) {

        TypeClassifier classifier = typeClassifier == null ? TypeClassifier.defaultClassifier() : typeClassifier;
        this.parseIssues = issues;

        // Every element is matched by local name, so documents that put the
        // ReqIF elements into a prefixed namespace are read as well.
        Element datatypesElement = XmlUtils.firstDescendantByLocalName(coreContent, ReqIFConst.DATATYPES);
        for (Element dataType : XmlUtils.childElements(datatypesElement)) {
            issues.put(this.dataTypes, issues.readOrSkip("datatype", () -> readDatatype(dataType, issues)), "Datatype");
        }

        Element specTypesElement = XmlUtils.firstDescendantByLocalName(coreContent, ReqIFConst.SPEC_TYPES);
        for (Element specType : XmlUtils.childElements(specTypesElement)) {
            issues.put(this.specTypes, issues.readOrSkip("spec type", () -> readSpecType(specType, issues)), "Spec type");
        }

        for (Element specObj : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.SPEC_OBJECT)) {
            SpecType type = resolveType(specObj, ReqIFConst.SPEC_OBJECT_TYPE_REF, issues);
            issues.put(this.specObjects, issues.readOrSkip("spec object",
                    () -> new SpecObject(specObj, type, classifier, issues)), "Spec object");
        }

        for (Element specRelation : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.SPEC_RELATION)) {
            SpecType type = resolveType(specRelation, ReqIFConst.SPEC_RELATION_TYPE_REF, issues);
            issues.put(this.specRelation, issues.readOrSkip("spec relation",
                    () -> new SpecRelation(specRelation, type, issues)), "Spec relation");
        }

        for (Element relationGroup : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.RELATION_GROUP)) {
            issues.put(this.relationGroups, issues.readOrSkip("relation group",
                    () -> new RelationGroup(relationGroup)), "Relation group");
        }

        for (Element specification : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.SPECIFICATION)) {
            SpecType type = resolveType(specification, ReqIFConst.SPEC_TYPE_REF, issues);
            issues.put(this.specifications, issues.readOrSkip("specification",
                    () -> new Specification(specification, type, this.specObjects, issues)), "Specification");
        }
    }

    /**
     * @return the problems found while reading this content; empty for content
     *         that was not read from a document
     */
    public java.util.List<ParseIssue> getParseIssues() {
        return this.parseIssues.getIssues();
    }


    private static Datatype readDatatype(Element dataType, ParseIssues issues) {

        String dataTypeNodeName = XmlUtils.localName(dataType);
        String dataTypeID = XmlUtils.attribute(dataType, ReqIFConst.IDENTIFIER);
        // LONG-NAME is optional in ReqIF
        String dataTypeName = XmlUtils.attribute(dataType, ReqIFConst.LONG_NAME);

        Datatype datatype;
        switch (dataTypeNodeName.substring(dataTypeNodeName.lastIndexOf("-") + 1)) {

            case ReqIFConst.BOOLEAN:
                datatype = new DatatypeBoolean(dataTypeID, dataTypeName);
                break;

            case ReqIFConst.INTEGER:
                // MIN/MAX are optional in ReqIF
                datatype = new DatatypeInteger(dataTypeID, dataTypeName,
                        XmlUtils.attribute(dataType, ReqIFConst.MIN), XmlUtils.attribute(dataType, ReqIFConst.MAX));
                break;

            case ReqIFConst.STRING:
                // MAX-LENGTH is optional in ReqIF
                datatype = new DatatypeString(dataTypeID, dataTypeName, XmlUtils.attribute(dataType, ReqIFConst.MAX_LENGTH));
                break;

            case ReqIFConst.ENUMERATION:
                datatype = new DatatypeEnumeration(dataTypeID, dataTypeName, dataType, issues);
                break;

            case ReqIFConst.XHTML:
                datatype = new DatatypeXHTML(dataTypeID, dataTypeName);
                break;

            case ReqIFConst.DATE:
                datatype = new DatatypeDate(dataTypeID, dataTypeName);
                break;

            case ReqIFConst.DOUBLE, ReqIFConst.REAL:
                datatype = new DatatypeDouble(dataTypeID, dataTypeName,
                        XmlUtils.attribute(dataType, ReqIFConst.ACCURACY),
                        XmlUtils.attribute(dataType, ReqIFConst.MIN),
                        XmlUtils.attribute(dataType, ReqIFConst.MAX));
                break;

            default:
                // kinds the parser does not model are kept under their element name
                datatype = new Datatype(dataTypeID, dataTypeName, ReqIFConst.UNDEFINED, dataTypeNodeName);
                break;
        }
        // IDENTIFIER (required, checked here), DESC, LAST-CHANGE and ALTERNATIVE-ID
        datatype.readIdentifiable(dataType);
        return datatype;
    }

    private SpecType readSpecType(Element specType, ParseIssues issues) {

        switch (XmlUtils.localName(specType)) {
            case ReqIFConst.SPECIFICATION_TYPE:		return new SpecificationType(specType, this.dataTypes, issues);
            case ReqIFConst.SPEC_OBJECT_TYPE:		return new SpecObjectType(specType, this.dataTypes, issues);
            case ReqIFConst.SPEC_RELATION_TYPE:		return new SpecRelationType(specType, this.dataTypes, issues);
            case ReqIFConst.RELATION_GROUP_TYPE:	return new RelationGroupType(specType, this.dataTypes, issues);
            default:								return new SpecType(specType, this.dataTypes, issues);
        }
    }

    /**
     * @return the spec type the element's TYPE refers to, or null if the
     *         reference cannot be resolved (the element reports that)
     */
    private SpecType resolveType(Element element, String refName, ParseIssues issues) {

        Element typeRef = XmlUtils.firstDescendantByLocalName(element, refName);
        return typeRef == null ? null : this.specTypes.get(typeRef.getTextContent().trim());
    }

}
