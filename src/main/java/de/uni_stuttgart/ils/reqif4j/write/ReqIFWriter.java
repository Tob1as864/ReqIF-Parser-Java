package de.uni_stuttgart.ils.reqif4j.write;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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

/**
 * Serializes a parsed {@link ReqIFDocument} back to ReqIF XML.
 *
 * The writer works on the object model, not on the source DOM, so a document
 * that was assembled or modified programmatically can be written just like one
 * that was read from a file:
 *
 * <pre>
 * ReqIF reqif = new ReqIF("in.reqif");
 * new ReqIFWriter().write(reqif.getReqIFDocument(), Path.of("out.reqif"));
 * </pre>
 *
 * Attribute values are emitted sorted by their definition id so the output is
 * reproducible. XHTML values keep their original nodes when the document was
 * read from a file, otherwise the rendered markup is parsed back in.
 */
public class ReqIFWriter {

	public static final String REQIF_NAMESPACE = "http://www.omg.org/spec/ReqIF/20110401/reqif.xsd";
	public static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";

	/** REQ-IF-TOOL-ID and SOURCE-TOOL-ID written when the header has none. */
	public static final String DEFAULT_TOOL_ID = "reqif4j";
	/** The only REQ-IF-VERSION the schema allows. */
	public static final String REQIF_VERSION = "1.0";

	private static final String XHTML_PREFIX = "xhtml";
	/** user data keys of the output DOM holding the LAST-CHANGE defaults */
	private static final String DEFAULT_LAST_CHANGE = "reqif4j.defaultLastChange";
	private static final String NOW = "reqif4j.now";
	/** the modified elements, which take the time of writing as LAST-CHANGE */
	private static final String STAMPED = "reqif4j.stamped";

	private boolean indent = false;
	private Clock clock = Clock.systemUTC();

	/**
	 * Enables pretty-printing.
	 *
	 * Off by default on purpose: the indenter inserts whitespace into mixed
	 * content, which changes XHTML attribute values (a paragraph would gain
	 * leading and trailing blanks). Only switch it on when readability of the
	 * output matters more than exact XHTML content.
	 */
	public ReqIFWriter setIndent(boolean indent) {
		this.indent = indent;
		return this;
	}


	/**
	 * Sets the clock that supplies the timestamp for required dates the model
	 * does not carry; see {@link #buildDocument(ReqIFDocument)}. Mainly useful
	 * to make generated output reproducible.
	 */
	public ReqIFWriter setClock(Clock clock) {
		this.clock = clock == null ? Clock.systemUTC() : clock;
		return this;
	}


	/**
	 * Writes the document to the given file.
	 */
	public void write(ReqIFDocument document, Path file) throws IOException {
		try (OutputStream out = Files.newOutputStream(file)) {
			write(document, out);
		}
	}

	/**
	 * Writes the document to the given stream. The stream is not closed.
	 */
	public void write(ReqIFDocument document, OutputStream out) throws IOException {
		serialize(document, out).apply();
	}

	/**
	 * Writes the document without yet taking the new LAST-CHANGE of modified
	 * elements into the model, for writers that store the output further and
	 * apply the stamps only once that succeeded.
	 */
	Stamps serialize(ReqIFDocument document, OutputStream out) throws IOException {
		Stamps stamps = new Stamps();
		try {
			transformer().transform(new DOMSource(build(document, stamps)), new StreamResult(out));
		} catch (TransformerException e) {
			throw new IOException("Failed to write ReqIF document", e);
		}
		return stamps;
	}

	/**
	 * The modified elements of a written document and the LAST-CHANGE they
	 * were written with.
	 */
	static final class Stamps {

		private final List<Identifiable> elements = new ArrayList<Identifiable>();
		private String lastChange;

		/**
		 * Takes the written LAST-CHANGE into the model; the elements are no
		 * longer modified.
		 */
		void apply() {
			for (Identifiable element : this.elements) {
				element.setLastChange(this.lastChange);
			}
		}
	}

	/**
	 * @return the document serialized as an XML string; like the other
	 *         output methods, this takes the LAST-CHANGE of modified elements
	 *         into the model, see {@link #buildDocument(ReqIFDocument)}
	 */
	public String toXml(ReqIFDocument document) {
		try {
			StringWriter writer = new StringWriter();
			Stamps stamps = new Stamps();
			transformer().transform(new DOMSource(build(document, stamps)), new StreamResult(writer));
			stamps.apply();
			return writer.toString();
		} catch (TransformerException e) {
			throw new ReqIFWriteException("Failed to serialize ReqIF document", e);
		}
	}


	/**
	 * Builds the output DOM from the object model.
	 *
	 * The schema requires some values a model may lack, typically one that was
	 * generated rather than parsed. They are filled in on output only; this
	 * method does not change the model:
	 *
	 * <ul>
	 * <li>LAST-CHANGE of an element: the time of writing if the element was
	 * {@link Identifiable#isModified() modified}; otherwise, if it has none,
	 * the header's CREATION-TIME, or the time of writing if the header has
	 * none either;</li>
	 * <li>header CREATION-TIME: the time of writing;</li>
	 * <li>header REQ-IF-TOOL-ID and SOURCE-TOOL-ID: {@value #DEFAULT_TOOL_ID};
	 * REQ-IF-VERSION: {@value #REQIF_VERSION}; TITLE: empty.</li>
	 * </ul>
	 *
	 * Not filled in, because no default would be meaningful: a missing header
	 * and missing identifiers. {@link de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator}
	 * reports both.
	 *
	 * The output methods ({@link #write(ReqIFDocument, OutputStream)},
	 * {@link #toXml(ReqIFDocument)}, ...) in addition give modified elements
	 * the LAST-CHANGE they were written with once the output succeeded; they
	 * are then no longer modified, so writing again does not report a new
	 * change.
	 */
	public Document buildDocument(ReqIFDocument document) {
		return build(document, new Stamps());
	}

	private Document build(ReqIFDocument document, Stamps stamps) {

		if (document == null) {
			throw new ReqIFWriteException("Cannot write a null ReqIF document");
		}

		Document xml = newDocument();
		// one timestamp for the whole document, so all defaults agree
		String now = this.clock.instant().truncatedTo(ChronoUnit.SECONDS).toString();
		String creationTime = document.getHeader() == null ? "" : document.getHeader().getCreationTime();
		xml.setUserData(DEFAULT_LAST_CHANGE, isBlank(creationTime) ? now : creationTime, null);
		xml.setUserData(NOW, now, null);
		stamps.lastChange = now;
		xml.setUserData(STAMPED, stamps.elements, null);

		Element root = xml.createElementNS(REQIF_NAMESPACE, "REQ-IF");
		root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + XHTML_PREFIX, XHTML_NAMESPACE);
		if (document.getLang() != null) {
			root.setAttributeNS(XMLConstants.XML_NS_URI, "xml:lang", document.getLang());
		}
		xml.appendChild(root);

		if (document.getHeader() != null) {
			root.appendChild(header(xml, document.getHeader(), now));
		}
		root.appendChild(coreContent(xml, document.getCoreContent()));

		// tool extensions are copied verbatim
		for (Node extension : document.getToolExtensions()) {
			root.appendChild(xml.importNode(extension, true));
		}

		// only needed while building; not part of the returned document
		xml.setUserData(DEFAULT_LAST_CHANGE, null, null);
		xml.setUserData(NOW, null, null);
		xml.setUserData(STAMPED, null, null);
		return xml;
	}


	/**
	 * REQ-IF-HEADER is an xsd:sequence; only COMMENT and REPOSITORY-ID are
	 * optional, the other elements get a default if the header lacks them.
	 */
	private Element header(Document xml, ReqIFHeader header, String now) {

		Element theHeader = element(xml, ReqIFConst.THE_HEADER);
		Element reqifHeader = element(xml, ReqIFConst.REQ_IF_HEADER);
		reqifHeader.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(header.getID()));
		theHeader.appendChild(reqifHeader);

		appendTextIfPresent(xml, reqifHeader, ReqIFConst.COMMENT, header.getComment());
		appendText(xml, reqifHeader, ReqIFConst.CREATION_TIME, orDefault(header.getCreationTime(), now));
		appendTextIfPresent(xml, reqifHeader, ReqIFConst.REPOSITORY_ID, header.getRepositoryID());
		appendText(xml, reqifHeader, ReqIFConst.REQ_IF_TOOL_ID, orDefault(header.getToolID(), DEFAULT_TOOL_ID));
		appendText(xml, reqifHeader, ReqIFConst.REQ_IF_VERSION, orDefault(header.getReqIFVersion(), REQIF_VERSION));
		appendText(xml, reqifHeader, ReqIFConst.SOURCE_TOOL_ID, orDefault(header.getSourceToolID(), DEFAULT_TOOL_ID));
		appendText(xml, reqifHeader, ReqIFConst.TITLE, nullToEmpty(header.getTitle()));

		return theHeader;
	}

	private Element coreContent(Document xml, ReqIFCoreContent content) {

		Element coreContent = element(xml, ReqIFConst.CORE_CONTENT);
		Element reqifContent = element(xml, "REQ-IF-CONTENT");
		coreContent.appendChild(reqifContent);

		if (content == null) {
			return coreContent;
		}

		Element datatypes = element(xml, ReqIFConst.DATATYPES);
		for (Datatype datatype : content.getDatatypes().values()) {
			Element written = datatype(xml, datatype);
			if (written != null) {
				datatypes.appendChild(written);
			}
		}
		reqifContent.appendChild(datatypes);

		Element specTypes = element(xml, ReqIFConst.SPEC_TYPES);
		for (SpecType specType : content.getSpecTypes().values()) {
			Element written = specType(xml, specType);
			if (written != null) {
				specTypes.appendChild(written);
			}
		}
		reqifContent.appendChild(specTypes);

		Element specObjects = element(xml, ReqIFConst.SPEC_OBJECTS);
		for (SpecObject specObject : content.getSpecObjects().values()) {
			specObjects.appendChild(specObject(xml, specObject));
		}
		reqifContent.appendChild(specObjects);

		Element specRelations = element(xml, ReqIFConst.SPEC_RELATIONS);
		for (SpecRelation specRelation : content.getSpecRelation().values()) {
			specRelations.appendChild(specRelation(xml, specRelation));
		}
		reqifContent.appendChild(specRelations);

		Element specifications = element(xml, ReqIFConst.SPECIFICATIONS);
		for (Specification specification : content.getSpecifications().values()) {
			specifications.appendChild(specification(xml, specification));
		}
		reqifContent.appendChild(specifications);

		// REQ-IF-CONTENT is an xsd:sequence: the relation groups come last
		if (!content.getRelationGroups().isEmpty()) {
			Element relationGroups = element(xml, ReqIFConst.SPEC_RELATION_GROUPS);
			for (RelationGroup relationGroup : content.getRelationGroups().values()) {
				relationGroups.appendChild(relationGroup(xml, relationGroup));
			}
			reqifContent.appendChild(relationGroups);
		}

		return coreContent;
	}


	private Element datatype(Document xml, Datatype datatype) {

		String elementName = ReqIFElements.datatypeDefinition(datatype.getType());
		if (elementName == null) {
			// datatype kinds the parser does not model are written back under
			// their original element name
			elementName = datatype.getSourceElementName();
		}
		if (elementName == null) {
			return null;
		}

		Element definition = element(xml, elementName);
		identifiable(xml, definition, datatype);

		if (datatype instanceof DatatypeInteger) {
			DatatypeInteger integer = (DatatypeInteger) datatype;
			definition.setAttribute(ReqIFConst.MIN, Long.toString(integer.getMin()));
			definition.setAttribute(ReqIFConst.MAX, Long.toString(integer.getMax()));

		} else if (datatype instanceof DatatypeDouble) {
			DatatypeDouble real = (DatatypeDouble) datatype;
			definition.setAttribute(ReqIFConst.ACCURACY, Integer.toString(real.getAccuracy()));
			definition.setAttribute(ReqIFConst.MIN, XsdTypes.formatDouble(real.getMin()));
			definition.setAttribute(ReqIFConst.MAX, XsdTypes.formatDouble(real.getMax()));

		} else if (datatype instanceof DatatypeString) {
			definition.setAttribute(ReqIFConst.MAX_LENGTH,
					Integer.toString(((DatatypeString) datatype).getMaxLength()));

		} else if (datatype instanceof DatatypeEnumeration) {
			Element specifiedValues = element(xml, ReqIFConst.SPECIFIED_VALUES);
			for (DatatypeEnumerationValue value : ((DatatypeEnumeration) datatype).getEnumValues().values()) {
				specifiedValues.appendChild(enumValue(xml, value));
			}
			definition.appendChild(specifiedValues);
		}

		return definition;
	}

	private Element enumValue(Document xml, DatatypeEnumerationValue value) {

		Element enumValue = element(xml, ReqIFConst.ENUM_VALUE);
		identifiable(xml, enumValue, value);

		Element embedded = element(xml, ReqIFConst.EMBEDDED_VALUE);
		// both attributes are required, OTHER-CONTENT even when empty
		embedded.setAttribute(ReqIFConst.KEY, nullToEmpty(value.getKey()));
		embedded.setAttribute(ReqIFConst.OTHER_CONTENT, nullToEmpty(value.getOtherContent()));

		Element properties = element(xml, ReqIFConst.PROPERTIES);
		properties.appendChild(embedded);
		enumValue.appendChild(properties);

		return enumValue;
	}


	private Element specType(Document xml, SpecType specType) {

		// A spec type kind the parser does not model must keep its original
		// element name; writing it as a SPEC-OBJECT-TYPE would silently change
		// the document's meaning.
		String elementName = specType.getType();
		if (elementName == null || ReqIFConst.UNDEFINED.equals(elementName)) {
			elementName = specType.getSourceElementName();
		}
		if (elementName == null) {
			elementName = ReqIFConst.SPEC_OBJECT_TYPE;
		}

		Element type = element(xml, elementName);
		identifiable(xml, type, specType);

		Element specAttributes = element(xml, ReqIFConst.SPEC_ATTRIBUTES);
		for (AttributeDefinition definition : specType.getAttributeDefinitions().values()) {
			Element written = attributeDefinition(xml, definition);
			if (written != null) {
				specAttributes.appendChild(written);
			}
		}
		type.appendChild(specAttributes);

		return type;
	}

	private Element attributeDefinition(Document xml, AttributeDefinition definition) {

		Datatype datatype = definition.getDataType();
		// datatype kinds the parser does not model keep their original name
		String elementName = definition.getElementName();
		if (datatype == null || elementName == null) {
			return null;
		}

		Element attributeDefinition = element(xml, elementName);
		identifiable(xml, attributeDefinition, definition);
		setAttributeIfPresent(attributeDefinition, ReqIFConst.IS_EDITABLE, toString(definition.getEditable()));

		// required on every enumeration attribute definition, "false" included
		if (ReqIFConst.ENUMERATION.equals(datatype.getType())) {
			boolean multiValued = definition instanceof AttributeDefinitionEnumeration
					&& ((AttributeDefinitionEnumeration) definition).isMultiValued();
			attributeDefinition.setAttribute(ReqIFConst.MULTI_VALUED, Boolean.toString(multiValued));
		}

		Element type = element(xml, ReqIFConst.TYPE);
		String datatypeElement = ReqIFElements.datatypeDefinition(datatype.getType());
		if (datatypeElement == null) {
			datatypeElement = datatype.getSourceElementName();
		}
		Element datatypeRef = element(xml, ReqIFElements.datatypeDefinitionRef(datatypeElement));
		datatypeRef.setTextContent(nullToEmpty(datatype.getID()));
		type.appendChild(datatypeRef);
		attributeDefinition.appendChild(type);

		Element defaultValue = defaultValue(xml, definition, datatype);
		if (defaultValue != null) {
			attributeDefinition.appendChild(defaultValue);
		}

		return attributeDefinition;
	}

	private Element defaultValue(Document xml, AttributeDefinition definition, Datatype datatype) {

		if (definition instanceof AttributeDefinitionEnumeration) {
			List<String> refs = ((AttributeDefinitionEnumeration) definition).getDefaultValueRefs();
			if (refs.isEmpty()) {
				return null;
			}
			Element defaultValue = element(xml, ReqIFConst.DEFAULT_VALUE);
			defaultValue.appendChild(enumerationValue(xml, definition, refs));
			return defaultValue;
		}

		// an enumeration default is a list of enum value references, which only
		// an enumeration definition carries; text would be schema-invalid
		if (definition.getDefaultValue() == null || ReqIFConst.ENUMERATION.equals(datatype.getType())) {
			return null;
		}
		if (ReqIFConst.XHTML.equals(datatype.getType())) {
			// an XHTML value carries its content in a THE-VALUE element, not in
			// an attribute
			AttributeValueXHTML parsed = definition.getDefaultXhtmlValue();
			Element value = xhtmlValue(xml, definition,
					parsed != null ? parsed : new AttributeValueXHTML(definition.getDefaultValue(), definition));
			if (value == null) {
				return null;
			}
			Element defaultValue = element(xml, ReqIFConst.DEFAULT_VALUE);
			defaultValue.appendChild(value);
			return defaultValue;
		}
		String elementName = ReqIFElements.attributeValue(datatype.getType());
		if (elementName == null) {
			return null;
		}

		Element value = element(xml, elementName);
		value.setAttribute(ReqIFConst.THE_VALUE, definition.getDefaultValue());
		value.appendChild(definitionRef(xml, definition));

		Element defaultValue = element(xml, ReqIFConst.DEFAULT_VALUE);
		defaultValue.appendChild(value);
		return defaultValue;
	}


	private Element specObject(Document xml, SpecObject specObject) {

		Element element = element(xml, ReqIFConst.SPEC_OBJECT);
		identifiable(xml, element, specObject);
		element.appendChild(values(xml, specObject.getValues()));
		element.appendChild(typeRef(xml, ReqIFConst.SPEC_OBJECT_TYPE, specObject.getSpecTypeID()));
		return element;
	}

	private Element specRelation(Document xml, SpecRelation specRelation) {

		Element element = element(xml, ReqIFConst.SPEC_RELATION);
		identifiable(xml, element, specRelation);
		element.appendChild(values(xml, specRelation.getValues()));
		element.appendChild(typeRef(xml, ReqIFConst.SPEC_RELATION_TYPE, specRelation.getRelationTypeRef()));
		element.appendChild(objectRef(xml, ReqIFConst.SOURCE, specRelation.getSourceObjID()));
		element.appendChild(objectRef(xml, ReqIFConst.TARGET, specRelation.getTargetObjID()));
		return element;
	}

	private Element specification(Document xml, Specification specification) {

		Element element = element(xml, ReqIFConst.SPECIFICATION);
		identifiable(xml, element, specification);
		element.appendChild(values(xml, specification.getValues()));
		element.appendChild(typeRef(xml, ReqIFConst.SPECIFICATION_TYPE, specification.getSpecTypeID()));

		List<SpecHierarchy> children = specification.getChildren();
		if (!children.isEmpty()) {
			element.appendChild(children(xml, children));
		}
		return element;
	}

	private Element children(Document xml, List<SpecHierarchy> hierarchies) {

		Element children = element(xml, ReqIFConst.CHILDREN);
		for (SpecHierarchy hierarchy : hierarchies) {
			children.appendChild(specHierarchy(xml, hierarchy));
		}
		return children;
	}

	private Element specHierarchy(Document xml, SpecHierarchy hierarchy) {

		Element element = element(xml, ReqIFConst.SPEC_HIERARCHY);
		identifiable(xml, element, hierarchy);

		setAttributeIfPresent(element, ReqIFConst.IS_TABLE_INTERNAL, toString(hierarchy.getTableInternal()));
		setAttributeIfPresent(element, ReqIFConst.IS_EDITABLE, toString(hierarchy.getEditable()));
		if (!hierarchy.getEditableAttributeRefs().isEmpty()) {
			Element editableAtts = element(xml, ReqIFConst.EDITABLE_ATTS);
			SpecType objectType = hierarchy.getSpecObject() == null ? null
					: hierarchy.getSpecObject().getResolvedSpecType();
			for (java.util.Map.Entry<String, String> ref : hierarchy.getEditableAttributeRefs()) {
				// the reference must name the kind the definition is written as
				AttributeDefinition definition = objectType == null ? null
						: objectType.getAttributeDefinition(ref.getKey());
				Element refElement = element(xml, definition != null && definition.getElementName() != null
						? definition.getElementName() + "-REF" : ref.getValue());
				refElement.setTextContent(ref.getKey());
				editableAtts.appendChild(refElement);
			}
			element.appendChild(editableAtts);
		}
		// written also when unresolved, so the reference is not lost
		if (hierarchy.getSpecObjectID() != null) {
			element.appendChild(objectRef(xml, ReqIFConst.OBJECT, hierarchy.getSpecObjectID()));
		}
		List<SpecHierarchy> nested = hierarchy.getChildren();
		if (!nested.isEmpty()) {
			element.appendChild(children(xml, nested));
		}
		return element;
	}


	private Element values(Document xml, java.util.Map<String, AttributeValue> attributeValues) {

		Element values = element(xml, ReqIFConst.VALUES);

		// only the element's own values, in document order; defaults stay with
		// their attribute definitions
		for (AttributeValue attributeValue : attributeValues.values()) {
			Element written = attributeValue(xml, attributeValue);
			if (written != null) {
				values.appendChild(written);
			}
		}
		return values;
	}

	private Element attributeValue(Document xml, AttributeValue attributeValue) {

		AttributeDefinition definition = attributeValue.getAttributeDefinitionType();
		// a definition that cannot be written cannot be referenced either
		if (definition == null || definition.getDataType() == null || definition.getElementName() == null) {
			return null;
		}
		String datatypeCategory = definition.getDataType().getType();

		if (ReqIFConst.ENUMERATION.equals(datatypeCategory)) {
			if (!(attributeValue instanceof AttributeValueEnumeration)) {
				return null;
			}
			// an empty selection is a value too ("nothing selected"); left out, the
			// definition's default would apply when the document is read again
			return enumerationValue(xml, definition,
					((AttributeValueEnumeration) attributeValue).getValueRefs());
		}

		if (ReqIFConst.XHTML.equals(datatypeCategory)) {
			return xhtmlValue(xml, definition, attributeValue);
		}

		String elementName = ReqIFElements.attributeValue(datatypeCategory);
		if (elementName == null) {
			// datatype kinds the parser does not model: keep the original name
			elementName = attributeValue.getSourceElementName();
		}
		if (elementName == null) {
			return null;
		}
		Object value = attributeValue.getValue();
		if (value == null) {
			return null;
		}

		Element element = element(xml, elementName);
		element.setAttribute(ReqIFConst.THE_VALUE,
				value instanceof Double ? XsdTypes.formatDouble((Double) value) : String.valueOf(value));
		element.appendChild(definitionRef(xml, definition));
		return element;
	}

	private Element enumerationValue(Document xml, AttributeDefinition definition, List<String> enumValueRefs) {

		Element element = element(xml, ReqIFElements.attributeValue(ReqIFConst.ENUMERATION));
		element.appendChild(definitionRef(xml, definition));

		Element values = element(xml, ReqIFConst.VALUES);
		for (String ref : enumValueRefs) {
			Element enumRef = element(xml, ReqIFConst.ENUM_VALUE_REF);
			enumRef.setTextContent(ref);
			values.appendChild(enumRef);
		}
		element.appendChild(values);
		return element;
	}

	private Element xhtmlValue(Document xml, AttributeDefinition definition, AttributeValue attributeValue) {

		// THE-VALUE must hold an XHTML div or p; an attribute without content
		// is left out instead of being written as an invalid empty value
		Node content = xhtmlContent(xml, attributeValue);
		if (content == null) {
			return null;
		}

		Element element = element(xml, ReqIFElements.attributeValue(ReqIFConst.XHTML));
		element.appendChild(definitionRef(xml, definition));

		Element theValue = element(xml, ReqIFConst.THE_VALUE);
		theValue.appendChild(content);
		element.appendChild(theValue);

		if (attributeValue instanceof AttributeValueXHTML) {
			AttributeValueXHTML xhtml = (AttributeValueXHTML) attributeValue;
			setAttributeIfPresent(element, ReqIFConst.IS_SIMPLIFIED, toString(xhtml.getSimplified()));
			if (xhtml.getOriginalValue() != null && xhtml.getOriginalValue().getNode() != null) {
				Element original = element(xml, ReqIFConst.THE_ORIGINAL_VALUE);
				original.appendChild(xml.importNode(xhtml.getOriginalValue().getNode(), true));
				element.appendChild(original);
			}
		}
		return element;
	}

	/**
	 * Reuses the source nodes of a parsed document; for programmatically built
	 * values the rendered markup is parsed back into the xhtml namespace.
	 */
	private Node xhtmlContent(Document xml, AttributeValue attributeValue) {

		if (attributeValue instanceof AttributeValueXHTML) {
			AttributeValueXHTML xhtmlValue = (AttributeValueXHTML) attributeValue;
			if (xhtmlValue.getDivValue() != null && xhtmlValue.getDivValue().getNode() != null) {
				return xml.importNode(xhtmlValue.getDivValue().getNode(), true);
			}
		}

		Object value = attributeValue.getValue();
		if (value == null || value.toString().isEmpty()) {
			return null;
		}
		return xml.importNode(XhtmlParser.parseDiv(value.toString()), true);
	}

	private Element definitionRef(Document xml, AttributeDefinition definition) {

		Element definitionElement = element(xml, ReqIFConst.DEFINITION);
		Element ref = element(xml, definition.getElementName() + "-REF");
		ref.setTextContent(nullToEmpty(definition.getID()));
		definitionElement.appendChild(ref);
		return definitionElement;
	}

	private Element typeRef(Document xml, String specTypeKind, String specTypeID) {

		Element type = element(xml, ReqIFConst.TYPE);
		Element ref = element(xml, ReqIFElements.specTypeRef(specTypeKind));
		ref.setTextContent(nullToEmpty(specTypeID));
		type.appendChild(ref);
		return type;
	}

	private Element objectRef(Document xml, String wrapper, String specObjectID) {

		Element element = element(xml, wrapper);
		Element ref = element(xml, ReqIFConst.SPEC_OBJECT_REF);
		ref.setTextContent(nullToEmpty(specObjectID));
		element.appendChild(ref);
		return element;
	}

	private Element relationGroup(Document xml, RelationGroup relationGroup) {

		Element element = element(xml, ReqIFConst.RELATION_GROUP);
		identifiable(xml, element, relationGroup);

		element.appendChild(wrappedRef(xml, ReqIFConst.TYPE, ReqIFConst.RELATION_GROUP_TYPE_REF,
				relationGroup.getRelationGroupTypeRef()));
		element.appendChild(wrappedRef(xml, ReqIFConst.SOURCE_SPECIFICATION, ReqIFConst.SPECIFICATION_REF,
				relationGroup.getSourceSpecificationRef()));
		element.appendChild(wrappedRef(xml, ReqIFConst.TARGET_SPECIFICATION, ReqIFConst.SPECIFICATION_REF,
				relationGroup.getTargetSpecificationRef()));

		if (!relationGroup.getSpecRelationRefs().isEmpty()) {
			Element relations = element(xml, ReqIFConst.SPEC_RELATIONS);
			for (String ref : relationGroup.getSpecRelationRefs()) {
				Element relationRef = element(xml, ReqIFConst.SPEC_RELATION_REF);
				relationRef.setTextContent(ref);
				relations.appendChild(relationRef);
			}
			element.appendChild(relations);
		}
		return element;
	}

	private Element wrappedRef(Document xml, String wrapper, String refName, String id) {

		Element element = element(xml, wrapper);
		Element ref = element(xml, refName);
		ref.setTextContent(nullToEmpty(id));
		element.appendChild(ref);
		return element;
	}

	/**
	 * Writes IDENTIFIER, LONG-NAME, DESC, LAST-CHANGE and ALTERNATIVE-ID. Optional
	 * properties the element does not have are left out rather than written
	 * empty, so a document round-trips unchanged; the required LAST-CHANGE gets
	 * the document's default.
	 *
	 * The ALTERNATIVE-ID is wrapped twice, as the schema prescribes:
	 * {@code <ALTERNATIVE-ID><ALTERNATIVE-ID IDENTIFIER="..."/></ALTERNATIVE-ID>}.
	 * It must be the first child, so call this right after creating the element.
	 */
	private void identifiable(Document xml, Element element, Identifiable identifiable) {

		element.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(identifiable.getID()));
		setAttributeIfPresent(element, ReqIFConst.LONG_NAME, identifiable.getName());
		setAttributeIfPresent(element, ReqIFConst.DESC, identifiable.getDesc());
		// required by the schema; a modified element changed now, see
		// buildDocument for the default of one that never had a value
		if (identifiable.isModified()) {
			@SuppressWarnings("unchecked")
			List<Identifiable> stamped = (List<Identifiable>) xml.getUserData(STAMPED);
			stamped.add(identifiable);
		}
		String lastChange = identifiable.isModified() ? (String) xml.getUserData(NOW)
				: isBlank(identifiable.getLastChange()) ? (String) xml.getUserData(DEFAULT_LAST_CHANGE)
				: identifiable.getLastChange();
		element.setAttribute(ReqIFConst.LAST_CHANGE, lastChange);

		String alternativeID = identifiable.getAlternativeID();
		if (alternativeID != null && !alternativeID.isEmpty()) {
			Element wrapper = element(xml, ReqIFConst.ALTERNATIVE_ID);
			Element alternative = element(xml, ReqIFConst.ALTERNATIVE_ID);
			alternative.setAttribute(ReqIFConst.IDENTIFIER, alternativeID);
			wrapper.appendChild(alternative);
			element.appendChild(wrapper);
		}
	}

	private static void setAttributeIfPresent(Element element, String name, String value) {
		if (value != null) {
			element.setAttribute(name, value);
		}
	}

	private Element element(Document xml, String name) {
		return xml.createElementNS(REQIF_NAMESPACE, name);
	}

	private void appendTextIfPresent(Document xml, Element parent, String name, String text) {

		if (text == null || text.isEmpty()) {
			return;
		}
		appendText(xml, parent, name, text);
	}

	private void appendText(Document xml, Element parent, String name, String text) {

		Element element = element(xml, name);
		element.setTextContent(text);
		parent.appendChild(element);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String toString(Boolean value) {
		return value == null ? null : value.toString();
	}

	private static String orDefault(String value, String fallback) {
		return isBlank(value) ? fallback : value;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private Document newDocument() {
		try {
			return SecureXml.newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			throw new ReqIFWriteException("Failed to create an XML document", e);
		}
	}

	private Transformer transformer() {
		try {
			Transformer transformer = SecureXml.newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
			transformer.setOutputProperty(OutputKeys.INDENT, this.indent ? "yes" : "no");
			if (this.indent) {
				transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			}
			return transformer;
		} catch (TransformerException e) {
			throw new ReqIFWriteException("Failed to create an XML transformer", e);
		}
	}
}
