package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.Collection;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

public class SpecObject extends SpecElementWithAttributes {
	
	
	protected SpecType specType;
	private String specTypeRef;
	protected String type;
	protected TypeClassifier typeClassifier = TypeClassifier.defaultClassifier();
	
	
	
	
	public String getType() {
		return this.type;
	}
	
	/**
	 * @return the kind of the spec type (e.g. SPEC-OBJECT-TYPE), or null if
	 *         the type reference could not be resolved
	 */
	public String getSpecType() {
		return this.specType == null ? null : this.specType.getType();
	}

	/**
	 * @return the LONG-NAME of the spec type, or null if the type reference
	 *         could not be resolved
	 */
	public String getSpecTypeName() {
		return this.specType == null ? null : this.specType.getName();
	}

	/**
	 * @return the IDENTIFIER of the spec type this object refers to - also if
	 *         the reference could not be resolved - or null if it has none
	 */
	public String getSpecTypeID() {
		return this.specType != null ? this.specType.getID() : this.specTypeRef;
	}
	
	
	public boolean isReq() {
		return this.typeClassifier.isRequirement(this);
	}
	
	public boolean isHeadline() {
		
		if(this.type.equals(ReqIFConst.HEADLINE)) {
			return true;
		}else{
			return false;
		}
	}
	
	public boolean isSubReq() {
		return this.typeClassifier.isSubRequirement(this);
	}
	
	public boolean isText() {
		
		if(!this.isReq() && !this.isHeadline() && !this.isSubReq()) {
			return true;
		}else{
			return false;
		}
	}
	
	
	public SpecObject(Node specObject){
		super(specObject);
	}
	
	/**
	 * Creates a spec object from plain values, for documents that are generated
	 * instead of parsed. The content category is derived by the classifier, as
	 * when reading.
	 */
	public SpecObject(String id, SpecType specType, Collection<AttributeValue> attributeValues,
			TypeClassifier typeClassifier) {
		super(id, null);

		this.specType = specType;
		this.typeClassifier = typeClassifier == null ? TypeClassifier.defaultClassifier() : typeClassifier;

		AttributeValues values = resetValues(specType);
		if(attributeValues != null) {
			for(AttributeValue attributeValue: attributeValues) {
				values.put(attributeValue);
			}
		}
		this.type = this.typeClassifier.classify(this);
	}

	public SpecObject(String id, SpecType specType, Collection<AttributeValue> attributeValues) {
		this(id, specType, attributeValues, TypeClassifier.defaultClassifier());
	}

	public SpecObject(Node specObject, SpecType specType) {
		this(specObject, specType, TypeClassifier.defaultClassifier());
	}

	public SpecObject(Node specObject, SpecType specType, TypeClassifier typeClassifier) {
		this(specObject, specType, typeClassifier, ParseIssues.collecting());
	}

	/**
	 * @param specType the resolved spec type; if null, the object is kept with
	 *                 its unresolved type reference, but its values cannot be
	 *                 interpreted and are reported and skipped
	 * @param issues   receives the problems found
	 */
	public SpecObject(Node specObject, SpecType specType, TypeClassifier typeClassifier, ParseIssues issues) {
		super(specObject);

		this.specType = specType;
		this.specTypeRef = typeRef(specObject, ReqIFConst.SPEC_OBJECT_TYPE_REF);
		this.typeClassifier = typeClassifier == null ? TypeClassifier.defaultClassifier() : typeClassifier;

		readAttributeValues(specObject, specType, issues);

		// Classify only after all attribute values are available, so an
		// attribute-based classifier (e.g. the ReqIF Implementation Guide
		// profile) can inspect them.
		this.type = this.typeClassifier.classify(this);
	}

	/**
	 * The content category may depend on the values (attribute-based
	 * classifiers), so it is derived again.
	 */
	@Override
	protected void valuesChanged() {
		this.type = this.typeClassifier.classify(this);
	}

	/**
	 * Reads the attribute values of the given node. Shared by
	 * {@link SpecObject} and {@link SpecRelation}.
	 */
	protected void readAttributeValues(Node specObject, SpecType specType) {
		readAttributeValues(specObject, specType, ParseIssues.collecting());
	}

	/**
	 * Reads the attribute values of the given node; without a spec type they
	 * cannot be interpreted and are reported and skipped.
	 */
	protected void readAttributeValues(Node specObject, SpecType specType, ParseIssues issues) {
		readValues(specObject, specType, getSpecTypeID(), issues);
	}

}
