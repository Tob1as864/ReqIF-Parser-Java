# Requirements Interchange Format (ReqIF)
ReqIF (Requirements Interchange Format) is an OMG (Object Management Group) standard for the exchange of requirements between different tools. It is designed to facilitate the transfer of requirements information, ensuring that data can be shared and understood across various platforms and organizations. ReqIF provides a standardized XML format for requirements, making it easier to manage and collaborate on requirements throughout the development lifecycle.

# Project Purpose - Provide a free Java ReqIF Parser
The requirements-interchange-format (ReqIF) library is a parser (and in the future maybe generator) for ReqIF XML documents.
The classes ReqIF.java and ReqIFz.java can access and decompress ReqIF content for further automation in a Java application.

This fork is based on https://github.com/bfriebel/requirements-interchange-format and fixed old library dependencies.

# Supported Formats
ReqIF file extensions .reqif and .reqifz (compressed).

# Build & Test
The project builds with Maven (Java 17+):

```
mvn verify
```

Every push and pull request runs the test suite via GitHub Actions
(`.github/workflows/ci.yml`). The tests in `src/test/java` cover the
fixed parser defects documented in `FEHLERANALYSE.md` (namespace-prefixed
XHTML, multiselect enumerations, image/object conversion, picture lookup
in .reqifz archives, and crash robustness).

# Type classification

ReqIF has no semantic "this is a requirement" flag. The categories
`REQ`, `SUB-REQ`, `HEADLINE` and `TEXT` are **this parser's own** content
categories, not official ReqIF types — the standard only defines structural
types (`SPEC-OBJECT-TYPE`, `SPECIFICATION-TYPE`, `SPEC-RELATION-TYPE`) with
free-form `LONG-NAME`s and free-form attributes. What an object *means* is a
convention of the exporting tool or exchange profile.

Classification is therefore a pluggable strategy (`TypeClassifier`). The
strategy receives the fully parsed `SpecObject`, so it may decide based on
the spec type name, the attribute values, or both. Two implementations ship
with the library.

## Default: `LongNameTypeClassifier`

The default applies a substring heuristic on the spec type name
("req", "sub", "headline"). It fits profiles whose type names follow that
convention, but misses e.g. German type names or exports that only encode
the content kind in attributes. It is used automatically when no classifier
is passed.

## Recommended for DOORS/Polarion exports: `ReqIFImplementationGuideClassifier`

The ProSTEP iViP / ReqIF Implementor Forum "ReqIF Implementation Guide"
standardizes **attribute names** (while type names stay free-form). Tools
such as IBM DOORS, PTC and Polarion emit:

- `ReqIF.ChapterName` — set on heading objects
- `ReqIF.Text` — the requirement/description body

This classifier decides by those attributes, independent of the type name,
which is more robust and tool-independent:

```java
import de.uni_stuttgart.ils.reqif4j.specification.ReqIFImplementationGuideClassifier;

ReqIF reqif = new ReqIF("doors-export.reqif", new ReqIFImplementationGuideClassifier());

// custom attribute names, if your profile differs:
ReqIF reqif2 = new ReqIF("export.reqif",
        new ReqIFImplementationGuideClassifier("Heading", "Body"));
```

Rule: non-empty `ReqIF.ChapterName` → `HEADLINE`; else non-empty
`ReqIF.Text` → `REQ`; else `TEXT`. Note that the guide does not distinguish
normative requirements from informational text (both use `ReqIF.Text`), so
text-bearing objects are reported as `REQ`; use a custom classifier if your
profile marks requirements with an extra attribute.

## Custom classifier

Any other convention can be implemented directly:

```java
TypeClassifier classifier = new TypeClassifier() {
    @Override
    public String classify(SpecObject specObject) {
        String name = specObject.getSpecTypeName().toLowerCase();
        if (name.contains("anforderung")) return ReqIFConst.REQ;
        if (name.contains("überschrift")) return ReqIFConst.HEADLINE;
        return ReqIFConst.TEXT;
    }

    @Override
    public boolean isRequirement(SpecObject specObject) {
        return ReqIFConst.REQ.equals(specObject.getType());
    }

    @Override
    public boolean isSubRequirement(SpecObject specObject) {
        return false;
    }
};

ReqIF reqif = new ReqIF("spec.reqif", classifier);          // .reqif
ReqIFz reqifz = new ReqIFz("archive.reqifz", classifier);   // .reqifz
```

# Spec relations
`SpecRelation` extends `SpecObject`, but a relation has no *content*
category, so the inherited `getType()` returns `ReqIFConst.UNDEFINED`.
The relation's own type and its endpoints are exposed separately:

```java
SpecRelation rel = reqif.getReqIFCoreContent().getSpecRelation("sr-1");

rel.getSourceObjID();        // "so-1"
rel.getTargetObjID();        // "so-2"
rel.getRelationTypeRef();    // "st-rel"     (SPEC-RELATION-TYPE-REF)
rel.getRelationTypeName();   // "satisfies"  (resolved LONG-NAME)
rel.getSpecType();           // "SPEC-RELATION-TYPE"
rel.getAttribute("LinkComment");  // relation attribute values are parsed
```

`isReq()`, `isSubReq()`, `isHeadline()` and `isText()` all return `false`
for relations.

# XHTML content
XHTML attribute values are available in two representations that are always
in sync — the token list is derived from the node tree:

```java
AttributeValueXHTML desc = (AttributeValueXHTML) specObject.getAttributes().get("Description");

desc.getDivValue();     // typed node tree (XHTMLElementTbl, XHTMLElementTh, ...)
desc.getElementList();  // flat token list
desc.getValue();        // rendered XHTML string
```

Token grammar of `getElementList()`:

| element type | content tokens |
|---|---|
| `P` / `H` | `TXT` text \| `VAR` name [guid] \| `BR` \| `OBJ` path |
| `TBL` | `TR` ( (`TH`\|`TC`) text (`OBJ` path)* )* |
| `L` | `LE` ( inline \| `L` … `/L` \| `TBL` … )* |
| `OBJ` | path |

Header cells are reported as `TH`, data cells as `TC`; the cell text holds
the complete cell content and images inside a cell follow as `OBJ` pairs.
Nested lists use balanced `L` / `/L` markers. Ordered lists (`ol`) are
treated like unordered ones.

## Rendering (`getValue()`)

`getValue()` returns the XHTML as a string. Text and attribute values are
escaped, so the result is well-formed and can be parsed again or embedded
safely. All XML attributes are preserved, void elements are self-closing,
and elements without a dedicated node class (`a`, `em`, `strong`, …) keep
their content:

```html
<div><p style="color:red">a &lt; b &amp; c</p>
<p>Siehe <a href="http://x.y">diesen Link</a> und <em>Betonung</em>.</p>
<br/><table><tr><td colspan="2">merged</td></tr></table></div>
```

Namespace declarations are omitted because tag names are rendered without
their prefix.
