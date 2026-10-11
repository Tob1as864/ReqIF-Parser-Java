package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.util.SecureXml;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import de.uni_stuttgart.ils.reqif4j.validate.ReqIFValidator;
import de.uni_stuttgart.ils.reqif4j.validate.ValidationResult;

/**
 * Provides the official OMG ReqIF 1.2 schema for conformance tests.
 *
 * The XSD is not bundled with the library (licensing), so it is downloaded on
 * first use together with everything it imports ({@code xml.xsd},
 * {@code driver.xsd} and the XHTML modules) and cached flat, by file name, in
 * one directory - the layout {@link ReqIFValidator#validateAgainstSchema}
 * expects.
 *
 * The cache directory is {@code $REQIF_SCHEMA_DIR}, or {@code target/reqif-schema}
 * if the variable is not set. Without network access the conformance tests are
 * skipped locally; on CI ({@code CI=true}) a failed download fails the build,
 * so the tests can never silently stop running there.
 */
public final class OfficialReqIFSchema {

	public static final String REQIF_XSD_URL = "https://www.omg.org/spec/ReqIF/20110401/reqif.xsd";

	private static Path cachedSchema;
	private static String unavailable;

	private OfficialReqIFSchema() {
	}

	/**
	 * @return the local reqif.xsd with all its imports next to it
	 */
	public static synchronized Path reqifXsd() {

		if (cachedSchema != null) {
			return cachedSchema;
		}
		if (unavailable == null) {
			Path directory = directory();
			try {
				Files.createDirectories(directory);
				download(directory);
				cachedSchema = directory.resolve(fileName(URI.create(REQIF_XSD_URL)));
				return cachedSchema;
			} catch (IOException | RuntimeException e) {
				unavailable = "Official ReqIF schema is not available in " + directory + ": " + e;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				unavailable = "Interrupted while downloading the ReqIF schema";
			}
		}
		// remembered, so offline every further test gives up at once instead of
		// waiting for another connect timeout
		if ("true".equalsIgnoreCase(System.getenv("CI"))) {
			fail(unavailable);
		}
		Assumptions.abort(unavailable);
		return null;
	}

	/**
	 * Fails with all schema violations if the XML the writer produces for the
	 * document is not valid against the official ReqIF schema.
	 */
	public static void assertSchemaValid(ReqIFDocument document) throws IOException {
		assertValid(new ReqIFValidator().validateAgainstSchema(document, reqifXsd()));
	}

	/**
	 * Fails with all schema violations if the file is not valid against the
	 * official ReqIF schema.
	 */
	public static void assertSchemaValid(Path reqifFile) throws IOException {
		assertValid(new ReqIFValidator().validateAgainstSchema(reqifFile, reqifXsd()));
	}

	private static void assertValid(ValidationResult result) {
		assertTrue(result.getIssues().isEmpty(), "not valid against the ReqIF schema:\n" + result);
	}

	private static Path directory() {
		String configured = System.getenv("REQIF_SCHEMA_DIR");
		return configured == null || configured.isBlank()
				? Path.of("target", "reqif-schema")
				: Path.of(configured);
	}

	/**
	 * Fetches reqif.xsd and, transitively, every schema it imports or includes.
	 * Files already in the cache are not downloaded again.
	 */
	private static void download(Path directory) throws IOException, InterruptedException {

		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.proxy(ProxySelector.getDefault())
				.connectTimeout(Duration.ofSeconds(20))
				.build();

		Deque<URI> pending = new ArrayDeque<URI>();
		Set<String> seen = new HashSet<String>();
		pending.add(URI.create(REQIF_XSD_URL));

		while (!pending.isEmpty()) {
			URI location = pending.poll();
			String fileName = fileName(location);
			if (!seen.add(fileName)) {
				continue;
			}

			Path local = directory.resolve(fileName);
			if (!Files.isRegularFile(local)) {
				fetch(client, location, local);
			}
			for (URI imported : referencedSchemas(local)) {
				pending.add(location.resolve(imported));
			}
		}
	}

	private static void fetch(HttpClient client, URI location, Path target) throws IOException, InterruptedException {

		// w3.org and omg.org serve the same files over https; the http URLs in
		// the schemas would need a plain http proxy that CI and sandboxes lack
		URI secure = "http".equalsIgnoreCase(location.getScheme())
				? URI.create("https" + location.toString().substring("http".length()))
				: location;

		HttpRequest request = HttpRequest.newBuilder(secure)
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", "reqif4j-tests")
				.GET()
				.build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream body = response.body()) {
			if (response.statusCode() != 200) {
				throw new IOException("HTTP " + response.statusCode() + " for " + secure);
			}
			// write to a temporary file first, so an interrupted download never
			// leaves a truncated schema in the cache
			Path partial = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
			try {
				Files.copy(body, partial, StandardCopyOption.REPLACE_EXISTING);
				Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} finally {
				Files.deleteIfExists(partial);
			}
		}
	}

	/**
	 * @return the schemaLocation of every xs:import, xs:include and xs:redefine
	 *         of the schema; locations mentioned in annotations are ignored
	 */
	private static Set<URI> referencedSchemas(Path schema) throws IOException {

		Set<URI> locations = new HashSet<URI>();
		Document xsd;
		try {
			xsd = SecureXml.newDocumentBuilder().parse(schema.toFile());
		} catch (Exception e) {
			throw new IOException("Cached schema " + schema + " is not readable; delete it to download it again", e);
		}
		for (String kind : new String[] { "import", "include", "redefine" }) {
			for (Element reference : XmlUtils.descendantsByLocalName(xsd, kind)) {
				String location = reference.getAttribute("schemaLocation");
				if (!location.isEmpty() && "http://www.w3.org/2001/XMLSchema".equals(reference.getNamespaceURI())) {
					locations.add(URI.create(location));
				}
			}
		}
		return locations;
	}

	private static String fileName(URI location) {
		String path = location.getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}
}
