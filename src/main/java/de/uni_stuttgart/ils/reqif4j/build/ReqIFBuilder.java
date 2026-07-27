package de.uni_stuttgart.ils.reqif4j.build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeBoolean;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeDate;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeDouble;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumerationValue;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeInteger;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeString;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeXHTML;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFHeader;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObjectType;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelation;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelationType;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.specification.SpecificationType;
import de.uni_stuttgart.ils.reqif4j.specification.TypeClassifier;

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

	private final Map<String, Datatype> datatypes = new LinkedHashMap<String, Datatype>();
	private final Map<String, SpecType> specTypes = new LinkedHashMap<String, SpecType>();
	private final Map<String, SpecObject> specObjects = new LinkedHashMap<String, SpecObject>();
	private final List<SpecRelation> specRelations = new ArrayList<SpecRelation>();
	private final List<Specification> specifications = new ArrayList<Specification>();

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


	// ---------------------------------------------------------------- datatypes

	/** Adds a datatype the builder has no shorthand for. */
	public ReqIFBuilder datatype(Datatype datatype) {
		this.datatypes.put(datatype.getID(), datatype);
		return this;
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

	/** Written as DATATYPE-DEFINITION-REAL. */
	public ReqIFBuilder realDatatype(String id, String name) {
		return datatype(new DatatypeDouble(id, name));
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

	private ReqIFBuilder specType(SpecType specType, Consumer<SpecTypeBuilder> attributes) {

		attributes.accept(new SpecTypeBuilder(specType, this.datatypes));
		this.specTypes.put(specType.getID(), specType);
		return this;
	}


	// ------------------------------------------------------------- spec objects

	public ReqIFBuilder specObject(String id, String specTypeID, Consumer<ValuesBuilder> values) {

		SpecType specType = requireSpecType(specTypeID);
		ValuesBuilder builder = new ValuesBuilder(specType);
		values.accept(builder);

		this.specObjects.put(id, new SpecObject(id, specType, builder.build(), this.typeClassifier));
		return this;
	}

	public ReqIFBuilder specObject(String id, String specTypeID) {
		return specObject(id, specTypeID, values -> { });
	}


	// ----------------------------------------------------------- spec relations

	public ReqIFBuilder specRelation(String id, String specTypeID, String sourceID, String targetID,
			Consumer<ValuesBuilder> values) {

		SpecType specType = requireSpecType(specTypeID);
		requireSpecObject(sourceID);
		requireSpecObject(targetID);

		ValuesBuilder builder = new ValuesBuilder(specType);
		values.accept(builder);

		this.specRelations.add(new SpecRelation(id, specType, sourceID, targetID, builder.build()));
		return this;
	}

	public ReqIFBuilder specRelation(String id, String specTypeID, String sourceID, String targetID) {
		return specRelation(id, specTypeID, sourceID, targetID, values -> { });
	}


	// ----------------------------------------------------------- specifications

	public ReqIFBuilder specification(String id, String name, String specTypeID,
			Consumer<SpecificationBuilder> content) {

		SpecType specType = requireSpecType(specTypeID);
		SpecificationBuilder builder = new SpecificationBuilder(specType);
		content.accept(builder);

		this.specifications.add(new Specification(id, name, specType, builder.values.build(),
				builder.children(1, 0)));
		return this;
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
		for (SpecRelation relation : this.specRelations) {
			content.addSpecRelation(relation);
		}
		for (Specification specification : this.specifications) {
			content.addSpecification(specification);
		}

		return new ReqIFDocument(this.headerBuilder.build(), content);
	}


	private SpecType requireSpecType(String specTypeID) {

		SpecType specType = this.specTypes.get(specTypeID);
		if (specType == null) {
			throw new ReqIFBuildException("Unknown spec type: " + specTypeID);
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

		public SpecificationBuilder child(String hierarchyID, String specObjectID) {
			return child(hierarchyID, specObjectID, child -> { });
		}

		public SpecificationBuilder child(String hierarchyID, String specObjectID,
				Consumer<HierarchyBuilder> children) {

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

		private HierarchyBuilder(String hierarchyID, String specObjectID) {
			this.hierarchyID = hierarchyID;
			this.specObjectID = specObjectID;
		}

		public HierarchyBuilder child(String hierarchyID, String specObjectID) {
			return child(hierarchyID, specObjectID, child -> { });
		}

		public HierarchyBuilder child(String hierarchyID, String specObjectID,
				Consumer<HierarchyBuilder> children) {

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
			return new SpecHierarchy(this.hierarchyID, level, section,
					ReqIFBuilder.this.specObjects.get(this.specObjectID), nested);
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

		public HeaderBuilder reqifVersion(String reqifVersion) {
			this.reqifVersion = reqifVersion;
			return this;
		}

		private ReqIFHeader build() {
			return new ReqIFHeader(this.id, this.title, this.comment, this.creationTime,
					this.toolID, this.sourceToolID, this.reqifVersion);
		}
	}

	/** Convenience for enumeration values. */
	public static List<String> enumValues(String... enumValueIDs) {
		return new ArrayList<String>(Arrays.asList(enumValueIDs));
	}
}
