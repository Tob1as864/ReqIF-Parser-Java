package de.uni_stuttgart.ils.reqif4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared ReqIF test fixtures. The main fixture uses a namespace-prefixed
 * XHTML block (as produced by real ReqIF tools such as DOORS or Polarion),
 * a multi-valued enumeration and an enumeration default value.
 */
public final class TestFixtures {

	private TestFixtures() {
	}

	public static final String REQIF_FIXTURE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<REQ-IF xmlns="http://www.omg.org/spec/ReqIF/20110401/reqif.xsd" xmlns:xhtml="http://www.w3.org/1999/xhtml">
			  <THE-HEADER>
			    <REQ-IF-HEADER IDENTIFIER="header-1">
			      <COMMENT>Created by: Tester</COMMENT>
			      <CREATION-TIME>2026-07-23T10:00:00Z</CREATION-TIME>
			      <REQ-IF-TOOL-ID>reqif4j</REQ-IF-TOOL-ID>
			      <REQ-IF-VERSION>1.0</REQ-IF-VERSION>
			      <SOURCE-TOOL-ID>test</SOURCE-TOOL-ID>
			      <TITLE>TestDoc</TITLE>
			    </REQ-IF-HEADER>
			  </THE-HEADER>
			  <CORE-CONTENT>
			    <REQ-IF-CONTENT>
			      <DATATYPES>
			        <DATATYPE-DEFINITION-STRING IDENTIFIER="dt-string" LONG-NAME="String" MAX-LENGTH="255">
			          <ALTERNATIVE-ID IDENTIFIER="alt-dt-string"/>
			        </DATATYPE-DEFINITION-STRING>
			        <DATATYPE-DEFINITION-BOOLEAN IDENTIFIER="dt-bool" LONG-NAME="Bool"/>
			        <DATATYPE-DEFINITION-INTEGER IDENTIFIER="dt-int" LONG-NAME="Int" MIN="0" MAX="9223372036854775807"/>
			        <DATATYPE-DEFINITION-INTEGER IDENTIFIER="dt-int-unbounded" LONG-NAME="IntUnbounded"/>
			        <DATATYPE-DEFINITION-STRING IDENTIFIER="dt-string-unbounded" LONG-NAME="StringUnbounded"/>
			        <DATATYPE-DEFINITION-DATE IDENTIFIER="dt-date" LONG-NAME="Date"/>
			        <DATATYPE-DEFINITION-XHTML IDENTIFIER="dt-xhtml" LONG-NAME="XhtmlType"/>
			        <DATATYPE-DEFINITION-CUSTOM IDENTIFIER="dt-custom" LONG-NAME="Custom"/>
			        <DATATYPE-DEFINITION-ENUMERATION IDENTIFIER="dt-enum" LONG-NAME="Color">
			          <SPECIFIED-VALUES>
			            <ENUM-VALUE IDENTIFIER="ev-red" LONG-NAME="Red">
			              <PROPERTIES>
			                <EMBEDDED-VALUE KEY="1" OTHER-CONTENT="#ff0000"/>
			              </PROPERTIES>
			            </ENUM-VALUE>
			            <ENUM-VALUE IDENTIFIER="ev-green" LONG-NAME="Green">
			              <PROPERTIES>
			                <EMBEDDED-VALUE KEY="2"/>
			              </PROPERTIES>
			            </ENUM-VALUE>
			            <ENUM-VALUE IDENTIFIER="ev-blue" LONG-NAME="Blue">
			              <PROPERTIES>
			                <EMBEDDED-VALUE KEY="3"/>
			              </PROPERTIES>
			            </ENUM-VALUE>
			          </SPECIFIED-VALUES>
			        </DATATYPE-DEFINITION-ENUMERATION>
			      </DATATYPES>
			      <SPEC-TYPES>
			        <SPEC-OBJECT-TYPE IDENTIFIER="st-req" LONG-NAME="Requirement Type">
			          <ALTERNATIVE-ID IDENTIFIER="alt-st-req"/>
			          <SPEC-ATTRIBUTES>
			            <ATTRIBUTE-DEFINITION-STRING IDENTIFIER="ad-title" LONG-NAME="Title">
			              <TYPE><DATATYPE-DEFINITION-STRING-REF>dt-string</DATATYPE-DEFINITION-STRING-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-STRING>
			            <ATTRIBUTE-DEFINITION-INTEGER IDENTIFIER="ad-prio" LONG-NAME="Priority">
			              <TYPE><DATATYPE-DEFINITION-INTEGER-REF>dt-int</DATATYPE-DEFINITION-INTEGER-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-INTEGER>
			            <ATTRIBUTE-DEFINITION-ENUMERATION IDENTIFIER="ad-color" LONG-NAME="Colors" MULTI-VALUED="true">
			              <TYPE><DATATYPE-DEFINITION-ENUMERATION-REF>dt-enum</DATATYPE-DEFINITION-ENUMERATION-REF></TYPE>
			              <DEFAULT-VALUE>
			                <ATTRIBUTE-VALUE-ENUMERATION>
			                  <DEFINITION><ATTRIBUTE-DEFINITION-ENUMERATION-REF>ad-color</ATTRIBUTE-DEFINITION-ENUMERATION-REF></DEFINITION>
			                  <VALUES><ENUM-VALUE-REF>ev-blue</ENUM-VALUE-REF></VALUES>
			                </ATTRIBUTE-VALUE-ENUMERATION>
			              </DEFAULT-VALUE>
			            </ATTRIBUTE-DEFINITION-ENUMERATION>
			            <ATTRIBUTE-DEFINITION-XHTML IDENTIFIER="ad-desc" LONG-NAME="Description">
			              <TYPE><DATATYPE-DEFINITION-XHTML-REF>dt-xhtml</DATATYPE-DEFINITION-XHTML-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-XHTML>
			          </SPEC-ATTRIBUTES>
			        </SPEC-OBJECT-TYPE>
			        <SPEC-RELATION-TYPE IDENTIFIER="st-rel" LONG-NAME="satisfies">
			          <SPEC-ATTRIBUTES>
			            <ATTRIBUTE-DEFINITION-STRING IDENTIFIER="ad-linkcomment" LONG-NAME="LinkComment">
			              <TYPE><DATATYPE-DEFINITION-STRING-REF>dt-string</DATATYPE-DEFINITION-STRING-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-STRING>
			          </SPEC-ATTRIBUTES>
			        </SPEC-RELATION-TYPE>
			        <RELATION-GROUP-TYPE IDENTIFIER="st-relgroup" LONG-NAME="Traceability">
			          <SPEC-ATTRIBUTES/>
			        </RELATION-GROUP-TYPE>
			        <SPECIFICATION-TYPE IDENTIFIER="st-spec" LONG-NAME="Spec Type">
			          <SPEC-ATTRIBUTES>
			            <ATTRIBUTE-DEFINITION-DATE IDENTIFIER="ad-review" LONG-NAME="ReviewDate">
			              <TYPE><DATATYPE-DEFINITION-DATE-REF>dt-date</DATATYPE-DEFINITION-DATE-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-DATE>
			          </SPEC-ATTRIBUTES>
			        </SPECIFICATION-TYPE>
			      </SPEC-TYPES>
			      <SPEC-OBJECTS>
			        <SPEC-OBJECT IDENTIFIER="so-1">
			          <ALTERNATIVE-ID IDENTIFIER="alt-so-1"/>
			          <TYPE><SPEC-OBJECT-TYPE-REF>st-req</SPEC-OBJECT-TYPE-REF></TYPE>
			          <VALUES>
			            <ATTRIBUTE-VALUE-STRING THE-VALUE="First requirement">
			              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>ad-title</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-STRING>
			            <ATTRIBUTE-VALUE-INTEGER THE-VALUE="5">
			              <DEFINITION><ATTRIBUTE-DEFINITION-INTEGER-REF>ad-prio</ATTRIBUTE-DEFINITION-INTEGER-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-INTEGER>
			            <ATTRIBUTE-VALUE-ENUMERATION>
			              <DEFINITION><ATTRIBUTE-DEFINITION-ENUMERATION-REF>ad-color</ATTRIBUTE-DEFINITION-ENUMERATION-REF></DEFINITION>
			              <VALUES>
			                <ENUM-VALUE-REF>ev-red</ENUM-VALUE-REF>
			                <ENUM-VALUE-REF>ev-green</ENUM-VALUE-REF>
			              </VALUES>
			            </ATTRIBUTE-VALUE-ENUMERATION>
			            <ATTRIBUTE-VALUE-XHTML>
			              <DEFINITION><ATTRIBUTE-DEFINITION-XHTML-REF>ad-desc</ATTRIBUTE-DEFINITION-XHTML-REF></DEFINITION>
			              <THE-VALUE>
			                <xhtml:div>Intro <xhtml:p>Text with <xhtml:span>span content</xhtml:span></xhtml:p><xhtml:object data="files/image.png" type="image/png">alternative text</xhtml:object><xhtml:table><xhtml:thead><xhtml:tr><xhtml:th>Header A</xhtml:th><xhtml:th>Header B</xhtml:th></xhtml:tr></xhtml:thead><xhtml:tbody><xhtml:tr><xhtml:td><xhtml:p>First para</xhtml:p><xhtml:p>Second para</xhtml:p></xhtml:td><xhtml:td><xhtml:object data="files/cell.png" type="image/png">cell image</xhtml:object></xhtml:td></xhtml:tr></xhtml:tbody></xhtml:table><xhtml:ul><xhtml:li>Outer item<xhtml:ul><xhtml:li>Inner item</xhtml:li></xhtml:ul></xhtml:li></xhtml:ul><xhtml:ol><xhtml:li>Numbered item</xhtml:li></xhtml:ol></xhtml:div>
			              </THE-VALUE>
			            </ATTRIBUTE-VALUE-XHTML>
			          </VALUES>
			        </SPEC-OBJECT>
			        <SPEC-OBJECT IDENTIFIER="so-2">
			          <TYPE><SPEC-OBJECT-TYPE-REF>st-req</SPEC-OBJECT-TYPE-REF></TYPE>
			          <VALUES>
			            <ATTRIBUTE-VALUE-STRING THE-VALUE="Second">
			              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>ad-title</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-STRING>
			          </VALUES>
			        </SPEC-OBJECT>
			      </SPEC-OBJECTS>
			      <SPEC-RELATIONS>
			        <SPEC-RELATION IDENTIFIER="sr-1">
			          <VALUES>
			            <ATTRIBUTE-VALUE-STRING THE-VALUE="derived during review">
			              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>ad-linkcomment</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-STRING>
			          </VALUES>
			          <TYPE><SPEC-RELATION-TYPE-REF>st-rel</SPEC-RELATION-TYPE-REF></TYPE>
			          <SOURCE><SPEC-OBJECT-REF>so-1</SPEC-OBJECT-REF></SOURCE>
			          <TARGET><SPEC-OBJECT-REF>so-2</SPEC-OBJECT-REF></TARGET>
			        </SPEC-RELATION>
			      </SPEC-RELATIONS>
			      <SPEC-RELATION-GROUPS>
			        <RELATION-GROUP IDENTIFIER="rg-1" LONG-NAME="System to Software">
			          <ALTERNATIVE-ID IDENTIFIER="alt-rg-1"/>
			          <TYPE><RELATION-GROUP-TYPE-REF>st-relgroup</RELATION-GROUP-TYPE-REF></TYPE>
			          <SOURCE-SPECIFICATION><SPECIFICATION-REF>spec-1</SPECIFICATION-REF></SOURCE-SPECIFICATION>
			          <TARGET-SPECIFICATION><SPECIFICATION-REF>spec-1</SPECIFICATION-REF></TARGET-SPECIFICATION>
			          <SPEC-RELATIONS><SPEC-RELATION-REF>sr-1</SPEC-RELATION-REF></SPEC-RELATIONS>
			        </RELATION-GROUP>
			      </SPEC-RELATION-GROUPS>
			      <SPECIFICATIONS>
			        <SPECIFICATION IDENTIFIER="spec-1" LONG-NAME="Main Spec">
			          <ALTERNATIVE-ID IDENTIFIER="alt-spec-1"/>
			          <TYPE><SPECIFICATION-TYPE-REF>st-spec</SPECIFICATION-TYPE-REF></TYPE>
			          <VALUES>
			            <ATTRIBUTE-VALUE-DATE THE-VALUE="2026-01-01">
			              <DEFINITION><ATTRIBUTE-DEFINITION-DATE-REF>ad-review</ATTRIBUTE-DEFINITION-DATE-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-DATE>
			          </VALUES>
			          <CHILDREN>
			            <SPEC-HIERARCHY IDENTIFIER="sh-1">
			              <OBJECT><SPEC-OBJECT-REF>so-1</SPEC-OBJECT-REF></OBJECT>
			              <CHILDREN>
			                <SPEC-HIERARCHY IDENTIFIER="sh-2">
			                  <OBJECT><SPEC-OBJECT-REF>so-2</SPEC-OBJECT-REF></OBJECT>
			                </SPEC-HIERARCHY>
			              </CHILDREN>
			            </SPEC-HIERARCHY>
			          </CHILDREN>
			        </SPECIFICATION>
			      </SPECIFICATIONS>
			    </REQ-IF-CONTENT>
			  </CORE-CONTENT>
			  <TOOL-EXTENSIONS>
			    <REQ-IF-TOOL-EXTENSION>
			      <myTool:settings xmlns:myTool="http://example.com/mytool" view="table"/>
			    </REQ-IF-TOOL-EXTENSION>
			  </TOOL-EXTENSIONS>
			</REQ-IF>
			""";

	/**
	 * A document that is valid against the official OMG ReqIF schema and uses
	 * every Identifiable property (LAST-CHANGE, LONG-NAME, DESC, ALTERNATIVE-ID)
	 * on the elements that carry one, including spec objects, relations and
	 * hierarchy nodes. Values are set for some attributes only, and the
	 * ALTERNATIVE-ID uses the nested form the schema prescribes.
	 */
	public static final String SCHEMA_VALID_REQIF = """
			<?xml version="1.0" encoding="UTF-8"?>
			<REQ-IF xmlns="http://www.omg.org/spec/ReqIF/20110401/reqif.xsd" xmlns:xhtml="http://www.w3.org/1999/xhtml">
			  <THE-HEADER>
			    <REQ-IF-HEADER IDENTIFIER="header-1">
			      <COMMENT>Created by: Tester</COMMENT>
			      <CREATION-TIME>2026-07-23T10:00:00Z</CREATION-TIME>
			      <REQ-IF-TOOL-ID>reqif4j</REQ-IF-TOOL-ID>
			      <REQ-IF-VERSION>1.0</REQ-IF-VERSION>
			      <SOURCE-TOOL-ID>test</SOURCE-TOOL-ID>
			      <TITLE>Schema-valid fixture</TITLE>
			    </REQ-IF-HEADER>
			  </THE-HEADER>
			  <CORE-CONTENT>
			    <REQ-IF-CONTENT>
			      <DATATYPES>
			        <DATATYPE-DEFINITION-STRING IDENTIFIER="dt-string" LAST-CHANGE="2026-07-01T08:00:00Z" LONG-NAME="String" DESC="Plain text" MAX-LENGTH="255">
			          <ALTERNATIVE-ID><ALTERNATIVE-ID IDENTIFIER="alt-dt-string"/></ALTERNATIVE-ID>
			        </DATATYPE-DEFINITION-STRING>
			        <DATATYPE-DEFINITION-INTEGER IDENTIFIER="dt-int" LAST-CHANGE="2026-07-01T08:00:00Z" LONG-NAME="Int" MIN="0" MAX="1000"/>
			        <DATATYPE-DEFINITION-BOOLEAN IDENTIFIER="dt-bool" LAST-CHANGE="2026-07-01T08:00:00Z" LONG-NAME="Bool"/>
			        <DATATYPE-DEFINITION-DATE IDENTIFIER="dt-date" LAST-CHANGE="2026-07-01T08:00:00Z" LONG-NAME="Date"/>
			        <DATATYPE-DEFINITION-XHTML IDENTIFIER="dt-xhtml" LAST-CHANGE="2026-07-01T08:00:00Z" LONG-NAME="Formatted text"/>
			        <DATATYPE-DEFINITION-ENUMERATION IDENTIFIER="dt-enum" LAST-CHANGE="2026-07-01T08:00:00Z" LONG-NAME="Status">
			          <SPECIFIED-VALUES>
			            <ENUM-VALUE IDENTIFIER="ev-draft" LAST-CHANGE="2026-07-01T08:00:00Z" LONG-NAME="Draft" DESC="Not reviewed yet">
			              <PROPERTIES>
			                <EMBEDDED-VALUE KEY="1" OTHER-CONTENT="grey"/>
			              </PROPERTIES>
			            </ENUM-VALUE>
			            <ENUM-VALUE IDENTIFIER="ev-approved" LAST-CHANGE="2026-07-01T08:00:00Z" LONG-NAME="Approved">
			              <PROPERTIES>
			                <EMBEDDED-VALUE KEY="2" OTHER-CONTENT="green"/>
			              </PROPERTIES>
			            </ENUM-VALUE>
			          </SPECIFIED-VALUES>
			        </DATATYPE-DEFINITION-ENUMERATION>
			      </DATATYPES>
			      <SPEC-TYPES>
			        <SPEC-OBJECT-TYPE IDENTIFIER="st-req" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="Requirement" DESC="A single requirement">
			          <SPEC-ATTRIBUTES>
			            <ATTRIBUTE-DEFINITION-STRING IDENTIFIER="ad-id" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="ReqIF.ForeignID">
			              <TYPE><DATATYPE-DEFINITION-STRING-REF>dt-string</DATATYPE-DEFINITION-STRING-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-STRING>
			            <ATTRIBUTE-DEFINITION-XHTML IDENTIFIER="ad-text" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="ReqIF.Text">
			              <TYPE><DATATYPE-DEFINITION-XHTML-REF>dt-xhtml</DATATYPE-DEFINITION-XHTML-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-XHTML>
			            <ATTRIBUTE-DEFINITION-INTEGER IDENTIFIER="ad-prio" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="Priority">
			              <TYPE><DATATYPE-DEFINITION-INTEGER-REF>dt-int</DATATYPE-DEFINITION-INTEGER-REF></TYPE>
			              <DEFAULT-VALUE>
			                <ATTRIBUTE-VALUE-INTEGER THE-VALUE="3">
			                  <DEFINITION><ATTRIBUTE-DEFINITION-INTEGER-REF>ad-prio</ATTRIBUTE-DEFINITION-INTEGER-REF></DEFINITION>
			                </ATTRIBUTE-VALUE-INTEGER>
			              </DEFAULT-VALUE>
			            </ATTRIBUTE-DEFINITION-INTEGER>
			            <ATTRIBUTE-DEFINITION-BOOLEAN IDENTIFIER="ad-safety" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="Safety relevant">
			              <TYPE><DATATYPE-DEFINITION-BOOLEAN-REF>dt-bool</DATATYPE-DEFINITION-BOOLEAN-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-BOOLEAN>
			            <ATTRIBUTE-DEFINITION-DATE IDENTIFIER="ad-due" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="Due">
			              <TYPE><DATATYPE-DEFINITION-DATE-REF>dt-date</DATATYPE-DEFINITION-DATE-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-DATE>
			            <ATTRIBUTE-DEFINITION-ENUMERATION IDENTIFIER="ad-status" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="Status" MULTI-VALUED="true">
			              <TYPE><DATATYPE-DEFINITION-ENUMERATION-REF>dt-enum</DATATYPE-DEFINITION-ENUMERATION-REF></TYPE>
			            </ATTRIBUTE-DEFINITION-ENUMERATION>
			          </SPEC-ATTRIBUTES>
			        </SPEC-OBJECT-TYPE>
			        <SPEC-RELATION-TYPE IDENTIFIER="st-rel" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="refines"/>
			        <SPECIFICATION-TYPE IDENTIFIER="st-spec" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="Specification"/>
			        <RELATION-GROUP-TYPE IDENTIFIER="st-group" LAST-CHANGE="2026-07-02T08:00:00Z" LONG-NAME="Trace"/>
			      </SPEC-TYPES>
			      <SPEC-OBJECTS>
			        <SPEC-OBJECT IDENTIFIER="so-1" LAST-CHANGE="2026-07-03T09:15:00Z" LONG-NAME="Boot" DESC="First requirement">
			          <ALTERNATIVE-ID><ALTERNATIVE-ID IDENTIFIER="REQ-1"/></ALTERNATIVE-ID>
			          <VALUES>
			            <ATTRIBUTE-VALUE-STRING THE-VALUE="REQ-1">
			              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>ad-id</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-STRING>
			            <ATTRIBUTE-VALUE-XHTML>
			              <DEFINITION><ATTRIBUTE-DEFINITION-XHTML-REF>ad-text</ATTRIBUTE-DEFINITION-XHTML-REF></DEFINITION>
			              <THE-VALUE><xhtml:div><xhtml:p>The system shall <xhtml:b>boot</xhtml:b> within 5 s.</xhtml:p></xhtml:div></THE-VALUE>
			            </ATTRIBUTE-VALUE-XHTML>
			            <ATTRIBUTE-VALUE-INTEGER THE-VALUE="1">
			              <DEFINITION><ATTRIBUTE-DEFINITION-INTEGER-REF>ad-prio</ATTRIBUTE-DEFINITION-INTEGER-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-INTEGER>
			            <ATTRIBUTE-VALUE-BOOLEAN THE-VALUE="true">
			              <DEFINITION><ATTRIBUTE-DEFINITION-BOOLEAN-REF>ad-safety</ATTRIBUTE-DEFINITION-BOOLEAN-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-BOOLEAN>
			            <ATTRIBUTE-VALUE-DATE THE-VALUE="2026-12-31T00:00:00Z">
			              <DEFINITION><ATTRIBUTE-DEFINITION-DATE-REF>ad-due</ATTRIBUTE-DEFINITION-DATE-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-DATE>
			            <ATTRIBUTE-VALUE-ENUMERATION>
			              <DEFINITION><ATTRIBUTE-DEFINITION-ENUMERATION-REF>ad-status</ATTRIBUTE-DEFINITION-ENUMERATION-REF></DEFINITION>
			              <VALUES><ENUM-VALUE-REF>ev-approved</ENUM-VALUE-REF></VALUES>
			            </ATTRIBUTE-VALUE-ENUMERATION>
			          </VALUES>
			          <TYPE><SPEC-OBJECT-TYPE-REF>st-req</SPEC-OBJECT-TYPE-REF></TYPE>
			        </SPEC-OBJECT>
			        <SPEC-OBJECT IDENTIFIER="so-2" LAST-CHANGE="2026-07-03T09:20:00Z">
			          <VALUES>
			            <ATTRIBUTE-VALUE-STRING THE-VALUE="REQ-2">
			              <DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>ad-id</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-STRING>
			            <ATTRIBUTE-VALUE-BOOLEAN THE-VALUE="false">
			              <DEFINITION><ATTRIBUTE-DEFINITION-BOOLEAN-REF>ad-safety</ATTRIBUTE-DEFINITION-BOOLEAN-REF></DEFINITION>
			            </ATTRIBUTE-VALUE-BOOLEAN>
			          </VALUES>
			          <TYPE><SPEC-OBJECT-TYPE-REF>st-req</SPEC-OBJECT-TYPE-REF></TYPE>
			        </SPEC-OBJECT>
			      </SPEC-OBJECTS>
			      <SPEC-RELATIONS>
			        <SPEC-RELATION IDENTIFIER="sr-1" LAST-CHANGE="2026-07-04T10:00:00Z" LONG-NAME="so-2 refines so-1" DESC="Trace link">
			          <TYPE><SPEC-RELATION-TYPE-REF>st-rel</SPEC-RELATION-TYPE-REF></TYPE>
			          <SOURCE><SPEC-OBJECT-REF>so-2</SPEC-OBJECT-REF></SOURCE>
			          <TARGET><SPEC-OBJECT-REF>so-1</SPEC-OBJECT-REF></TARGET>
			        </SPEC-RELATION>
			      </SPEC-RELATIONS>
			      <SPECIFICATIONS>
			        <SPECIFICATION IDENTIFIER="spec-1" LAST-CHANGE="2026-07-05T11:00:00Z" LONG-NAME="System Spec" DESC="Top level">
			          <TYPE><SPECIFICATION-TYPE-REF>st-spec</SPECIFICATION-TYPE-REF></TYPE>
			          <CHILDREN>
			            <SPEC-HIERARCHY IDENTIFIER="sh-1" LAST-CHANGE="2026-07-05T11:00:00Z" LONG-NAME="1 Boot" DESC="Chapter">
			              <OBJECT><SPEC-OBJECT-REF>so-1</SPEC-OBJECT-REF></OBJECT>
			              <CHILDREN>
			                <SPEC-HIERARCHY IDENTIFIER="sh-2" LAST-CHANGE="2026-07-05T11:00:00Z">
			                  <OBJECT><SPEC-OBJECT-REF>so-2</SPEC-OBJECT-REF></OBJECT>
			                </SPEC-HIERARCHY>
			              </CHILDREN>
			            </SPEC-HIERARCHY>
			          </CHILDREN>
			        </SPECIFICATION>
			      </SPECIFICATIONS>
			      <SPEC-RELATION-GROUPS>
			        <RELATION-GROUP IDENTIFIER="rg-1" LAST-CHANGE="2026-07-06T12:00:00Z" LONG-NAME="Trace group" DESC="All refinements">
			          <TYPE><RELATION-GROUP-TYPE-REF>st-group</RELATION-GROUP-TYPE-REF></TYPE>
			          <SOURCE-SPECIFICATION><SPECIFICATION-REF>spec-1</SPECIFICATION-REF></SOURCE-SPECIFICATION>
			          <TARGET-SPECIFICATION><SPECIFICATION-REF>spec-1</SPECIFICATION-REF></TARGET-SPECIFICATION>
			          <SPEC-RELATIONS><SPEC-RELATION-REF>sr-1</SPEC-RELATION-REF></SPEC-RELATIONS>
			        </RELATION-GROUP>
			      </SPEC-RELATION-GROUPS>
			    </REQ-IF-CONTENT>
			  </CORE-CONTENT>
			</REQ-IF>
			""";

	/**
	 * Writes the given content to {@code dir/name} and returns the path.
	 */
	public static Path write(Path dir, String name, String content) throws IOException {
		Path file = dir.resolve(name);
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	/**
	 * Writes the default fixture to {@code dir/test.reqif} and returns the path.
	 */
	public static Path writeDefaultFixture(Path dir) throws IOException {
		return write(dir, "test.reqif", REQIF_FIXTURE);
	}

	/**
	 * @return the fixture with all inter-tag whitespace removed (minified XML,
	 *         i.e. without the whitespace text nodes the parser used to rely on).
	 */
	public static String minified(String xml) {
		return xml.replaceAll(">\\s+<", "><").trim();
	}
}
