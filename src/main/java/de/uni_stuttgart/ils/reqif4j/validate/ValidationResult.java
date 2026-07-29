package de.uni_stuttgart.ils.reqif4j.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The findings of a validation run.
 */
public class ValidationResult {

	private final List<ValidationIssue> issues = new ArrayList<ValidationIssue>();

	void add(ValidationIssue.Severity severity, String elementID, String message) {
		this.issues.add(new ValidationIssue(severity, elementID, message));
	}

	void error(String elementID, String message) {
		add(ValidationIssue.Severity.ERROR, elementID, message);
	}

	void warning(String elementID, String message) {
		add(ValidationIssue.Severity.WARNING, elementID, message);
	}

	public List<ValidationIssue> getIssues() {
		return Collections.unmodifiableList(this.issues);
	}

	public List<ValidationIssue> getErrors() {
		List<ValidationIssue> errors = new ArrayList<ValidationIssue>();
		for (ValidationIssue issue : this.issues) {
			if (issue.isError()) {
				errors.add(issue);
			}
		}
		return errors;
	}

	/**
	 * @return true if the document has no errors (warnings are tolerated)
	 */
	public boolean isValid() {
		return getErrors().isEmpty();
	}

	/**
	 * Throws if the document has errors; useful before writing.
	 */
	public ValidationResult throwIfInvalid() {

		if (!isValid()) {
			StringBuilder message = new StringBuilder("ReqIF document is invalid:");
			for (ValidationIssue issue : getErrors()) {
				message.append("\n  ").append(issue);
			}
			throw new IllegalStateException(message.toString());
		}
		return this;
	}

	@Override
	public String toString() {

		if (this.issues.isEmpty()) {
			return "no issues";
		}
		StringBuilder text = new StringBuilder();
		for (ValidationIssue issue : this.issues) {
			text.append(issue).append('\n');
		}
		return text.toString();
	}
}
