package de.uni_stuttgart.ils.reqif4j.reqif;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.uni_stuttgart.ils.reqif4j.datatypes.*;
import de.uni_stuttgart.ils.reqif4j.specification.*;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ReqIFCoreContent {


    private Map<String, Datatype> dataTypes = new LinkedHashMap<String, Datatype>();
    private Map<String, SpecType> specTypes = new LinkedHashMap<String, SpecType>();
    private Map<String, SpecObject> specObjects = new LinkedHashMap<String, SpecObject>();
    private Map<String, SpecRelation> specRelation = new LinkedHashMap<>();
    private Map<String, Specification> specifications = new LinkedHashMap<String, Specification>();
    private Map<String, RelationGroup> relationGroups = new LinkedHashMap<String, RelationGroup>();


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

        if (typeClassifier == null) {
            typeClassifier = TypeClassifier.defaultClassifier();
        }


        // Every element is matched by local name, so documents that put the
        // ReqIF elements into a prefixed namespace are read as well.
        Element datatypesElement = XmlUtils.firstDescendantByLocalName(coreContent, ReqIFConst.DATATYPES);
        if (datatypesElement != null) {

            for (Element dataType : XmlUtils.childElements(datatypesElement)) {

                String dataTypeNodeName = XmlUtils.localName(dataType);
                {

                    String dataTypeID = dataType.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
                    String dataTypeName = dataType.getAttributes().getNamedItem(ReqIFConst.LONG_NAME).getTextContent();
                    String dataTypeAlternativeID = XmlUtils.alternativeID(dataType);

                    switch (dataTypeNodeName.substring(dataTypeNodeName.lastIndexOf("-") + 1)) {

                        case ReqIFConst.BOOLEAN:
                            this.dataTypes.put(dataTypeID, new DatatypeBoolean(dataTypeID, dataTypeName));
                            break;

                        case ReqIFConst.INTEGER:
                            // MIN/MAX are optional in ReqIF
                            String min = XmlUtils.attribute(dataType, ReqIFConst.MIN);
                            String max = XmlUtils.attribute(dataType, ReqIFConst.MAX);
                            this.dataTypes.put(dataTypeID, new DatatypeInteger(dataTypeID, dataTypeName, min, max));
                            break;

                        case ReqIFConst.STRING:
                            // MAX-LENGTH is optional in ReqIF
                            String maxLength = XmlUtils.attribute(dataType, ReqIFConst.MAX_LENGTH);
                            this.dataTypes.put(dataTypeID, new DatatypeString(dataTypeID, dataTypeName, maxLength));
                            break;

                        case ReqIFConst.ENUMERATION:
                            Node enumeration = dataType;
                            this.dataTypes.put(dataTypeID, new DatatypeEnumeration(dataTypeID, dataTypeName, enumeration));
                            break;

                        case ReqIFConst.XHTML:
                            this.dataTypes.put(dataTypeID, new DatatypeXHTML(dataTypeID, dataTypeName));
                            break;

                        case ReqIFConst.DATE:
                            this.dataTypes.put(dataTypeID, new DatatypeDate(dataTypeID, dataTypeName));
                            break;

                        case ReqIFConst.DOUBLE, ReqIFConst.REAL:
                            this.dataTypes.put(dataTypeID, new DatatypeDouble(dataTypeID, dataTypeName));
                            break;

                        default:
                            // Register unknown datatypes under their ID (formerly the
                            // key was null, making them unresolvable).
                            this.dataTypes.put(dataTypeID, new Datatype(dataTypeID, dataTypeName, ReqIFConst.UNDEFINED, dataTypeNodeName));
                            break;
                    }
                    if (this.dataTypes.get(dataTypeID) != null) {
                        this.dataTypes.get(dataTypeID).setAlternativeID(dataTypeAlternativeID);
                    }
                }
            }
        }


        Element specTypesElement = XmlUtils.firstDescendantByLocalName(coreContent, ReqIFConst.SPEC_TYPES);
        if (specTypesElement != null) {

            for (Element specType : XmlUtils.childElements(specTypesElement)) {

                String specTypeNodeName = XmlUtils.localName(specType);
                {

                    String specTypeID = specType.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();

                    switch (specTypeNodeName) {

                        case ReqIFConst.SPECIFICATION_TYPE:
                            this.specTypes.put(specTypeID, new SpecificationType(specType, this.dataTypes));
                            break;

                        case ReqIFConst.SPEC_OBJECT_TYPE:
                            this.specTypes.put(specTypeID, new SpecObjectType(specType, this.dataTypes));
                            break;

                        case ReqIFConst.SPEC_RELATION_TYPE:
                            this.specTypes.put(specTypeID, new SpecRelationType(specType, this.dataTypes));
                            break;

                        case ReqIFConst.RELATION_GROUP_TYPE:
                            this.specTypes.put(specTypeID, new RelationGroupType(specType, this.dataTypes));
                            break;

                        default:
                            this.specTypes.put(specTypeID, new SpecType(specType, this.dataTypes));
                            break;
                    }
                }
            }
        }


        for (Element specObj : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.SPEC_OBJECT)) {

            String specObjID = XmlUtils.attribute(specObj, ReqIFConst.IDENTIFIER);
            Element typeRef = XmlUtils.firstDescendantByLocalName(specObj, ReqIFConst.SPEC_OBJECT_TYPE_REF);
            String specObjTypeRef = typeRef == null ? null : typeRef.getTextContent().trim();

            this.specObjects.put(specObjID, new SpecObject(specObj, this.specTypes.get(specObjTypeRef), typeClassifier));
        }


        for (Element specRelation : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.SPEC_RELATION)) {

            String specRelID = XmlUtils.attribute(specRelation, ReqIFConst.IDENTIFIER);
            Element typeRef = XmlUtils.firstDescendantByLocalName(specRelation, ReqIFConst.SPEC_RELATION_TYPE_REF);
            String specRelTypeRef = typeRef == null ? null : typeRef.getTextContent().trim();

            this.specRelation.put(specRelID, new SpecRelation(specRelation, this.specTypes.get(specRelTypeRef)));
        }


        for (Element relationGroup : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.RELATION_GROUP)) {
            RelationGroup group = new RelationGroup(relationGroup);
            this.relationGroups.put(group.getID(), group);
        }


        for (Element specification : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.SPECIFICATION)) {

            String specID = XmlUtils.attribute(specification, ReqIFConst.IDENTIFIER);
            Element typeRef = XmlUtils.firstDescendantByLocalName(specification, ReqIFConst.SPEC_TYPE_REF);
            String specTypeRef = typeRef == null ? null : typeRef.getTextContent().trim();

            this.specifications.put(specID, new Specification(specification, this.specTypes.get(specTypeRef), this.specObjects));
        }


    }

}
