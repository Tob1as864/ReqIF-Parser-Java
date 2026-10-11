package de.uni_stuttgart.ils.reqif4j.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.ls.LSInput;

/**
 * The resolver must map remote schema imports to local files and must never
 * hand a remote location back to the parser, which could fetch it.
 */
class LocalSchemaResolverTest {

	private static final String XSD = "http://www.w3.org/2001/XMLSchema";

	@Test
	void remoteImportIsMappedToTheFileWithTheSameName(@TempDir Path dir) throws Exception {

		Path local = Files.writeString(dir.resolve("xml.xsd"), "<schema/>");
		LocalSchemaResolver resolver = new LocalSchemaResolver(dir);

		LSInput input = resolver.resolveResource(XSD, null, null, "http://www.w3.org/2001/xml.xsd", null);

		assertEquals(local.toUri().toString(), input.getSystemId());
		assertTrue(resolver.getMissing().isEmpty());
	}

	@Test
	void relativeImportOfARemoteSchemaIsMappedAsWell(@TempDir Path dir) throws Exception {

		Path local = Files.writeString(dir.resolve("xhtml-notations-1.xsd"), "<schema/>");
		LocalSchemaResolver resolver = new LocalSchemaResolver(dir);

		LSInput input = resolver.resolveResource(XSD, null, null, "xhtml-notations-1.xsd",
				"https://www.w3.org/MarkUp/SCHEMA/xhtml-framework-1.xsd");

		assertEquals(local.toUri().toString(), input.getSystemId());
	}

	@Test
	void missingRemoteImportIsAnsweredLocallyAndRemembered(@TempDir Path dir) {

		LocalSchemaResolver resolver = new LocalSchemaResolver(dir);

		LSInput input = resolver.resolveResource(XSD, null, null, "http://www.w3.org/2001/xml.xsd", null);

		// an empty input instead of null: null would let the parser fetch the URL
		assertEquals("", input.getStringData());
		assertEquals("xml.xsd", resolver.getMissing().get("http://www.w3.org/2001/xml.xsd"));
	}

	@Test
	void remoteLocationCannotEscapeTheSchemaDirectory(@TempDir Path dir) throws Exception {

		Path schemas = Files.createDirectory(dir.resolve("schemas"));
		Files.writeString(dir.resolve("other.xsd"), "<schema/>");
		LocalSchemaResolver resolver = new LocalSchemaResolver(schemas);

		LSInput input = resolver.resolveResource(XSD, null, null, "http://host/x/..%5C..%5Cother.xsd", null);

		assertEquals("", input.getStringData());
		assertEquals(1, resolver.getMissing().size());
	}

	@Test
	void localLocationsAreLeftToTheParser(@TempDir Path dir) {

		LocalSchemaResolver resolver = new LocalSchemaResolver(dir);
		String base = dir.resolve("reqif.xsd").toUri().toString();

		assertNull(resolver.resolveResource(XSD, null, null, "driver.xsd", base));
		assertNull(resolver.resolveResource(XSD, null, null, "C:/schemas/common.xsd", null));
		assertNull(resolver.resolveResource(XSD, null, null, base, null));
		assertTrue(resolver.getMissing().isEmpty());
	}
}
