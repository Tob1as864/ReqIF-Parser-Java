package de.uni_stuttgart.ils.reqif4j.validate;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinitionEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeDouble;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumerationValue;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeInteger;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeString;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFHeader;
import de.uni_stuttgart.ils.reqif4j.specification.RelationGroup;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelation;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.util.SecureXml;
import de.uni_stuttgart.ils.reqif4j.util.XhtmlParser;
import de.uni_stuttgart.ils.reqif4j.util.XsdTypes;
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
 * Validated are: identifiers present, valid xsd:IDs and globally unique,
 * resolvable datatype and spec type references, attribute values matching
 * their spec type, enum value references, relation endpoints, spec hierarchy
 * targets and relation group references. Values the schema constrains
 * lexically - LAST-CHANGE, CREATION-TIME and DATE values (xsd:dateTime),
 * enum KEYs (xsd:integer), REQ-IF-VERSION (fixed to 1.0) - are errors when
 * they do not fit; values outside the limits of their datatype (MAX-LENGTH,
 * MIN/MAX) are warnings, because the schema cannot express those limits.
 *
 * Required values the writer fills in when the model lacks them (see
 * {@link ReqIFWriter#buildDocument(ReqIFDocument)}) are not reported. A
 * document without errors is written schema-valid, except for the XHTML
 * content model: which XHTML elements a value may use is only checked by
 * {@link #validateAgainstSchema(ReqIFDocument, Path)}.
 *
 * This is a model-level check, not a schema check. The OMG ReqIF XSD is not
 * bundled with this library; if you have it, pass it to
 * {@link #validateAgainstSchema(ReqIFDocument, Path)} (or, for a file on disk,
 * {@link #validateAgainstSchema(Path, Path)}) for a full XML Schema validation
 * on top.
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
		Map<String, String> identifiers = new HashMap<String, String>();
		if (document.getHeader() == null) {
			// the schema requires THE-HEADER, and the writer cannot invent its IDENTIFIER
			result.error(null, "Document has no THE-HEADER");
		} else {
			checkHeader(document.getHeader(), result, identifiers);
		}

		ReqIFCoreContent content = document.getCoreContent();
		if (content == null) {
			result.error(null, "Document has no CORE-CONTENT");
			return result;
		}

		checkDatatypes(content, result, identifiers);
		checkSpecTypes(content, result, identifiers);
		checkSpecObjects(content, result, identifiers);
		checkSpecRelations(content, result, identifiers);
		checkSpecifications(content, result, identifiers);
		checkRelationGroups(content, result, identifiers);

		return result;
	}


	/**
	 * Empty header fields are fine, the writer fills them in; present ones must
	 * match the schema.
	 */
	private void checkHeader(ReqIFHeader header, ValidationResult result, Map<String, String> identifiers) {

		if (isBlank(header.getID())) {
			result.error(null, "REQ-IF-HEADER has no IDENTIFIER");
		} else {
			checkIdentifier(header.getID(), "REQ-IF-HEADER", result, identifiers);
		}
		checkText(header.getID(), "TITLE", header.getTitle(), result);
		checkText(header.getID(), "COMMENT", header.getComment(), result);
		checkText(header.getID(), "REQ-IF-TOOL-ID", header.getToolID(), result);
		checkText(header.getID(), "SOURCE-TOOL-ID", header.getSourceToolID(), result);
		if (!isBlank(header.getCreationTime()) && !XsdTypes.isDateTime(header.getCreationTime())) {
			result.error(header.getID(), "CREATION-TIME '" + header.getCreationTime() + "' is not an xsd:dateTime");
		}
		if (!isBlank(header.getReqIFVersion()) && !ReqIFWriter.REQIF_VERSION.equals(header.getReqIFVersion())) {
			result.error(header.getID(), "REQ-IF-VERSION is '" + header.getReqIFVersion()
					+ "', but the schema only allows " + ReqIFWriter.REQIF_VERSION);
		}
	}

	private void checkDatatypes(ReqIFCoreContent content, ValidationResult result, Map<String, String> identifiers) {

		for (Datatype datatype : content.getDatatypes().values()) {
			checkIdentifiable(datatype, "DATATYPE-DEFINITION", result, identifiers);

			if (datatype instanceof DatatypeInteger) {
				DatatypeInteger integer = (DatatypeInteger) datatype;
				checkBounds(datatype, integer.getMin() <= integer.getMax(), integer.getMin(), integer.getMax(), result);

			} else if (datatype instanceof DatatypeDouble) {
				DatatypeDouble real = (DatatypeDouble) datatype;
				checkBounds(datatype, real.getMin() <= real.getMax(), real.getMin(), real.getMax(), result);

			} else if (datatype instanceof DatatypeEnumeration) {
				for (DatatypeEnumerationValue value : ((DatatypeEnumeration) datatype).getEnumValues().values()) {
					checkIdentifiable(value, "ENUM-VALUE", result, identifiers);
					checkText(value.getID(), "OTHER-CONTENT of enum value", value.getOtherContent(), result);
					if (isBlank(value.getKey())) {
						result.error(value.getID(), "Enum value '" + value.getName() + "' has no KEY");
					} else if (!XsdTypes.isInteger(value.getKey())) {
						result.error(value.getID(), "KEY '" + value.getKey() + "' of enum value '" + value.getName()
								+ "' is not an integer");
					}
				}
			}
		}
	}

	private void checkBounds(Datatype datatype, boolean ordered, Object min, Object max, ValidationResult result) {
		if (!ordered) {
			result.warning(datatype.getID(), "Datatype '" + datatype.getName() + "' has MIN " + min
					+ " greater than MAX " + max + "; no value can satisfy it");
		}
	}

	private void checkSpecTypes(ReqIFCoreContent content, ValidationResult result, Map<String, String> identifiers) {

		for (SpecType specType : content.getSpecTypes().values()) {
			checkIdentifiable(specType, "SPEC-TYPE", result, identifiers);

			for (AttributeDefinition definition : specType.getAttributeDefinitions().values()) {
				checkIdentifiable(definition, "ATTRIBUTE-DEFINITION", result, identifiers);
				// written verbatim, so it must already be in schema notation
				if (definition.getDefaultValue() != null && !(definition instanceof AttributeDefinitionEnumeration)) {
					checkScalar(definition.getID(), "Default value", definition, definition.getDefaultValue(), result);
				}

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
			checkIdentifiable(specObject, "SPEC-OBJECT", result, identifiers);
			checkSpecTypeRef(content, specObject.getID(), specObject.getSpecTypeID(), "SPEC-OBJECT", result);
			checkAttributeValues(specObject.getID(), specObject.getSpecTypeID(), content,
					specObject.getValues(), result);
		}
	}

	private void checkSpecRelations(ReqIFCoreContent content, ValidationResult result,
			Map<String, String> identifiers) {

		for (SpecRelation relation : content.getSpecRelation().values()) {
			checkIdentifiable(relation, "SPEC-RELATION", result, identifiers);
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
					relation.getValues(), result);
		}
	}

	private void checkSpecifications(ReqIFCoreContent content, ValidationResult result,
			Map<String, String> identifiers) {

		for (Specification specification : content.getSpecifications().values()) {
			checkIdentifiable(specification, "SPECIFICATION", result, identifiers);
			checkSpecTypeRef(content, specification.getID(), specification.getSpecTypeID(), "SPECIFICATION", result);
			checkAttributeValues(specification.getID(), specification.getSpecTypeID(), content,
					specification.getValues(), result);

			for (SpecHierarchy hierarchy : specification.getAllSpecHierarchies()) {
				checkIdentifiable(hierarchy, "SPEC-HIERARCHY", result, identifiers);

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
			checkIdentifiable(group, "RELATION-GROUP", result, identifiers);

			if (isBlank(group.getRelationGroupTypeRef())) {
				result.error(group.getID(), "Relation group has no type reference");
			} else if (content.getSpecType(group.getRelationGroupTypeRef()) == null) {
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

		if (isBlank(specificationRef)) {
			result.error(group.getID(), "Relation group has no " + role + " specification");
		} else if (content.getSpecification(specificationRef) == null) {
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

		// the explicit values, the ones that are written, in document order
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
			if (attributeValue.getValue() != null && !(attributeValue instanceof AttributeValueEnumeration)
					&& !(attributeValue instanceof AttributeValueXHTML)) {
				Object value = attributeValue.getValue();
				// typed values are written in xsd notation, so check that form
				String text = value instanceof Double ? XsdTypes.formatDouble((Double) value) : String.valueOf(value);
				checkScalar(ownerID, "Attribute '" + attributeValue.getName() + "'", definition, text, result);
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
	 * A value must be lexically valid for its datatype (an error: the written
	 * file would violate the schema) and should respect the datatype's limits
	 * (a warning: the schema cannot express them, but importing tools may
	 * reject the value).
	 *
	 * @param text the value as written, e.g. "42" or "2026-07-23T10:00:00Z"
	 */
	private void checkScalar(String ownerID, String what, AttributeDefinition definition, String text,
			ValidationResult result) {

		Datatype datatype = definition.getDataType();
		if (datatype == null) {
			return;
		}
		String category = datatype.getType();
		if (!XsdTypes.isXmlText(text)) {
			result.error(ownerID, what + " contains characters XML does not allow (control characters)");
			return;
		}

		if (ReqIFConst.ENUMERATION.equals(category)) {
			// only reachable for a default of a definition that is not an
			// AttributeDefinitionEnumeration; enum values are references
			result.error(ownerID, what + " of an enumeration must be given as enum value references "
					+ "(an enumeration attribute definition), not as text");

		} else if (ReqIFConst.XHTML.equals(category)) {
			try {
				XhtmlParser.parseDiv(text);
			} catch (IllegalArgumentException e) {
				result.error(ownerID, what + " is not well-formed XHTML");
			}

		} else if (ReqIFConst.DATE.equals(category)) {
			if (!XsdTypes.isDateTime(text)) {
				result.error(ownerID, what + ": '" + text + "' is not an xsd:dateTime");
			}

		} else if (ReqIFConst.BOOLEAN.equals(category)) {
			if (!XsdTypes.isBoolean(text)) {
				result.error(ownerID, what + ": '" + text + "' is not an xsd:boolean (true, false, 1 or 0)");
			}

		} else if (datatype instanceof DatatypeString) {
			// xsd counts characters, not UTF-16 units
			int length = text.codePointCount(0, text.length());
			int maxLength = ((DatatypeString) datatype).getMaxLength();
			if (length > maxLength) {
				result.warning(ownerID, what + " is " + length + " characters long, but datatype '"
						+ datatype.getName() + "' allows " + maxLength);
			}

		} else if (datatype instanceof DatatypeInteger) {
			DatatypeInteger integer = (DatatypeInteger) datatype;
			if (!XsdTypes.isInteger(text)) {
				result.error(ownerID, what + ": '" + text + "' is not an integer");
			} else if (outside(new BigInteger(text.trim()), integer.getMin(), integer.getMax())) {
				result.warning(ownerID, what + " " + text.trim() + " is outside the range " + integer.getMin()
						+ ".." + integer.getMax() + " of datatype '" + datatype.getName() + "'");
			}

		} else if (datatype instanceof DatatypeDouble) {
			DatatypeDouble real = (DatatypeDouble) datatype;
			if (!XsdTypes.isDouble(text)) {
				result.error(ownerID, what + ": '" + text + "' is not an xsd:double");
				return;
			}
			double value = XsdTypes.parseDouble(text);
			if (value < real.getMin() || value > real.getMax()) {
				result.warning(ownerID, what + " " + text.trim() + " is outside the range " + real.getMin()
						+ ".." + real.getMax() + " of datatype '" + datatype.getName() + "'");
			}
		}
	}

	/**
	 * Text with characters XML does not allow cannot be written at all.
	 */
	private void checkText(String ownerID, String what, String text, ValidationResult result) {
		if (!XsdTypes.isXmlText(text)) {
			result.error(ownerID, what + " contains characters XML does not allow (control characters)");
		}
	}

	private static boolean outside(BigInteger value, long min, long max) {
		return value.compareTo(BigInteger.valueOf(min)) < 0 || value.compareTo(BigInteger.valueOf(max)) > 0;
	}

	/**
	 * Checks the identifier and, if present, the LAST-CHANGE of an element. A
	 * missing LAST-CHANGE is fine: the writer fills it in.
	 */
	private void checkIdentifiable(Identifiable identifiable, String kind, ValidationResult result,
			Map<String, String> identifiers) {

		checkIdentifier(identifiable.getID(), kind, result, identifiers);
		checkText(identifiable.getID(), "LONG-NAME of " + kind, identifiable.getName(), result);
		checkText(identifiable.getID(), "DESC of " + kind, identifiable.getDesc(), result);

		String lastChange = identifiable.getLastChange();
		if (!isBlank(lastChange) && !XsdTypes.isDateTime(lastChange)) {
			result.error(identifiable.getID(), "LAST-CHANGE '" + lastChange + "' of " + kind
					+ " is not an xsd:dateTime");
		}
	}

	/**
	 * ReqIF identifiers must be present, valid xsd:IDs and unique across the
	 * whole document.
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
		if (!XsdTypes.isNCName(id)) {
			// e.g. starts with a digit or contains a blank or colon
			result.error(id, "Identifier '" + id + "' of " + kind + " is not a valid xsd:ID");
		}
		String previousKind = identifiers.put(id, kind);
		if (previousKind != null) {
			result.error(id, "Identifier is used twice: as " + previousKind + " and as " + kind);
		}
	}


	/**
	 * Validates the XML the writer produces for this document against an XML
	 * Schema. The OMG ReqIF XSD is not bundled with this library for licensing
	 * reasons; download it from the OMG and pass its path here.
	 *
	 * What is checked is the output, not the model: required values the writer
	 * fills in (LAST-CHANGE, header fields, see
	 * {@link ReqIFWriter#buildDocument(ReqIFDocument)}) are not reported as
	 * missing.
	 *
	 * The official {@code reqif.xsd} imports {@code xml.xsd}, {@code driver.xsd}
	 * and the XHTML modules ({@code xhtml-*-1.xsd}) by http URL. Validation never
	 * goes online: these imports are looked up by file name in the directory of
	 * {@code schema}, so place them next to it.
	 *
	 * @param schema path to reqif.xsd
	 * @return the schema violations, empty if the document is schema-valid
	 */
	public ValidationResult validateAgainstSchema(ReqIFDocument document, Path schema) throws IOException {

		return validate(new DOMSource(new ReqIFWriter().buildDocument(document)), schema);
	}

	/**
	 * Validates a ReqIF file as it is on disk, e.g. a file received from another
	 * tool or one written by {@link ReqIFWriter}. Unlike
	 * {@link #validateAgainstSchema(ReqIFDocument, Path)} the findings carry the
	 * line numbers of the file.
	 *
	 * @param reqifFile the .reqif file to check
	 * @param schema    path to reqif.xsd, with its imports next to it
	 * @return the schema violations, empty if the file is schema-valid
	 */
	public ValidationResult validateAgainstSchema(Path reqifFile, Path schema) throws IOException {

		try {
			InputSource input = new InputSource(reqifFile.toUri().toString());
			return validate(new SAXSource(SecureXml.newXMLReader(), input), schema);
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("Failed to create an XML reader", e);
		}
	}

	private ValidationResult validate(Source source, Path schema) throws IOException {

		ValidationResult result = new ValidationResult();
		Path schemaFile = schema.toAbsolutePath();
		LocalSchemaResolver resolver = new LocalSchemaResolver(schemaFile.getParent());

		// Problems while loading the schema itself - an unreadable import is only a
		// warning to the parser - would make every finding below meaningless.
		ValidationResult schemaProblems = new ValidationResult();
		IssueCollector schemaCollector = new IssueCollector(schemaProblems);
		Schema xsd = null;
		try {
			SchemaFactory factory = SecureXml.newSchemaFactory(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			factory.setResourceResolver(resolver);
			factory.setErrorHandler(schemaCollector);
			xsd = factory.newSchema(schemaFile.toFile());
		} catch (SAXException e) {
			schemaCollector.reportUnlessReported(e);
		}
		if (xsd == null || !schemaProblems.getIssues().isEmpty() || !resolver.getMissing().isEmpty()) {
			StringBuilder message = new StringBuilder("Schema could not be loaded: ").append(schemaFile);
			for (ValidationIssue problem : schemaProblems.getIssues()) {
				message.append("\n  ").append(problem.getMessage());
			}
			appendMissingImports(message, resolver, schemaFile);
			result.error(null, message.toString());
			return result;
		}

		IssueCollector collector = new IssueCollector(result);
		try {
			Validator validator = xsd.newValidator();
			validator.setErrorHandler(collector);
			validator.validate(source);
		} catch (SAXException e) {
			// normally reported through the error handler already; anything else
			// stopped the validation and must not be lost
			collector.reportUnlessReported(e);
		}
		return result;
	}

	private static void appendMissingImports(StringBuilder message, LocalSchemaResolver resolver, Path schemaFile) {

		if (resolver.getMissing().isEmpty()) {
			return;
		}
		message.append("\nThe schema imports files that are not available locally. Download them and place them next to ")
				.append(schemaFile.getFileName()).append(" in ").append(schemaFile.getParent()).append(':');
		for (Map.Entry<String, String> missing : resolver.getMissing().entrySet()) {
			message.append("\n  ").append(missing.getValue()).append(" (").append(missing.getKey()).append(')');
		}
	}

	/**
	 * Records parser findings as issues. Warnings stay warnings; errors and
	 * fatal errors are errors.
	 *
	 * After a fatal error the parser throws a new exception for the same
	 * problem; that one must not be reported again. Errors and warnings are
	 * never merged: findings on a DOM source carry no position, so equal
	 * messages may well be different problems.
	 */
	private static final class IssueCollector implements ErrorHandler {

		private final ValidationResult result;
		private final Set<String> fatalErrors = new HashSet<String>();

		IssueCollector(ValidationResult result) {
			this.result = result;
		}

		@Override
		public void warning(SAXParseException exception) {
			this.result.warning(null, message(exception));
		}

		@Override
		public void error(SAXParseException exception) {
			this.result.error(null, message(exception));
		}

		@Override
		public void fatalError(SAXParseException exception) {
			this.fatalErrors.add(fingerprint(exception));
			this.result.error(null, message(exception));
		}

		/**
		 * For an exception that ended parsing: reports it unless it repeats a
		 * fatal error the handler has seen already.
		 */
		void reportUnlessReported(SAXException exception) {
			if (!this.fatalErrors.contains(fingerprint(exception))) {
				this.result.error(null, exception instanceof SAXParseException
						? message((SAXParseException) exception)
						: exception.getMessage());
			}
		}

		private static String fingerprint(SAXException exception) {
			if (exception instanceof SAXParseException) {
				SAXParseException parseException = (SAXParseException) exception;
				return parseException.getSystemId() + ':' + parseException.getLineNumber() + ':'
						+ parseException.getColumnNumber() + ':' + parseException.getMessage();
			}
			return String.valueOf(exception.getMessage());
		}

		private static String message(SAXParseException exception) {
			// a DOM source has no line numbers
			return exception.getLineNumber() < 0
					? exception.getMessage()
					: "line " + exception.getLineNumber() + ": " + exception.getMessage();
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
