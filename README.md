# Requirements Interchange Format (ReqIF)
ReqIF (Requirements Interchange Format) is an OMG (Object Management Group) standard for the exchange of requirements between different tools. It is designed to facilitate the transfer of requirements information, ensuring that data can be shared and understood across various platforms and organizations. ReqIF provides a standardized XML format for requirements, making it easier to manage and collaborate on requirements throughout the development lifecycle.

# Project Purpose - Provide a free Java ReqIF Parser
The requirements-interchange-format (ReqIF) library is a parser (and in the future maybe generator) for ReqIF XML documents.
The classes ReqIF.java and ReqIFz.java can access and decompress ReqIF content for further automation in a Java application.

This fork is based on https://github.com/bfriebel/requirements-interchange-format and fixed old library dependencies.

# Supported Formats
ReqIF file extensions .reqif and .reqifz (compressed).

Elements are matched by their local name, so it does not matter whether a
document puts the ReqIF elements into the default namespace
(`<REQ-IF xmlns="...">`) or into a prefixed one (`<rif:REQ-IF xmlns:rif="...">`).
The same holds for the embedded XHTML (`xhtml:div`, `reqif-xhtml:div`, ...).

# Using reqif4j as a dependency

Released artifacts are published into the separate, public repository
[Tob1as864/maven-repo](https://github.com/Tob1as864/maven-repo), which holds a
plain Maven repository layout and is served over `raw.githubusercontent.com`.
No GitHub token and no `settings.xml` entry is needed.

Maven:

```xml
<repositories>
  <repository>
    <id>tob1as864</id>
    <url>https://raw.githubusercontent.com/Tob1as864/maven-repo/main</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>de.uni_stuttgart.ils</groupId>
    <artifactId>reqif4j</artifactId>
    <version>1.1.0</version>
  </dependency>
</dependencies>
```

The repository `<id>` is just a local name for the declaration — pick any name
that is unique inside your own pom; it is unrelated to the library's
`artifactId`. Its only technical purpose is linking a repository to matching
`<server>` credentials or mirrors in `settings.xml`, neither of which this
repository needs.

To use a development build, additionally allow snapshots for the repository.
Maven enables them by default, so this is only needed if you switched them off:

```xml
<snapshots><enabled>true</enabled></snapshots>
```

Gradle:

```kotlin
repositories {
    maven { url = uri("https://raw.githubusercontent.com/Tob1as864/maven-repo/main") }
}

dependencies {
    implementation("de.uni_stuttgart.ils:reqif4j:1.1.0")
}
```

Sources and javadoc jars are published alongside every version, so IDEs can
show the API documentation. Note that raw.githubusercontent.com is CDN-cached
for a few minutes, so a freshly published version may not resolve immediately.

## Publishing a new version

`.github/workflows/release.yml` builds the artifacts and commits them into the
`maven-repo` repository. It runs when a `v*` tag is pushed (tag `v1.2.0`
publishes version `1.2.0`), or on demand via *Actions -> Publish to Maven repo
-> Run workflow*, where an empty version input publishes the current SNAPSHOT.

Release versions are immutable: publishing a version that already exists there
fails instead of overwriting it. The pom version is only changed for the build,
so no version bump is committed to this repository.

The same publish step can be run locally, without pushing:

```
PUSH=false .github/scripts/publish-maven-repo.sh 1.2.0
```

### One-time setup of the publishing credentials

The workflow authenticates against `maven-repo` with an SSH deploy key, which
grants write access to that one repository only:

1. Create the key pair locally, without a passphrase:
   `ssh-keygen -t ed25519 -C "reqif4j release workflow" -f maven-repo-key -N ""`
2. In **Tob1as864/maven-repo** -> *Settings -> Deploy keys -> Add deploy key*:
   paste the contents of `maven-repo-key.pub` and tick **Allow write access**.
3. In **this** repository -> *Settings -> Secrets and variables -> Actions ->
   New repository secret* (a repository secret, not an environment secret):
   name `MAVEN_REPO_DEPLOY_KEY`, value the **complete** contents of the private
   key file `maven-repo-key`, from `-----BEGIN OPENSSH PRIVATE KEY-----` through
   `-----END OPENSSH PRIVATE KEY-----`.
4. Delete both local key files.

The secret must hold an OpenSSH private key in its original multi-line form;
the workflow rejects anything else before it starts publishing. PuTTY's own
`.ppk` format does not work - if you generate the key with PuTTYgen, use
*Conversions -> Export OpenSSH key* and store that exported file's contents.
The key must not have a passphrase, because the workflow runs unattended.

The same deploy key setup is repeated per library that publishes into
`maven-repo`; each library repository gets its own key.

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

# Writing ReqIF (experimental)
Beyond reading, the library can serialize the object model back to ReqIF
XML. This is the foundation for generating documents; it works on the
model, so a document read from a file and one modified programmatically
are written the same way.

```java
ReqIF reqif = new ReqIF("in.reqif");

new ReqIFWriter().write(reqif.getReqIFDocument(), Path.of("out.reqif"));
String xml = new ReqIFWriter().toXml(reqif.getReqIFDocument());
```

Round-tripping (parse -> write) is lossless: every element and attribute
of the ReqIF schema is read into the model and written back, including
`xml:lang`, the header's `REPOSITORY-ID`, `IS-EDITABLE` of attribute
definitions and hierarchy nodes, `IS-TABLE-INTERNAL` and `EDITABLE-ATTS`,
and XHTML values with `THE-ORIGINAL-VALUE` / `IS-SIMPLIFIED` (e.g. an
embedded OLE object of which `THE-VALUE` only shows the preview).
`LosslessRoundTripTest` checks this with a document that uses all of
them; the comparison ignores only what the schema leaves open (namespace
prefixes, attribute order, the order of xsd:all children, formatting).
Typed values are written in canonical notation, so the lexical form of
a value may change while its meaning does not: `1` becomes `true` for a
boolean, `1e3` becomes `1000.0` for a real, `+5` becomes `5`.
Attribute values are written in document order. Integer values have no
size limit (`AttributeValueInteger.getBigInteger()`).

## Attribute values

An element's values are available in two views:

```java
SpecObject specObject = reqif.getReqIFCoreContent().getSpecObject("so-1");

// what the element itself carries - this is what is written back -
// keyed by attribute definition IDENTIFIER, in document order
Map<String, AttributeValue> own = specObject.getValues();
AttributeValue text = specObject.getValue("ad-text");

// effective values by LONG-NAME, including the defaults of attribute
// definitions the element has no value for (read-only)
Object priority = specObject.getAttribute("Priority");

// changing values, by attribute definition IDENTIFIER; the value class
// follows the datatype, unknown definitions and enum values are refused
specObject.set("ad-prio", 7)
          .setXhtml("ad-text", "<p>The system shall boot.</p>")
          .setEnum("ad-status", "ev-draft", "ev-approved");
specObject.removeValue("ad-text");   // the default applies again
```

Defaults are never turned into values: an attribute the document leaves
unset stays unset when the document is written, and the default keeps
applying through its attribute definition. Prefer `getValues()` when
names may repeat - LONG-NAMEs need not be unique within a spec type.

## Identity and metadata of elements

Every identifiable element - datatypes, enum values, spec types, attribute
definitions, spec objects, spec relations, specifications, spec hierarchy
nodes and relation groups - extends `Identifiable` and exposes the
properties the ReqIF schema defines for it:

```java
SpecObject specObject = reqif.getReqIFCoreContent().getSpecObject("so-1");

specObject.getID();             // IDENTIFIER
specObject.getName();           // LONG-NAME, null if the document declares none
specObject.getDesc();           // DESC
specObject.getLastChange();     // LAST-CHANGE, as written (xsd:dateTime)
specObject.getAlternativeID();  // ALTERNATIVE-ID

specObject.setDesc("reviewed").setLastChange("2026-10-11T12:00:00Z");
```

They are written back unchanged; properties a document does not declare
stay absent instead of being written empty.

Elements created from plain values, and elements changed through the API
(values, properties, structure), are *modified* (`isModified()`): the
writer gives them the time of writing as `LAST-CHANGE`, so a tool that
imports the document sees what changed, and once the output succeeded
takes that time into the model - writing again reports no new change.
`setLastChange(...)` after the last change keeps the given value;
`ReqIFCoreContent.clearModified()` keeps every element's `LAST-CHANGE`.
`buildDocument` and the validator leave the model alone. `ALTERNATIVE-ID` is written
in the nested form the schema prescribes
(`<ALTERNATIVE-ID><ALTERNATIVE-ID IDENTIFIER="..."/></ALTERNATIVE-ID>`);
the flat form older versions of this library wrote is still read.

## Schema-valid output

The writer emits every value the official ReqIF schema requires, also when
the model does not carry it - typically for a document built with
`ReqIFBuilder`. The model itself is not changed:

| missing in the model | written as |
|---|---|
| `LAST-CHANGE` of a modified element | the time of writing |
| `LAST-CHANGE` of an element | the header's `CREATION-TIME`, or the time of writing |
| header `CREATION-TIME` | the time of writing |
| header `REQ-IF-TOOL-ID`, `SOURCE-TOOL-ID` | `reqif4j` |
| header `REQ-IF-VERSION` | `1.0` (the only value the schema allows) |
| header `TITLE` | empty |
| `ACCURACY` / `MIN` / `MAX` of a REAL datatype | 15 / full double range |

`MULTI-VALUED` and `OTHER-CONTENT` are always written, also when `false`
or empty, and infinite REAL values use the xsd notation `INF` / `-INF`.
The time of writing comes from a `java.time.Clock`; pass a fixed one for
reproducible output:

```java
new ReqIFWriter().setClock(Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC))
        .write(document, Path.of("out.reqif"));
```

What no default can fix - a missing header, missing or invalid
identifiers, malformed dates - is reported by `ReqIFValidator` (see
below). A document the validator accepts without errors is written
schema-valid, with one exception: which XHTML elements a value may use
(ReqIF allows a subset, e.g. no `font` or `img`) is only checked by
`validateAgainstSchema`, against the official schema.

Indenting is **off by default**: the XML indenter inserts whitespace into
mixed content, which would change XHTML attribute values. Enable it with
`new ReqIFWriter().setIndent(true)` only when readable output matters more
than exact XHTML content.

## Changing documents

The content is changed through its methods, which keep the references
between elements consistent; the maps its getters return are read-only.

```java
ReqIFCoreContent content = reqif.getReqIFCoreContent();

// hierarchy: insert, move (to any specification or node) and remove nodes
content.addSpecHierarchy("spec-1", 0, new SpecHierarchy("sh-new", 0, 0, specObject, null));
content.moveSpecHierarchy("sh-new", "sh-1", 2);   // below sh-1, third child
content.removeSpecHierarchy("sh-new");            // with everything below it

// elements: identifiers must be unused anywhere in the document
content.addSpecObject(new SpecObject("so-3", content.getSpecType("st-req"), List.of()));
content.removeSpecObject("so-1");     // its nodes' children move up, its relations go
content.removeSpecRelation("sr-1");   // also from its relation groups
content.removeSpecification("spec-2"); // with the relation groups between it and others
content.removeSpecType("st-old");     // refused while an element has this type
content.removeDatatype("dt-old");     // refused while an attribute uses it

// relations and relation groups
content.getSpecRelation("sr-2").setSourceObjID("so-3").setTargetObjID("so-2");
content.getRelationGroup("rg-1").addSpecRelationRef("sr-2");
```

Levels and section numbers of hierarchy nodes follow every change; a node
is in one place only (`getParent()`) and never below itself.

## Creating documents from scratch

`ReqIFBuilder` assembles the same object model the parser produces, so a
generated document is written exactly like a parsed one. Identifiers are
validated while building: referencing an unknown datatype, spec type,
spec object, attribute definition or enum value fails immediately with a
`ReqIFBuildException` instead of producing a broken document.

```java
ReqIFDocument document = ReqIFBuilder.create()
        .header(h -> h.id("hdr-1").title("My Spec").toolID("reqif4j")
                      .creationTime("2026-07-23T10:00:00Z"))
        .stringDatatype("dt-string", "String", 4096)
        .xhtmlDatatype("dt-xhtml", "XHTML")
        .enumerationDatatype("dt-enum", "Color", e -> e
                .value("ev-red", "Red", "1", "#ff0000")
                .value("ev-blue", "Blue", "2"))
        .specObjectType("st-req", "Requirement Type", t -> t
                .stringAttribute("ad-title", "ReqIF.Name", "dt-string")
                .xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml")
                .enumerationAttribute("ad-color", "Colors", "dt-enum",
                                      true, List.of("ev-blue")))
        .specificationType("st-spec", "Specification Type", t -> {})
        .specRelationType("st-rel", "satisfies", t -> {})
        .specObject("so-1", "st-req", o -> o
                .set("ad-title", "First requirement")
                .setEnum("ad-color", "ev-red", "ev-blue")   // multiselect
                .setXhtml("ad-text", "<p>The system shall boot.</p>"))
        .specObject("so-2", "st-req")
        .specRelation("sr-1", "st-rel", "so-1", "so-2")
        .specification("spec-1", "Main Spec", "st-spec", s -> s
                .child("sh-1", "so-1", c -> c.child("sh-2", "so-2")))
        .build();

new ReqIFWriter().write(document, Path.of("out.reqif"));
```

Everything the parser reads can be built as well:

```java
builder
        .relationGroupType("st-group", "Trace", t -> {})
        .specObjectType("st-req", "Requirement Type", t -> t
                .xhtmlAttribute("ad-text", "ReqIF.Text", "dt-xhtml")
                .editable("ad-text", true))                         // IS-EDITABLE
        .specObject("so-ole", "st-req", o -> o                      // IS-SIMPLIFIED
                .setXhtml("ad-text", "<p>preview</p>", "<p>complete content</p>"))
        .specification("spec-1", "System", "st-spec", s -> s
                .child("sh-1", "so-1", h -> h.tableInternal(false).editable(true)
                        .editableAttributes("ad-text")))            // EDITABLE-ATTS
        .relationGroup("rg-1", "System to software", "st-group", "spec-1", "spec-2", "sr-1")
        .with("so-1", e -> e.setDesc("Boot").setAlternativeID("REQ-1"))   // any element
        .lang("de")                                                 // xml:lang
        .toolExtension("<tool:settings xmlns:tool=\"urn:example\"/>");
```

Tool extensions must be elements of a namespace of their own, as the
schema requires. The builder's elements count as unchanged: they get the
header's `CREATION-TIME` as `LAST-CHANGE` unless one is set with `with`.

XHTML values may be passed with or without a surrounding `div`; the
markup is parsed, so the node tree and the token list work on generated
values too. The content category of generated spec objects is derived by
the same `TypeClassifier` used when reading (`typeClassifier(...)` to
override).

## Reading and writing .reqifz archives

A .reqifz archive holds one or more ReqIF documents plus the files they
reference - images, embedded OLE objects, PDFs or anything else. Reading
it keeps every entry: the documents in `getReqIFDocuments()`, all other
files in `getAttachmentFiles()` (pictures additionally in
`getPictureFiles()`). The archive is extracted into a temporary
directory that `close()` deletes again, so use try-with-resources and
read the files before closing. Archives can also be read from a stream:

```java
try (ReqIFz reqifz = new ReqIFz(uploadStream, "upload.reqifz")) {
    File ole = reqifz.getAttachmentFiles().get("files/diagram.ole");
}
```

Attachments travel with the document, stored under the path the XHTML
`object` elements reference:

```java
Map<String, byte[]> attachments = Map.of("files/diagram.png", pngBytes);
new ReqIFzWriter().write(document, "spec.reqif", attachments, Path.of("spec.reqifz"));
```

An archive that was read can be re-packed - the documents are serialized
from the model (so modifications are included) while every attachment is
copied unchanged from the extracted files:

```java
try (ReqIFz source = new ReqIFz("in.reqifz")) {
    new ReqIFzWriter().write(source, Path.of("out.reqifz"));
}
```

# Validation

`ReqIFValidator` checks a document before it is written: identifiers
present, valid `xsd:ID`s and globally unique, resolvable datatype and
spec type references, attribute values matching their spec type, enum
value references, relation endpoints, spec hierarchy targets and relation
group references.

Values the schema constrains are errors when they do not fit:
`LAST-CHANGE`, `CREATION-TIME` and DATE values must be `xsd:dateTime`
(a date alone is not enough), enum `KEY`s integers, `REQ-IF-VERSION`
`1.0`, and default values - written as they are - must already be in
schema notation (`true`/`false`, `1.5`, `INF`). Values outside the
limits of their datatype (`MAX-LENGTH`, `MIN`/`MAX`) are warnings: the
schema cannot express those limits, but importing tools may reject them.
Text with characters XML cannot hold (control characters) is an error,
as are relation groups without type, source or target specification.

```java
new ReqIFValidator().validate(document).throwIfInvalid();
new ReqIFWriter().write(document, Path.of("out.reqif"));
```

This is a model-level check. The OMG ReqIF XSD is **not bundled** with
this library for licensing reasons; if you have it, run a full XML
Schema validation on top - either of the XML the writer produces for a
document, or of a file on disk (findings then carry line numbers):

```java
ValidationResult schemaIssues =
        new ReqIFValidator().validateAgainstSchema(document, Path.of("schema/reqif.xsd"));
ValidationResult fileIssues =
        new ReqIFValidator().validateAgainstSchema(Path.of("in.reqif"), Path.of("schema/reqif.xsd"));
```

`reqif.xsd` imports further schemas by http URL: `xml.xsd`,
[`driver.xsd`](http://www.omg.org/spec/ReqIF/20110402/driver.xsd) and the
XHTML modules it references (`xhtml-*-1.xsd`). Validation never goes
online; these imports are looked up by file name in the directory of
`reqif.xsd`, so download them into the same folder. A missing import is
reported by name together with its original URL.

The test suite validates against the official schema. It downloads the
schema files on first use into `target/reqif-schema` (or the directory
in the `REQIF_SCHEMA_DIR` environment variable); without network access
these tests are skipped locally and fail on CI.

Relations may connect spec objects, and relation groups specifications,
of another document of the same .reqifz archive (the schema types these
references `GLOBAL-REF`). Validate the documents of an archive together
to resolve them across documents:

```java
try (ReqIFz archive = new ReqIFz("export.reqifz")) {
    new ReqIFValidator().validate(archive).throwIfInvalid();
}
```

Identifiers duplicated within one category cannot be seen in the model
(maps are keyed by identifier, so the later element has replaced the
earlier); the parser reports them while reading, see below.

# Tested against real exports

`CorpusTest` reads exports of IBM DOORS, Siemens Polarion, ReqIF Studio,
Enterprise Architect, Capella and StrictDoc as well as the reference files
of the ReqIF Implementor Forum (`src/test/resources/corpus`, with their
origin and license). Each document is written back as read - or differs
for a stated reason, such as a document in the namespace of a ReqIF draft
- and written the same way a second time; the validator raises no error
for a schema-valid file, and a document it finds no error in is written
schema-valid. Archives are re-packed with all their entries.

# Reading imperfect documents

Real exports are not always schema-valid. A document with broken parts is
still read: what cannot be used is skipped, everything else is kept, and
each problem is recorded instead of stopping the reading with an
exception:

```java
ReqIF reqif = new ReqIF("export.reqif");
for (ParseIssue issue : reqif.getReqIFDocument().getParseIssues()) {
    System.out.println(issue);   // e.g. "[so-1] Value references attribute definition ad-x, ..."
}
```

- elements without `IDENTIFIER` are skipped;
- an unresolvable type reference keeps the element and its reference (it
  is written back); its values cannot be interpreted and are kept as
  `UninterpretedAttributeValue`s - the XML element as read, written back
  unchanged - as are values of unknown attribute definitions and values
  whose kind does not match their definition (Polarion, for example,
  writes `ReqIF.Text` values as `ATTRIBUTE-VALUE-STRING`);
- an attribute definition whose datatype is missing is kept as read, too;
- values that do not fit their datatype (`THE-VALUE="abc"` for an integer)
  are kept as written and written back unchanged;
- identifiers used twice within one kind are reported, the later element
  wins.

`ReqIFValidator.validate` reports these issues as warnings - they say
what happened while reading - next to its own findings, which report as
errors what is still wrong with the model (an unresolved reference, a
value that does not fit its datatype) until it is fixed. The model
classes' constructors that take no `ParseIssues` read just as tolerantly
and discard the issues; only an element without IDENTIFIER makes them
throw a `ReqIFParseException`.

# Unmodelled ReqIF kinds

The parser models the datatype and spec type kinds of the standard. Kinds
it does not know - vendor extensions or later ReqIF revisions - are kept
generically rather than dropped: the original element name is remembered
and written back unchanged, and values are carried as their raw
`THE-VALUE`. This applies to datatype definitions, attribute definitions,
attribute values and spec types, so a document round-trips without losing
or altering them.

Limitation: a value of an unknown kind is only preserved when it is
carried in a `THE-VALUE` attribute. Kinds that store their value in child
elements are not covered.
