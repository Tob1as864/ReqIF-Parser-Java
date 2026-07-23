package de.uni_stuttgart.ils.reqif4j.specification;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValue;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

/**
 * Default {@link TypeClassifier}: the historic LONG-NAME substring heuristic.
 *
 * A spec type whose name contains "req" is a requirement type ("sub" + "req"
 * a sub-requirement type), a name containing "headline" is a headline type,
 * everything else is text. Whether a requirement-typed object really is a
 * requirement is decided by the first boolean attribute whose name contains
 * "req" (respectively "sub" and "req").
 *
 * This matches tool profiles with type names like "Req", "SubReq" and
 * "Headline". For other conventions provide your own {@link TypeClassifier}.
 */
public class LongNameTypeClassifier implements TypeClassifier {

	static final LongNameTypeClassifier INSTANCE = new LongNameTypeClassifier();

	@Override
	public String classifySpecType(SpecType specType) {

		String name = specType.getName() == null ? "" : specType.getName().toLowerCase();

		if (name.contains(ReqIFConst.REQ.toLowerCase())) {
			if (name.contains(ReqIFConst.SUB.toLowerCase())) {
				return ReqIFConst.SUB_REQ;
			}
			return ReqIFConst.REQ;
		}
		if (name.contains(ReqIFConst.HEADLINE.toLowerCase())) {
			return ReqIFConst.HEADLINE;
		}
		return ReqIFConst.TEXT;
	}

	@Override
	public boolean isRequirement(SpecObject specObject) {

		if (!ReqIFConst.REQ.equals(specObject.getType())) {
			return false;
		}
		for (AttributeValue attributeValue : specObject.getAttributes().values()) {

			if (isBoolean(attributeValue)
					&& attributeValue.getName().toLowerCase().contains(ReqIFConst.REQ.toLowerCase())) {
				return Boolean.TRUE.equals(attributeValue.getValue());
			}
		}
		return false;
	}

	@Override
	public boolean isSubRequirement(SpecObject specObject) {

		if (!ReqIFConst.SUB_REQ.equals(specObject.getType())) {
			return false;
		}
		for (AttributeValue attributeValue : specObject.getAttributes().values()) {

			if (isBoolean(attributeValue)
					&& attributeValue.getName().toLowerCase().contains(ReqIFConst.SUB.toLowerCase())
					&& attributeValue.getName().toLowerCase().contains(ReqIFConst.REQ.toLowerCase())) {
				return Boolean.TRUE.equals(attributeValue.getValue());
			}
		}
		return false;
	}

	private static boolean isBoolean(AttributeValue attributeValue) {
		return attributeValue.getAttributeDefinitionType() != null
				&& attributeValue.getAttributeDefinitionType().getDataType() != null
				&& ReqIFConst.BOOLEAN.equals(attributeValue.getAttributeDefinitionType().getDataType().getType());
	}
}
