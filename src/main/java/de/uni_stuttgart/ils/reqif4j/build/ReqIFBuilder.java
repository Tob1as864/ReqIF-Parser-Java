package de.uni_stuttgart.ils.reqif4j.build;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeBoolean;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeDate;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeDouble;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumerationValue;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeInteger;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeString;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeXHTML;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFHeader;
import de.uni_stuttgart.ils.reqif4j.specification.RelationGroup;
import de.uni_stuttgart.ils.reqif4j.specification.RelationGroupType;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObjectType;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelation;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelationType;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.specification.SpecificationType;
import de.uni_stuttgart.ils.reqif4j.specification.TypeClassifier;
import de.uni_stuttgart.ils.reqif4j.util.SecureXml;
import de.uni_stuttgart.ils.reqif4j.util.XsdTypes;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * Fluent builder for creating ReqIF documents from scratch. The result is the
 * same object model the parser produces, so it can be written with
 * {@code ReqIFWriter}:
 *
 * <pre>
 * ReqIFDocument document = ReqIFBuilder.create()
 *         .header(h -&gt; h.id("hdr-1").title("My Spec").toolID("reqif4j"))
 *         .stringDatatype("dt-string", "String", 4096)
 *         .xhtmlDatatype("dt-xhtml", "XHTML")
 *         .specObjectType("st-req", "Requirement Type", t -&gt; t
 *                 .stringAttribute("ad-title", "ReqIF.Name", "dt-string")
 *                 .xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml"))
 *         .specificationType("st-spec", "Specification Type", t -&gt; {})
 *         .specObject("so-1", "st-req", o -&gt; o
 *                 .set("ad-title", "First requirement")
 *                 .setXhtml("ad-text", "&lt;p&gt;The system shall boot.&lt;/p&gt;"))
 *         .specification("spec-1", "Main Spec", "st-spec", s -&gt; s
 *                 .child("sh-1", "so-1"))
 *         .build();
 *
 * new ReqIFWriter().write(document, Path.of("out.reqif"));
 * </pre>
 *
 * Identifiers are validated while building: referencing an unknown datatype,
 * spec type or spec object fails immediately with a
 * {@link ReqIFBuildException} instead of producing a broken document.
 */
public class ReqIFBuilder {

	/** IDENTIFIER of the header if {@link HeaderBuilder#id(String)} is not set. */
	public static final String DEFAULT_HEADER_ID = "reqif4j-header";

	private final Map<String, Datatype> datatypes = new LinkedHashMap<String, Datatype>();
	private final Map<String, SpecType> specTypes = new LinkedHashMap<String, SpecType>();
	private final Map<String, SpecObject> specObjects = new LinkedHashMap<String, SpecObject>();
	private final Map<String, SpecRelation> specRelations = new LinkedHashMap<String, SpecRelation>();
	private final Map<String, Specification> specifications = new LinkedHashMap<String, Specification>();
	private final List<RelationGroup> relationGroups = new ArrayList<RelationGroup>();
	private final List<Element> toolExtensions = new ArrayList<Element>();
	private String lang;

	private final Map<String, String> identifiers = new LinkedHashMap<String, String>();
	/** the identifiers claimed during the running call, to undo it if it fails */
	private List<String> claimed;
	/** every element by IDENTIFIER, for with(); rebuilt after elements were added */
	private Map<String, Identifiable> elements;
	private final HeaderBuilder headerBuilder = new HeaderBuilder();
	private TypeClassifier typeClassifier = TypeClassifier.defaultClassifier();


	public static ReqIFBuilder create() {
		return new ReqIFBuilder();
	}

	/**
	 * Sets the classifier used to derive the content category of the created
	 * spec objects; defaults to the LONG-NAME heuristic.
	 */
	public ReqIFBuilder typeClassifier(TypeClassifier typeClassifier) {
		this.typeClassifier = typeClassifier == null ? TypeClassifier.defaultClassifier() : typeClassifier;
		return this;
	}

	public ReqIFBuilder header(Consumer<HeaderBuilder> header) {
		header.accept(this.headerBuilder);
		return this;
	}

	/**
	 * @param lang the document language (xml:lang), e.g. "de" or "en-US"
	 */
	public ReqIFBuilder lang(String lang) {
		this.lang = lang;
		return this;
	}

	/**
	 * Adds a tool extension (REQ-IF-TOOL-EXTENSION): vendor-specific XML the
	 * document carries along without interpreting it.
	 *
	 * @param xml the content, one or more elements, e.g.
	 *            {@code <tool:settings xmlns:tool="urn:example"/>}
	 * @throws ReqIFBuildException if the content is not well-formed XML, or
	 *         holds text or elements without a namespace of their own, which
	 *         the schema does not allow there
	 */
	public ReqIFBuilder toolExtension(String xml) {
		this.toolExtensions.add(toolExtensionElement(xml == null ? "" : xml));
		return this;
	}

	/**
	 * Sets the properties every element has - LONG-NAME, DESC, LAST-CHANGE,
	 * ALTERNATIVE-ID - on an element added before, e.g.
	 * {@code .with("so-1", e -> e.setDesc("Boot").setAlternativeID("REQ-1"))}.
	 *
	 * @param id any element: datatype, enum value, spec type, attribute
	 *           definition, spec object, relation, specification, spec
	 *           hierarchy or relation group
	 * @throws ReqIFBuildException if no element has this IDENTIFIER
	 */
	public ReqIFBuilder with(String id, Consumer<Identifiable> properties) {
		if (this.elements == null) {
			this.elements = new LinkedHashMap<String, Identifiable>();
			for (Identifiable element : allElements()) {
				this.elements.putIfAbsent(element.getID(), element);
			}
		}
		Identifiable element = this.elements.get(id);
		if (element == null) {
			throw new ReqIFBuildException("Unknown element: " + id);
		}
		String previous = element.getLastChange();
		properties.accept(element);
		String lastChange = element.getLastChange();
		if (lastChange != null && !XsdTypes.isDateTime(lastChange)) {
			element.setLastChange(previous);
			throw new ReqIFBuildException("LAST-CHANGE of " + id + " is not an xsd:dateTime: " + lastChange);
		}
		return this;
	}

	private List<Identifiable> allElements() {

		List<Identifiable> all = new ArrayList<Identifiable>();
		for (Datatype datatype : this.datatypes.values()) {
			all.add(datatype);
			if (datatype instanceof DatatypeEnumeration) {
				all.addAll(((DatatypeEnumeration) datatype).getEnumValues().values());
			}
		}
		for (SpecType specType : this.specTypes.values()) {
			all.add(specType);
			all.addAll(specType.getAttributeDefinitions().values());
		}
		all.addAll(this.specObjects.values());
		all.addAll(this.specRelations.values());
		for (Specification specification : this.specifications.values()) {
			all.add(specification);
			all.addAll(specification.getAllSpecHierarchies());
		}
		all.addAll(this.relationGroups);
		return all;
	}


	// ---------------------------------------------------------------- datatypes

	/** Adds a datatype the builder has no shorthand for. */
	public ReqIFBuilder datatype(Datatype datatype) {
		return atomically(() -> {
			claim(datatype.getID(), "datatype");
			this.datatypes.put(datatype.getID(), datatype);
		});
	}

	public ReqIFBuilder stringDatatype(String id, String name, int maxLength) {
		return datatype(new DatatypeString(id, name, Integer.toString(maxLength)));
	}

	public ReqIFBuilder integerDatatype(String id, String name, long min, long max) {
		return datatype(new DatatypeInteger(id, name, Long.toString(min), Long.toString(max)));
	}

	public ReqIFBuilder booleanDatatype(String id, String name) {
		return datatype(new DatatypeBoolean(id, name));
	}

	public ReqIFBuilder dateDatatype(String id, String name) {
		return datatype(new DatatypeDate(id, name));
	}

	/**
	 * Written as DATATYPE-DEFINITION-REAL, with the default accuracy and the
	 * full double range ({@link DatatypeDouble#DEFAULT_ACCURACY}).
	 */
	public ReqIFBuilder realDatatype(String id, String name) {
		return datatype(new DatatypeDouble(id, name));
	}

	/**
	 * Written as DATATYPE-DEFINITION-REAL.
	 *
	 * @param accuracy the maximum precision of the values, not negative
	 * @param min      lower bound, may be {@link Double#NEGATIVE_INFINITY}
	 * @param max      upper bound, not below {@code min}
	 */
	public ReqIFBuilder realDatatype(String id, String name, int accuracy, double min, double max) {
		if (accuracy < 0) {
			throw new ReqIFBuildException("Accuracy of real datatype '" + id + "' must not be negative: " + accuracy);
		}
		if (Double.isNaN(min) || Double.isNaN(max) || min > max) {
			throw new ReqIFBuildException("Real datatype '" + id + "' needs min <= max, got " + min + " and " + max);
		}
		return datatype(new DatatypeDouble(id, name, accuracy, min, max));
	}

	public ReqIFBuilder xhtmlDatatype(String id, String name) {
		return datatype(new DatatypeXHTML(id, name));
	}

	public ReqIFBuilder enumerationDatatype(String id, String name, Consumer<EnumerationBuilder> values) {

		EnumerationBuilder builder = new EnumerationBuilder();
		values.accept(builder);
		return datatype(new DatatypeEnumeration(id, name, builder.values));
	}


	// --------------------------------------------------------------- spec types

	public ReqIFBuilder specObjectType(String id, String name, Consumer<SpecTypeBuilder> attributes) {
		return specType(new SpecObjectType(id, name), attributes);
	}

	public ReqIFBuilder specificationType(String id, String name, Consumer<SpecTypeBuilder> attributes) {
		return specType(new SpecificationType(id, name), attributes);
	}

	public ReqIFBuilder specRelationType(String id, String name, Consumer<SpecTypeBuilder> attributes) {
		return specType(new SpecRelationType(id, name), attributes);
	}

	public ReqIFBuilder relationGroupType(String id, String name, Consumer<SpecTypeBuilder> attributes) {
		return specType(new RelationGroupType(id, name), attributes);
	}

	private ReqIFBuilder specType(SpecType specType, Consumer<SpecTypeBuilder> attributes) {
		return atomically(() -> {
			claim(specType.getID(), "spec type");
			attributes.accept(new SpecTypeBuilder(specType, this.datatypes, this::claim));
			this.specTypes.put(specType.getID(), specType);
		});
	}


	// ------------------------------------------------------------- spec objects

	public ReqIFBuilder specObject(String id, String specTypeID, Consumer<ValuesBuilder> values) {
		return atomically(() -> {
			claim(id, "spec object");
			SpecType specType = requireSpecType(specTypeID, ReqIFConst.SPEC_OBJECT_TYPE);
			ValuesBuilder builder = new ValuesBuilder(specType);
			values.accept(builder);

			this.specObjects.put(id, new SpecObject(id, specType, builder.build(), this.typeClassifier));
		});
	}

	public ReqIFBuilder specObject(String id, String specTypeID) {
		return specObject(id, specTypeID, values -> { });
	}


	// ----------------------------------------------------------- spec relations

	public ReqIFBuilder specRelation(String id, String specTypeID, String sourceID, String targetID,
			Consumer<ValuesBuilder> values) {

		return atomically(() -> {
			claim(id, "spec relation");
			SpecType specType = requireSpecType(specTypeID, ReqIFConst.SPEC_RELATION_TYPE);
			requireSpecObject(sourceID);
			requireSpecObject(targetID);

			ValuesBuilder builder = new ValuesBuilder(specType);
			values.accept(builder);

			this.specRelations.put(id, new SpecRelation(id, specType, sourceID, targetID, builder.build()));
		});
	}

	public ReqIFBuilder specRelation(String id, String specTypeID, String sourceID, String targetID) {
		return specRelation(id, specTypeID, sourceID, targetID, values -> { });
	}


	// ----------------------------------------------------------- specifications

	public ReqIFBuilder specification(String id, String name, String specTypeID,
			Consumer<SpecificationBuilder> content) {

		return atomically(() -> {
			claim(id, "specification");
			SpecType specType = requireSpecType(specTypeID, ReqIFConst.SPECIFICATION_TYPE);
			SpecificationBuilder builder = new SpecificationBuilder(specType);
			content.accept(builder);

			this.specifications.put(id, new Specification(id, name, specType, builder.values.build(),
					builder.children(1, 0)));
		});
	}


	// ----------------------------------------------------------- relation groups

	/**
	 * Adds a relation group: relations between a source and a target
	 * specification, e.g. a traceability view between two of them.
	 *
	 * @param specRelationIDs the relations that belong to the group
	 */
	public ReqIFBuilder relationGroup(String id, String name, String relationGroupTypeID,
			String sourceSpecificationID, String targetSpecificationID, String... specRelationIDs) {

		return atomically(() -> {
			claim(id, "relation group");
			requireSpecType(relationGroupTypeID, ReqIFConst.RELATION_GROUP_TYPE);
			requireSpecification(sourceSpecificationID);
			requireSpecification(targetSpecificationID);
			List<String> relations = specRelationIDs == null ? List.of() : Arrays.asList(specRelationIDs);
			for (String relationID : relations) {
				if (!this.specRelations.containsKey(relationID)) {
					throw new ReqIFBuildException("Unknown spec relation: " + relationID);
				}
			}
			this.relationGroups.add(new RelationGroup(id, name, relationGroupTypeID, sourceSpecificationID,
					targetSpecificationID, relations));
		});
	}

	private void requireSpecification(String specificationID) {
		if (!this.specifications.containsKey(specificationID)) {
			throw new ReqIFBuildException("Unknown specification: " + specificationID);
		}
	}


	/**
	 * @return the assembled document, ready to be written
	 */
	public ReqIFDocument build() {

		ReqIFCoreContent content = new ReqIFCoreContent();
		for (Datatype datatype : this.datatypes.values()) {
			content.addDatatype(datatype);
		}
		for (SpecType specType : this.specTypes.values()) {
			content.addSpecType(specType);
		}
		for (SpecObject specObject : this.specObjects.values()) {
			content.addSpecObject(specObject);
		}
		for (SpecRelation relation : this.specRelations.values()) {
			content.addSpecRelation(relation);
		}
		for (Specification specification : this.specifications.values()) {
			content.addSpecification(specification);
		}
		for (RelationGroup relationGroup : this.relationGroups) {
			content.addRelationGroup(relationGroup);
		}

		// the elements are new, not changed: they get the default LAST-CHANGE
		content.clearModified();
		ReqIFDocument document = new ReqIFDocument(this.headerBuilder.build(defaultHeaderID()), content)
				.setLang(this.lang);
		if (!this.toolExtensions.isEmpty()) {
			document.getToolExtensions().add(toolExtensionsElement(this.toolExtensions));
		}
		return document;
	}

	/**
	 * @return REQ-IF-TOOL-EXTENSION holding the content
	 * @throws ReqIFBuildException if the content is not well-formed XML or
	 *         not what the schema allows there: elements of a namespace other
	 *         than ReqIF's
	 */
	private static Element toolExtensionElement(String xml) {

		String wrapped = "<reqif:" + ReqIFConst.REQ_IF_TOOL_EXTENSION + " xmlns:reqif=\"" + ReqIFWriter.REQIF_NAMESPACE
				+ "\">" + xml + "</reqif:" + ReqIFConst.REQ_IF_TOOL_EXTENSION + ">";
		Element extension;
		try {
			DocumentBuilder parser = SecureXml.newDocumentBuilder();
			// the exception says what is wrong; nothing goes to stderr
			parser.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
			extension = parser.parse(new InputSource(new StringReader(wrapped))).getDocumentElement();
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new ReqIFBuildException("Tool extension is not well-formed XML: " + e.getMessage(), e);
		}
		for (Node child = extension.getFirstChild(); child != null; child = child.getNextSibling()) {
			if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
					&& !child.getNodeValue().isBlank()) {
				throw new ReqIFBuildException("A tool extension holds elements only, not text: " + xml);
			}
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				String namespace = child.getNamespaceURI();
				if (namespace == null || namespace.isEmpty() || ReqIFWriter.REQIF_NAMESPACE.equals(namespace)) {
					throw new ReqIFBuildException("The elements of a tool extension need a namespace of their own, "
							+ "not ReqIF's and not none: " + child.getNodeName());
				}
			}
		}
		return extension;
	}

	/**
	 * @return TOOL-EXTENSIONS holding the extensions; the schema allows one
	 */
	private static Element toolExtensionsElement(List<Element> extensions) {
		try {
			Document document = SecureXml.newDocumentBuilder().newDocument();
			Element toolExtensions = document.createElementNS(ReqIFWriter.REQIF_NAMESPACE, ReqIFConst.TOOL_EXTENSIONS);
			for (Element extension : extensions) {
				Element imported = (Element) document.importNode(extension, true);
				// the prefix only served the parsing of the content
				imported.setPrefix(null);
				imported.removeAttributeNS(javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "reqif");
				toolExtensions.appendChild(imported);
			}
			return toolExtensions;
		} catch (ParserConfigurationException e) {
			throw new ReqIFBuildException("Cannot create the tool extensions: " + e.getMessage(), e);
		}
	}


	/**
	 * The schema requires a header IDENTIFIER that is unique in the document;
	 * if none was set, the first free one of reqif4j-header, reqif4j-header-2, ...
	 */
	private String defaultHeaderID() {

		Set<String> used = new HashSet<String>(this.identifiers.keySet());
		for (SpecType specType : this.specTypes.values()) {
			used.addAll(specType.getAttributeDefinitions().keySet());
		}
		for (Datatype datatype : this.datatypes.values()) {
			if (datatype instanceof DatatypeEnumeration) {
				used.addAll(((DatatypeEnumeration) datatype).getEnumValues().keySet());
			}
		}
		String id = DEFAULT_HEADER_ID;
		for (int suffix = 2; used.contains(id); suffix++) {
			id = DEFAULT_HEADER_ID + "-" + suffix;
		}
		return id;
	}


	/**
	 * ReqIF identifiers must be unique across the whole document, so a clash is
	 * refused instead of silently replacing the earlier element.
	 */
	void claim(String id, String kind) {

		if (id == null || id.isBlank()) {
			throw new ReqIFBuildException("A " + kind + " needs an identifier");
		}
		String previousKind = this.identifiers.get(id);
		if (previousKind != null) {
			throw new ReqIFBuildException("Identifier '" + id + "' is already used by a " + previousKind
					+ "; identifiers must be unique across the document");
		}
		this.identifiers.put(id, kind);
		if (this.claimed != null) {
			this.claimed.add(id);
		}
		this.elements = null;
	}

	/**
	 * Adds an element and the elements it contains; if that fails, the
	 * identifiers claimed on the way are free again, so the call can be
	 * repeated with corrected arguments.
	 */
	private ReqIFBuilder atomically(Runnable add) {
		List<String> outer = this.claimed;
		this.claimed = new ArrayList<String>();
		try {
			add.run();
			if (outer != null) {
				outer.addAll(this.claimed);
			}
		} catch (RuntimeException e) {
			for (String id : this.claimed) {
				this.identifiers.remove(id);
			}
			throw e;
		} finally {
			this.claimed = outer;
			this.elements = null;
		}
		return this;
	}

	/**
	 * @param kind SPEC-OBJECT-TYPE, SPEC-RELATION-TYPE, SPECIFICATION-TYPE or
	 *             RELATION-GROUP-TYPE
	 */
	private SpecType requireSpecType(String specTypeID, String kind) {

		SpecType specType = this.specTypes.get(specTypeID);
		if (specType == null) {
			throw new ReqIFBuildException("Unknown spec type: " + specTypeID);
		}
		if (!kind.equals(specType.getType())) {
			throw new ReqIFBuildException("Spec type " + specTypeID + " is a " + specType.getType()
					+ ", but a " + kind + " is needed here");
		}
		return specType;
	}

	private void requireSpecObject(String specObjectID) {

		if (!this.specObjects.containsKey(specObjectID)) {
			throw new ReqIFBuildException("Unknown spec object: " + specObjectID);
		}
	}


	// ------------------------------------------------------------ nested builders

	/** Collects the enum values of an enumeration datatype. */
	public static class EnumerationBuilder {

		private final List<DatatypeEnumerationValue> values = new ArrayList<DatatypeEnumerationValue>();

		public EnumerationBuilder value(String id, String name, String key) {
			return value(id, name, key, "");
		}

		public EnumerationBuilder value(String id, String name, String key, String otherContent) {
			for (DatatypeEnumerationValue existing : this.values) {
				if (existing.getID().equals(id)) {
					throw new ReqIFBuildException("Enum value '" + id + "' is declared twice");
				}
			}
			this.values.add(new DatatypeEnumerationValue(id, name, key, otherContent));
			return this;
		}
	}

	/** Builds the specification hierarchy of a specification. */
	public class SpecificationBuilder {

		private final ValuesBuilder values;
		private final List<HierarchyBuilder> hierarchies = new ArrayList<HierarchyBuilder>();

		private SpecificationBuilder(SpecType specType) {
			this.values = new ValuesBuilder(specType);
		}

		/** Sets an attribute value of the specification itself. */
		public SpecificationBuilder set(String attributeDefinitionID, Object value) {
			this.values.set(attributeDefinitionID, value);
			return this;
		}

		/** Sets an XHTML value of the specification itself. */
		public SpecificationBuilder setXhtml(String attributeDefinitionID, String markup) {
			this.values.setXhtml(attributeDefinitionID, markup);
			return this;
		}

		/**
		 * Sets a simplified XHTML value of the specification itself, see
		 * {@link ValuesBuilder#setXhtml(String, String, String)}.
		 */
		public SpecificationBuilder setXhtml(String attributeDefinitionID, String markup, String originalMarkup) {
			this.values.setXhtml(attributeDefinitionID, markup, originalMarkup);
			return this;
		}

		/** Selects enum values of the specification itself. */
		public SpecificationBuilder setEnum(String attributeDefinitionID, String... enumValueIDs) {
			this.values.setEnum(attributeDefinitionID, enumValueIDs);
			return this;
		}

		public SpecificationBuilder child(String hierarchyID, String specObjectID) {
			return child(hierarchyID, specObjectID, child -> { });
		}

		public SpecificationBuilder child(String hierarchyID, String specObjectID,
				Consumer<HierarchyBuilder> children) {

			claim(hierarchyID, "spec hierarchy");
			HierarchyBuilder hierarchy = new HierarchyBuilder(hierarchyID, specObjectID);
			children.accept(hierarchy);
			this.hierarchies.add(hierarchy);
			return this;
		}

		private List<SpecHierarchy> children(int level, int sectionOffset) {

			List<SpecHierarchy> children = new ArrayList<SpecHierarchy>();
			int section = sectionOffset;
			for (HierarchyBuilder hierarchy : this.hierarchies) {
				children.add(hierarchy.build(level, ++section));
			}
			return children;
		}
	}

	/** Builds one node of the specification hierarchy. */
	public class HierarchyBuilder {

		private final String hierarchyID;
		private final String specObjectID;
		private final List<HierarchyBuilder> children = new ArrayList<HierarchyBuilder>();
		private Boolean tableInternal;
		private Boolean editable;
		private final List<String> editableAttributeIDs = new ArrayList<String>();

		private HierarchyBuilder(String hierarchyID, String specObjectID) {
			this.hierarchyID = hierarchyID;
			this.specObjectID = specObjectID;
		}

		/** IS-TABLE-INTERNAL: whether the node is part of a table in the tool's view */
		public HierarchyBuilder tableInternal(boolean tableInternal) {
			this.tableInternal = tableInternal;
			return this;
		}

		/** IS-EDITABLE: whether the object may be edited through this node */
		public HierarchyBuilder editable(boolean editable) {
			this.editable = editable;
			return this;
		}

		/**
		 * EDITABLE-ATTS: the attributes of the object's spec type that may be
		 * edited through this node.
		 */
		public HierarchyBuilder editableAttributes(String... attributeDefinitionIDs) {
			this.editableAttributeIDs.clear();
			this.editableAttributeIDs.addAll(Arrays.asList(attributeDefinitionIDs));
			return this;
		}

		public HierarchyBuilder child(String hierarchyID, String specObjectID) {
			return child(hierarchyID, specObjectID, child -> { });
		}

		public HierarchyBuilder child(String hierarchyID, String specObjectID,
				Consumer<HierarchyBuilder> children) {

			claim(hierarchyID, "spec hierarchy");
			HierarchyBuilder hierarchy = new HierarchyBuilder(hierarchyID, specObjectID);
			children.accept(hierarchy);
			this.children.add(hierarchy);
			return this;
		}

		private SpecHierarchy build(int level, int section) {

			requireSpecObject(this.specObjectID);

			List<SpecHierarchy> nested = new ArrayList<SpecHierarchy>();
			for (HierarchyBuilder child : this.children) {
				nested.add(child.build(level + 1, section));
			}
			SpecObject specObject = ReqIFBuilder.this.specObjects.get(this.specObjectID);
			SpecHierarchy hierarchy = new SpecHierarchy(this.hierarchyID, level, section, specObject, nested)
					.setTableInternal(this.tableInternal).setEditable(this.editable);
			if (!this.editableAttributeIDs.isEmpty()) {
				List<AttributeDefinition> definitions = new ArrayList<AttributeDefinition>();
				for (String id : this.editableAttributeIDs) {
					AttributeDefinition definition = specObject.getResolvedSpecType().getAttributeDefinition(id);
					if (definition == null) {
						throw new ReqIFBuildException("Spec hierarchy " + this.hierarchyID + ": the type of spec object "
								+ this.specObjectID + " has no attribute definition " + id);
					}
					definitions.add(definition);
				}
				hierarchy.setEditableAttributes(definitions);
			}
			return hierarchy;
		}
	}

	/** Collects the header fields. */
	public static class HeaderBuilder {

		private String id = "";
		private String title = "";
		private String comment = "";
		private String creationTime = "";
		private String toolID = "";
		private String sourceToolID = "";
		private String reqifVersion = "1.0";
		private String repositoryID;

		public HeaderBuilder id(String id) {
			this.id = id;
			return this;
		}

		public HeaderBuilder title(String title) {
			this.title = title;
			return this;
		}

		public HeaderBuilder comment(String comment) {
			this.comment = comment;
			return this;
		}

		/** @param creationTime xsd:dateTime, e.g. 2026-07-23T10:00:00Z */
		public HeaderBuilder creationTime(String creationTime) {
			this.creationTime = creationTime;
			return this;
		}

		public HeaderBuilder toolID(String toolID) {
			this.toolID = toolID;
			return this;
		}

		public HeaderBuilder sourceToolID(String sourceToolID) {
			this.sourceToolID = sourceToolID;
			return this;
		}

		public HeaderBuilder repositoryID(String repositoryID) {
			this.repositoryID = repositoryID;
			return this;
		}

		public HeaderBuilder reqifVersion(String reqifVersion) {
			this.reqifVersion = reqifVersion;
			return this;
		}

		private ReqIFHeader build(String defaultID) {
			return new ReqIFHeader(this.id == null || this.id.isBlank() ? defaultID : this.id, this.title,
					this.comment, this.creationTime, this.toolID, this.sourceToolID, this.reqifVersion)
					.setRepositoryID(this.repositoryID);
		}
	}

	/** Convenience for enumeration values. */
	public static List<String> enumValues(String... enumValueIDs) {
		return new ArrayList<String>(Arrays.asList(enumValueIDs));
	}
}
