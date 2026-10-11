package de.uni_stuttgart.ils.reqif4j.specification;

import java.util.List;

/**
 * What holds spec hierarchy nodes: a specification (the top level) or a
 * hierarchy node (the levels below).
 */
public interface HierarchyParent {

	/**
	 * @return the IDENTIFIER of this specification or hierarchy node
	 */
	String getID();

	/**
	 * @return the direct children in document order (a copy)
	 */
	List<SpecHierarchy> getChildren();

	/**
	 * @return the number of direct children
	 */
	default int getChildCount() {
		return getChildren().size();
	}

	/**
	 * @return the direct child with this IDENTIFIER, or null
	 */
	default SpecHierarchy getChild(String specHierarchyID) {
		for (SpecHierarchy child : getChildren()) {
			if (child.getID().equals(specHierarchyID)) {
				return child;
			}
		}
		return null;
	}

	/**
	 * Inserts a child node at a position, 0 for first.
	 *
	 * @throws IllegalArgumentException if a child with the same IDENTIFIER
	 *         exists or the position is out of range
	 */
	HierarchyParent addChild(int index, SpecHierarchy child);

	/**
	 * Replaces the children by these, in this order, in one step: to reorder
	 * them, or to add and remove several at once. Former children that are
	 * not among them are removed with everything below them.
	 *
	 * @throws IllegalArgumentException if two have the same IDENTIFIER, one is
	 *         placed elsewhere, or one is this node or above it
	 */
	HierarchyParent setChildren(List<SpecHierarchy> children);

	/**
	 * Removes a direct child node, with everything below it.
	 *
	 * @return the removed node, or null if there is no such child
	 */
	SpecHierarchy removeChild(String specHierarchyID);
}
