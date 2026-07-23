package de.uni_stuttgart.ils.reqif4j.xhtml;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.ils.reqif4j.attributes.XHTML;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;

public class XHTMLElement extends XHTMLNode {
	
	protected List<XHTMLNode> children = null;
	
	public boolean hasChildren() {
		return children != null && !children.isEmpty();
	}
	
	public List<XHTMLNode> getChildren() {
		return this.children;
	}
	
	

	public XHTMLElement(Node xhtmlElement) {
		super(xhtmlElement);
		
		this.children = new ArrayList<XHTMLNode>();
		
		addChildren(xhtmlElement);
	}

	public XHTMLElement(Node xhtmlElement, XHTMLNode parent) {
		super(xhtmlElement, parent);
		
		this.children = new ArrayList<XHTMLNode>();
		
		addChildren(xhtmlElement);
	}
	
	
	
	private void addChildren(Node xhtmlElement) {
		
		NodeList children = xhtmlElement.getChildNodes();
		
		for(int child = 0; child < children.getLength(); child++) {
				
			Node childNode = children.item(child);
			// Match by local name so namespace-prefixed XHTML (xhtml:p,
			// reqif-xhtml:object, ...) is recognized; strip digits to map h1-h6 to h.
			String nodeName = XmlUtils.localName(childNode);

			switch (nodeName.replaceAll("[0-9]", "")) {
			
				case XHTML.BR:		this.children.add(new XHTMLElementBr(childNode, this));
									break;

				case XHTML.H:		this.children.add(new XHTMLElementH(childNode, this));
									break;
				
				case XHTML.LI:		this.children.add(new XHTMLElementLi(childNode, this));
									break;
									
				case XHTML.OBJECT:	this.children.add(new XHTMLElementObject(childNode, this));
									break;
				
				case XHTML.P:		this.children.add(new XHTMLElementP(childNode, this));
									break;
				
				case XHTML.SPAN:	this.children.add(new XHTMLElementSpan(childNode, this));
									break;
									
				case XHTML._TEXT:	if(!childNode.getTextContent().trim().isEmpty()) {
										this.children.add(new XHTMLElementText(childNode, this));
									}
									break;
				
				case XHTML.TBODY:	this.children.add(new XHTMLElementTBody(childNode, this));
									break;
				
				case XHTML.TBL:		this.children.add(new XHTMLElementTbl(childNode, this));
									break;
				
				case XHTML.TD:		this.children.add(new XHTMLElementTd(childNode, this));
									break;
				
				case XHTML.TH:		this.children.add(new XHTMLElementTh(childNode, this));
									break;
				
				case XHTML.THEAD:	this.children.add(new XHTMLElementTHead(childNode, this));
									break;
				
				case XHTML.TR:		this.children.add(new XHTMLElementTr(childNode, this));
									break;
				
				case XHTML.UL:		this.children.add(new XHTMLElementUl(childNode, this));
									break;
				
				case XHTML.VAR:		this.children.add(new XHTMLElementVar(childNode, this));
									break;
				
				default:			this.children.add(new XHTMLNode(childNode, this));
									break;
			}	
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('<').append(tagName).append('>');
		sb.append((!children.isEmpty() ? this.listToString(children) : ""));
		sb.append("</").append(tagName).append('>');
		return sb.toString();
	}

}
