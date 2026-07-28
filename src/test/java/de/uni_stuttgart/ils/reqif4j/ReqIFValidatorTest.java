package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.specification.RelationGroup;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationIssue;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationResult;

/**
 * The validator catches referential problems before a document is written.
 * The OMG XSD is not bundled, so this is a model-level check; XSD validation
 * is available separately against a schema the caller supplies.
 */
class ReqIFValidatorTest {

	private static ValidationResult validate(ReqIFDocument document) {
		return new ReqIFValidator().validate(document);
	}

	private static boolean hasError(ValidationResult result, String fragment) {
		return result.getErrors().stream().anyMatch(issue -> issue.getMessage().contains(fragment));
	}

	/** A minimal, valid document built with the builder. */
	private static ReqIFBuilder validBuilder() {
		return ReqIFBuilder.create()
				.header(h -> h.id("hdr-1").title("Spec").toolID("reqif4j"))
				.stringDatatype("dt-string", "String", 255)
				.enumerationDatatype("dt-enum", "Color", e -> e
						.value("ev-red", "Red", "1")
						.value("ev-blue", "Blue", "2"))
				.specObjectType("st-req", "Requirement Type", t -> t
						.stringAttribute("ad-title", "Title", "dt-string")
						.enumerationAttribute("ad-color", "Colors", "dt-enum", false))
				.specificationType("st-spec", "Spec Type", t -> { })
				.specRelationType("st-rel", "satisfies", t -> { })
				.specObject("so-1", "st-req", o -> o.set("ad-title", "First"))
				.specObject("so-2", "st-req")
				.specRelation("sr-1", "st-rel", "so-1", "so-2")
				.specification("spec-1", "Main", "st-spec", s -> s.child("sh-1", "so-1"));
	}


	@Test
	void parsedFixtureIsValid(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());

		ValidationResult result = validate(reqif.getReqIFDocument());

		assertTrue(result.isValid(), "the fixture must validate cleanly, got:\n" + result);
	}

	@Test
	void generatedDocumentIsValid() {
		assertTrue(validate(validBuilder().build()).isValid());
	}

	@Test
	void missingSpecObjectOfARelationIsReported() {
		ReqIFDocument document = validBuilder().build();
		// remove the relation target after building
		document.getCoreContent().getSpecObjects().remove("so-2");

		ValidationResult result = validate(document);

		assertFalse(result.isValid());
		assertTrue(hasError(result, "Relation target so-2 is not a known spec object"), result.toString());
	}

	@Test
	void unknownSpecHierarchyTargetIsReported() {
		ReqIFDocument document = validBuilder().build();
		document.getCoreContent().getSpecObjects().remove("so-1");

		ValidationResult result = validate(document);

		assertTrue(hasError(result, "not declared in SPEC-OBJECTS"), result.toString());
	}

	@Test
	void attributeValueFromAForeignSpecTypeIsReported() {
		ReqIFDocument document = validBuilder().build();

		// move a value of so-1 onto a spec object of a different type
		SpecObject so1 = document.getCoreContent().getSpecObject("so-1");
		AttributeValue title = so1.getAttributes().get("Title");
		document.getCoreContent().addSpecType(
				new de.uni_stuttgart.ils.reqif4j.specification.SpecObjectType("st-other", "Other Type"));
		document.getCoreContent().addSpecObject(new SpecObject("so-3",
				document.getCoreContent().getSpecType("st-other"), List.of(title)));

		ValidationResult result = validate(document);

		assertTrue(hasError(result, "does not belong to spec type st-other"), result.toString());
	}

	@Test
	void singleValuedEnumWithSeveralValuesIsReported() {
		ReqIFDocument document = validBuilder()
				.specObject("so-multi", "st-req", o -> o.setEnum("ad-color", "ev-red", "ev-blue"))
				.build();

		ValidationResult result = validate(document);

		assertTrue(hasError(result, "is not MULTI-VALUED but has 2 values"), result.toString());
	}

	@Test
	void identifierUsedTwiceAcrossCategoriesIsReported() {
		ReqIFDocument document = validBuilder().build();
		// reuse the datatype id for a relation group
		document.getCoreContent().addRelationGroup(
				new RelationGroup("dt-string", "Clash", "st-rel", "spec-1", "spec-1", List.of()));

		ValidationResult result = validate(document);

		assertTrue(hasError(result, "Identifier is used twice"), result.toString());
	}

	@Test
	void relationGroupWithUnknownReferencesIsReported() {
		ReqIFDocument document = validBuilder().build();
		document.getCoreContent().addRelationGroup(
				new RelationGroup("rg-1", "Group", "st-nope", "spec-nope", "spec-1", List.of("sr-nope")));

		ValidationResult result = validate(document);

		assertTrue(hasError(result, "references type st-nope"), result.toString());
		assertTrue(hasError(result, "source specification spec-nope"), result.toString());
		assertTrue(hasError(result, "references relation sr-nope"), result.toString());
	}

	@Test
	void missingHeaderIsOnlyAWarning() {
		ReqIFDocument document = new ReqIFDocument(null,
				validBuilder().build().getCoreContent());

		ValidationResult result = validate(document);

		assertTrue(result.isValid(), "a missing header must not make the document invalid");
		assertEquals(1, result.getIssues().size());
		assertEquals(ValidationIssue.Severity.WARNING, result.getIssues().get(0).getSeverity());
	}

	@Test
	void throwIfInvalidReportsEveryError() {
		ReqIFDocument document = validBuilder().build();
		document.getCoreContent().getSpecObjects().remove("so-2");

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> validate(document).throwIfInvalid());

		assertTrue(failure.getMessage().contains("ReqIF document is invalid"));
		assertTrue(failure.getMessage().contains("so-2"), failure.getMessage());
	}

	@Test
	void schemaValidationRunsAgainstASuppliedXsd(@TempDir Path tempDir) throws Exception {
		// a deliberately narrow schema: the root element must be REQ-IF
		String xsd = """
				<?xml version="1.0" encoding="UTF-8"?>
				<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
				           targetNamespace="http://www.omg.org/spec/ReqIF/20110401/reqif.xsd"
				           xmlns:reqif="http://www.omg.org/spec/ReqIF/20110401/reqif.xsd"
				           elementFormDefault="qualified">
				  <xs:element name="SOMETHING-ELSE" type="xs:string"/>
				</xs:schema>
				""";
		Path schema = TestFixtures.write(tempDir, "narrow.xsd", xsd);

		ValidationResult result = new ReqIFValidator()
				.validateAgainstSchema(validBuilder().build(), schema);

		assertFalse(result.isValid(),
				"a document whose root the schema does not declare must be reported");
	}
}
