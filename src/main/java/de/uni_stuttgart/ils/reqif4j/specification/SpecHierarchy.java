package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.attributes.XHTML;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTMLElementList;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLNode;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementDiv;

public class SpecHierarchy extends Identifiable implements HierarchyParent {
	
	
	private int hierarchyLvl;
	private int section;
	private SpecObject specObject;
	private String specObjectRef;
	private Boolean tableInternal;
	private Boolean editable;
	private final List<Map.Entry<String, String>> editableAttributes = new ArrayList<Map.Entry<String, String>>();
	private final HierarchyChildren children = new HierarchyChildren(this);
	private HierarchyParent parent;
	
	
	
	
	/**
	 * @return the IDENTIFIER of this hierarchy node, same as {@link #getID()}
	 */
	public String getSpecHierarchyID() {
		return getID();
	}
	
	/**
	 * @return the IDENTIFIER of the referenced spec object; also when the
	 *         reference could not be resolved, null if there is none
	 */
	public String getSpecObjectID(){
		return this.specObject != null ? this.specObject.getID() : this.specObjectRef;
	}

	/**
	 * @return IS-TABLE-INTERNAL: whether this node is part of a table in the
	 *         tool's view, or null if the document does not say
	 */
	public Boolean getTableInternal() {
		return this.tableInternal;
	}

	public SpecHierarchy setTableInternal(Boolean tableInternal) {
		this.tableInternal = tableInternal;
		markModified();
		return this;
	}

	/**
	 * @return IS-EDITABLE: whether the referenced object may be edited, or
	 *         null if the document does not say
	 */
	public Boolean getEditable() {
		return this.editable;
	}

	public SpecHierarchy setEditable(Boolean editable) {
		this.editable = editable;
		markModified();
		return this;
	}

	/**
	 * @return the IDENTIFIERs of the attribute definitions listed in
	 *         EDITABLE-ATTS (editable on this node), in document order
	 */
	public List<String> getEditableAttributeIDs() {
		List<String> ids = new ArrayList<String>();
		for(Map.Entry<String, String> ref: this.editableAttributes) {
			ids.add(ref.getKey());
		}
		return ids;
	}

	/**
	 * @return the EDITABLE-ATTS references as read, in document order:
	 *         attribute definition IDENTIFIER and the reference element name,
	 *         e.g. ATTRIBUTE-DEFINITION-STRING-REF (read-only)
	 */
	public List<Map.Entry<String, String>> getEditableAttributeRefs() {
		return java.util.Collections.unmodifiableList(this.editableAttributes);
	}

	/**
	 * Sets the attributes of the node's object that are editable on this node
	 * (EDITABLE-ATTS); none or null to remove the list.
	 *
	 * @throws IllegalArgumentException if a definition is null, of a datatype
	 *         kind that cannot be written, or not an attribute of the spec
	 *         type of the node's object
	 */
	public SpecHierarchy setEditableAttributes(List<AttributeDefinition> attributeDefinitions) {
		List<Map.Entry<String, String>> refs = new ArrayList<Map.Entry<String, String>>();
		SpecType objectType = this.specObject == null ? null : this.specObject.getResolvedSpecType();
		for(AttributeDefinition definition: attributeDefinitions == null ? List.<AttributeDefinition>of() : attributeDefinitions) {
			if(definition == null || definition.getElementName() == null) {
				throw new IllegalArgumentException("Editable attributes need attribute definitions of a known kind");
			}
			if(objectType != null && objectType.getAttributeDefinition(definition.getID()) != definition) {
				throw new IllegalArgumentException("Attribute definition " + definition.getID()
						+ " is not an attribute of spec type " + objectType.getID() + " of the node's object");
			}
			refs.add(new java.util.AbstractMap.SimpleImmutableEntry<String, String>(
					definition.getID(), definition.getElementName() + "-REF"));
		}
		this.editableAttributes.clear();
		this.editableAttributes.addAll(refs);
		markModified();
		return this;
	}
	
	public int getHierarchyLvl() {
		return this.hierarchyLvl;
	}
	
	public int getSection() {
		return this.section;
	}
	
	public SpecObject getSpecObject() {
		return this.specObject;
	}
	
	public Map<String, AttributeValue> getAttributes() {
		return this.specObject.getAttributes();
	}
	
	public Object getAttribute(String attributeName) {
		return (Object)this.specObject.getAttribute(attributeName);
	}
	
	/**
	 * @return the token list of the object's first XHTML value - its own values
	 *         before defaults, so another attribute's default never stands in
	 *         for the object's text - or null if it has none
	 */
	public AttributeValueXHTMLElementList getXHTMLContent() {
		AttributeValueXHTML xhtml = firstXhtmlValue();
		return xhtml == null ? null : xhtml.getElementList();
	}

	/**
	 * @return the block elements of the object's first XHTML value (see
	 *         {@link #getXHTMLContent()}), or null if it has none
	 */
	public List<XHTMLNode> getXHTMLDivContent() {
		AttributeValueXHTML xhtml = firstXhtmlValue();
		XHTMLElementDiv value = xhtml == null ? null : xhtml.getDivValue();
		if(value == null) {
			return null;
		}
		// a value may consist of a single p instead of a div
		return XHTML.P.equals(value.getTagName()) ? List.of(value) : value.getChildren();
	}

	/**
	 * @return the first XHTML value with content, the object's own values
	 *         first; failing that any XHTML value, as earlier versions returned
	 *         an empty token list rather than null for it
	 */
	private AttributeValueXHTML firstXhtmlValue() {
		AttributeValueXHTML withoutContent = null;
		for(java.util.Collection<AttributeValue> values: List.of(this.specObject.getValues().values(),
				this.specObject.getAttributes().values())) {
			for(AttributeValue attributeValue: values) {
				if(attributeValue instanceof AttributeValueXHTML) {
					AttributeValueXHTML xhtml = (AttributeValueXHTML) attributeValue;
					if(xhtml.getDivValue() != null) {
						return xhtml;
					}
					if(withoutContent == null) {
						withoutContent = xhtml;
					}
				}
			}
		}
		return withoutContent;
	}

	public String getSpecType() {
		return this.specObject.getSpecType();
	}
	
	public String getSpecTypeName() {
		return this.specObject.getSpecTypeName();
	}
	
	public String getType() {
		return this.specObject.getType();
	}
	
	public boolean isReq() {
		if(this.specObject.isReq()) {
			return true;
		}else{
			return false;
		}
	}
	
	public boolean isDef() {
		if(this.specObject.isReq() && this.section == 1) {
			return true;
		}else{
			return false;
		}
	}
	
	public boolean isHeadline() {
		if(this.specObject.isHeadline()) {
			return true;
		}else{
			return false;
		}
	}
	
	public boolean isSubReq() {
		if(this.specObject.isSubReq()) {
			return true;
		}else{
			return false;
		}
	}
	
	public boolean isText() {
		if(this.specObject.isText()) {
			return true;
		}else{
			return false;
		}
	}
	
	/**
	 * @return the direct children in document order (a copy)
	 */
	@Override
	public List<SpecHierarchy> getChildren() {
		return this.children.list();
	}

	/**
	 * @return all nodes below this one, depth-first in document order
	 */
	public List<SpecHierarchy> getAllChildren() {
		return this.children.descendants();
	}

	/**
	 * Appends a child node.
	 *
	 * @throws IllegalArgumentException see {@link #addChild(int, SpecHierarchy)}
	 */
	public SpecHierarchy addChild(SpecHierarchy child) {
		return addChild(this.children.size(), child);
	}

	/**
	 * Inserts a child node at a position, 0 for first.
	 *
	 * @throws IllegalArgumentException if the node is already placed somewhere
	 *         (move it with {@link de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent#moveSpecHierarchy}),
	 *         a child with the same IDENTIFIER exists, the node is this one or
	 *         above it, or the position is out of range
	 */
	@Override
	public SpecHierarchy addChild(int index, SpecHierarchy child) {
		this.children.add(index, child);
		child.place(this.hierarchyLvl + 1, this.section);
		markModified();
		return this;
	}

	/**
	 * Removes a direct child node, with everything below it.
	 *
	 * @return the removed node, or null if there is no such child
	 */
	@Override
	public SpecHierarchy removeChild(String specHierarchyID) {
		SpecHierarchy removed = this.children.remove(specHierarchyID);
		if(removed != null) {
			markModified();
		}
		return removed;
	}

	/**
	 * @return the specification or hierarchy node this node is placed below,
	 *         or null if it is not placed
	 */
	public HierarchyParent getParent() {
		return this.parent;
	}

	void setParent(HierarchyParent parent) {
		this.parent = parent;
	}

	HierarchyChildren hierarchyChildren() {
		return this.children;
	}

	/**
	 * Points this node at another spec object.
	 */
	public SpecHierarchy setSpecObject(SpecObject specObject) {
		this.specObject = specObject;
		this.specObjectRef = specObject == null ? null : specObject.getID();
		markModified();
		return this;
	}

	/**
	 * Sets level and section of this node and everything below it, after it
	 * was inserted somewhere.
	 */
	void place(int hierarchyLvl, int section) {
		this.hierarchyLvl = hierarchyLvl;
		this.section = section;
		for(SpecHierarchy child: this.children.list()) {
			child.place(hierarchyLvl + 1, section);
		}
	}
	
	
	
	
	/**
	 * Creates a hierarchy node from plain values, for documents that are
	 * generated instead of parsed.
	 */
	public SpecHierarchy(String specHierarchyID, int hierarchyLvl, int section, SpecObject specObject,
			List<SpecHierarchy> children) {
		super(specHierarchyID, null);

		this.hierarchyLvl = hierarchyLvl;
		this.section = section;
		this.specObject = specObject;

		if(children != null) {
			for(SpecHierarchy child: children) {
				this.children.put(child);
			}
		}
	}

	public SpecHierarchy(int hierarchyLvl, int section, Node specHierarchy, Map<String, SpecObject> specObjects) {
		this(hierarchyLvl, section, specHierarchy, specObjects, ParseIssues.collecting());
	}

	/**
	 * @param issues receives nested hierarchy nodes that cannot be read or are
	 *               declared twice
	 */
	public SpecHierarchy(int hierarchyLvl, int section, Node specHierarchy, Map<String, SpecObject> specObjects,
			ParseIssues issues) {
		super(specHierarchy);
		
		this.hierarchyLvl = hierarchyLvl;
		this.section = section;
		
		Element object = XmlUtils.firstChildElementByLocalName(specHierarchy, ReqIFConst.OBJECT);
		Element specObjectRef = XmlUtils.firstDescendantByLocalName(object, ReqIFConst.SPEC_OBJECT_REF);
		if(specObjectRef != null) {
			// the reference is kept even if it cannot be resolved, so it is not
			// lost on writing and the validator can report it
			this.specObjectRef = specObjectRef.getTextContent().trim();
			this.specObject = specObjects.get(this.specObjectRef);
		}

		this.tableInternal = XmlUtils.booleanAttribute(specHierarchy, ReqIFConst.IS_TABLE_INTERNAL);
		this.editable = XmlUtils.booleanAttribute(specHierarchy, ReqIFConst.IS_EDITABLE);
		Element editableAtts = XmlUtils.firstChildElementByLocalName(specHierarchy, ReqIFConst.EDITABLE_ATTS);
		for(Element ref: XmlUtils.childElements(editableAtts)) {
			this.editableAttributes.add(new java.util.AbstractMap.SimpleImmutableEntry<String, String>(
					ref.getTextContent().trim(), XmlUtils.localName(ref)));
		}

		Element childrenElement = XmlUtils.firstChildElementByLocalName(specHierarchy, ReqIFConst.CHILDREN);
		for(Element newSpecHierarchy: XmlUtils.childElements(childrenElement)) {
			SpecHierarchy child = issues.readOrSkip("spec hierarchy",
					() -> new SpecHierarchy(this.hierarchyLvl+1, section, newSpecHierarchy, specObjects, issues));
			this.children.put(child, issues);
		}
	}
}
