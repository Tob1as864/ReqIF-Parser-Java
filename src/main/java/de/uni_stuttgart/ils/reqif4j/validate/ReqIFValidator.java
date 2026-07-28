package de.uni_stuttgart.ils.reqif4j.validate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinitionEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.specification.RelationGroup;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelation;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * Checks a ReqIF document for structural and referential problems before it is
 * written:
 *
 * <pre>
 * new ReqIFValidator().validate(document).throwIfInvalid();
 * new ReqIFWriter().write(document, Path.of("out.reqif"));
 * </pre>
 *
 * Validated are: identifiers present and globally unique, resolvable datatype
 * and spec type references, attribute values matching their spec type, enum
 * value references, relation endpoints, spec hierarchy targets and relation
 * group references.
 *
 * This is a model-level check, not a schema check. The OMG ReqIF XSD is not
 * bundled with this library; if you have it, pass it to
 * {@link #validateAgainstSchema(ReqIFDocument, Path)} for a full XML Schema
 * validation on top.
 */
public class ReqIFValidator {

	/**
	 * @return the findings; use {@link ValidationResult#isValid()} or
	 *         {@link ValidationResult#throwIfInvalid()}
	 */
	public ValidationResult validate(ReqIFDocument document) {

		ValidationResult result = new ValidationResult();

		if (document == null) {
			result.error(null, "Document is null");
			return result;
		}
		if (document.getHeader() == null) {
			result.warning(null, "Document has no THE-HEADER");
		} else if (isBlank(document.getHeader().getID())) {
			result.error(null, "REQ-IF-HEADER has no IDENTIFIER");
		}

		ReqIFCoreContent content = document.getCoreContent();
		if (content == null) {
			result.error(null, "Document has no CORE-CONTENT");
			return result;
		}

		Map<String, String> identifiers = new HashMap<String, String>();
		checkDatatypes(content, result, identifiers);
		checkSpecTypes(content, result, identifiers);
		checkSpecObjects(content, result, identifiers);
		checkSpecRelations(content, result, identifiers);
		checkSpecifications(content, result, identifiers);
		checkRelationGroups(content, result, identifiers);

		return result;
	}


	private void checkDatatypes(ReqIFCoreContent content, ValidationResult result, Map<String, String> identifiers) {

		for (Datatype datatype : content.getDatatypes().values()) {
			checkIdentifier(datatype.getID(), "DATATYPE-DEFINITION", result, identifiers);
		}
	}

	private void checkSpecTypes(ReqIFCoreContent content, ValidationResult result, Map<String, String> identifiers) {

		for (SpecType specType : content.getSpecTypes().values()) {
			checkIdentifier(specType.getID(), "SPEC-TYPE", result, identifiers);

			for (AttributeDefinition definition : specType.getAttributeDefinitions().values()) {
				checkIdentifier(definition.getID(), "ATTRIBUTE-DEFINITION", result, identifiers);

				Datatype datatype = definition.getDataType();
				if (datatype == null) {
					result.error(definition.getID(), "Attribute definition '" + definition.getName()
							+ "' references a datatype that does not exist");

				} else if (content.getDatatype(datatype.getID()) == null) {
					result.error(definition.getID(), "Attribute definition '" + definition.getName()
							+ "' references datatype " + datatype.getID() + ", which is not declared in DATATYPES");
				}

				checkEnumDefaults(content, definition, result);
			}
		}
	}

	private void checkEnumDefaults(ReqIFCoreContent content, AttributeDefinition definition, ValidationResult result) {

		if (!(definition instanceof AttributeDefinitionEnumeration)) {
			return;
		}
		AttributeDefinitionEnumeration enumDefinition = (AttributeDefinitionEnumeration) definition;
		List<String> defaults = enumDefinition.getDefaultValueRefs();

		checkEnumRefs(definition, defaults, result, "default value");
		if (!enumDefinition.isMultiValued() && defaults.size() > 1) {
			result.error(definition.getID(), "Attribute definition '" + definition.getName()
					+ "' is not MULTI-VALUED but declares " + defaults.size() + " default values");
		}
	}

	private void checkEnumRefs(AttributeDefinition definition, List<String> refs, ValidationResult result,
			String what) {

		if (!(definition.getDataType() instanceof DatatypeEnumeration)) {
			return;
		}
		DatatypeEnumeration datatype = (DatatypeEnumeration) definition.getDataType();
		for (String ref : refs) {
			if (datatype.getEnumValueName(ref) == null) {
				result.error(definition.getID(), "Attribute definition '" + definition.getName() + "' references "
						+ what + " " + ref + ", which datatype " + datatype.getID() + " does not declare");
			}
		}
	}

	private void checkSpecObjects(ReqIFCoreContent content, ValidationResult result, Map<String, String> identifiers) {

		for (SpecObject specObject : content.getSpecObjects().values()) {
			checkIdentifier(specObject.getID(), "SPEC-OBJECT", result, identifiers);
			checkSpecTypeRef(content, specObject.getID(), specObject.getSpecTypeID(), "SPEC-OBJECT", result);
			checkAttributeValues(specObject.getID(), specObject.getSpecTypeID(), content,
					specObject.getAttributes(), result);
		}
	}

	private void checkSpecRelations(ReqIFCoreContent content, ValidationResult result,
			Map<String, String> identifiers) {

		for (SpecRelation relation : content.getSpecRelation().values()) {
			checkIdentifier(relation.getID(), "SPEC-RELATION", result, identifiers);
			checkSpecTypeRef(content, relation.getID(), relation.getRelationTypeRef(), "SPEC-RELATION", result);

			if (content.getSpecObject(relation.getSourceObjID()) == null) {
				result.error(relation.getID(), "Relation source " + relation.getSourceObjID()
						+ " is not a known spec object");
			}
			if (content.getSpecObject(relation.getTargetObjID()) == null) {
				result.error(relation.getID(), "Relation target " + relation.getTargetObjID()
						+ " is not a known spec object");
			}
			checkAttributeValues(relation.getID(), relation.getRelationTypeRef(), content,
					relation.getAttributes(), result);
		}
	}

	private void checkSpecifications(ReqIFCoreContent content, ValidationResult result,
			Map<String, String> identifiers) {

		for (Specification specification : content.getSpecifications().values()) {
			checkIdentifier(specification.getID(), "SPECIFICATION", result, identifiers);
			checkSpecTypeRef(content, specification.getID(), specification.getSpecTypeID(), "SPECIFICATION", result);
			checkAttributeValues(specification.getID(), specification.getSpecTypeID(), content,
					specification.getAttributes(), result);

			for (SpecHierarchy hierarchy : specification.getAllSpecHierarchies()) {
				checkIdentifier(hierarchy.getSpecHierarchyID(), "SPEC-HIERARCHY", result, identifiers);

				if (hierarchy.getSpecObject() == null) {
					result.error(hierarchy.getSpecHierarchyID(),
							"Spec hierarchy references a spec object that does not exist");

				} else if (content.getSpecObject(hierarchy.getSpecObjectID()) == null) {
					result.error(hierarchy.getSpecHierarchyID(), "Spec hierarchy references spec object "
							+ hierarchy.getSpecObjectID() + ", which is not declared in SPEC-OBJECTS");
				}
			}
		}
	}

	private void checkRelationGroups(ReqIFCoreContent content, ValidationResult result,
			Map<String, String> identifiers) {

		for (RelationGroup group : content.getRelationGroups().values()) {
			checkIdentifier(group.getID(), "RELATION-GROUP", result, identifiers);

			if (group.getRelationGroupTypeRef() != null
					&& content.getSpecType(group.getRelationGroupTypeRef()) == null) {
				result.error(group.getID(), "Relation group references type " + group.getRelationGroupTypeRef()
						+ ", which is not declared in SPEC-TYPES");
			}
			checkSpecificationRef(content, group, group.getSourceSpecificationRef(), "source", result);
			checkSpecificationRef(content, group, group.getTargetSpecificationRef(), "target", result);

			for (String relationRef : group.getSpecRelationRefs()) {
				if (content.getSpecRelation(relationRef) == null) {
					result.error(group.getID(), "Relation group references relation " + relationRef
							+ ", which is not declared in SPEC-RELATIONS");
				}
			}
		}
	}

	private void checkSpecificationRef(ReqIFCoreContent content, RelationGroup group, String specificationRef,
			String role, ValidationResult result) {

		if (specificationRef != null && content.getSpecification(specificationRef) == null) {
			result.error(group.getID(), "Relation group " + role + " specification " + specificationRef
					+ " is not declared in SPECIFICATIONS");
		}
	}

	/**
	 * Every attribute value must belong to the spec type of its owner, and enum
	 * values must exist in the referenced datatype.
	 */
	private void checkAttributeValues(String ownerID, String specTypeID, ReqIFCoreContent content,
			Map<String, AttributeValue> attributeValues, ValidationResult result) {

		SpecType specType = specTypeID == null ? null : content.getSpecType(specTypeID);
		if (specType == null) {
			return;
		}

		for (AttributeValue attributeValue : attributeValues.values()) {
			AttributeDefinition definition = attributeValue.getAttributeDefinitionType();

			if (definition == null) {
				result.error(ownerID, "Attribute value '" + attributeValue.getName() + "' has no definition");
				continue;
			}
			if (specType.getAttributeDefinition(definition.getID()) == null) {
				result.error(ownerID, "Attribute value '" + attributeValue.getName() + "' uses definition "
						+ definition.getID() + ", which does not belong to spec type " + specTypeID);
			}
			if (attributeValue instanceof AttributeValueEnumeration) {
				AttributeValueEnumeration enumValue = (AttributeValueEnumeration) attributeValue;
				checkEnumRefs(definition, enumValue.getValueRefs(), result, "enum value");

				if (definition instanceof AttributeDefinitionEnumeration
						&& !((AttributeDefinitionEnumeration) definition).isMultiValued()
						&& enumValue.getValueRefs().size() > 1) {
					result.error(ownerID, "Attribute '" + attributeValue.getName() + "' is not MULTI-VALUED but has "
							+ enumValue.getValueRefs().size() + " values");
				}
			}
		}
	}

	private void checkSpecTypeRef(ReqIFCoreContent content, String ownerID, String specTypeID, String kind,
			ValidationResult result) {

		if (isBlank(specTypeID)) {
			result.error(ownerID, kind + " has no type reference");

		} else if (content.getSpecType(specTypeID) == null) {
			result.error(ownerID, kind + " references type " + specTypeID
					+ ", which is not declared in SPEC-TYPES");
		}
	}

	/**
	 * ReqIF identifiers must be present and unique across the whole document.
	 *
	 * Note that identifiers duplicated <em>within</em> one category cannot be
	 * detected here: the parser keys its maps by identifier, so a duplicate has
	 * already replaced its predecessor.
	 */
	private void checkIdentifier(String id, String kind, ValidationResult result, Map<String, String> identifiers) {

		if (isBlank(id)) {
			result.error(null, kind + " has no IDENTIFIER");
			return;
		}
		String previousKind = identifiers.put(id, kind);
		if (previousKind != null) {
			result.error(id, "Identifier is used twice: as " + previousKind + " and as " + kind);
		}
	}


	/**
	 * Validates the written XML against an XML Schema. The OMG ReqIF XSD is not
	 * bundled with this library for licensing reasons; download it from the OMG
	 * and pass its path here.
	 *
	 * @param schema path to reqif.xsd
	 * @return the schema violations, empty if the document is schema-valid
	 */
	public ValidationResult validateAgainstSchema(ReqIFDocument document, Path schema) throws IOException {

		ValidationResult result = new ValidationResult();
		try {
			SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema xsd = factory.newSchema(schema.toFile());

			Validator validator = xsd.newValidator();
			validator.setErrorHandler(new ErrorHandler() {

				@Override
				public void warning(SAXParseException exception) {
					result.warning(null, message(exception));
				}

				@Override
				public void error(SAXParseException exception) {
					result.error(null, message(exception));
				}

				@Override
				public void fatalError(SAXParseException exception) {
					result.error(null, message(exception));
				}

				private String message(SAXParseException exception) {
					return "line " + exception.getLineNumber() + ": " + exception.getMessage();
				}
			});
			validator.validate(new DOMSource(new ReqIFWriter().buildDocument(document)));

		} catch (SAXException e) {
			result.error(null, "Schema validation failed: " + e.getMessage());
		}
		return result;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
