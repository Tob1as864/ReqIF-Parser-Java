package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.specification.RelationGroup;
import de.uni_stuttgart.ils.reqif4j.specification.SpecHierarchy;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.specification.SpecRelation;
import de.uni_stuttgart.ils.reqif4j.specification.Specification;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * Changing a document after it was read or built: values, hierarchy,
 * relations and the removal of elements, with LAST-CHANGE maintained by the
 * writer.
 */
class MutationApiTest {

	private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-11T12:34:56Z"), ZoneOffset.UTC);
	private static final String NOW = "LAST-CHANGE=\"2026-10-11T12:34:56Z\"";

	private static ReqIFDocument read(Path dir) throws Exception {
		return new ReqIF(TestFixtures.write(dir, "in.reqif", TestFixtures.SCHEMA_VALID_REQIF).toString())
				.getReqIFDocument();
	}

	private static ReqIFDocument reread(Path dir, ReqIFDocument document) throws Exception {
		return new ReqIF(TestFixtures.write(dir, "out.reqif",
				new ReqIFWriter().setClock(FIXED).toXml(document)).toString()).getReqIFDocument();
	}

	@Test
	void changedValuesGetANewLastChangeAndTheRestKeepsTheirs(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		SpecObject so1 = document.getCoreContent().getSpecObject("so-1");
		so1.set("ad-prio", 7).setEnum("ad-status", "ev-draft", "ev-approved").setXhtml("ad-text",
				"<div><p>New text</p></div>");

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("IDENTIFIER=\"so-1\" " + NOW), xml);
		assertTrue(xml.contains("<SPEC-OBJECT IDENTIFIER=\"so-2\" LAST-CHANGE=\"2026-07-03T09:20:00Z\""), xml);
		OfficialReqIFSchema.assertSchemaValid(document);

		SpecObject written = reread(dir, document).getCoreContent().getSpecObject("so-1");
		assertEquals(BigInteger.valueOf(7), new BigInteger(written.getValues().get("ad-prio").getValue().toString()));
		assertEquals("2026-10-11T12:34:56Z", written.getLastChange());
	}

	@Test
	void unknownDefinitionsAndWrongKindsAreRefused(@TempDir Path dir) throws Exception {

		SpecObject so1 = read(dir).getCoreContent().getSpecObject("so-1");

		assertThrows(IllegalArgumentException.class, () -> so1.set("ad-unknown", "x"));
		assertThrows(IllegalArgumentException.class, () -> so1.set("ad-prio", "not a number"));
		assertThrows(IllegalArgumentException.class, () -> so1.setEnum("ad-status", "ev-unknown"));
		assertFalse(so1.isModified(), "a refused change leaves the object unchanged");
	}

	@Test
	void removingAValueMarksTheObject(@TempDir Path dir) throws Exception {

		SpecObject so1 = read(dir).getCoreContent().getSpecObject("so-1");

		assertNull(so1.removeValue("ad-not-set"));
		assertFalse(so1.isModified());
		so1.removeValue("ad-prio");
		assertTrue(so1.isModified());
		assertFalse(so1.getValues().containsKey("ad-prio"));
	}

	@Test
	void removingAnObjectLiftsItsChildrenAndDropsItsRelations(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		ReqIFCoreContent content = document.getCoreContent();

		assertEquals("so-1", content.removeSpecObject("so-1").getID());

		Specification spec = content.getSpecification("spec-1");
		assertEquals(List.of("sh-2"), ids(spec.getChildren()));
		SpecHierarchy sh2 = spec.getChildren().get(0);
		assertEquals(1, sh2.getHierarchyLvl());
		assertEquals(1, sh2.getSection());
		assertNull(content.getSpecRelation("sr-1"));
		assertEquals(List.of(), content.getRelationGroup("rg-1").getSpecRelationRefs());
		assertTrue(spec.isModified());
		assertTrue(content.getRelationGroup("rg-1").isModified());

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);
		assertFalse(xml.contains("so-1"), xml);
		assertTrue(xml.contains("IDENTIFIER=\"spec-1\" " + NOW), xml);
		assertTrue(xml.contains("<SPEC-HIERARCHY IDENTIFIER=\"sh-2\" LAST-CHANGE=\"2026-07-05T11:00:00Z\""), xml);
		OfficialReqIFSchema.assertSchemaValid(document);
	}

	@Test
	void hierarchyNodesAreInsertedMovedAndRemoved(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		ReqIFCoreContent content = document.getCoreContent();
		Specification spec = content.getSpecification("spec-1");
		SpecObject so2 = content.getSpecObject("so-2");

		spec.addChild(0, new SpecHierarchy("sh-new", 0, 0, so2, null));
		assertEquals(List.of("sh-new", "sh-1"), ids(spec.getChildren()));
		assertEquals(1, content.getSpecHierarchy("sh-new").getSection());
		assertEquals(2, content.getSpecHierarchy("sh-1").getSection());
		assertEquals(2, content.getSpecHierarchy("sh-2").getSection(), "sections pass down to the children");
		assertEquals(3, spec.getNumberOfSpecObjects());
		assertEquals(2, spec.getNumberOfSections());

		// below its own child: refused, nothing changed
		assertThrows(IllegalArgumentException.class, () -> content.moveSpecHierarchy("sh-1", "sh-2", 0));
		assertThrows(IllegalArgumentException.class, () -> content.moveSpecHierarchy("sh-1", "sh-1", 0));
		assertThrows(IllegalArgumentException.class, () -> content.moveSpecHierarchy("sh-2", "spec-1", 3));
		assertThrows(IllegalArgumentException.class, () -> content.moveSpecHierarchy("sh-2", "nowhere", 0));
		assertEquals(List.of("sh-new", "sh-1"), ids(spec.getChildren()));

		content.moveSpecHierarchy("sh-2", "spec-1", 2);
		assertEquals(List.of("sh-new", "sh-1", "sh-2"), ids(spec.getChildren()));
		assertEquals(1, content.getSpecHierarchy("sh-2").getHierarchyLvl());
		assertEquals(3, content.getSpecHierarchy("sh-2").getSection());
		assertTrue(content.getSpecHierarchy("sh-1").isModified(), "sh-1 lost a child");

		content.moveSpecHierarchy("sh-new", "sh-2", 0);
		assertEquals(2, content.getSpecHierarchy("sh-new").getHierarchyLvl());
		assertEquals(2, content.getSpecHierarchy("sh-new").getSection());
		assertEquals(1, content.getSpecHierarchy("sh-1").getSection());

		// within the same parent, the position counts after the move
		spec.addChild(new SpecHierarchy("sh-last", 0, 0, so2, null));
		content.moveSpecHierarchy("sh-1", "spec-1", 2);
		assertEquals(List.of("sh-2", "sh-last", "sh-1"), ids(spec.getChildren()));

		assertEquals("sh-2", content.removeSpecHierarchy("sh-2").getID());
		assertNull(content.getSpecHierarchy("sh-new"), "the node goes with everything below it");
		assertNull(content.removeSpecHierarchy("sh-2"));
		assertEquals(List.of("sh-last", "sh-1"), ids(spec.getChildren()));
		assertTrue(content.getSpecObjects().containsKey("so-2"), "the objects stay");

		assertThrows(IllegalArgumentException.class,
				() -> spec.addChild(new SpecHierarchy("sh-1", 0, 0, so2, null)));
		OfficialReqIFSchema.assertSchemaValid(document);
	}

	@Test
	void typesAndDatatypesInUseAreNotRemoved(@TempDir Path dir) throws Exception {

		ReqIFCoreContent content = read(dir).getCoreContent();

		IllegalStateException inUse = assertThrows(IllegalStateException.class, () -> content.removeSpecType("st-req"));
		assertTrue(inUse.getMessage().contains("so-1"), inUse.getMessage());
		assertThrows(IllegalStateException.class, () -> content.removeSpecType("st-rel"));
		assertThrows(IllegalStateException.class, () -> content.removeSpecType("st-spec"));
		assertThrows(IllegalStateException.class, () -> content.removeSpecType("st-group"));
		assertThrows(IllegalStateException.class, () -> content.removeDatatype("dt-int"));
		assertNull(content.removeSpecType("st-unknown"));
		assertNull(content.removeDatatype("dt-unknown"));

		assertEquals("rg-1", content.removeRelationGroup("rg-1").getID());
		assertEquals("st-group", content.removeSpecType("st-group").getID());
		assertTrue(content.getSpecRelation().containsKey("sr-1"), "the group's relations stay");
	}

	@Test
	void removingASpecificationRemovesItsRelationGroups(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		ReqIFCoreContent content = document.getCoreContent();

		content.removeSpecification("spec-1");

		assertNull(content.getRelationGroup("rg-1"));
		assertNull(content.getSpecHierarchy("sh-1"));
		assertEquals(2, content.getSpecObjects().size());
		assertEquals(1, content.getSpecRelation().size());
		OfficialReqIFSchema.assertSchemaValid(document);
	}

	@Test
	void identifiersCannotBeTakenTwice(@TempDir Path dir) throws Exception {

		ReqIFCoreContent content = read(dir).getCoreContent();
		SpecObject so1 = content.getSpecObject("so-1");

		assertSame(content, content.addSpecObject(so1), "adding the same element again changes nothing");
		assertThrows(IllegalArgumentException.class,
				() -> content.addSpecObject(new SpecObject("so-1", content.getSpecType("st-req"), null)));
		assertThrows(IllegalArgumentException.class,
				() -> content.addRelationGroup(new RelationGroup("dt-string", null, "st-group", "spec-1", "spec-1", null)));
		assertThrows(IllegalArgumentException.class,
				() -> content.addSpecObject(new SpecObject((String) null, content.getSpecType("st-req"), List.of())));
	}

	@Test
	void relationsAndGroupsAreRewired(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		ReqIFCoreContent content = document.getCoreContent();
		SpecRelation sr1 = content.getSpecRelation("sr-1");
		RelationGroup rg1 = content.getRelationGroup("rg-1");

		sr1.setSourceObjID("so-1").setTargetObjID("so-2");
		content.addSpecRelation(new SpecRelation("sr-2", content.getSpecType("st-rel"), "so-1", "so-2", null));
		rg1.addSpecRelationRef("sr-2").addSpecRelationRef("sr-2");
		assertEquals(List.of("sr-1", "sr-2"), rg1.getSpecRelationRefs());
		assertTrue(rg1.removeSpecRelationRef("sr-1"));
		assertFalse(rg1.removeSpecRelationRef("sr-1"));

		ReqIFCoreContent written = reread(dir, document).getCoreContent();
		assertEquals("so-1", written.getSpecRelation("sr-1").getSourceObjID());
		assertEquals("so-2", written.getSpecRelation("sr-1").getTargetObjID());
		assertEquals("2026-10-11T12:34:56Z", written.getSpecRelation("sr-1").getLastChange());
		assertEquals(List.of("sr-2"), written.getRelationGroup("rg-1").getSpecRelationRefs());
		assertEquals("2026-10-11T12:34:56Z", written.getRelationGroup("rg-1").getLastChange());
		OfficialReqIFSchema.assertSchemaValid(document);
	}

	@Test
	void clearModifiedKeepsTheLastChange(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		ReqIFCoreContent content = document.getCoreContent();
		content.getSpecObject("so-1").set("ad-prio", 9);
		content.getSpecHierarchy("sh-2").setEditable(true);

		content.clearModified();

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);
		assertFalse(xml.contains("2026-10-11"), xml);
	}

	@Test
	void builtDocumentsStartUnchanged() {

		ReqIFDocument document = new de.uni_stuttgart.ils.reqif4j.build.ReqIFBuilder()
				.header(h -> h.id("hdr").creationTime("2026-07-23T10:00:00Z"))
				.stringDatatype("dt-string", "String", 255)
				.specObjectType("st-req", "Requirement", t -> t.stringAttribute("ad-id", "ID", "dt-string"))
				.specObject("so-1", "st-req", o -> o.set("ad-id", "R-1"))
				.specObject("so-2", "st-req", o -> o.set("ad-id", "R-2"))
				.build();

		document.getCoreContent().getSpecObject("so-2").set("ad-id", "R-2b");
		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("<SPEC-OBJECT IDENTIFIER=\"so-1\" LAST-CHANGE=\"2026-07-23T10:00:00Z\""), xml);
		assertTrue(xml.contains("<SPEC-OBJECT IDENTIFIER=\"so-2\" " + NOW), xml);
		assertTrue(xml.contains("<SPEC-OBJECT-TYPE IDENTIFIER=\"st-req\" LAST-CHANGE=\"2026-07-23T10:00:00Z\""), xml);
	}

	@Test
	void aNodeIsInOnePlaceOnlyAndNeverBelowItself(@TempDir Path dir) throws Exception {

		ReqIFCoreContent content = read(dir).getCoreContent();
		Specification spec = content.getSpecification("spec-1");
		SpecHierarchy sh1 = content.getSpecHierarchy("sh-1");
		SpecHierarchy sh2 = content.getSpecHierarchy("sh-2");

		assertSame(spec, sh1.getParent());
		assertSame(sh1, sh2.getParent());
		assertThrows(IllegalArgumentException.class, () -> spec.addChild(sh2), "already placed");
		assertThrows(IllegalArgumentException.class, () -> sh1.removeChild("sh-2").addChild(sh1), "below itself");
		assertThrows(IllegalArgumentException.class, () -> sh2.addChild(sh2), "below itself");
		assertNull(sh2.getParent());

		spec.addChild(sh2);
		assertSame(spec, sh2.getParent());
		assertEquals(List.of("sh-1", "sh-2"), ids(spec.getAllSpecHierarchies()));

		// the flattened lists follow changes further down
		sh2.addChild(new SpecHierarchy("sh-3", 0, 0, content.getSpecObject("so-1"), null));
		assertEquals(List.of("sh-1", "sh-2", "sh-3"), ids(spec.getAllSpecHierarchies()));
		assertEquals(3, spec.getNumberOfSpecObjects());
		assertEquals(List.of("sh-1", "sh-2", "sh-3"), ids(content.getOrderedSpecHierarchyList()));
		assertThrows(UnsupportedOperationException.class, () -> spec.getAllSpecHierarchies().clear());
	}

	@Test
	void aNodeOfAnotherParentIsNotTakenByAConstructor(@TempDir Path dir) throws Exception {

		ReqIFCoreContent content = read(dir).getCoreContent();
		SpecObject so1 = content.getSpecObject("so-1");
		SpecHierarchy fresh = new SpecHierarchy("sh-fresh", 0, 0, so1, null);
		SpecHierarchy placed = content.getSpecHierarchy("sh-2");

		assertThrows(IllegalArgumentException.class,
				() -> new SpecHierarchy("sh-x", 0, 0, so1, List.of(fresh, placed)));
		assertNull(fresh.getParent(), "nothing was taken");
		assertSame(content.getSpecHierarchy("sh-1"), placed.getParent());
		content.addSpecHierarchy("spec-1", 0, fresh);
	}

	@Test
	void aRefusedRemovalChangesNothing() {

		ReqIFCoreContent content = new ReqIFCoreContent();
		de.uni_stuttgart.ils.reqif4j.specification.SpecType type = new de.uni_stuttgart.ils.reqif4j.specification.SpecType(
				"st", null, de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst.SPEC_OBJECT_TYPE);
		SpecObject a = new SpecObject("so-a", type, List.of());
		SpecObject b = new SpecObject("so-b", type, List.of());
		content.addSpecType(type).addSpecObject(a).addSpecObject(b);
		// two nodes sh-x, one below the node showing so-a: as read from a
		// document that repeats an identifier
		SpecHierarchy showsA = new SpecHierarchy("sh-a", 1, 1, a,
				List.of(new SpecHierarchy("sh-x", 2, 1, b, null)));
		content.addSpecification(new Specification("spec", null, null, null,
				List.of(showsA, new SpecHierarchy("sh-x", 1, 2, b, null))));

		assertThrows(IllegalStateException.class, () -> content.removeSpecObject("so-a"));

		assertSame(a, content.getSpecObject("so-a"));
		assertEquals(List.of("sh-a", "sh-x"), ids(content.getSpecification("spec").getChildren()));
		assertEquals(List.of("sh-x"), ids(showsA.getChildren()));
	}

	@Test
	void theContentIsChangedThroughItsMethodsOnly(@TempDir Path dir) throws Exception {

		ReqIFCoreContent content = read(dir).getCoreContent();

		assertThrows(UnsupportedOperationException.class, () -> content.getSpecObjects().remove("so-1"));
		assertThrows(UnsupportedOperationException.class, () -> content.getSpecRelation().clear());
		assertThrows(UnsupportedOperationException.class, () -> content.getRelationGroups().remove("rg-1"));
	}

	@Test
	void writingTwiceGivesTheSameLastChange(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		SpecObject so1 = document.getCoreContent().getSpecObject("so-1");
		so1.set("ad-prio", 8);

		String first = new ReqIFWriter().setClock(FIXED).toXml(document);
		String second = new ReqIFWriter().setClock(Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC))
				.toXml(document);

		assertEquals(first, second);
		assertEquals("2026-10-11T12:34:56Z", so1.getLastChange());
		assertFalse(so1.isModified());
	}

	@Test
	void aChangeAfterAnExplicitLastChangeCounts(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		SpecObject so1 = document.getCoreContent().getSpecObject("so-1");
		SpecObject so2 = document.getCoreContent().getSpecObject("so-2");
		so1.setDesc("changed").setLastChange("2026-08-01T00:00:00Z");
		so2.setLastChange("2026-08-01T00:00:00Z").setDesc("changed");

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("IDENTIFIER=\"so-1\" LAST-CHANGE=\"2026-08-01T00:00:00Z\""), xml);
		assertTrue(xml.contains("IDENTIFIER=\"so-2\" " + NOW), xml);
	}

	@Test
	void liftingNeverPutsTwoEqualIdentifiersSideBySide() {

		ReqIFCoreContent content = new ReqIFCoreContent();
		de.uni_stuttgart.ils.reqif4j.specification.SpecType type = new de.uni_stuttgart.ils.reqif4j.specification.SpecType(
				"st", null, de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst.SPEC_OBJECT_TYPE);
		SpecObject a = new SpecObject("so-a", type, List.of());
		SpecObject b = new SpecObject("so-b", type, List.of());
		content.addSpecType(type).addSpecObject(a).addSpecObject(b);
		// [X (so-a) > G (so-b), G (so-a)]: once both so-a nodes are gone, one G is left
		content.addSpecification(new Specification("spec", null, null, null, List.of(
				new SpecHierarchy("sh-x", 1, 1, a, List.of(new SpecHierarchy("sh-g", 2, 1, b, null))))));
		Specification spec = content.getSpecification("spec");
		spec.addChild(new SpecHierarchy("sh-g", 0, 0, a, null));

		content.removeSpecObject("so-a");

		assertEquals(List.of("sh-g"), ids(spec.getChildren()));
		assertSame(b, spec.getChildren().get(0).getSpecObject());
		assertEquals(1, spec.getChildren().get(0).getHierarchyLvl());
	}

	@Test
	void identifiersAreUniqueAcrossTheDocument(@TempDir Path dir) throws Exception {

		ReqIFCoreContent content = read(dir).getCoreContent();
		SpecObject so1 = content.getSpecObject("so-1");

		assertThrows(IllegalArgumentException.class,
				() -> content.addSpecHierarchy("spec-1", 0, new SpecHierarchy("sh-2", 0, 0, so1, null)));
		assertThrows(IllegalArgumentException.class,
				() -> content.addSpecHierarchy("sh-2", 0, new SpecHierarchy("so-1", 0, 0, so1, null)));
		assertThrows(IllegalArgumentException.class, () -> content.addSpecHierarchy("sh-new", 0,
				new SpecHierarchy("sh-3", 0, 0, so1, null)), "no such parent");
		assertThrows(IllegalArgumentException.class, () -> content.addSpecHierarchy("spec-1", 0,
				new SpecHierarchy("sh-3", 0, 0, so1, List.of(new SpecHierarchy("sh-1", 0, 0, so1, null)))),
				"a node below clashes");
		assertThrows(IllegalArgumentException.class,
				() -> content.addSpecObject(new SpecObject("sh-1", content.getSpecType("st-req"), List.of())));
		assertThrows(IllegalArgumentException.class,
				() -> content.addSpecObject(new SpecObject(" ", content.getSpecType("st-req"), List.of())));
		assertNull(content.getSpecHierarchy("sh-3"));

		content.addSpecHierarchy("sh-2", 0, new SpecHierarchy("sh-3", 0, 0, so1, null));
		assertEquals(3, content.getSpecHierarchy("sh-3").getHierarchyLvl());
	}

	@Test
	void newElementsInAReadDocumentGetTheTimeOfWriting(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		ReqIFCoreContent content = document.getCoreContent();
		SpecObject added = new SpecObject("so-3", content.getSpecType("st-req"), List.of());
		content.addSpecObject(added);
		content.addSpecHierarchy("spec-1", 1, new SpecHierarchy("sh-3", 0, 0, added, null));

		String xml = new ReqIFWriter().setClock(FIXED).toXml(document);

		assertTrue(xml.contains("<SPEC-OBJECT IDENTIFIER=\"so-3\" " + NOW), xml);
		assertTrue(xml.contains("<SPEC-HIERARCHY IDENTIFIER=\"sh-3\" " + NOW), xml);
		assertTrue(xml.contains("IDENTIFIER=\"so-2\" LAST-CHANGE=\"2026-07-03T09:20:00Z\""), xml);
	}

	@Test
	void validatingDoesNotTakeTheStamps(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		SpecObject so1 = document.getCoreContent().getSpecObject("so-1");
		so1.setDesc("changed");

		new ReqIFWriter().setClock(FIXED).buildDocument(document);
		OfficialReqIFSchema.assertSchemaValid(document);

		assertTrue(so1.isModified());
		assertEquals("2026-07-03T09:15:00Z", so1.getLastChange());
	}

	@Test
	void anArchiveTakesTheStampsOnceWritten(@TempDir Path dir) throws Exception {

		ReqIFDocument document = read(dir);
		SpecObject so1 = document.getCoreContent().getSpecObject("so-1");
		so1.setDesc("changed");
		de.uni_stuttgart.ils.reqif4j.write.ReqIFzWriter zipWriter = new de.uni_stuttgart.ils.reqif4j.write.ReqIFzWriter(
				new ReqIFWriter().setClock(FIXED));

		// a directory cannot be written as an archive
		assertThrows(java.io.IOException.class, () -> zipWriter.write(document, "a.reqif", dir));
		assertTrue(so1.isModified(), "a failed write is no save");

		zipWriter.write(document, "a.reqif", dir.resolve("out.reqifz"));
		assertFalse(so1.isModified());
		assertEquals("2026-10-11T12:34:56Z", so1.getLastChange());
	}

	@Test
	void enumSelectionsMustNotContainNull(@TempDir Path dir) throws Exception {

		SpecObject so1 = read(dir).getCoreContent().getSpecObject("so-1");

		assertThrows(IllegalArgumentException.class, () -> so1.setEnum("ad-status", (String[]) null));
		assertThrows(IllegalArgumentException.class, () -> so1.setEnum("ad-status", "ev-draft", null));
		IllegalArgumentException wrongSetter = assertThrows(IllegalArgumentException.class,
				() -> so1.set("ad-status", "ev-draft"));
		assertTrue(wrongSetter.getMessage().contains("setEnum"), wrongSetter.getMessage());
	}

	private static List<String> ids(List<SpecHierarchy> nodes) {
		return nodes.stream().map(SpecHierarchy::getID).toList();
	}
}
