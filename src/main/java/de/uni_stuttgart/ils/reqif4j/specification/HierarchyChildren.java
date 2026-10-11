package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ordered children of a specification or a spec hierarchy node, keyed by
 * IDENTIFIER. Each child knows its parent, so a node is in one place only and
 * cannot be put below itself.
 *
 * The flattened list of all nodes below is kept until the structure below
 * changes, as callers often ask for it repeatedly.
 */
final class HierarchyChildren {

	private final HierarchyParent owner;
	private final Map<String, SpecHierarchy> children = new LinkedHashMap<String, SpecHierarchy>();
	private List<SpecHierarchy> descendants;

	HierarchyChildren(HierarchyParent owner) {
		this.owner = owner;
	}

	/**
	 * Adds a child while the owner is being read or created; a child with an
	 * IDENTIFIER that is already there replaces the earlier one.
	 */
	void put(SpecHierarchy child) {
		SpecHierarchy previous = this.children.put(child.getID(), child);
		if (previous != null && previous != child) {
			previous.setParent(null);
		}
		child.setParent(this.owner);
		changed();
	}

	/**
	 * Adds a child read from a document; one with an IDENTIFIER that is
	 * already there replaces the earlier one, which is reported.
	 *
	 * @param child may be null (a skipped node), then nothing happens
	 */
	void put(SpecHierarchy child, de.uni_stuttgart.ils.reqif4j.reqif.ParseIssues issues) {
		if (child == null) {
			return;
		}
		if (this.children.containsKey(child.getID())) {
			issues.reportDuplicate("Spec hierarchy", child.getID());
		}
		put(child);
	}

	List<SpecHierarchy> list() {
		return new ArrayList<SpecHierarchy>(this.children.values());
	}

	/**
	 * @param index position among the children, 0 for first; the number of
	 *              children appends
	 * @throws IllegalArgumentException if the node is already placed somewhere,
	 *         a child with the same IDENTIFIER exists, the node is the owner or
	 *         above it, or the index is out of range
	 */
	void add(int index, SpecHierarchy child) {

		if (child == null || child.getID() == null) {
			throw new IllegalArgumentException("A spec hierarchy node needs an identifier");
		}
		if (child.getParent() != null) {
			throw new IllegalArgumentException("Spec hierarchy " + child.getID() + " is already placed below "
					+ child.getParent().getID() + "; remove or move it instead");
		}
		for (HierarchyParent ancestor = this.owner; ancestor != null; ancestor = parentOf(ancestor)) {
			if (ancestor == child) {
				throw new IllegalArgumentException("Spec hierarchy " + child.getID() + " cannot be placed below itself");
			}
		}
		if (this.children.containsKey(child.getID())) {
			throw new IllegalArgumentException("Spec hierarchy " + child.getID() + " is already a child here");
		}
		if (index < 0 || index > this.children.size()) {
			throw new IllegalArgumentException("Position " + index + " is outside 0.." + this.children.size());
		}
		if (index == this.children.size()) {
			this.children.put(child.getID(), child);
		} else {
			List<SpecHierarchy> ordered = list();
			ordered.add(index, child);
			this.children.clear();
			for (SpecHierarchy node : ordered) {
				this.children.put(node.getID(), node);
			}
		}
		child.setParent(this.owner);
		changed();
	}

	private static HierarchyParent parentOf(HierarchyParent node) {
		return node instanceof SpecHierarchy ? ((SpecHierarchy) node).getParent() : null;
	}

	SpecHierarchy remove(String id) {
		SpecHierarchy removed = this.children.remove(id);
		if (removed != null) {
			removed.setParent(null);
			changed();
		}
		return removed;
	}

	int size() {
		return this.children.size();
	}

	/**
	 * @return all nodes below, depth-first in document order (read-only)
	 */
	List<SpecHierarchy> descendants() {
		if (this.descendants == null) {
			List<SpecHierarchy> all = new ArrayList<SpecHierarchy>();
			for (SpecHierarchy child : this.children.values()) {
				all.add(child);
				all.addAll(child.getAllChildren());
			}
			this.descendants = Collections.unmodifiableList(all);
		}
		return this.descendants;
	}

	/**
	 * Forgets the flattened lists here and above, after the structure changed.
	 */
	void changed() {
		this.descendants = null;
		HierarchyParent parent = parentOf(this.owner);
		if (parent instanceof SpecHierarchy) {
			((SpecHierarchy) parent).hierarchyChildren().changed();
		} else if (parent instanceof Specification) {
			((Specification) parent).hierarchyChildren().changed();
		}
	}
}
