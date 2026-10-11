package de.uni_stuttgart.ils.reqif4j.datatypes;

import de.uni_stuttgart.ils.reqif4j.reqif.Identifiable;

public class Datatype extends Identifiable {
	
	
	private String type;
	private String sourceElementName;




	public String getType() {
		return this.type;
	}

	/**
	 * @return the DATATYPE-DEFINITION-* element name this datatype was read
	 *         from, or null when it was not created from a document. Needed to
	 *         write back datatypes the parser does not model explicitly.
	 */
	public String getSourceElementName() {
		return this.sourceElementName;
	}

	@Override
	public Datatype setAlternativeID(String alternativeID) {
		super.setAlternativeID(alternativeID);
		return this;
	}




	public Datatype(String id, String name, String type) {
		super(id, name);

		this.type = type;
	}

	public Datatype(String id, String name, String type, String sourceElementName) {

		this(id, name, type);
		this.sourceElementName = sourceElementName;
	}

}
