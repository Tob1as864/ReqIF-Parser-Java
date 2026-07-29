package de.uni_stuttgart.ils.reqif4j.write;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinitionEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumerationValue;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeInteger;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeString;
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

	private static final String XHTML_PREFIX = "xhtml";

	private boolean indent = false;

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
		try {
			transformer().transform(new DOMSource(buildDocument(document)), new StreamResult(out));
		} catch (TransformerException e) {
			throw new IOException("Failed to write ReqIF document", e);
		}
	}

	/**
	 * @return the document serialized as an XML string
	 */
	public String toXml(ReqIFDocument document) {
		try {
			StringWriter writer = new StringWriter();
			transformer().transform(new DOMSource(buildDocument(document)), new StreamResult(writer));
			return writer.toString();
		} catch (TransformerException e) {
			throw new ReqIFWriteException("Failed to serialize ReqIF document", e);
		}
	}


	/**
	 * Builds the output DOM from the object model.
	 */
	public Document buildDocument(ReqIFDocument document) {

		if (document == null) {
			throw new ReqIFWriteException("Cannot write a null ReqIF document");
		}

		Document xml = newDocument();
		Element root = xml.createElementNS(REQIF_NAMESPACE, "REQ-IF");
		root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:" + XHTML_PREFIX, XHTML_NAMESPACE);
		xml.appendChild(root);

		if (document.getHeader() != null) {
			root.appendChild(header(xml, document.getHeader()));
		}
		root.appendChild(coreContent(xml, document.getCoreContent()));

		// tool extensions are copied verbatim
		for (Node extension : document.getToolExtensions()) {
			root.appendChild(xml.importNode(extension, true));
		}

		return xml;
	}


	private Element header(Document xml, ReqIFHeader header) {

		Element theHeader = element(xml, ReqIFConst.THE_HEADER);
		Element reqifHeader = element(xml, ReqIFConst.REQ_IF_HEADER);
		reqifHeader.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(header.getID()));
		theHeader.appendChild(reqifHeader);

		appendTextIfPresent(xml, reqifHeader, ReqIFConst.COMMENT, header.getComment());
		appendTextIfPresent(xml, reqifHeader, ReqIFConst.CREATION_TIME, header.getCreationTime());
		appendTextIfPresent(xml, reqifHeader, ReqIFConst.REQ_IF_TOOL_ID, header.getToolID());
		appendTextIfPresent(xml, reqifHeader, ReqIFConst.REQ_IF_VERSION, header.getReqIFVersion());
		appendTextIfPresent(xml, reqifHeader, ReqIFConst.SOURCE_TOOL_ID, header.getSourceToolID());
		appendTextIfPresent(xml, reqifHeader, ReqIFConst.TITLE, header.getTitle());

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

		if (!content.getRelationGroups().isEmpty()) {
			Element relationGroups = element(xml, ReqIFConst.SPEC_RELATION_GROUPS);
			for (RelationGroup relationGroup : content.getRelationGroups().values()) {
				relationGroups.appendChild(relationGroup(xml, relationGroup));
			}
			reqifContent.appendChild(relationGroups);
		}

		Element specifications = element(xml, ReqIFConst.SPECIFICATIONS);
		for (Specification specification : content.getSpecifications().values()) {
			specifications.appendChild(specification(xml, specification));
		}
		reqifContent.appendChild(specifications);

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
		definition.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(datatype.getID()));
		definition.setAttribute(ReqIFConst.LONG_NAME, nullToEmpty(datatype.getName()));
		appendAlternativeID(xml, definition, datatype.getAlternativeID());

		if (datatype instanceof DatatypeInteger) {
			DatatypeInteger integer = (DatatypeInteger) datatype;
			definition.setAttribute(ReqIFConst.MIN, Long.toString(integer.getMin()));
			definition.setAttribute(ReqIFConst.MAX, Long.toString(integer.getMax()));

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
		enumValue.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(value.getID()));
		enumValue.setAttribute(ReqIFConst.LONG_NAME, nullToEmpty(value.getName()));

		Element embedded = element(xml, ReqIFConst.EMBEDDED_VALUE);
		embedded.setAttribute(ReqIFConst.KEY, nullToEmpty(value.getKey()));
		if (value.getOtherContent() != null && !value.getOtherContent().isEmpty()) {
			embedded.setAttribute(ReqIFConst.OTHER_CONTENT, value.getOtherContent());
		}

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
		type.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(specType.getID()));
		type.setAttribute(ReqIFConst.LONG_NAME, nullToEmpty(specType.getName()));
		appendAlternativeID(xml, type, specType.getAlternativeID());

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
		if (datatype == null) {
			return null;
		}
		String elementName = ReqIFElements.attributeDefinition(datatype.getType());
		if (elementName == null) {
			// datatype kinds the parser does not model: keep the original name
			elementName = definition.getSourceElementName();
		}
		if (elementName == null) {
			return null;
		}

		Element attributeDefinition = element(xml, elementName);
		attributeDefinition.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(definition.getID()));
		attributeDefinition.setAttribute(ReqIFConst.LONG_NAME, nullToEmpty(definition.getName()));
		appendAlternativeID(xml, attributeDefinition, definition.getAlternativeID());

		if (definition instanceof AttributeDefinitionEnumeration
				&& ((AttributeDefinitionEnumeration) definition).isMultiValued()) {
			attributeDefinition.setAttribute(ReqIFConst.MULTI_VALUED, "true");
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

		if (definition.getDefaultValue() == null) {
			return null;
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
		element.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(specObject.getID()));
		appendAlternativeID(xml, element, specObject.getAlternativeID());
		element.appendChild(values(xml, specObject.getAttributes()));
		element.appendChild(typeRef(xml, ReqIFConst.SPEC_OBJECT_TYPE, specObject.getSpecTypeID()));
		return element;
	}

	private Element specRelation(Document xml, SpecRelation specRelation) {

		Element element = element(xml, ReqIFConst.SPEC_RELATION);
		element.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(specRelation.getID()));
		appendAlternativeID(xml, element, specRelation.getAlternativeID());
		element.appendChild(values(xml, specRelation.getAttributes()));
		element.appendChild(typeRef(xml, ReqIFConst.SPEC_RELATION_TYPE, specRelation.getRelationTypeRef()));
		element.appendChild(objectRef(xml, ReqIFConst.SOURCE, specRelation.getSourceObjID()));
		element.appendChild(objectRef(xml, ReqIFConst.TARGET, specRelation.getTargetObjID()));
		return element;
	}

	private Element specification(Document xml, Specification specification) {

		Element element = element(xml, ReqIFConst.SPECIFICATION);
		element.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(specification.getID()));
		element.setAttribute(ReqIFConst.LONG_NAME, nullToEmpty(specification.getName()));
		appendAlternativeID(xml, element, specification.getAlternativeID());
		element.appendChild(values(xml, specification.getAttributes()));
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
		element.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(hierarchy.getSpecHierarchyID()));
		appendAlternativeID(xml, element, hierarchy.getAlternativeID());

		if (hierarchy.getSpecObject() != null) {
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

		List<AttributeValue> sorted = new ArrayList<AttributeValue>(attributeValues.values());
		// deterministic output regardless of the map implementation
		sorted.sort(Comparator.comparing(value -> {
			AttributeDefinition definition = value.getAttributeDefinitionType();
			return definition == null || definition.getID() == null ? "" : definition.getID();
		}));

		for (AttributeValue attributeValue : sorted) {
			Element written = attributeValue(xml, attributeValue);
			if (written != null) {
				values.appendChild(written);
			}
		}
		return values;
	}

	private Element attributeValue(Document xml, AttributeValue attributeValue) {

		AttributeDefinition definition = attributeValue.getAttributeDefinitionType();
		if (definition == null || definition.getDataType() == null) {
			return null;
		}
		String datatypeCategory = definition.getDataType().getType();

		if (ReqIFConst.ENUMERATION.equals(datatypeCategory)) {
			if (!(attributeValue instanceof AttributeValueEnumeration)) {
				return null;
			}
			List<String> refs = ((AttributeValueEnumeration) attributeValue).getValueRefs();
			if (refs.isEmpty()) {
				return null;
			}
			return enumerationValue(xml, definition, refs);
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
		element.setAttribute(ReqIFConst.THE_VALUE, String.valueOf(value));
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

		Element element = element(xml, ReqIFElements.attributeValue(ReqIFConst.XHTML));
		element.appendChild(definitionRef(xml, definition));

		Element theValue = element(xml, ReqIFConst.THE_VALUE);
		Node content = xhtmlContent(xml, attributeValue);
		if (content != null) {
			theValue.appendChild(content);
		}
		element.appendChild(theValue);
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
		return xml.importNode(parseXhtml(value.toString()), true);
	}

	private Node parseXhtml(String markup) {

		String namespaced = markup.startsWith("<div")
				? markup.replaceFirst("<div", "<div xmlns=\"" + XHTML_NAMESPACE + "\"")
				: "<div xmlns=\"" + XHTML_NAMESPACE + "\">" + markup + "</div>";
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			return factory.newDocumentBuilder()
					.parse(new ByteArrayInputStream(namespaced.getBytes(StandardCharsets.UTF_8)))
					.getDocumentElement();
		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new ReqIFWriteException("XHTML attribute value is not well-formed: " + markup, e);
		}
	}


	private Element definitionRef(Document xml, AttributeDefinition definition) {

		Element definitionElement = element(xml, ReqIFConst.DEFINITION);
		String refName = ReqIFElements.attributeDefinitionRef(definition.getDataType().getType());
		if (refName == null && definition.getSourceElementName() != null) {
			refName = definition.getSourceElementName() + "-REF";
		}
		Element ref = element(xml, refName);
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
		element.setAttribute(ReqIFConst.IDENTIFIER, nullToEmpty(relationGroup.getID()));
		element.setAttribute(ReqIFConst.LONG_NAME, nullToEmpty(relationGroup.getName()));
		appendAlternativeID(xml, element, relationGroup.getAlternativeID());

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
	 * ALTERNATIVE-ID is the first child of an identifiable element.
	 */
	private void appendAlternativeID(Document xml, Element parent, String alternativeID) {

		if (alternativeID == null || alternativeID.isEmpty()) {
			return;
		}
		Element element = element(xml, ReqIFConst.ALTERNATIVE_ID);
		element.setAttribute(ReqIFConst.IDENTIFIER, alternativeID);
		parent.appendChild(element);
	}

	private Element element(Document xml, String name) {
		return xml.createElementNS(REQIF_NAMESPACE, name);
	}

	private void appendTextIfPresent(Document xml, Element parent, String name, String text) {

		if (text == null || text.isEmpty()) {
			return;
		}
		Element element = element(xml, name);
		element.setTextContent(text);
		parent.appendChild(element);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private Document newDocument() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.newDocument();
		} catch (ParserConfigurationException e) {
			throw new ReqIFWriteException("Failed to create an XML document", e);
		}
	}

	private Transformer transformer() {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
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
