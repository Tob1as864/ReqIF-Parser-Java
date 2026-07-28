package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.Map;

import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.datatypes.Datatype;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

public class RelationGroupType extends SpecType {


	public RelationGroupType(Node specType, Map<String, Datatype> dataTypes) {
		super(specType, dataTypes);

		this.type = ReqIFConst.RELATION_GROUP_TYPE;
	}

	/**
	 * Creates a relation group type from plain values, for documents that are
	 * generated instead of parsed.
	 */
	public RelationGroupType(String id, String name) {
		super(id, name, ReqIFConst.RELATION_GROUP_TYPE);
	}

}
