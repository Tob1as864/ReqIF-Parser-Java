package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

/**
 * A group of relations between two specifications
 * ({@code SPEC-RELATION-GROUPS/RELATION-GROUP}).
 *
 * Relation groups are optional in ReqIF but used by tools to bundle the links
 * between a source and a target specification, for example a traceability view
 * between a system and a software specification.
 */
public class RelationGroup extends Identifiable {

	private String relationGroupTypeRef;
	private String sourceSpecificationRef;
	private String targetSpecificationRef;
	private final List<String> specRelationRefs = new ArrayList<String>();


	/**
	 * @return the IDENTIFIER of the RELATION-GROUP-TYPE, or null
	 */
	public String getRelationGroupTypeRef() {
		return this.relationGroupTypeRef;
	}

	/**
	 * @return the IDENTIFIER of the source specification, or null
	 */
	public String getSourceSpecificationRef() {
		return this.sourceSpecificationRef;
	}

	/**
	 * @return the IDENTIFIER of the target specification, or null
	 */
	public String getTargetSpecificationRef() {
		return this.targetSpecificationRef;
	}

	/**
	 * @return the IDENTIFIERs of the relations belonging to this group
	 */
	public List<String> getSpecRelationRefs() {
		return Collections.unmodifiableList(this.specRelationRefs);
	}




	/**
	 * Creates a relation group from plain values, for documents that are
	 * generated instead of parsed.
	 */
	public RelationGroup(String id, String name, String relationGroupTypeRef,
			String sourceSpecificationRef, String targetSpecificationRef, List<String> specRelationRefs) {
		super(id, name);

		this.relationGroupTypeRef = relationGroupTypeRef;
		this.sourceSpecificationRef = sourceSpecificationRef;
		this.targetSpecificationRef = targetSpecificationRef;
		if (specRelationRefs != null) {
			this.specRelationRefs.addAll(specRelationRefs);
		}
	}

	public RelationGroup(Node relationGroup) {
		super(relationGroup);


		this.relationGroupTypeRef = refIn(relationGroup, ReqIFConst.TYPE, ReqIFConst.RELATION_GROUP_TYPE_REF);
		this.sourceSpecificationRef = refIn(relationGroup, ReqIFConst.SOURCE_SPECIFICATION, ReqIFConst.SPECIFICATION_REF);
		this.targetSpecificationRef = refIn(relationGroup, ReqIFConst.TARGET_SPECIFICATION, ReqIFConst.SPECIFICATION_REF);

		Element relations = XmlUtils.firstChildElementByLocalName(relationGroup, ReqIFConst.SPEC_RELATIONS);
		if (relations != null) {
			for (Element ref : XmlUtils.descendantsByLocalName(relations, ReqIFConst.SPEC_RELATION_REF)) {
				this.specRelationRefs.add(ref.getTextContent().trim());
			}
		}
	}

	private static String refIn(Node parent, String wrapperName, String refName) {

		Element wrapper = XmlUtils.firstChildElementByLocalName(parent, wrapperName);
		Element ref = XmlUtils.firstDescendantByLocalName(wrapper, refName);
		return ref == null ? null : ref.getTextContent().trim();
	}
}
