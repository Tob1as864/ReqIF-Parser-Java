package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.write.ReqIFWriter;

/**
 * The acceptance test for lossless import and export: a schema-valid document
 * that uses every element and attribute of the ReqIF schema is written back
 * with the same content.
 */
class LosslessRoundTripTest {

	static String fullFeatureDocument() throws Exception {
		try (InputStream in = LosslessRoundTripTest.class.getResourceAsStream("/full-feature.reqif")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void everyElementAndAttributeSurvivesARoundTrip(@TempDir Path tempDir) throws Exception {

		String original = fullFeatureDocument();
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "in.reqif", original).toString());

		String written = new ReqIFWriter().toXml(reqif.getReqIFDocument());

		assertEquals(CanonicalXml.of(original), CanonicalXml.of(written));
	}

	@Test
	void theFixtureIsSchemaValidAndStaysSo(@TempDir Path tempDir) throws Exception {

		Path in = TestFixtures.write(tempDir, "in.reqif", fullFeatureDocument());
		OfficialReqIFSchema.assertSchemaValid(in);

		OfficialReqIFSchema.assertSchemaValid(new ReqIF(in.toString()).getReqIFDocument());
	}

	@Test
	void writingIsStable(@TempDir Path tempDir) throws Exception {

		ReqIF first = new ReqIF(TestFixtures.write(tempDir, "in.reqif", fullFeatureDocument()).toString());
		String once = new ReqIFWriter().toXml(first.getReqIFDocument());
		ReqIF second = new ReqIF(TestFixtures.write(tempDir, "once.reqif", once).toString());

		assertEquals(once, new ReqIFWriter().toXml(second.getReqIFDocument()));
	}
}
