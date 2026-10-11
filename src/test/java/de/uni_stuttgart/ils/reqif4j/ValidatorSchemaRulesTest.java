package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationIssue;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationResult;

/**
 * The model validator reports what would make the written file
 * schema-invalid as an error, and values outside their datatype's limits -
 * which the schema cannot express - as a warning.
 */
class ValidatorSchemaRulesTest {

	@Test
	void identifierMustBeAnXsdID() {

		ReqIFDocument document = document(b -> b.specObject("1st requirement", "st-req"));

		assertEquals(List.of("ERROR [1st requirement]: Identifier '1st requirement' of SPEC-OBJECT is not a valid xsd:ID"),
				issues(document));
	}

	@Test
	void lastChangeMustBeAnXsdDateTime() {

		ReqIFDocument document = document(b -> { });
		document.getCoreContent().getSpecObject("so-1").setLastChange("yesterday");

		assertEquals(List.of("ERROR [so-1]: LAST-CHANGE 'yesterday' of SPEC-OBJECT is not an xsd:dateTime"),
				issues(document));
	}

	@Test
	void headerFieldsMustMatchTheSchema() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr").creationTime("2026-07-23").reqifVersion("1.2"))
				.build();

		assertEquals(List.of(
				"ERROR [hdr]: CREATION-TIME '2026-07-23' is not an xsd:dateTime",
				"ERROR [hdr]: REQ-IF-VERSION is '1.2', but the schema only allows 1.0"), issues(document));
	}

	@Test
	void enumValuesNeedAnIntegerKeyAndAUniqueIdentifier() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.enumerationDatatype("dt-enum", "Color", e -> e
						.value("ev-red", "Red", "red")
						.value("hdr", "Clash", "2"))
				.build();

		assertEquals(List.of(
				"ERROR [ev-red]: KEY 'red' of enum value 'Red' is not an integer",
				"ERROR [hdr]: Identifier is used twice: as REQ-IF-HEADER and as ENUM-VALUE"), issues(document));
	}

	@Test
	void dateValuesAndDefaultsMustBeXsdDateTimes() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.dateDatatype("dt-date", "Date")
				.specObjectType("st", "Type", t -> t.attribute("ad-due", "Due", "dt-date", "2026-02-30T00:00:00Z"))
				.specObject("so-1", "st", o -> o.set("ad-due", "31.12.2026"))
				.build();

		assertEquals(List.of(
				"ERROR [ad-due]: Default value: '2026-02-30T00:00:00Z' is not an xsd:dateTime",
				"ERROR [so-1]: Attribute 'Due': '31.12.2026' is not an xsd:dateTime"), issues(document));
	}

	@Test
	void defaultValuesAreWrittenVerbatimSoMustBeInSchemaNotation() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.booleanDatatype("dt-bool", "Bool")
				.realDatatype("dt-real", "Real")
				.integerDatatype("dt-int", "Int", 0, 10)
				.specObjectType("st", "Type", t -> t
						.attribute("ad-flag", "Flag", "dt-bool", "yes")
						.attribute("ad-effort", "Effort", "dt-real", "1.5d")
						.attribute("ad-count", "Count", "dt-int", "three"))
				.build();

		assertEquals(List.of(
				"ERROR [ad-flag]: Default value: 'yes' is not an xsd:boolean (true, false, 1 or 0)",
				"ERROR [ad-effort]: Default value: '1.5d' is not an xsd:double",
				"ERROR [ad-count]: Default value: 'three' is not an integer"), issues(document));
	}

	@Test
	void enumValueWithoutKeyIsNamedAsSuch() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.enumerationDatatype("dt-enum", "Color", e -> e.value("ev-red", "Red", null))
				.build();

		assertEquals(List.of("ERROR [ev-red]: Enum value 'Red' has no KEY"), issues(document));
	}

	@Test
	void stringLengthCountsCharactersNotUtf16Units() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.stringDatatype("dt-short", "Short", 2)
				.specObjectType("st", "Type", t -> t.stringAttribute("ad-icon", "Icon", "dt-short"))
				.specObject("so-1", "st", o -> o.set("ad-icon", "\uD83D\uDE00\uD83D\uDE00"))
				.build();

		assertEquals(List.of(), issues(document), "two emoji are two characters");
	}

	@Test
	void valuesOutsideTheirDatatypeAreWarnings() throws Exception {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.stringDatatype("dt-short", "Short", 3)
				.integerDatatype("dt-prio", "Priority", 1, 5)
				.realDatatype("dt-ratio", "Ratio", 2, 0.0, 1.0)
				.specObjectType("st", "Type", t -> t
						.stringAttribute("ad-code", "Code", "dt-short")
						.integerAttribute("ad-prio", "Priority", "dt-prio")
						.realAttribute("ad-ratio", "Ratio", "dt-ratio"))
				.specObject("so-1", "st", o -> o.set("ad-code", "ABCD").set("ad-prio", 9).set("ad-ratio", 1.5))
				.build();

		ValidationResult result = new ReqIFValidator().validate(document);

		assertTrue(result.isValid(), "range violations do not make the file schema-invalid");
		assertEquals(List.of(
				"WARNING [so-1]: Attribute 'Code' is 4 characters long, but datatype 'Short' allows 3",
				"WARNING [so-1]: Attribute 'Priority' 9 is outside the range 1..5 of datatype 'Priority'",
				"WARNING [so-1]: Attribute 'Ratio' 1.5 is outside the range 0.0..1.0 of datatype 'Ratio'"),
				issues(document));

		// the contract: a document without errors is written schema-valid
		OfficialReqIFSchema.assertSchemaValid(document);
	}

	@Test
	void datatypeWithMinAboveMaxIsAWarning() {

		ReqIFDocument document = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.integerDatatype("dt-int", "Broken", 10, 1)
				.build();

		assertEquals(List.of("WARNING [dt-int]: Datatype 'Broken' has MIN 10 greater than MAX 1; no value can satisfy it"),
				issues(document));
	}

	/**
	 * A valid document with one spec type and one spec object, plus whatever
	 * the test adds.
	 */
	private static ReqIFDocument document(Consumer<ReqIFBuilder> additions) {
		ReqIFBuilder builder = ReqIFBuilder.create()
				.header(h -> h.id("hdr"))
				.stringDatatype("dt-string", "String", 100)
				.specObjectType("st-req", "Requirement", t -> t.stringAttribute("ad-name", "Name", "dt-string"))
				.specObject("so-1", "st-req", o -> o.set("ad-name", "x"));
		additions.accept(builder);
		return builder.build();
	}

	private static List<String> issues(ReqIFDocument document) {
		return new ReqIFValidator().validate(document).getIssues().stream()
				.map(ValidationIssue::toString)
				.collect(Collectors.toList());
	}
}
