package de.uni_stuttgart.ils.reqif4j.datatypes;

import java.math.BigInteger;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;

public class DatatypeInteger extends Datatype {


	private final long max;
	private final long min;


	public long getMax() {
		return this.max;
	}

	public long getMin() {
		return this.min;
	}


	/**
	 * MIN/MAX are optional in ReqIF and may exceed the int range (DOORS
	 * regularly exports long-range bounds). Missing or unparseable values fall
	 * back to the long range; larger values are clamped.
	 */
	public DatatypeInteger(String id, String name, String min, String max) {
		super(id, name, ReqIFConst.INTEGER);

		this.min = parseBound(min, Long.MIN_VALUE);
		this.max = parseBound(max, Long.MAX_VALUE);
	}

	private static long parseBound(String value, long fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException outOfLongRange) {
			try {
				BigInteger bound = new BigInteger(value.trim());
				return bound.signum() < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
			} catch (NumberFormatException notANumber) {
				return fallback;
			}
		}
	}

}
