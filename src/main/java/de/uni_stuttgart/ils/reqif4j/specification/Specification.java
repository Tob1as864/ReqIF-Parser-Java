package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class Specification extends SpecElementWithAttributes {
	
	
	private SpecType type;
	private String specTypeRef;
	private int numberOfSpecObjects;
	private int sectionCounter = 0;
	private Map<String, SpecHierarchy> children = new LinkedHashMap<String, SpecHierarchy>();
	private List<SpecHierarchy> allSpecHierarchies = new ArrayList<SpecHierarchy>();
	
	
	
	
	
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
	
	
	public int getNumberOfSpecObjects() {
		return this.numberOfSpecObjects;
	}
	
	public int getNumberOfSections() {
		return this.sectionCounter;
	}

	public List<SpecHierarchy> getChildren() {

        return new ArrayList<SpecHierarchy>(this.children.values());
	}
	
	public List<SpecObject> getLvlOneSpecHierarchies() {
		
		List<SpecObject> children = new ArrayList<SpecObject>();
		for(SpecHierarchy specHierarchy: this.children.values()) {
			children.add(specHierarchy.getSpecObject());
		}
		
		return children;
	}
	
	public List<SpecHierarchy> getAllSpecHierarchies() {
		return this.allSpecHierarchies;
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
			for(SpecHierarchy child: children) {
				this.children.put(child.getSpecHierarchyID(), child);
				this.sectionCounter++;
			}
		}

		List<SpecHierarchy> allChildren = new ArrayList<SpecHierarchy>();
		for(SpecHierarchy specHierarchy: this.children.values()) {
			allChildren.add(specHierarchy);
			allChildren.addAll(specHierarchy.getAllChildren());
		}
		this.allSpecHierarchies.addAll(allChildren);
		this.numberOfSpecObjects = allChildren.size();
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
		for(Element specHierarchy: XmlUtils.childElements(childrenElement)) {
			int section = this.sectionCounter + 1;
			SpecHierarchy child = issues.readOrSkip("spec hierarchy",
					() -> new SpecHierarchy(1, section, specHierarchy, specObjects, issues));
			if(child != null) {
				this.sectionCounter = section;
			}
			issues.put(this.children, child, "Spec hierarchy");
		}
		
		List<SpecHierarchy> allChildren = new ArrayList<SpecHierarchy>();
		for(SpecHierarchy specHierarchy: this.children.values()) {
			allChildren.add(specHierarchy);
			for(SpecHierarchy child: specHierarchy.getAllChildren()) {
				allChildren.add(child);
			}
		}
		this.allSpecHierarchies.addAll(allChildren);
		this.numberOfSpecObjects = allChildren.size();
	}

}
