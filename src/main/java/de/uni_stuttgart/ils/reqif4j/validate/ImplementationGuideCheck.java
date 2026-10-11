package de.uni_stuttgart.ils.reqif4j.validate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeDefinition;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.attributes.XHTML;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFCoreContent;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFDocument;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFz;
import de.uni_stuttgart.ils.reqif4j.specification.SpecElementWithAttributes;
import de.uni_stuttgart.ils.reqif4j.specification.SpecType;
import de.uni_stuttgart.ils.reqif4j.util.XhtmlParser;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementDiv;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementObject;

/**
 * Checks the conventions of the <em>ReqIF Implementation Guide</em> of the
 * ReqIF Implementor Forum (prostep ivip), which tools rely on when they
 * exchange documents but the schema does not express:
 *
 * <ul>
 * <li>the standard attributes ({@code ReqIF.Text}, {@code ReqIF.ChapterName},
 * {@code ReqIF.ForeignID}, ...) have the datatype tools expect: dates are
 * DATE, {@code ReqIF.ForeignDeleted} is BOOLEAN, {@code ReqIF.Text} is XHTML,
 * the names and descriptions are STRING or XHTML;</li>
 * <li>embedded objects state their MIME type, reference a file of the
 * archive by a relative path, and an object that is not a PNG image (e.g. an
 * OLE object) contains a PNG image as preview for tools that cannot show
 * it;</li>
 * <li>a simplified XHTML value (IS-SIMPLIFIED) carries THE-ORIGINAL-VALUE and
 * vice versa;</li>
 * <li>when an archive is checked, every file an object references is in
 * it.</li>
 * </ul>
 *
 * Findings are warnings - the document is valid ReqIF, but importing tools
 * may show it differently than intended - except for files missing from an
 * archive, which are errors. Use it next to {@link ReqIFValidator}:
 *
 * <pre>
 * ValidationResult conventions = new ImplementationGuideCheck().check(document);
 * </pre>
 */
public class ImplementationGuideCheck {

	/** PNG, the image format of previews */
	public static final String PNG = "image/png";

	/** the datatype kinds each standard attribute is expected to have, sorted */
	private static final Map<String, List<String>> STANDARD_ATTRIBUTES = Map.ofEntries(
			Map.entry("ReqIF.Text", List.of(ReqIFConst.XHTML)),
			Map.entry("ReqIF.ChapterName", List.of(ReqIFConst.STRING, ReqIFConst.XHTML)),
			Map.entry("ReqIF.Name", List.of(ReqIFConst.STRING, ReqIFConst.XHTML)),
			Map.entry("ReqIF.Description", List.of(ReqIFConst.STRING, ReqIFConst.XHTML)),
			Map.entry("ReqIF.Prefix", List.of(ReqIFConst.STRING, ReqIFConst.XHTML)),
			Map.entry("ReqIF.ForeignID", List.of(ReqIFConst.INTEGER, ReqIFConst.STRING, ReqIFConst.XHTML)),
			Map.entry("ReqIF.ForeignCreatedBy", List.of(ReqIFConst.STRING, ReqIFConst.XHTML)),
			Map.entry("ReqIF.ForeignModifiedBy", List.of(ReqIFConst.STRING, ReqIFConst.XHTML)),
			Map.entry("ReqIF.ForeignCreatedOn", List.of(ReqIFConst.DATE)),
			Map.entry("ReqIF.ForeignModifiedOn", List.of(ReqIFConst.DATE)),
			Map.entry("ReqIF.ForeignDeleted", List.of(ReqIFConst.BOOLEAN)));

	/** a URI scheme, e.g. http: or file: */
	private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]+:.*");
	/** a Windows drive, e.g. C:\ */
	private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:[\\\\/].*");

	/**
	 * Checks one document; references from objects to files cannot be
	 * checked without the archive, see {@link #check(ReqIFz)}.
	 */
	public ValidationResult check(ReqIFDocument document) {
		ValidationResult result = new ValidationResult();
		check(document, new Scope(null, "", ""), result);
		return result;
	}

	/**
	 * Checks every document of an archive, and that the files their objects
	 * reference are in it.
	 */
	public ValidationResult check(ReqIFz archive) {
		ValidationResult result = new ValidationResult();
		// entry names spelled like references: ./ and .. collapsed, decoded
		Set<String> files = new java.util.HashSet<String>();
		for (String entry : archive.getAttachmentFiles().keySet()) {
			String normalized = resolve("", entry);
			files.add(normalized != null ? normalized : entry);
		}
		for (Map.Entry<String, ReqIFDocument> document : archive.getReqIFDocuments().entrySet()) {
			String entry = document.getKey().replace('\\', '/');
			// objects reference files relative to the document
			String directory = entry.contains("/") ? entry.substring(0, entry.lastIndexOf('/') + 1) : "";
			check(document.getValue(), new Scope(files, directory, document.getKey() + ": "), result);
		}
		return result;
	}

	/**
	 * @param files     the archive's files, or null if not known
	 * @param directory the directory of the document in the archive, "" or
	 *                  ending with /
	 * @param where     what each message starts with
	 */
	private static final class Scope {
		final Set<String> files;
		final String directory;
		final String where;

		Scope(Set<String> files, String directory, String where) {
			this.files = files;
			this.directory = directory;
			this.where = where;
		}
	}

	private void check(ReqIFDocument document, Scope scope, ValidationResult result) {
		String where = scope.where;

		ReqIFCoreContent content = document.getCoreContent();
		if (content == null) {
			return;
		}
		for (SpecType specType : content.getSpecTypes().values()) {
			for (AttributeDefinition definition : specType.getAttributeDefinitions().values()) {
				checkStandardAttribute(definition, result, where);
				AttributeValueXHTML defaultValue = xhtmlDefault(definition);
				if (defaultValue != null) {
					// objects show wherever the default applies
					checkXhtml(definition.getID(), defaultValue, scope, result);
				}
			}
		}
		for (Collection<? extends SpecElementWithAttributes> elements : List.of(content.getSpecObjects().values(),
				content.getSpecRelation().values(), content.getSpecifications().values())) {
			for (SpecElementWithAttributes element : elements) {
				for (AttributeValue value : element.getValues().values()) {
					if (value instanceof AttributeValueXHTML) {
						checkXhtml(element.getID(), (AttributeValueXHTML) value, scope, result);
					}
				}
			}
		}
	}

	private static void checkStandardAttribute(AttributeDefinition definition, ValidationResult result, String where) {

		List<String> expected = definition.getName() == null ? null : STANDARD_ATTRIBUTES.get(definition.getName());
		if (expected == null || definition.getDataType() == null) {
			return;
		}
		String kind = ReqIFConst.datatypeSuffix(definition.getDataType().getType());
		if (kind != null && !expected.contains(kind)) {
			result.warning(definition.getID(), where + "The standard attribute " + definition.getName() + " is of kind "
					+ kind + "; tools expect " + String.join(" or ", expected));
		}
	}

	/**
	 * @return the default of an XHTML attribute, or null
	 */
	private static AttributeValueXHTML xhtmlDefault(AttributeDefinition definition) {
		if (definition.getDefaultXhtmlValue() != null) {
			return definition.getDefaultXhtmlValue();
		}
		boolean xhtml = definition.getDataType() != null && ReqIFConst.XHTML.equals(definition.getDataType().getType());
		String markup = definition.getDefaultValue();
		try {
			return xhtml && markup != null && !markup.isBlank() ? new AttributeValueXHTML(markup, definition) : null;
		} catch (IllegalArgumentException e) {
			// not well-formed: the validator reports that
			return null;
		}
	}

	private static void checkXhtml(String ownerID, AttributeValueXHTML value, Scope scope, ValidationResult result) {

		String where = scope.where;
		boolean hasOriginal = value.getOriginalValue() != null;
		if (Boolean.TRUE.equals(value.getSimplified()) && !hasOriginal) {
			result.warning(ownerID, where + "An XHTML value of attribute " + definitionID(value)
					+ " is IS-SIMPLIFIED but has no THE-ORIGINAL-VALUE");
		} else if (hasOriginal && !Boolean.TRUE.equals(value.getSimplified())) {
			result.warning(ownerID, where + "An XHTML value of attribute " + definitionID(value)
					+ " has THE-ORIGINAL-VALUE but is not marked IS-SIMPLIFIED");
		}
		// a preview often is in both THE-VALUE and THE-ORIGINAL-VALUE; it is
		// reported once
		Set<String> checked = new java.util.HashSet<String>();
		for (XHTMLElementDiv content : new XHTMLElementDiv[] { value.getDivValue(), value.getOriginalValue() }) {
			if (content != null && content.getNode() != null) {
				for (Element object : XmlUtils.descendantsByLocalName(content.getNode(), XHTML.OBJECT)) {
					if (isXhtmlObject(object) && checked.add(object.getAttribute("data") + "|" + object.getAttribute("type"))) {
						checkObject(ownerID, object, scope, result);
					}
				}
			}
		}
	}

	private static boolean isXhtmlObject(Node node) {
		return node instanceof Element && XHTML.OBJECT.equals(XmlUtils.localName(node))
				&& XhtmlParser.XHTML_NAMESPACE.equals(node.getNamespaceURI());
	}

	private static void checkObject(String ownerID, Element element, Scope scope, ValidationResult result) {

		XHTMLElementObject object = new XHTMLElementObject(element);
		String data = object.getData();
		String type = object.getMimeType() == null ? null : object.getMimeType().trim();
		boolean inline = data != null && data.regionMatches(true, 0, "data:", 0, 5);
		String what = scope.where + "Object " + (data == null ? "without data" : abbreviate(data));

		if (type == null || type.isEmpty()) {
			result.warning(ownerID, what + " has no MIME type (type attribute)");
		}
		if (data != null && !inline && isAbsolute(data)) {
			result.warning(ownerID, what + " is referenced by an absolute location; "
					+ "objects reference files of the archive by a relative path");
		} else if (data != null && !inline && scope.files != null) {
			String entry = resolve(scope.directory, data);
			if (entry == null) {
				result.error(ownerID, what + " points outside the archive");
			} else if (!scope.files.contains(entry)) {
				result.error(ownerID, what + " is not in the archive");
			}
		}
		// an object a tool may not be able to show, e.g. an OLE object, comes
		// with a PNG image as preview - unless it is the preview itself
		boolean preview = isXhtmlObject(element.getParentNode());
		if (type != null && !type.isEmpty() && !PNG.equalsIgnoreCase(type) && !preview && !hasPngPreview(element)) {
			result.warning(ownerID, what + " of type " + type + " has no PNG image as preview (a nested object)");
		}
	}

	private static boolean hasPngPreview(Element object) {
		for (Node child = object.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (isXhtmlObject(child) && PNG.equalsIgnoreCase(
					String.valueOf(new XHTMLElementObject(child).getMimeType()).trim())) {
				return true;
			}
		}
		return false;
	}

	/** keeps messages short, e.g. for inline data */
	private static String abbreviate(String data) {
		return data.length() <= 100 ? data : data.substring(0, 100) + "...";
	}

	/**
	 * @return the archive entry a reference names: decoded (%20), resolved
	 *         against the document's directory, with forward slashes; null if
	 *         it climbs above the root of the archive
	 */
	private static String resolve(String directory, String data) {
		String path = data.replace('\\', '/');
		try {
			String decoded = new java.net.URI(path).getPath();
			if (decoded != null) {
				path = decoded;
			}
		} catch (java.net.URISyntaxException e) {
			// not a valid URI, e.g. with a blank: taken as written
		}
		java.util.Deque<String> segments = new java.util.ArrayDeque<String>();
		for (String segment : (directory + path).split("/")) {
			if (segment.equals("..")) {
				if (segments.pollLast() == null) {
					return null;
				}
			} else if (!segment.isEmpty() && !segment.equals(".")) {
				segments.addLast(segment);
			}
		}
		return String.join("/", segments);
	}

	/** a URI with a scheme, a path from the root or a Windows drive */
	private static boolean isAbsolute(String data) {
		return DRIVE.matcher(data).matches() || SCHEME.matcher(data).matches() || data.startsWith("/")
				|| data.startsWith("\\");
	}

	private static String definitionID(AttributeValue value) {
		return value.getAttributeDefinitionType() == null ? null : value.getAttributeDefinitionType().getID();
	}
}
