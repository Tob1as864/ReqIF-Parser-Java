package de.uni_stuttgart.ils.reqif4j.reqif;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Receives the problems found while reading a document.
 *
 * A {@link #collecting() collecting} instance lets the reader skip what it
 * cannot use and carry on, so one broken element does not make a whole
 * document unreadable; the constructors of the model classes that take no
 * issues use one and discard it. A {@link #strict() strict} instance stops
 * at the first problem with a {@link ReqIFParseException}.
 */
public final class ParseIssues {

	private final boolean strict;
	private final List<ParseIssue> issues = new ArrayList<ParseIssue>();

	private ParseIssues(boolean strict) {
		this.strict = strict;
	}

	public static ParseIssues collecting() {
		return new ParseIssues(false);
	}

	public static ParseIssues strict() {
		return new ParseIssues(true);
	}

	/**
	 * @throws ReqIFParseException if this instance is strict
	 */
	public void report(String elementID, String message) {
		ParseIssue issue = new ParseIssue(elementID, message);
		if (this.strict) {
			throw new ReqIFParseException(issue.toString());
		}
		this.issues.add(issue);
	}

	/**
	 * @return the issues in the order they were found (read-only)
	 */
	public List<ParseIssue> getIssues() {
		return Collections.unmodifiableList(this.issues);
	}

	/**
	 * Runs a reader for one element; if the element cannot be read at all (it
	 * has no IDENTIFIER), it is reported and skipped.
	 *
	 * @return what the reader returned, or null if the element was skipped
	 */
	public <T> T readOrSkip(String kind, java.util.function.Supplier<T> reader) {
		try {
			return reader.get();
		} catch (ReqIFParseException e) {
			if (this.strict) {
				throw e;
			}
			report(null, e.getMessage() + "; the " + kind + " is skipped");
			return null;
		}
	}

	/**
	 * Adds an element read from the document to the map that holds its kind,
	 * keyed by identifier. A second element with the same identifier replaces
	 * the first, which is reported.
	 *
	 * @param element may be null (skipped element), then nothing happens
	 */
	public <T extends Identifiable> void put(java.util.Map<String, T> elements, T element, String kind) {
		if (element == null) {
			return;
		}
		if (elements.containsKey(element.getID())) {
			report(element.getID(), kind + " " + element.getID() + " is declared twice; the later one replaces the earlier");
		}
		elements.put(element.getID(), element);
	}
}
