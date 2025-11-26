package de.uni_stuttgart.ils.reqif4j.xhtml;

import org.w3c.dom.Node;

import java.util.List;


public class XHTMLNode {

	protected String tagName;
	protected XHTMLNode parent = null;
	protected Node node;
	
	
	/**
	 * @return the node name of this xhtml node
	 */
	public String getTagName() {
		return this.tagName;
	}
	
	/**
	 * @return the parent XHTMLNode of this XHTMLNode
	 */
	public XHTMLNode getParent() {
		return this.parent;
	}
	
	
	
	public XHTMLNode(Node xhtmlElement) {

		this.node = xhtmlElement;
		this.tagName = xhtmlElement.getNodeName();
	}
	
	public XHTMLNode(Node xhtmlElement, XHTMLNode parent) {
		this.node = xhtmlElement;
		this.tagName = xhtmlElement.getNodeName();
		this.parent = parent;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("<").append(tagName).append(">");
		sb.append("</").append(tagName).append(">");
		return sb.toString();
	}

	public Node getNode() {
		return node;
	}

	protected String listToString(List<XHTMLNode> list){
		StringBuilder sb = new StringBuilder();
		for(XHTMLNode node : list){
			sb.append(node.toString());
		}
		return sb.toString();
	}
}
