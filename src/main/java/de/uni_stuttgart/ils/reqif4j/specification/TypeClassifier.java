package de.uni_stuttgart.ils.reqif4j.specification;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

/**
 * Strategy for classifying spec objects. ReqIF itself has no semantic notion
 * of "this is a requirement" — spec types only carry free-form LONG-NAMEs
 * whose meaning is a convention of the exporting tool or project.
 *
 * The default implementation ({@link LongNameTypeClassifier}) applies the
 * historic substring heuristic ("req", "sub", "headline" in the spec type
 * name). Projects using other naming conventions (e.g. German profiles or
 * exports from Polarion/Jama) can supply their own implementation via
 * {@code new ReqIF(path, classifier)} or {@code new ReqIFz(path, classifier)}.
 */
public interface TypeClassifier {

	/**
	 * Classifies a spec type into one of the coarse content categories.
	 *
	 * @param specType the spec type of the spec object
	 * @return one of {@link ReqIFConst#REQ}, {@link ReqIFConst#SUB_REQ},
	 *         {@link ReqIFConst#HEADLINE} or {@link ReqIFConst#TEXT}
	 */
	String classifySpecType(SpecType specType);

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
