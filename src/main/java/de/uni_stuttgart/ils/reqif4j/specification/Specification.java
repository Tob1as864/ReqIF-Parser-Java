package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class Specification extends SpecElementWithAttributes implements HierarchyParent {
	
	
	private SpecType type;
	private String specTypeRef;
	private final HierarchyChildren children = new HierarchyChildren(this);
	
	
	
	
	
	/**
	 * @return the value of an attribute named "Description", or null if the
	 *         specification has no such attribute
	 */
	public String getDescription() {
		AttributeValue description = getAttributes().get("Description");
		return description == null ? null : (String) description.getValue();
	}
	
	
	public String getSpecType() {
		return this.type == null ? null : this.type.getType();
	}
	
	public String getSpecTypeName() {
		return this.type == null ? null : this.type.getName();
	}

	/**
	 * @return the IDENTIFIER of the specification type - also if the
	 *         reference could not be resolved - or null if it has none
	 */
	public String getSpecTypeID() {
		return this.type != null ? this.type.getID() : this.specTypeRef;
	}
	
	
	/**
	 * @return the number of hierarchy nodes, at all levels
	 */
	public int getNumberOfSpecObjects() {
		return this.children.descendants().size();
	}

	/**
	 * @return the number of top-level hierarchy nodes (sections)
	 */
	public int getNumberOfSections() {
		return this.children.size();
	}

	/**
	 * @return the top-level hierarchy nodes in document order (a copy)
	 */
	@Override
	public List<SpecHierarchy> getChildren() {
		return this.children.list();
	}
	
	public List<SpecObject> getLvlOneSpecHierarchies() {
		
		List<SpecObject> children = new ArrayList<SpecObject>();
		for(SpecHierarchy specHierarchy: this.children.list()) {
			children.add(specHierarchy.getSpecObject());
		}
		
		return children;
	}

	/**
	 * @return all hierarchy nodes, depth-first in document order (read-only)
	 */
	public List<SpecHierarchy> getAllSpecHierarchies() {
		return this.children.descendants();
	}

	/**
	 * @return the hierarchy node with this IDENTIFIER at any level, or null
	 */
	public SpecHierarchy getSpecHierarchy(String specHierarchyID) {
		return specHierarchyID == null ? null : this.children.descendant(specHierarchyID);
	}

	/**
	 * Appends a top-level hierarchy node.
	 *
	 * @throws IllegalArgumentException see {@link #addChild(int, SpecHierarchy)}
	 */
	public Specification addChild(SpecHierarchy child) {
		return addChild(this.children.size(), child);
	}

	/**
	 * Inserts a top-level hierarchy node at a position, 0 for first; the
	 * sections of the following nodes shift.
	 *
	 * @throws IllegalArgumentException if the node is already placed somewhere
	 *         (move it with {@link de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent#moveSpecHierarchy}),
	 *         a node with the same IDENTIFIER is already at the top level, or
	 *         the position is out of range
	 */
	@Override
	public Specification addChild(int index, SpecHierarchy child) {
		this.children.add(index, child);
		if(index == this.children.size() - 1) {
			// appended: nothing else moves
			child.place(1, index + 1);
		} else {
			renumber(index);
		}
		markModified();
		return this;
	}

	/**
	 * Removes a top-level hierarchy node, with everything below it.
	 *
	 * @return the removed node, or null if there is no such node
	 */
	@Override
	public SpecHierarchy removeChild(String specHierarchyID) {
		int position = this.children.indexOf(specHierarchyID);
		SpecHierarchy removed = this.children.remove(specHierarchyID);
		if(removed != null) {
			// the sections before it stay as they are
			renumber(position);
			markModified();
		}
		return removed;
	}

	@Override
	public int getChildCount() {
		return this.children.size();
	}

	@Override
	public SpecHierarchy getChild(String specHierarchyID) {
		return this.children.get(specHierarchyID);
	}

	@Override
	public Specification setChildren(List<SpecHierarchy> children) {
		this.children.replace(children);
		renumber(0);
		markModified();
		return this;
	}

	/**
	 * Top-level nodes are the sections, numbered from 1.
	 *
	 * @param from the first position whose section may have changed
	 */
	private void renumber(int from) {
		int index = 0;
		for(SpecHierarchy child: this.children.values()) {
			if(index >= from) {
				child.place(1, index + 1);
			}
			index++;
		}
	}

	HierarchyChildren hierarchyChildren() {
		return this.children;
	}
	
	
	
	
	/**
	 * Creates a specification from plain values, for documents that are
	 * generated instead of parsed.
	 */
	public Specification(String id, String name, SpecType specType,
			java.util.Collection<AttributeValue> attributeValues, List<SpecHierarchy> children) {
		super(id, name);

		this.type = specType;

		AttributeValues values = resetValues(specType);
		if(attributeValues != null) {
			for(AttributeValue attributeValue: attributeValues) {
				values.put(attributeValue);
			}
		}

		if(children != null) {
			this.children.putAll(children);
		}
	}

	public Specification(Node specification, SpecType specType, Map<String, SpecObject> specObjects) {
		this(specification, specType, specObjects, ParseIssues.collecting());
	}

	/**
	 * @param specType the resolved specification type; if null, the
	 *                 specification is kept with its unresolved type reference,
	 *                 but its values cannot be interpreted and are reported and
	 *                 skipped
	 * @param issues   receives the problems found
	 */
	public Specification(Node specification, SpecType specType, Map<String, SpecObject> specObjects, ParseIssues issues) {
		super(specification);
		
		this.type = specType;
		this.specTypeRef = typeRef(specification, ReqIFConst.SPEC_TYPE_REF);

		readValues(specification, specType, this.specTypeRef, issues);

		Element childrenElement = XmlUtils.firstChildElementByLocalName(specification, ReqIFConst.CHILDREN);
		int sectionCounter = 0;
		for(Element specHierarchy: XmlUtils.childElements(childrenElement)) {
			int section = sectionCounter + 1;
			SpecHierarchy child = issues.readOrSkip("spec hierarchy",
					() -> new SpecHierarchy(1, section, specHierarchy, specObjects, issues));
			if(child != null) {
				sectionCounter = section;
			}
			this.children.put(child, issues);
		}
	}

}
