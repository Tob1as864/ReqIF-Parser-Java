package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ordered children of a specification or a spec hierarchy node. Each
 * child knows its parent, so a node is in one place only and cannot be put
 * below itself.
 *
 * Two views of all nodes below are kept, as callers ask for them repeatedly:
 * the flattened list, rebuilt after the structure below changed, and an
 * index by IDENTIFIER, kept up to date as nodes come and go, so that looking
 * nodes up while changing a large hierarchy stays cheap.
 */
final class HierarchyChildren {

	private final HierarchyParent owner;
	private final List<SpecHierarchy> ordered = new ArrayList<SpecHierarchy>();
	private final Map<String, SpecHierarchy> byID = new HashMap<String, SpecHierarchy>();
	private List<SpecHierarchy> descendants;
	private Map<String, SpecHierarchy> index;
	/** whether two nodes below have the same IDENTIFIER (only read documents) */
	private boolean duplicates;

	HierarchyChildren(HierarchyParent owner) {
		this.owner = owner;
	}

	/**
	 * Adds the children of an owner that is being created; a child with an
	 * IDENTIFIER that is already there replaces the earlier one.
	 *
	 * @throws IllegalArgumentException if one of them is placed elsewhere;
	 *         then none is added
	 */
	void putAll(List<SpecHierarchy> children) {
		for (SpecHierarchy child : children) {
			checkPlaceable(child, true);
		}
		for (SpecHierarchy child : children) {
			put(child);
		}
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
		if (this.byID.containsKey(child.getID())) {
			issues.reportDuplicate("Spec hierarchy", child.getID());
		}
		put(child);
	}

	private void put(SpecHierarchy child) {
		SpecHierarchy previous = this.byID.put(child.getID(), child);
		if (previous == child) {
			return;
		}
		if (previous != null) {
			// in the place of the earlier one, as a map would keep it
			this.ordered.set(position(previous), child);
			previous.setParent(null);
			removed(previous);
		} else {
			this.ordered.add(child);
		}
		child.setParent(this.owner);
		added(child);
	}

	/**
	 * @return the children in order (a copy)
	 */
	List<SpecHierarchy> list() {
		return new ArrayList<SpecHierarchy>(this.ordered);
	}

	/**
	 * @return the children in order (a live view, read-only)
	 */
	List<SpecHierarchy> values() {
		return Collections.unmodifiableList(this.ordered);
	}

	/**
	 * @param index position among the children, 0 for first; the number of
	 *              children appends
	 * @throws IllegalArgumentException if the node is already placed somewhere,
	 *         a child with the same IDENTIFIER exists, the node is the owner or
	 *         above it, or the index is out of range
	 */
	void add(int index, SpecHierarchy child) {

		checkPlaceable(child, false);
		if (this.byID.containsKey(child.getID())) {
			throw new IllegalArgumentException("Spec hierarchy " + child.getID() + " is already a child here");
		}
		if (index < 0 || index > this.ordered.size()) {
			throw new IllegalArgumentException("Position " + index + " is outside 0.." + this.ordered.size());
		}
		this.ordered.add(index, child);
		this.byID.put(child.getID(), child);
		child.setParent(this.owner);
		added(child);
	}

	/**
	 * Replaces the children by these, in this order, in one step. Former
	 * children that are not among them are removed with everything below them.
	 *
	 * @throws IllegalArgumentException if two have the same IDENTIFIER, one is
	 *         placed elsewhere, or one is the owner or above it; then nothing
	 *         changes
	 */
	void replace(List<SpecHierarchy> children) {

		Set<String> ids = new HashSet<String>();
		for (SpecHierarchy child : children) {
			checkPlaceable(child, true);
			if (!ids.add(child.getID())) {
				throw new IllegalArgumentException("Spec hierarchy " + child.getID() + " is given twice");
			}
		}
		Set<SpecHierarchy> kept = Collections.newSetFromMap(new IdentityHashMap<SpecHierarchy, Boolean>());
		kept.addAll(children);
		for (SpecHierarchy former : this.ordered) {
			if (!kept.contains(former)) {
				former.setParent(null);
			}
		}
		this.ordered.clear();
		this.byID.clear();
		for (SpecHierarchy child : children) {
			this.ordered.add(child);
			this.byID.put(child.getID(), child);
			child.setParent(this.owner);
		}
		// many nodes at once: the views are rebuilt when needed
		for (HierarchyChildren level = this; level != null; level = level.above()) {
			level.descendants = null;
			level.index = null;
		}
	}

	/**
	 * @param ownChildToo whether a node that is already a child here may be
	 *                    placed again (in a replacement of all children)
	 * @throws IllegalArgumentException if the node has no IDENTIFIER, is
	 *         placed elsewhere, or is the owner or above it
	 */
	private void checkPlaceable(SpecHierarchy child, boolean ownChildToo) {
		if (child == null || child.getID() == null) {
			throw new IllegalArgumentException("A spec hierarchy node needs an identifier");
		}
		if (child.getParent() != null && !(ownChildToo && child.getParent() == this.owner)) {
			throw new IllegalArgumentException("Spec hierarchy " + child.getID() + " is already placed below "
					+ child.getParent().getID() + "; remove or move it instead");
		}
		for (HierarchyParent ancestor = this.owner; ancestor != null; ancestor = parentOf(ancestor)) {
			if (ancestor == child) {
				throw new IllegalArgumentException("Spec hierarchy " + child.getID() + " cannot be placed below itself");
			}
		}
	}

	private static HierarchyParent parentOf(HierarchyParent node) {
		return node instanceof SpecHierarchy ? ((SpecHierarchy) node).getParent() : null;
	}

	SpecHierarchy remove(String id) {
		SpecHierarchy removed = this.byID.remove(id);
		if (removed != null) {
			this.ordered.remove(position(removed));
			removed.setParent(null);
			removed(removed);
		}
		return removed;
	}

	/**
	 * @return the position of the child, or -1
	 */
	int indexOf(String id) {
		SpecHierarchy child = this.byID.get(id);
		return child == null ? -1 : position(child);
	}

	private int position(SpecHierarchy child) {
		for (int i = 0; i < this.ordered.size(); i++) {
			if (this.ordered.get(i) == child) {
				return i;
			}
		}
		return -1;
	}

	int size() {
		return this.ordered.size();
	}

	SpecHierarchy get(String id) {
		return this.byID.get(id);
	}

	/**
	 * @return all nodes below, depth-first in document order (read-only)
	 */
	List<SpecHierarchy> descendants() {
		if (this.descendants == null) {
			List<SpecHierarchy> all = new ArrayList<SpecHierarchy>();
			for (SpecHierarchy child : this.ordered) {
				all.add(child);
				all.addAll(child.getAllChildren());
			}
			this.descendants = Collections.unmodifiableList(all);
		}
		return this.descendants;
	}

	/**
	 * @return the node below with this IDENTIFIER; for an identifier used
	 *         twice the first in document order
	 */
	SpecHierarchy descendant(String id) {
		if (this.index == null) {
			this.index = new HashMap<String, SpecHierarchy>();
			this.duplicates = false;
			for (SpecHierarchy node : descendants()) {
				if (this.index.putIfAbsent(node.getID(), node) != null) {
					this.duplicates = true;
				}
			}
		}
		return this.index.get(id);
	}

	/**
	 * Updates the views here and above after a node, with everything below
	 * it, was added.
	 */
	private void added(SpecHierarchy node) {
		List<SpecHierarchy> subtree = null;
		for (HierarchyChildren level = this; level != null; level = level.above()) {
			level.descendants = null;
			if (level.index != null) {
				subtree = subtree != null ? subtree : subtree(node);
				for (SpecHierarchy added : subtree) {
					if (level.index.containsKey(added.getID())) {
						// which of the two comes first in document order is not
						// known here: the index starts over
						level.index = null;
						break;
					}
					level.index.put(added.getID(), added);
				}
			}
		}
	}

	/**
	 * Updates the views here and above after a node, with everything below
	 * it, was removed.
	 */
	private void removed(SpecHierarchy node) {
		List<SpecHierarchy> subtree = null;
		for (HierarchyChildren level = this; level != null; level = level.above()) {
			level.descendants = null;
			if (level.index != null && level.duplicates) {
				// another node may have the identifier: start over
				level.index = null;
			} else if (level.index != null) {
				subtree = subtree != null ? subtree : subtree(node);
				for (SpecHierarchy removed : subtree) {
					level.index.remove(removed.getID(), removed);
				}
			}
		}
	}

	private static List<SpecHierarchy> subtree(SpecHierarchy node) {
		List<SpecHierarchy> subtree = new ArrayList<SpecHierarchy>();
		subtree.add(node);
		subtree.addAll(node.getAllChildren());
		return subtree;
	}

	/**
	 * @return the children of the owner's parent, or null at the top
	 */
	private HierarchyChildren above() {
		HierarchyParent parent = parentOf(this.owner);
		if (parent instanceof SpecHierarchy) {
			return ((SpecHierarchy) parent).hierarchyChildren();
		}
		return parent instanceof Specification ? ((Specification) parent).hierarchyChildren() : null;
	}
}
