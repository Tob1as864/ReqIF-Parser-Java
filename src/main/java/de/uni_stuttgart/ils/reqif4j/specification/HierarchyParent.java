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
	 * Inserts a child node at a position, 0 for first.
	 *
	 * @throws IllegalArgumentException if a child with the same IDENTIFIER
	 *         exists or the position is out of range
	 */
	HierarchyParent addChild(int index, SpecHierarchy child);

	/**
	 * Removes a direct child node, with everything below it.
	 *
	 * @return the removed node, or null if there is no such child
	 */
	SpecHierarchy removeChild(String specHierarchyID);
}
