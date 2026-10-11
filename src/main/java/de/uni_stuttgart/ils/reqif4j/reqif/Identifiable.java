package de.uni_stuttgart.ils.reqif4j.reqif;

import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

/**
 * Common base of every ReqIF element that has an identity: datatypes, enum
 * values, spec types, attribute definitions, spec objects, spec relations,
 * specifications, spec hierarchy nodes and relation groups.
 *
 * Mirrors the abstract {@code Identifiable} of the ReqIF metamodel:
 *
 * <pre>
 * IDENTIFIER     required  the element's id, unique within the document
 * LAST-CHANGE    required  xsd:dateTime of the last modification
 * LONG-NAME      optional  human readable name
 * DESC           optional  description
 * ALTERNATIVE-ID optional  a second, tool specific id
 * </pre>
 *
 * Values are kept exactly as written in the document; an attribute the
 * document does not declare is {@code null}, so it is not invented when the
 * element is written back.
 *
 * Creating an element from plain values or changing one through the API
 * marks it {@link #isModified() modified}; the writer then gives it the time of writing as LAST-CHANGE,
 * so tools that import the document see what changed, and takes that time
 * into the model, so writing again gives the same result. Setting LAST-CHANGE
 * explicitly after the last change takes precedence.
 */
public abstract class Identifiable {

	private String id;
	private String name;
	private String desc;
	private String lastChange;
	private String alternativeID;
	private boolean modified;


	/**
	 * For elements created from plain values instead of a document. A new
	 * element counts as modified, so it is written with the time of writing
	 * as LAST-CHANGE unless one is set; the builder clears that for the
	 * documents it assembles.
	 */
	protected Identifiable(String id, String name) {
		this.id = id;
		this.name = name;
		this.modified = true;
	}

	/**
	 * Reads IDENTIFIER, LONG-NAME, DESC, LAST-CHANGE and ALTERNATIVE-ID of a
	 * parsed element.
	 */
	protected Identifiable(Node element) {
		readIdentifiable(element);
	}

	/**
	 * Fills the properties from an element; for elements that are created from
	 * values while a document is read (datatypes).
	 *
	 * @throws ReqIFParseException if the element has no IDENTIFIER, which the
	 *         schema requires and every reference relies on
	 */
	protected final void readIdentifiable(Node element) {
		this.id = XmlUtils.attribute(element, ReqIFConst.IDENTIFIER);
		if (this.id == null) {
			throw new ReqIFParseException(XmlUtils.localName(element) + " without " + ReqIFConst.IDENTIFIER);
		}
		this.name = XmlUtils.attribute(element, ReqIFConst.LONG_NAME);
		this.desc = XmlUtils.attribute(element, ReqIFConst.DESC);
		this.lastChange = XmlUtils.attribute(element, ReqIFConst.LAST_CHANGE);
		this.alternativeID = XmlUtils.alternativeID(element);
		// as read, not changed
		this.modified = false;
	}


	/**
	 * @return the IDENTIFIER
	 */
	public String getID() {
		return this.id;
	}

	/**
	 * @return the LONG-NAME, or null if the element declares none
	 */
	public String getName() {
		return this.name;
	}

	public Identifiable setName(String name) {
		this.name = name;
		markModified();
		return this;
	}

	/**
	 * @return the DESC, or null if the element declares none
	 */
	public String getDesc() {
		return this.desc;
	}

	public Identifiable setDesc(String desc) {
		this.desc = desc;
		markModified();
		return this;
	}

	/**
	 * @return the LAST-CHANGE exactly as written (xsd:dateTime), or null if the
	 *         element declares none
	 */
	public String getLastChange() {
		return this.lastChange;
	}

	/**
	 * Sets LAST-CHANGE explicitly; the element is no longer considered
	 * modified, so the writer keeps this value - unless the element is
	 * changed again afterwards.
	 *
	 * @param lastChange xsd:dateTime, e.g. 2026-07-23T10:00:00Z
	 */
	public Identifiable setLastChange(String lastChange) {
		this.lastChange = lastChange;
		this.modified = false;
		return this;
	}

	/**
	 * @return the IDENTIFIER of the optional ALTERNATIVE-ID, or null
	 */
	public String getAlternativeID() {
		return this.alternativeID;
	}

	public Identifiable setAlternativeID(String alternativeID) {
		this.alternativeID = alternativeID;
		markModified();
		return this;
	}

	/**
	 * @return true if the element was changed through the API since it was
	 *         read or built (and LAST-CHANGE was not set since)
	 */
	public boolean isModified() {
		return this.modified;
	}

	/**
	 * Marks the element changed; done by every setter and structural change.
	 */
	public void markModified() {
		this.modified = true;
	}

	/**
	 * Forgets that the element was changed; the writer then keeps the
	 * LAST-CHANGE it has.
	 */
	public void clearModified() {
		this.modified = false;
	}
}
