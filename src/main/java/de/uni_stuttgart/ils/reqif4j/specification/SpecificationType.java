package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.Map;

import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

public class SpecificationType extends SpecType {
	
	
	public SpecificationType(Node specType, Map<String, Datatype> dataTypes) {
		super(specType, dataTypes);

		this.type = ReqIFConst.SPECIFICATION_TYPE;
	}

	/**
	 * Creates a specification type from plain values, for documents that are
	 * generated instead of parsed.
	 */
	public SpecificationType(String id, String name) {
		super(id, name, ReqIFConst.SPECIFICATION_TYPE);
	}

}
