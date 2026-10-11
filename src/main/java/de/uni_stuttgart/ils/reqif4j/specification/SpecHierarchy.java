package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.attributes.XHTML;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTMLElementList;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLNode;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementDiv;

public class SpecHierarchy extends Identifiable {
	
	
	private int hierarchyLvl;
	private int section;
	private SpecObject specObject;
	private String specObjectRef;
	private Boolean tableInternal;
	private Boolean editable;
	private final List<Map.Entry<String, String>> editableAttributes = new ArrayList<Map.Entry<String, String>>();
	private Map<String, SpecHierarchy> children = new LinkedHashMap<String, SpecHierarchy>();
	
	
	
	
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
	
	public AttributeValueXHTMLElementList getXHTMLContent() {
		for(AttributeValue attributeValue: this.specObject.getAttributes().values()) {
			// getValue() returns the rendered string, so the element list has
			// to be fetched via its dedicated getter (the former cast threw
			// ClassCastException on every call).
			if(attributeValue instanceof AttributeValueXHTML) {
				return ((AttributeValueXHTML)attributeValue).getElementList();
			}
		}
		return null;
	}
	
	public List<XHTMLNode> getXHTMLDivContent() {
		for(AttributeValue attributeValue: this.specObject.getAttributes().values()) {
			if(attributeValue instanceof AttributeValueXHTML) {
				XHTMLElementDiv value = ((AttributeValueXHTML)attributeValue).getDivValue();
				if(value != null) {
					// a value may consist of a single p instead of a div
					return XHTML.P.equals(value.getTagName()) ? List.of(value) : value.getChildren();
				}
			}
		}
		return null;
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
	
	public List<SpecHierarchy> getChildren() {
        return new ArrayList<SpecHierarchy>(this.children.values());
	}
	
	public List<SpecHierarchy> getAllChildren() {
		
		List<SpecHierarchy> allChildren = new ArrayList<SpecHierarchy>();
		for(SpecHierarchy specHierarchy: this.children.values()) {
			allChildren.add(specHierarchy);
			for(SpecHierarchy child: specHierarchy.getAllChildren()) {
				allChildren.add(child);
			}
		}
		
		return allChildren;
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
				this.children.put(child.getSpecHierarchyID(), child);
			}
		}
	}

	public SpecHierarchy(int hierarchyLvl, int section, Node specHierarchy, Map<String, SpecObject> specObjects) {
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
		if(childrenElement != null) {

			for(Element newSpecHierarchy: XmlUtils.childElements(childrenElement)) {

				String specHierarchyID = XmlUtils.attribute(newSpecHierarchy, ReqIFConst.IDENTIFIER);

				this.children.put(specHierarchyID, new SpecHierarchy(this.hierarchyLvl+1, section, newSpecHierarchy, specObjects));
			}
		}
	}
}
