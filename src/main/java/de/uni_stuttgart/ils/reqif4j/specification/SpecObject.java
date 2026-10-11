package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

public class SpecObject extends Identifiable {
	
	
	protected SpecType specType;
	protected String type;
	protected TypeClassifier typeClassifier = TypeClassifier.defaultClassifier();
	protected Map<String, AttributeValue> attributeValues = new HashMap<String, AttributeValue>();
	
	
	
	
	public String getType() {
		return this.type;
	}
	
	public String getSpecType() {
		return this.specType.getType();
	}

	public String getSpecTypeName() {
		return this.specType.getName();
	}

	/**
	 * @return the IDENTIFIER of the spec type this object refers to, or null if
	 *         the reference could not be resolved
	 */
	public String getSpecTypeID() {
		return this.specType == null ? null : this.specType.getID();
	}
	
	public Map<String, AttributeValue> getAttributes() {
		return this.attributeValues;
	}
	
	public Object getAttribute(String attributeName) {
		AttributeValue attributeValue = this.attributeValues.get(attributeName);
		return attributeValue == null ? null : attributeValue.getValue();
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

		if(attributeValues != null) {
			for(AttributeValue attributeValue: attributeValues) {
				this.attributeValues.put(AttributeValueReader.key(attributeValue.getAttributeDefinitionType()), attributeValue);
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
		super(specObject);

		this.specType = specType;
		this.typeClassifier = typeClassifier == null ? TypeClassifier.defaultClassifier() : typeClassifier;

		readAttributeValues(specObject, specType);

		// Classify only after all attribute values are available, so an
		// attribute-based classifier (e.g. the ReqIF Implementation Guide
		// profile) can inspect them.
		this.type = this.typeClassifier.classify(this);
	}

	/**
	 * Reads all attribute values of the given node and fills in default values
	 * for attribute definitions that carry no explicit value. Shared by
	 * {@link SpecObject} and {@link SpecRelation}.
	 */
	protected void readAttributeValues(Node specObject, SpecType specType) {
		AttributeValueReader.read(specObject, specType, this.attributeValues);
	}

}
