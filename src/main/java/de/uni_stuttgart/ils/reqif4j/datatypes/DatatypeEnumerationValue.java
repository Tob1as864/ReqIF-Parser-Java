package de.uni_stuttgart.ils.reqif4j.datatypes;

import org.w3c.dom.Element;

import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class DatatypeEnumerationValue extends Identifiable {
	
	
	private java.lang.String key;
	private java.lang.String otherContent;
	
	
	
	
	public String getKey() {
		return this.key;
	}
	
	public String getOtherContent() {
		return this.otherContent;
	}
	
	
	
	
	public DatatypeEnumerationValue(String id, String name, String key, String otherContent) {
		super(id, name);

		this.key = key;
		this.otherContent = otherContent;
	}

	/**
	 * Reads an ENUM-VALUE element. A missing KEY or OTHER-CONTENT is read as "".
	 */
	DatatypeEnumerationValue(Element enumValue) {
		super(enumValue);

		Element embeddedValue = XmlUtils.firstDescendantByLocalName(enumValue, ReqIFConst.EMBEDDED_VALUE);
		this.key = XmlUtils.attributeOrEmpty(embeddedValue, ReqIFConst.KEY);
		this.otherContent = XmlUtils.attributeOrEmpty(embeddedValue, ReqIFConst.OTHER_CONTENT);
	}

}
