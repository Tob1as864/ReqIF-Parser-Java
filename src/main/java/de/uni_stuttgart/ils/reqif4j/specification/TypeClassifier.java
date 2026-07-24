package de.uni_stuttgart.ils.reqif4j.specification;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

/**
 * Strategy for classifying spec objects. ReqIF itself has no semantic notion
 * of "this is a requirement" — spec types only carry free-form LONG-NAMEs and
 * free-form attributes whose meaning is a convention of the exporting tool or
 * project.
 *
 * The classification methods receive the fully parsed {@link SpecObject}, so an
 * implementation may base its decision on the spec type name, on the object's
 * attribute values, or both.
 *
 * Two implementations are provided:
 * <ul>
 *   <li>{@link LongNameTypeClassifier} (the default) — substring heuristic on
 *       the spec type name ("req", "sub", "headline").</li>
 *   <li>{@link ReqIFImplementationGuideClassifier} — classifies by the
 *       standardized attribute names of the ProSTEP ReqIF Implementation Guide
 *       ({@code ReqIF.ChapterName}, {@code ReqIF.Text}), which real tools such
 *       as DOORS or Polarion emit; more robust and tool-independent.</li>
 * </ul>
 *
 * A custom implementation can be supplied via {@code new ReqIF(path, classifier)}
 * or {@code new ReqIFz(path, classifier)}.
 */
public interface TypeClassifier {

	/**
	 * Classifies a spec object into one of the coarse content categories. Called
	 * once during construction, after the object's attribute values are parsed.
	 *
	 * @param specObject the spec object being built (type, spec type and
	 *                   attribute values are available)
	 * @return one of {@link ReqIFConst#REQ}, {@link ReqIFConst#SUB_REQ},
	 *         {@link ReqIFConst#HEADLINE} or {@link ReqIFConst#TEXT}
	 */
	String classify(SpecObject specObject);

	/**
	 * Decides whether a spec object classified as {@link ReqIFConst#REQ}
	 * actually is a requirement, e.g. by evaluating a boolean flag attribute.
	 *
	 * @param specObject the spec object (type and attribute values are parsed)
	 */
	boolean isRequirement(SpecObject specObject);

	/**
	 * Decides whether a spec object classified as {@link ReqIFConst#SUB_REQ}
	 * actually is a sub-requirement.
	 *
	 * @param specObject the spec object (type and attribute values are parsed)
	 */
	boolean isSubRequirement(SpecObject specObject);

	/**
	 * @return the default classifier implementing the historic LONG-NAME
	 *         substring heuristic
	 */
	static TypeClassifier defaultClassifier() {
		return LongNameTypeClassifier.INSTANCE;
	}
}
