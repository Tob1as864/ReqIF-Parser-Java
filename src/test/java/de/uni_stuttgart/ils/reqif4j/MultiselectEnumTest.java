package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueEnumeration;
import de.uni_stuttgart.ils.reqif4j.datatypes.DatatypeEnumeration;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;

/**
 * Bug: only the first ENUM-VALUE-REF of a multi-valued (multiselect)
 * enumeration attribute was read; all further selected values were silently
 * dropped. Enum default values were lost entirely, and unknown enum ids
 * caused NPEs in the key/other-content lookups.
 */
class MultiselectEnumTest {

	private ReqIF reqif;

	@BeforeEach
	void parseFixture(@TempDir Path tempDir) throws Exception {
		reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
	}

	@Test
	void allSelectedEnumValuesAreParsed() {
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");
		AttributeValueEnumeration colors = (AttributeValueEnumeration) so1.getAttributes().get("Colors");

		assertEquals(List.of("Red", "Green"), colors.getValues(),
				"both selected enum values must be parsed, not just the first");
		assertEquals(List.of("ev-red", "ev-green"), colors.getValueRefs());
	}

	@Test
	void joinedStringValueContainsAllSelections() {
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");

		assertEquals("Red, Green", so1.getAttribute("Colors"));
	}

	@Test
	void enumDefaultValueIsAppliedWhenAttributeIsMissing() {
		SpecObject so2 = reqif.getReqIFCoreContent().getSpecObject("so-2");
		AttributeValueEnumeration colors = (AttributeValueEnumeration) so2.getAttributes().get("Colors");

		assertEquals(List.of("Blue"), colors.getValues(),
				"the DEFAULT-VALUE enum ref must be resolved when the spec object has no value");
	}

	@Test
	void enumValueKeyAndOtherContentAreResolved() {
		DatatypeEnumeration colorType = (DatatypeEnumeration) reqif.getReqIFCoreContent().getDatatype("dt-enum");

		assertEquals("Red", colorType.getEnumValueName("ev-red"));
		assertEquals("1", colorType.getEnumValueKey("ev-red"));
		assertEquals("#ff0000", colorType.getEnumValueOtherContent("ev-red"));
		assertEquals("", colorType.getEnumValueOtherContent("ev-green"),
				"missing OTHER-CONTENT must map to empty string");
	}

	@Test
	void unknownEnumIdsDoNotThrow() {
		DatatypeEnumeration colorType = (DatatypeEnumeration) reqif.getReqIFCoreContent().getDatatype("dt-enum");
		SpecType specType = reqif.getReqIFCoreContent().getSpecType("st-req");

		assertNull(colorType.getEnumValueName("does-not-exist"));
		assertNull(colorType.getEnumValueKey("does-not-exist"));
		assertNull(colorType.getEnumValueOtherContent("does-not-exist"));
		assertEquals("", specType.getEnumValueKey("does-not-exist"));
		assertEquals("", specType.getEnumValueOtherContent("does-not-exist"));
	}

	@Test
	void enumValuesKeepDocumentOrder() {
		DatatypeEnumeration colorType = (DatatypeEnumeration) reqif.getReqIFCoreContent().getDatatype("dt-enum");

		assertEquals(3, colorType.getEnumValues().size());
		assertTrue(colorType.getEnumValues().keySet().stream().toList()
				.equals(List.of("ev-red", "ev-green", "ev-blue")));
	}
}
