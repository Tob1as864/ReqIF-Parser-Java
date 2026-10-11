package de.uni_stuttgart.ils.reqif4j.write;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

/**
 * Maps the parser's datatype categories to the ReqIF element names used when
 * writing a document.
 *
 * Note that the parser normalizes {@code DATATYPE-DEFINITION-REAL} to the
 * category {@link ReqIFConst#DOUBLE}; on the way out the standard name REAL is
 * emitted again.
 */
final class ReqIFElements {

	private ReqIFElements() {
	}

	/** @return e.g. DATATYPE-DEFINITION-STRING */
	static String datatypeDefinition(String datatypeCategory) {
		String suffix = ReqIFConst.datatypeSuffix(datatypeCategory);
		return suffix == null ? null : "DATATYPE-DEFINITION-" + suffix;
	}

	/** @return e.g. DATATYPE-DEFINITION-STRING-REF */
	static String datatypeDefinitionRef(String sourceElementName) {
		return sourceElementName + "-REF";
	}

	/** @return e.g. ATTRIBUTE-VALUE-STRING */
	static String attributeValue(String datatypeCategory) {
		String suffix = ReqIFConst.datatypeSuffix(datatypeCategory);
		return suffix == null ? null : "ATTRIBUTE-VALUE-" + suffix;
	}

	/**
	 * @return the *-TYPE-REF element name belonging to a spec type kind
	 *         (SPEC-OBJECT-TYPE, SPECIFICATION-TYPE, SPEC-RELATION-TYPE)
	 */
	static String specTypeRef(String specTypeKind) {

		if (ReqIFConst.SPECIFICATION_TYPE.equals(specTypeKind)) {
			// the constant deliberately differs from the element name
			return ReqIFConst.SPEC_TYPE_REF;
		}
		return specTypeKind + "-REF";
	}
}
