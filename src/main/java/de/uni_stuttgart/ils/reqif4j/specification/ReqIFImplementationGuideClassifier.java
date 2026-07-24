package de.uni_stuttgart.ils.reqif4j.specification;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

/**
 * {@link TypeClassifier} based on the standardized attribute names of the
 * ProSTEP iViP / ReqIF Implementor Forum "ReqIF Implementation Guide". Tools
 * such as IBM DOORS, PTC and Polarion emit spec objects carrying these
 * attributes regardless of how their spec <em>types</em> happen to be named:
 *
 * <ul>
 *   <li>{@code ReqIF.ChapterName} — present/filled on heading objects</li>
 *   <li>{@code ReqIF.Text} — the requirement/description body</li>
 * </ul>
 *
 * Classification rule (attribute values, not the type name):
 * <ol>
 *   <li>a non-empty {@code ReqIF.ChapterName} → {@link ReqIFConst#HEADLINE}</li>
 *   <li>otherwise a non-empty {@code ReqIF.Text} → {@link ReqIFConst#REQ}</li>
 *   <li>otherwise → {@link ReqIFConst#TEXT}</li>
 * </ol>
 *
 * This is more robust and tool-independent than the LONG-NAME heuristic of
 * {@link LongNameTypeClassifier}, because the attribute names are fixed by the
 * guide while the type names are free-form.
 *
 * <p>Note: the guide does not, by itself, distinguish a normative requirement
 * from informational text — both carry {@code ReqIF.Text}. Objects with body
 * text are therefore reported as {@link ReqIFConst#REQ}. If your project
 * profile marks requirements with an extra attribute, supply the attribute
 * names via the {@linkplain #ReqIFImplementationGuideClassifier(String, String)
 * constructor} or provide a custom {@link TypeClassifier}.
 */
public class ReqIFImplementationGuideClassifier implements TypeClassifier {

	/** Default attribute LONG-NAME carrying a heading's text. */
	public static final String CHAPTER_NAME_ATTRIBUTE = "ReqIF.ChapterName";

	/** Default attribute LONG-NAME carrying the requirement/description body. */
	public static final String TEXT_ATTRIBUTE = "ReqIF.Text";

	private final String chapterNameAttribute;
	private final String textAttribute;

	/**
	 * Uses the standard attribute names {@link #CHAPTER_NAME_ATTRIBUTE} and
	 * {@link #TEXT_ATTRIBUTE}.
	 */
	public ReqIFImplementationGuideClassifier() {
		this(CHAPTER_NAME_ATTRIBUTE, TEXT_ATTRIBUTE);
	}

	/**
	 * @param chapterNameAttribute attribute LONG-NAME identifying heading objects
	 * @param textAttribute        attribute LONG-NAME carrying the body text
	 */
	public ReqIFImplementationGuideClassifier(String chapterNameAttribute, String textAttribute) {
		this.chapterNameAttribute = chapterNameAttribute;
		this.textAttribute = textAttribute;
	}

	@Override
	public String classify(SpecObject specObject) {

		if (hasContent(specObject, this.chapterNameAttribute)) {
			return ReqIFConst.HEADLINE;
		}
		if (hasContent(specObject, this.textAttribute)) {
			return ReqIFConst.REQ;
		}
		return ReqIFConst.TEXT;
	}

	@Override
	public boolean isRequirement(SpecObject specObject) {
		return ReqIFConst.REQ.equals(specObject.getType());
	}

	@Override
	public boolean isSubRequirement(SpecObject specObject) {
		return ReqIFConst.SUB_REQ.equals(specObject.getType());
	}

	/**
	 * @return true if the object has an attribute of the given name with a
	 *         non-blank value
	 */
	private static boolean hasContent(SpecObject specObject, String attributeName) {
		AttributeValue attributeValue = specObject.getAttributes().get(attributeName);
		if (attributeValue == null) {
			return false;
		}
		Object value = attributeValue.getValue();
		return value != null && !value.toString().trim().isEmpty();
	}
}
