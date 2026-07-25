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
			        <DATATYPE-DEFINITION-STRING IDENTIFIER="dt-string" LONG-NAME="String" MAX-LENGTH="255"/>
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
			                <xhtml:div>Intro <xhtml:p>Text with <xhtml:span>span content</xhtml:span></xhtml:p><xhtml:object data="files/image.png" type="image/png">alternative text</xhtml:object></xhtml:div>
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
			      <SPECIFICATIONS>
			        <SPECIFICATION IDENTIFIER="spec-1" LONG-NAME="Main Spec">
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
