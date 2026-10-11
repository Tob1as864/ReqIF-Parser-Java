package de.uni_stuttgart.ils.reqif4j.reqif;

public class ReqIFConst {
	
	public final static String IDENTIFIER = "IDENTIFIER";
	public final static String LONG_NAME = "LONG-NAME";
	public final static String DESC = "DESC";
	public final static String LAST_CHANGE = "LAST-CHANGE";
	public final static String THE_VALUE = "THE-VALUE";
	public final static String KEY = "KEY";
	public final static String OTHER_CONTENT = "OTHER-CONTENT";
	public final static String MIN = "MIN";
	public final static String MAX = "MAX";
	public final static String MAX_LENGTH = "MAX-LENGTH";
	public final static String ACCURACY = "ACCURACY";
	
	public final static String _TEXT = "#text";
	public final static String ENUM_VALUE = "ENUM-VALUE";
	public final static String ENUM_VALUE_REF = "ENUM-VALUE-REF";
	public final static String SPECIFIED_VALUES = "SPECIFIED-VALUES";
	public final static String EMBEDDED_VALUE = "EMBEDDED-VALUE";
	public final static String PROPERTIES = "PROPERTIES";
	public final static String MULTI_VALUED = "MULTI-VALUED";
	public final static String TYPE = "TYPE";
	public final static String VALUES = "VALUES";
	public final static String DATATYPES = "DATATYPES";
	public final static String DEFAULT_VALUE = "DEFAULT-VALUE";
	public final static String DEFINITION = "DEFINITION";
	public final static String SPEC_TYPES = "SPEC-TYPES";
	public final static String SPEC_ATTRIBUTES = "SPEC-ATTRIBUTES";
	public final static String ALTERNATIVE_ID = "ALTERNATIVE-ID";
	public final static String TOOL_EXTENSIONS = "TOOL-EXTENSIONS";
	public final static String REQ_IF_TOOL_EXTENSION = "REQ-IF-TOOL-EXTENSION";


	public final static String SPEC_RELATION_GROUPS = "SPEC-RELATION-GROUPS";
	public final static String RELATION_GROUP = "RELATION-GROUP";
	public final static String RELATION_GROUP_TYPE = "RELATION-GROUP-TYPE";
	public final static String RELATION_GROUP_TYPE_REF = "RELATION-GROUP-TYPE-REF";
	public final static String SOURCE_SPECIFICATION = "SOURCE-SPECIFICATION";
	public final static String TARGET_SPECIFICATION = "TARGET-SPECIFICATION";
	public final static String SPECIFICATION_REF = "SPECIFICATION-REF";
	public final static String SPEC_RELATION_REF = "SPEC-RELATION-REF";
	public final static String SPEC_OBJECTS = "SPEC-OBJECTS";
	public final static String SPEC_OBJECT = "SPEC-OBJECT";
	public final static String SPEC_RELATIONS = "SPEC-RELATIONS";
	public final static String SPEC_RELATION = "SPEC-RELATION";
	public final static String SOURCE = "SOURCE";
	public final static String TARGET = "TARGET";
	public final static String SPEC_HIERARCHY = "SPEC-HIERARCHY";
	public final static String CHILDREN = "CHILDREN";
	public final static String OBJECT = "OBJECT";
	public final static String SPECIFICATIONS = "SPECIFICATIONS";
	public final static String SPECIFICATION = "SPECIFICATION";
	public final static String THE_HEADER = "THE-HEADER";
	public final static String CORE_CONTENT = "CORE-CONTENT";
	public final static String REQ_IF_HEADER = "REQ-IF-HEADER";
	public final static String TITLE = "TITLE";
	public final static String REQ_IF_TOOL_ID = "REQ-IF-TOOL-ID";
	public final static String SOURCE_TOOL_ID = "SOURCE-TOOL-ID";
	public final static String REQ_IF_VERSION = "REQ-IF-VERSION";
	public final static String COMMENT = "COMMENT";
	public final static String CREATION_TIME = "CREATION-TIME";
	public final static String REPOSITORY_ID = "REPOSITORY-ID";
	public final static String IS_EDITABLE = "IS-EDITABLE";
	public final static String IS_TABLE_INTERNAL = "IS-TABLE-INTERNAL";
	public final static String EDITABLE_ATTS = "EDITABLE-ATTS";
	public final static String THE_ORIGINAL_VALUE = "THE-ORIGINAL-VALUE";
	public final static String IS_SIMPLIFIED = "IS-SIMPLIFIED";
	
	public final static String SPECIFICATION_TYPE = "SPECIFICATION-TYPE";
	public final static String SPEC_OBJECT_TYPE = "SPEC-OBJECT-TYPE";
	public final static String SPEC_OBJECT_REF = "SPEC-OBJECT-REF";
	public final static String SPEC_RELATION_TYPE = "SPEC-RELATION-TYPE";
	public final static String SPEC_RELATION_TYPE_REF = "SPEC-RELATION-TYPE-REF";
	public final static String SPEC_OBJECT_TYPE_REF = "SPEC-OBJECT-TYPE-REF";
	public final static String SPEC_TYPE_REF = "SPECIFICATION-TYPE-REF";
	
	public final static String BOOLEAN = "BOOLEAN";
	public final static String INTEGER = "INTEGER";
	public final static String STRING = "STRING";
	public final static String ENUMERATION = "ENUMERATION";
	public final static String XHTML = "XHTML";
	public final static String DATE = "DATE";
	public final static String REAL = "REAL";
	public final static String DOUBLE = "DOUBLE";
	public final static String UNDEFINED = "UNDEFINED";
	
	public final static String REQ = "REQ";
	public final static String DEF = "DEF";
	public final static String HEADLINE = "HEADLINE";
	public final static String SUB = "SUB";
	public final static String SUB_REQ = "SUB-REQ";
	public final static String TEXT = "TEXT";
	
	public final static String EOR = "EOR";
	public final static String EOD = "EOD";

	/**
	 * @return the suffix of the element names belonging to a datatype
	 *         category, e.g. "REAL" for DOUBLE as in DATATYPE-DEFINITION-REAL,
	 *         or null for a category the parser does not model
	 */
	public static String datatypeSuffix(String datatypeCategory) {
		if (datatypeCategory == null) {
			return null;
		}
		switch (datatypeCategory) {
			case DOUBLE:
			case REAL:		return REAL;
			case BOOLEAN:
			case INTEGER:
			case STRING:
			case DATE:
			case XHTML:
			case ENUMERATION:	return datatypeCategory;
			default:		return null;
		}
	}}
