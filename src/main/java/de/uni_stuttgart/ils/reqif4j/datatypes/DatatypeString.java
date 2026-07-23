package de.uni_stuttgart.ils.reqif4j.datatypes;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

public class DatatypeString extends Datatype {


	private final int maxLength;


	public int getMaxLength() {
		return this.maxLength;
	}


	/**
	 * MAX-LENGTH is optional in ReqIF; a missing or unparseable value means
	 * "unbounded" and maps to {@link Integer#MAX_VALUE}.
	 */
	public DatatypeString(String id, String name, String maxLength) {
		super(id, name, ReqIFConst.STRING);

		int parsedMaxLength = Integer.MAX_VALUE;
		if (maxLength != null && !maxLength.isBlank()) {
			try {
				parsedMaxLength = Integer.parseInt(maxLength.trim());
			} catch (NumberFormatException ignored) {
				// keep unbounded
			}
		}
		this.maxLength = parsedMaxLength;
	}

}
