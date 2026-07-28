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


        if (coreContent.getElementsByTagName("DATATYPES").item(0).hasChildNodes()) {

            NodeList dataTypes = coreContent.getElementsByTagName("DATATYPES").item(0).getChildNodes();
            for (int datatype = 0; datatype < dataTypes.getLength(); datatype++) {

                Node dataType = dataTypes.item(datatype);
                String dataTypeNodeName = dataType.getNodeName();
                if (!dataTypeNodeName.equals(ReqIFConst._TEXT)) {

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


        if (coreContent.getElementsByTagName(ReqIFConst.SPEC_TYPES).item(0).hasChildNodes()) {

            NodeList specTypes = coreContent.getElementsByTagName(ReqIFConst.SPEC_TYPES).item(0).getChildNodes();
            for (int spectype = 0; spectype < specTypes.getLength(); spectype++) {

                Node specType = specTypes.item(spectype);
                String specTypeNodeName = specType.getNodeName();
                if (!specTypeNodeName.equals(ReqIFConst._TEXT)) {

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


        if (coreContent.getElementsByTagName(ReqIFConst.SPEC_OBJECT).getLength() > 0) {

            NodeList specObjects = coreContent.getElementsByTagName(ReqIFConst.SPEC_OBJECT);
            for (int specobj = 0; specobj < specObjects.getLength(); specobj++) {

                Node specObj = specObjects.item(specobj);
                String specObjID = specObj.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
                String specObjTypeRef = ((Element) specObj).getElementsByTagName(ReqIFConst.SPEC_OBJECT_TYPE_REF).item(0).getTextContent();

                this.specObjects.put(specObjID, new SpecObject(specObj, this.specTypes.get(specObjTypeRef), typeClassifier));
            }
        }


        if (coreContent.getElementsByTagName(ReqIFConst.SPEC_RELATION).getLength() > 0) {
            NodeList specRelations = coreContent.getElementsByTagName(ReqIFConst.SPEC_RELATION);
            for (int specrelation = 0; specrelation < specRelations.getLength(); specrelation++) {

                Node specRelation = specRelations.item(specrelation);
                String specRelID = specRelation.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
                String specRelTypeRef = ((Element) specRelation).getElementsByTagName(ReqIFConst.SPEC_RELATION_TYPE_REF).item(0).getTextContent();


                this.specRelation.put(specRelID, new SpecRelation(specRelation, this.specTypes.get(specRelTypeRef)));
            }
        }


        for (Element relationGroup : XmlUtils.descendantsByLocalName(coreContent, ReqIFConst.RELATION_GROUP)) {
            RelationGroup group = new RelationGroup(relationGroup);
            this.relationGroups.put(group.getID(), group);
        }


        if (coreContent.getElementsByTagName(ReqIFConst.SPECIFICATION).getLength() > 0) {

            NodeList specifications = coreContent.getElementsByTagName(ReqIFConst.SPECIFICATION);
            for (int spec = 0; spec < specifications.getLength(); spec++) {

                Node specification = specifications.item(spec);
                String specID = specification.getAttributes().getNamedItem(ReqIFConst.IDENTIFIER).getTextContent();
                String specTypeRef = ((Element) specification).getElementsByTagName(ReqIFConst.SPEC_TYPE_REF).item(0).getTextContent();

                this.specifications.put(specID, new Specification(specification, this.specTypes.get(specTypeRef), this.specObjects));
            }
        }


    }

}
