package de.uni_stuttgart.ils.reqif4j.datatypes;

public class Datatype {
	
	
	private String id;
	private String name;
	private String type;
	private String sourceElementName;
	private String alternativeID;




	public String getID() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

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

	/**
	 * @return the IDENTIFIER of the optional ALTERNATIVE-ID, or null
	 */
	public String getAlternativeID() {
		return this.alternativeID;
	}

	public Datatype setAlternativeID(String alternativeID) {
		this.alternativeID = alternativeID;
		return this;
	}




	public Datatype(String id, String name, String type) {

		this.id = id;
		this.name = name;
		this.type = type;
	}

	public Datatype(String id, String name, String type, String sourceElementName) {

		this(id, name, type);
		this.sourceElementName = sourceElementName;
	}

}
