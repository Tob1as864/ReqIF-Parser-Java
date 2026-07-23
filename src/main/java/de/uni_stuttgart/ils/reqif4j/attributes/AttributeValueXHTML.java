package de.uni_stuttgart.ils.reqif4j.attributes;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFConst;
import de.uni_stuttgart.ils.reqif4j.util.XmlUtils;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementDiv;

public class AttributeValueXHTML extends AttributeValue {

	XHTMLElementDiv divValue;

	@Override
	public Object getValue() {
		if(divValue == null){
			return "";
		}
		return divValue.toString();
	}

	public AttributeValueXHTML(Node xhtmlContent, AttributeDefinition type) {
		super(type);

		// The div may carry a namespace prefix (xhtml:div, reqif-xhtml:div, ...),
		// so it has to be located by local name.
		Node div = XmlUtils.firstDescendantByLocalName(xhtmlContent, XHTML.DIV);
		this.divValue = div == null ? null : new XHTMLElementDiv(div);
		this.value = deconstructXHTML(xhtmlContent);
	}
	
	public AttributeValueXHTML(String value, AttributeDefinition type) {
		super(value, type);
		
		
	}
	
	public XHTMLElementDiv getDivValue() {
		return this.divValue;
	}

	/**
	 * @return the deconstructed element list (P/TBL/L/H/OBJ tokens with their
	 *         content), never null
	 */
	public AttributeValueXHTMLElementList getElementList() {
		if(this.value instanceof AttributeValueXHTMLElementList) {
			return (AttributeValueXHTMLElementList) this.value;
		}
		return new AttributeValueXHTMLElementList();
	}
	
	
	
	
	private AttributeValueXHTMLElementList deconstructXHTML(Node xhtmlContent) {

		AttributeValueXHTMLElementList xhtmlElementList = new AttributeValueXHTMLElementList();

		Node div = XmlUtils.firstDescendantByLocalName(xhtmlContent, XHTML.DIV);
		if(div == null) {
			return xhtmlElementList;
		}

		for(int e=0; e < div.getChildNodes().getLength(); e++) {
			Node xhtmlElement = div.getChildNodes().item(e);
			// Map the element's local name to the content token expected by
			// decostructXHTMLElement. The former implementation passed the raw
			// (prefixed) node name, which never matched, so all content lists
			// stayed empty.
			String token = elementToken(XmlUtils.localName(xhtmlElement));
			if(token != null) {
				xhtmlElementList.add(token, decostructXHTMLElement(token, xhtmlElement));
			}
		}
		return xhtmlElementList;
	}

	private static String elementToken(String localName) {
		switch(localName.replaceAll("[0-9]", "")) {
			case "p":		return "P";
			case "table":	return "TBL";
			case "ul":		return "L";
			case "h":		return "H";
			case "object":	return "OBJ";
			default:		return null;
		}
	}
		
	private List<String> decostructXHTMLElement(String elementType, Node xhtmlElement) {
		
		List<String> content = new ArrayList<String>();
		
		if(elementType.equals("P")) {
			for(int childNode=0; childNode < xhtmlElement.getChildNodes().getLength(); childNode++) {
				Node child = xhtmlElement.getChildNodes().item(childNode);
				if(child.getNodeName().contains("span")) {
					content.add("TXT");
					content.add(child.getTextContent().trim());
				
				}else if(child.getNodeName().equals(ReqIFConst._TEXT) && !child.getTextContent().isEmpty()) {
					content.add("TXT");
					content.add(child.getTextContent().trim());
				
				}else if(child.getNodeName().contains("var")) {		//		TODO
					//System.out.println("VAR:\n" + child.getTextContent().trim());
					content.add("VAR");
					if(child.getTextContent().trim().equals("")) {
						content.add("VARIABLE_NAME_MISSING");
					
					}else{
						content.add(child.getTextContent().trim());
						if(child.getAttributes().getNamedItem("GUID") != null) {
							content.add(child.getAttributes().getNamedItem("GUID").getTextContent());
						}
					}
					
				}else if(child.getNodeName().contains("br")) {
					//System.out.println();
					content.add("BR");
					
				}
			}
		}else if(elementType.equals("TBL")) {
			
			content.addAll(decontructTable(xhtmlElement));
			
		}else if(elementType.equals("L")) {		//		TODO: nested lists
			
			content.addAll(list(xhtmlElement));
			
		}else if(elementType.equals("H")) {
			//System.out.println("\n"+xhtmlElement.getTextContent().trim());
			//content.add(xhtmlElement.getTextContent().trim());
			//																							TODO
			for(int childNode=0; childNode < xhtmlElement.getChildNodes().getLength(); childNode++) {
				Node child = xhtmlElement.getChildNodes().item(childNode);
				
				if(child.getNodeName().contains("span")) {
					content.add("TXT");
					content.add(child.getTextContent().trim());

				}else if(child.getNodeName().equals(ReqIFConst._TEXT) && !child.getTextContent().isEmpty()) {
					content.add("TXT");
					content.add(child.getTextContent().trim());
					
				}else if(child.getNodeName().contains("var")) {
					content.add("VAR");
					if(child.getTextContent().trim().equals("")) {
						content.add("VARIABLE_NAME_MISSING");
					}else{
						content.add(child.getTextContent().trim());
						if(child.getAttributes().getNamedItem("GUID") != null) {
							content.add(child.getAttributes().getNamedItem("GUID").getTextContent());
						}
					}
				}
			}
			
		}else if(elementType.equals("OBJ")) {
			// Keep the original URI (forward slashes) so it matches the picture
			// entries of the .reqifz archive on every platform.
			String path = XmlUtils.attribute(xhtmlElement, "data");
			if(path != null) {
				content.add(path.trim());
			}
		}
		
		/*//
		for(int i=0; i<content.size(); i++) {
			System.out.println(content.get(i));
		}
		//*/
		
		return content;
	}
	
	private List<String> list(Node listNode) {
		
		List<String> list = new ArrayList<String>();
		
		///
		for(int childNode=0; childNode < listNode.getChildNodes().getLength(); childNode++) {
			Node child = listNode.getChildNodes().item(childNode);
			
			if(child.getNodeName().endsWith(":li")) {
				list.add("LE");
				
				for(int listElement=0; listElement < child.getChildNodes().getLength(); listElement++) {
					Node listChild = child.getChildNodes().item(listElement);
					String leName = listChild.getNodeName();
					
					if(leName.endsWith("span")) {
						list.add("TXT");
						list.add(listChild.getTextContent().trim());

					}else if(leName.equals(ReqIFConst._TEXT) && !listChild.getTextContent().trim().isEmpty()) {
						list.add("TXT");
						list.add(listChild.getTextContent().trim());
						
					}else if(leName.endsWith("var")) {
						list.add("VAR");
						if(listChild.getTextContent().trim().equals("")) {
							list.add("VARIABLE_NAME_MISSING");
						}else{
							list.add(listChild.getTextContent().trim());
						}
						
					///
					}else if(leName.endsWith("ul")) {
						list.add("L");
						list.addAll(list(listChild));
						//list.add("_L");
					//*/
						
					}else if(leName.endsWith("table")) {
						list.add("TBL");
						list.addAll(decontructTable(listChild));
						
					}
				}
			}
		}
		//*/
		
		list.add("/L");
		
		return list;
	}
			
	private List<String> decontructTable(Node tableNode) {
		
		List<String> tblContent = new ArrayList<String>();
		
		for(int tbl=0; tbl < tableNode.getChildNodes().getLength(); tbl++) {
			Node tbody = tableNode.getChildNodes().item(tbl);
			
			if(!tbody.getNodeName().equals(ReqIFConst._TEXT)) {
				
				for(int tr=0; tr < tbody.getChildNodes().getLength(); tr++) {
					Node trow = tbody.getChildNodes().item(tr);
					
					if(!trow.getNodeName().equals(ReqIFConst._TEXT)) {
						//System.out.println("\nTR");
						tblContent.add("TR");
						
						for(int tc=0; tc < trow.getChildNodes().getLength(); tc++) {
							Node tcoloumn = trow.getChildNodes().item(tc);
							
							if(!tcoloumn.getNodeName().equals(ReqIFConst._TEXT)) {
								//System.out.println("TC");
								tblContent.add("TC");
								if(tcoloumn.getChildNodes().getLength() <= 1) {
									//System.out.println(tcoloumn.getTextContent());
									tblContent.add(tcoloumn.getTextContent().trim());
								}else{
									tblContent.add(tcoloumn.getChildNodes().item(1).getTextContent().trim());
								}
							}
						}
					}
				}
			}
		}
		
		return tblContent;
	}

}
