package de.uni_stuttgart.ils.reqif4j.attributes;

import java.math.BigInteger;

/**
 * Value of an INTEGER attribute. ReqIF declares it as {@code xsd:integer},
 * which has no size limit, and DOORS regularly uses values beyond the int
 * range.
 *
 * {@link #getValue()} returns an {@link Integer} when the value fits, so
 * existing code comparing with int values keeps working, a {@link Long} when
 * it fits that, and a {@link BigInteger} otherwise. {@link #getBigInteger()}
 * always returns the exact value.
 */
public class AttributeValueInteger extends AttributeValue {

	private final BigInteger integer;

	public AttributeValueInteger(String value, AttributeDefinition type) {
		super(value, type);
		// Missing THE-VALUE reaches this constructor as null or "", both of
		// which must not crash the parser.
		this.integer = value == null || value.isBlank() ? BigInteger.ZERO : new BigInteger(value.trim());
		this.value = narrowest(this.integer);
	}

	/**
	 * @return the value as Integer, Long or BigInteger, whichever is the
	 *         smallest type that holds it
	 */
	@Override
	public Object getValue() {
		return this.value;
	}

	/**
	 * @return the exact value
	 */
	public BigInteger getBigInteger() {
		return this.integer;
	}

	private static Number narrowest(BigInteger value) {
		// bitLength excludes the sign bit
		if (value.bitLength() < Integer.SIZE) {
			return value.intValue();
		}
		if (value.bitLength() < Long.SIZE) {
			return value.longValue();
		}
		return value;
	}
}
