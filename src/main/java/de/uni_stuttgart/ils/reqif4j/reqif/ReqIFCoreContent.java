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


    /**
     * @return the dataTypes by IDENTIFIER, in document order (read-only; change
     *         them with the add and remove methods)
     */
    public Map<String, Datatype> getDatatypes() {
        return java.util.Collections.unmodifiableMap(this.dataTypes);
    }

    public Datatype getDatatype(String id) {
        return this.dataTypes.get(id);
    }

    /**
     * @return the specTypes by IDENTIFIER, in document order (read-only; change
     *         them with the add and remove methods)
     */
    public Map<String, SpecType> getSpecTypes() {
        return java.util.Collections.unmodifiableMap(this.specTypes);
    }

    public SpecType getSpecType(String id) {
        return this.specTypes.get(id);
    }

    /**
     * @return the specObjects by IDENTIFIER, in document order (read-only; change
     *         them with the add and remove methods)
     */
    public Map<String, SpecObject> getSpecObjects() {
        return java.util.Collections.unmodifiableMap(this.specObjects);
    }

    public SpecObject getSpecObject(String id) {
        return this.specObjects.get(id);
    }

    /**
     * @return the relations by IDENTIFIER, in document order (read-only; change
     *         them with the add and remove methods)
     */
    public Map<String, SpecRelation> getSpecRelation() {
        return java.util.Collections.unmodifiableMap(this.specRelation);
    }

    public SpecRelation getSpecRelation(String id) {
        return this.specRelation.get(id);
    }

    /**
     * @return the specifications by IDENTIFIER, in document order (read-only; change
     *         them with the add and remove methods)
     */
    public Map<String, Specification> getSpecifications() {
        return java.util.Collections.unmodifiableMap(this.specifications);
    }

    public Specification getSpecification(String id) {
        return this.specifications.get(id);
    }

    /**
     * @return the relationGroups by IDENTIFIER, in document order (read-only; change
     *         them with the add and remove methods)
     */
    public Map<String, RelationGroup> getRelationGroups() {
        return java.util.Collections.unmodifiableMap(this.relationGroups);
    }

    public RelationGroup getRelationGroup(String id) {
        return this.relationGroups.get(id);
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

    /*
     * Adding and removing elements. The add methods refuse an element whose
     * IDENTIFIER a datatype, spec type, spec object, relation, specification,
     * hierarchy node or relation group already has (attribute definitions and
     * enum values are not checked; the validator reports any clash); the
     * remove methods keep the references between the elements consistent.
     */

    /**
     * @throws IllegalArgumentException if the IDENTIFIER is missing or taken
     */
    public ReqIFCoreContent addDatatype(Datatype datatype) {
        return add(this.dataTypes, datatype);
    }

    /**
     * @throws IllegalArgumentException if the IDENTIFIER is missing or taken
     */
    public ReqIFCoreContent addSpecType(SpecType specType) {
        return add(this.specTypes, specType);
    }

    /**
     * @throws IllegalArgumentException if the IDENTIFIER is missing or taken
     */
    public ReqIFCoreContent addSpecObject(SpecObject specObject) {
        return add(this.specObjects, specObject);
    }

    /**
     * @throws IllegalArgumentException if the IDENTIFIER is missing or taken
     */
    public ReqIFCoreContent addSpecRelation(SpecRelation relation) {
        return add(this.specRelation, relation);
    }

    /**
     * @throws IllegalArgumentException if the IDENTIFIER is missing or taken
     */
    public ReqIFCoreContent addSpecification(Specification specification) {
        if (specification != null && this.specifications.get(specification.getID()) != specification) {
            for (SpecHierarchy node : specification.getAllSpecHierarchies()) {
                checkUnused(node.getID());
            }
        }
        return add(this.specifications, specification);
    }

    /**
     * @throws IllegalArgumentException if the IDENTIFIER is missing or taken
     */
    public ReqIFCoreContent addRelationGroup(RelationGroup relationGroup) {
        return add(this.relationGroups, relationGroup);
    }

    private <T extends Identifiable> ReqIFCoreContent add(Map<String, T> elements, T element) {

        if (element == null) {
            throw new IllegalArgumentException("Cannot add null");
        }
        if (elements.get(element.getID()) == element) {
            return this;
        }
        checkUnused(element.getID());
        elements.put(element.getID(), element);
        return this;
    }

    /**
     * @throws IllegalArgumentException if the identifier is blank or an
     *         element of this content has it
     */
    private void checkUnused(String id) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("An element needs an identifier");
        }
        for (Map<String, ? extends Identifiable> map : List.of(this.dataTypes, this.specTypes, this.specObjects,
                this.specRelation, this.specifications, this.relationGroups)) {
            if (map.containsKey(id)) {
                throw new IllegalArgumentException("Identifier " + id + " is already in use");
            }
        }
        if (find(id) != null) {
            throw new IllegalArgumentException("Identifier " + id + " is already in use by a spec hierarchy");
        }
    }

    /**
     * Inserts a new hierarchy node, with any nodes below it. Unlike
     * {@link HierarchyParent#addChild(int, SpecHierarchy)}, this checks that
     * no element of the document has the identifiers already.
     *
     * @param parentID the specification or hierarchy node to insert it below
     * @param index    the position among the parent's children, 0 for first
     * @throws IllegalArgumentException if the parent does not exist, an
     *         identifier is taken, the node is already placed somewhere, or
     *         the position is out of range
     */
    public SpecHierarchy addSpecHierarchy(String parentID, int index, SpecHierarchy node) {

        HierarchyParent parent = parent(parentID);
        if (node == null) {
            throw new IllegalArgumentException("Cannot add null");
        }
        checkUnused(node.getID());
        for (SpecHierarchy below : node.getAllChildren()) {
            checkUnused(below.getID());
        }
        parent.addChild(index, node);
        return node;
    }

    /**
     * @throws IllegalArgumentException if there is no such specification or
     *         hierarchy node
     */
    private HierarchyParent parent(String parentID) {
        HierarchyParent parent = this.specifications.get(parentID);
        if (parent == null) {
            Placement placement = find(parentID);
            if (placement == null) {
                throw new IllegalArgumentException("There is no specification or spec hierarchy " + parentID);
            }
            parent = placement.node;
        }
        return parent;
    }

    /**
     * Removes a spec object, the hierarchy nodes that show it - their children
     * move up into their place - and the relations from or to it.
     *
     * @return the removed object, or null if there is no such object
     */
    public SpecObject removeSpecObject(String id) {

        if (!this.specObjects.containsKey(id)) {
            return null;
        }
        // only the parents of the nodes that show the object change: the
        // nearest ancestor that does not show it itself
        List<HierarchyParent> parents = new ArrayList<HierarchyParent>();
        java.util.Set<HierarchyParent> seen = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<HierarchyParent, Boolean>());
        for (Specification specification : this.specifications.values()) {
            for (SpecHierarchy node : specification.getAllSpecHierarchies()) {
                if (id.equals(node.getSpecObjectID())) {
                    HierarchyParent parent = node.getParent();
                    while (parent instanceof SpecHierarchy && id.equals(((SpecHierarchy) parent).getSpecObjectID())) {
                        parent = ((SpecHierarchy) parent).getParent();
                    }
                    if (seen.add(parent)) {
                        parents.add(parent);
                    }
                }
            }
        }
        // checked before anything changes, so a refusal leaves all as it was
        List<List<SpecHierarchy>> lifted = new ArrayList<List<SpecHierarchy>>();
        for (HierarchyParent parent : parents) {
            lifted.add(checkedLifted(parent, id));
        }
        SpecObject removed = this.specObjects.remove(id);
        for (int i = 0; i < parents.size(); i++) {
            for (SpecHierarchy node : lifted.get(i)) {
                if (node.getParent() != parents.get(i)) {
                    // below a node that shows the object, which goes
                    node.getParent().removeChild(node.getID());
                }
            }
            // in one step, so the siblings are renumbered once
            parents.get(i).setChildren(lifted.get(i));
        }
        for (SpecRelation relation : new ArrayList<SpecRelation>(this.specRelation.values())) {
            if (id.equals(relation.getSourceObjID()) || id.equals(relation.getTargetObjID())) {
                removeSpecRelation(relation.getID());
            }
        }
        return removed;
    }

    /**
     * @return the children {@code parent} has once the nodes showing the
     *         object are replaced by their children
     * @throws IllegalStateException if that would put two nodes with the same
     *         IDENTIFIER side by side (only in documents that already repeat
     *         one)
     */
    private static List<SpecHierarchy> checkedLifted(HierarchyParent parent, String specObjectID) {
        List<SpecHierarchy> lifted = liftedChildren(parent, specObjectID);
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (SpecHierarchy child : lifted) {
            if (!ids.add(child.getID())) {
                throw new IllegalStateException("Removing spec object " + specObjectID + " would put spec hierarchy "
                        + child.getID() + " twice below " + parent.getID());
            }
        }
        return lifted;
    }

    /**
     * @return the children {@code parent} has once the nodes showing the
     *         object are replaced by their children
     */
    private static List<SpecHierarchy> liftedChildren(HierarchyParent parent, String specObjectID) {
        List<SpecHierarchy> lifted = new ArrayList<SpecHierarchy>();
        for (SpecHierarchy child : parent.getChildren()) {
            if (specObjectID.equals(child.getSpecObjectID())) {
                lifted.addAll(liftedChildren(child, specObjectID));
            } else {
                lifted.add(child);
            }
        }
        return lifted;
    }

    /**
     * Removes a relation, also from the relation groups it belongs to.
     *
     * @return the removed relation, or null if there is no such relation
     */
    public SpecRelation removeSpecRelation(String id) {

        SpecRelation removed = this.specRelation.remove(id);
        if (removed != null) {
            for (RelationGroup group : this.relationGroups.values()) {
                group.removeSpecRelationRef(id);
            }
        }
        return removed;
    }

    /**
     * Removes a specification with its hierarchy, and the relation groups
     * between it and another specification. The spec objects stay.
     *
     * @return the removed specification, or null if there is no such one
     */
    public Specification removeSpecification(String id) {

        Specification removed = this.specifications.remove(id);
        if (removed != null) {
            this.relationGroups.values().removeIf(group -> id.equals(group.getSourceSpecificationRef())
                    || id.equals(group.getTargetSpecificationRef()));
        }
        return removed;
    }

    /**
     * Removes a relation group; its relations stay.
     *
     * @return the removed group, or null if there is no such group
     */
    public RelationGroup removeRelationGroup(String id) {
        return this.relationGroups.remove(id);
    }

    /**
     * Removes a spec type that no element uses.
     *
     * @return the removed type, or null if there is no such type
     * @throws IllegalStateException if a spec object, relation, specification
     *         or relation group still has this type
     */
    public SpecType removeSpecType(String id) {

        if (!this.specTypes.containsKey(id)) {
            return null;
        }
        List<String> users = new ArrayList<String>();
        for (Map<String, ? extends SpecElementWithAttributes> elements : List.of(this.specObjects,
                this.specRelation, this.specifications)) {
            for (SpecElementWithAttributes element : elements.values()) {
                if (id.equals(typeID(element))) {
                    users.add(element.getID());
                }
            }
        }
        for (RelationGroup group : this.relationGroups.values()) {
            if (id.equals(group.getRelationGroupTypeRef())) {
                users.add(group.getID());
            }
        }
        if (!users.isEmpty()) {
            throw new IllegalStateException("Spec type " + id + " is still used by " + String.join(", ", users));
        }
        return this.specTypes.remove(id);
    }

    private static String typeID(SpecElementWithAttributes element) {
        if (element instanceof SpecObject) {
            return ((SpecObject) element).getSpecTypeID();
        }
        return element instanceof Specification ? ((Specification) element).getSpecTypeID() : null;
    }

    /**
     * Removes a datatype that no attribute definition uses.
     *
     * @return the removed datatype, or null if there is no such datatype
     * @throws IllegalStateException if an attribute definition still has
     *         this datatype
     */
    public Datatype removeDatatype(String id) {

        Datatype datatype = this.dataTypes.get(id);
        if (datatype == null) {
            return null;
        }
        List<String> users = new ArrayList<String>();
        for (SpecType specType : this.specTypes.values()) {
            for (de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition definition
                    : specType.getAttributeDefinitions().values()) {
                if (definition.getDataType() == datatype) {
                    users.add(definition.getID());
                }
            }
        }
        if (!users.isEmpty()) {
            throw new IllegalStateException("Datatype " + id + " is still used by " + String.join(", ", users));
        }
        return this.dataTypes.remove(id);
    }

    /**
     * @return the hierarchy node with this IDENTIFIER in any specification, or
     *         null if there is none
     */
    public SpecHierarchy getSpecHierarchy(String id) {
        Placement placement = find(id);
        return placement == null ? null : placement.node;
    }

    /**
     * Removes a hierarchy node with everything below it. The spec objects
     * stay.
     *
     * @return the removed node, or null if there is no such node
     */
    public SpecHierarchy removeSpecHierarchy(String id) {
        Placement placement = find(id);
        return placement == null ? null : placement.parent.removeChild(id);
    }

    /**
     * Moves a hierarchy node, with everything below it, to another place, in
     * the same or another specification.
     *
     * @param newParentID the specification or hierarchy node to move it to
     * @param index       the position among the new parent's children after
     *                    the move, 0 for first
     * @throws IllegalArgumentException if the node or the new parent does not
     *         exist, the new parent is the node itself or below it, or the
     *         position is out of range
     */
    public SpecHierarchy moveSpecHierarchy(String id, String newParentID, int index) {

        Placement placement = find(id);
        if (placement == null) {
            throw new IllegalArgumentException("There is no spec hierarchy " + id);
        }
        HierarchyParent newParent = parent(newParentID);
        // the new parent must not be the node or below it
        for (HierarchyParent above = newParent; above instanceof SpecHierarchy;
                above = ((SpecHierarchy) above).getParent()) {
            if (above == placement.node) {
                throw new IllegalArgumentException("Spec hierarchy " + id + " cannot be moved below itself");
            }
        }
        boolean sameParent = newParent == placement.parent;
        int size = newParent.getChildCount() - (sameParent ? 1 : 0);
        if (index < 0 || index > size) {
            throw new IllegalArgumentException("Position " + index + " is outside 0.." + size);
        }
        if (!sameParent && newParent.getChild(id) != null) {
            throw new IllegalArgumentException("Spec hierarchy " + id + " is already a child of " + newParentID);
        }
        placement.parent.removeChild(id);
        newParent.addChild(index, placement.node);
        return placement.node;
    }

    private static final class Placement {
        final HierarchyParent parent;
        final SpecHierarchy node;

        Placement(HierarchyParent parent, SpecHierarchy node) {
            this.parent = parent;
            this.node = node;
        }
    }

    private Placement find(String id) {
        if (id == null) {
            return null;
        }
        for (Specification specification : this.specifications.values()) {
            SpecHierarchy node = specification.getSpecHierarchy(id);
            if (node != null) {
                return new Placement(node.getParent(), node);
            }
        }
        return null;
    }

    /**
     * Marks every element as unchanged, so that the writer keeps their
     * LAST-CHANGE instead of giving them the time of writing.
     */
    public void clearModified() {

        List<Identifiable> all = new ArrayList<Identifiable>();
        for (Datatype datatype : this.dataTypes.values()) {
            all.add(datatype);
            if (datatype instanceof DatatypeEnumeration) {
                all.addAll(((DatatypeEnumeration) datatype).getEnumValues().values());
            }
        }
        for (SpecType specType : this.specTypes.values()) {
            all.add(specType);
            all.addAll(specType.getAttributeDefinitions().values());
        }
        all.addAll(this.specObjects.values());
        all.addAll(this.specRelation.values());
        for (Specification specification : this.specifications.values()) {
            all.add(specification);
            all.addAll(specification.getAllSpecHierarchies());
        }
        all.addAll(this.relationGroups.values());
        for (Identifiable identifiable : all) {
            identifiable.clearModified();
        }
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
