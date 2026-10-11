package de.uni_stuttgart.ils.reqif4j.validate;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import de.uni_stuttgart.ils.reqif4j.util.SecureXml;

/**
 * Resolves the remote imports of a schema to files in a local directory.
 *
 * The official {@code reqif.xsd} imports {@code xml.xsd} and the XHTML
 * modules ({@code driver.xsd}, {@code xhtml-*-1.xsd}) by absolute http URL.
 * Schema validation must not go online, so every http(s) location is looked up
 * by its file name in the schema's own directory instead. Other locations
 * (relative paths, {@code file:}, Windows drive letters) are left to the
 * parser, which may only load local files.
 *
 * A remote import that is not available locally is answered with an empty
 * input - never handed back to the parser, which could otherwise fetch it if
 * it ignores the external access restriction - and remembered, so the caller
 * can name it in the error message.
 *
 * Mapping by file name assumes the imported file names are unique, which holds
 * for the ReqIF schema set.
 */
final class LocalSchemaResolver implements LSResourceResolver {

	/** a single path segment, so a location can never point outside the directory */
	private static final Pattern PLAIN_FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

	private final Path schemaDirectory;
	private final DOMImplementationLS inputs;
	private final Map<String, String> missing = new LinkedHashMap<String, String>();

	LocalSchemaResolver(Path schemaDirectory) {
		this.schemaDirectory = schemaDirectory;
		try {
			this.inputs = (DOMImplementationLS) SecureXml.newDocumentBuilder().getDOMImplementation();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("No DOM implementation available", e);
		}
	}

	/**
	 * @return the remote imports that could not be found locally, as the
	 *         location the schema referenced to the file name looked up
	 */
	Map<String, String> getMissing() {
		return this.missing;
	}

	@Override
	public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {

		URI location = resolve(systemId, baseURI);
		if (location == null || !isRemote(location)) {
			return null;
		}

		String rawPath = location.getRawPath();
		String fileName = rawPath == null ? "" : rawPath.substring(rawPath.lastIndexOf('/') + 1);
		Path local = PLAIN_FILE_NAME.matcher(fileName).matches() ? this.schemaDirectory.resolve(fileName) : null;

		LSInput input = this.inputs.createLSInput();
		input.setPublicId(publicId);
		input.setBaseURI(baseURI);
		if (local == null || !Files.isRegularFile(local)) {
			this.missing.put(location.toString(), fileName);
			input.setSystemId(location.toString());
			input.setStringData("");
		} else {
			input.setSystemId(local.toUri().toString());
		}
		return input;
	}

	private static boolean isRemote(URI location) {
		String scheme = location.getScheme();
		return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
	}

	private static URI resolve(String systemId, String baseURI) {

		if (systemId == null) {
			return null;
		}
		try {
			URI location = new URI(systemId);
			if (location.isAbsolute() || baseURI == null) {
				return location;
			}
			return new URI(baseURI).resolve(location);
		} catch (URISyntaxException | IllegalArgumentException e) {
			return null;
		}
	}
}
