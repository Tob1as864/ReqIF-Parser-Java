package de.uni_stuttgart.ils.reqif4j.build;

import java.util.List;
import java.util.Map;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinitionEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;

/**
 * Adds attribute definitions to a spec type while building a document. The
 * referenced datatype must already be declared; otherwise a
 * {@link ReqIFBuildException} is thrown.
 */
public class SpecTypeBuilder {

	private final SpecType specType;
	private final Map<String, Datatype> datatypes;
	private final java.util.function.BiConsumer<String, String> claim;

	SpecTypeBuilder(SpecType specType, Map<String, Datatype> datatypes,
			java.util.function.BiConsumer<String, String> claim) {
		this.specType = specType;
		this.datatypes = datatypes;
		this.claim = claim;
	}


	public SpecTypeBuilder stringAttribute(String id, String name, String datatypeID) {
		return attribute(id, name, datatypeID, ReqIFConst.STRING, null);
	}

	public SpecTypeBuilder stringAttribute(String id, String name, String datatypeID, String defaultValue) {
		return attribute(id, name, datatypeID, ReqIFConst.STRING, defaultValue);
	}

	public SpecTypeBuilder integerAttribute(String id, String name, String datatypeID) {
		return attribute(id, name, datatypeID, ReqIFConst.INTEGER, null);
	}

	public SpecTypeBuilder booleanAttribute(String id, String name, String datatypeID) {
		return attribute(id, name, datatypeID, ReqIFConst.BOOLEAN, null);
	}

	public SpecTypeBuilder dateAttribute(String id, String name, String datatypeID) {
		return attribute(id, name, datatypeID, ReqIFConst.DATE, null);
	}

	public SpecTypeBuilder realAttribute(String id, String name, String datatypeID) {
		return attribute(id, name, datatypeID, ReqIFConst.DOUBLE, null);
	}

	public SpecTypeBuilder xhtmlAttribute(String id, String name, String datatypeID) {
		return attribute(id, name, datatypeID, ReqIFConst.XHTML, null);
	}

	/**
	 * Adds an enumeration attribute.
	 *
	 * @param multiValued      whether more than one enum value may be selected
	 * @param defaultValueRefs IDENTIFIERs of the default enum values, may be empty
	 */
	public SpecTypeBuilder enumerationAttribute(String id, String name, String datatypeID, boolean multiValued,
			List<String> defaultValueRefs) {

		Datatype datatype = requireDatatype(datatypeID, ReqIFConst.ENUMERATION);
		this.claim.accept(id, "attribute definition");
		this.specType.addAttributeDefinition(
				new AttributeDefinitionEnumeration(id, name, datatype, multiValued, defaultValueRefs));
		return this;
	}

	public SpecTypeBuilder enumerationAttribute(String id, String name, String datatypeID, boolean multiValued) {
		return enumerationAttribute(id, name, datatypeID, multiValued, null);
	}

	/** Adds an attribute definition for a datatype without a shorthand. */
	public SpecTypeBuilder attribute(String id, String name, String datatypeID, String defaultValue) {

		Datatype datatype = requireDatatype(datatypeID, null);
		this.claim.accept(id, "attribute definition");
		this.specType.addAttributeDefinition(new AttributeDefinition(id, name, datatype, defaultValue));
		return this;
	}


	private SpecTypeBuilder attribute(String id, String name, String datatypeID, String expectedCategory,
			String defaultValue) {

		Datatype datatype = requireDatatype(datatypeID, expectedCategory);
		this.claim.accept(id, "attribute definition");
		this.specType.addAttributeDefinition(new AttributeDefinition(id, name, datatype, defaultValue));
		return this;
	}

	private Datatype requireDatatype(String datatypeID, String expectedCategory) {

		Datatype datatype = this.datatypes.get(datatypeID);
		if (datatype == null) {
			throw new ReqIFBuildException("Unknown datatype: " + datatypeID);
		}
		if (expectedCategory != null && !expectedCategory.equals(datatype.getType())) {
			throw new ReqIFBuildException("Datatype " + datatypeID + " is of kind " + datatype.getType()
					+ ", but " + expectedCategory + " was expected");
		}
		return datatype;
	}
}
