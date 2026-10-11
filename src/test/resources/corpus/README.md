# ReqIF test corpus

Exports of real requirements tools and the reference files of the ReqIF
Implementor Forum, used by `CorpusTest`: every document must be read
without failing, written back as read (or differ for a stated reason),
written the same way a second time, and the validator must agree with the
official ReqIF schema.

## Source and license

Copied from the test suite of the Python library *reqif*,
<https://github.com/strictdoc-project/reqif>, commit
`b36694051d6b0502682bcd8f83567d4e1f387d1b`, folders
`tests/integration/reqif_software` and `tests/integration/examples`.
That project is licensed under the Apache License 2.0
([LICENSE-Apache-2.0.txt](LICENSE-Apache-2.0.txt)); the files are
redistributed unchanged under that license. Duplicates were left out, and
the folder names of the Implementor Forum files were shortened.

## Files

| File | Tool | Notes |
|---|---|---|
| `Doors/01_anonimized_example_date_data_type` | IBM DOORS | excerpt: references a specification type and attribute definitions it does not contain |
| `Doors/02_example_from_a_user` | IBM DOORS | schema-valid |
| `Doors/03_example_from_a_user` | IBM DOORS | excerpt without `THE-HEADER`; the object's type is missing, its 12 values are kept as written |
| `Doors/04_…`, `Doors/05_…` | IBM DOORS | excerpts referencing missing types; `xsi:schemaLocation` |
| `Doors/06_example_from_a_user` | IBM DOORS | an attribute definition references a missing datatype; it is kept as written |
| `Doors/10_example_capella_requirements_vp` | Capella Requirements VP | schema-valid |
| `Polarion/01_anonimized_example` | Siemens Polarion | anonymized: DATE values `aaa` |
| `Polarion/02_example`, `Polarion/03_reqifz_example` | Siemens Polarion | an identifier used twice; `ReqIF.Text` values written as `ATTRIBUTE-VALUE-STRING` although the attribute is XHTML |
| `ReqIF_Studio/01_anonimized_example` | ReqIF Studio | relations reference a type that is missing |
| `SparxSystems_Enterprise_Architect_8.0/01_example` | Enterprise Architect 8 | relations and hierarchy reference objects that are missing |
| `StrictDoc/01_example` | StrictDoc | lacks the required `LAST-CHANGE` and `MAX-LENGTH`; the writer fills them in |
| `StrictDoc/02_reqifz_example` | StrictDoc | archive with an SVG attachment |
| `ci.eclipse.org/TC1000` … `TC1801` | ReqIF Implementor Forum reference files (Eclipse RMF) | schema-valid; `TC1700` is an archive whose relations connect the objects of another document |
| `org.eclipse.rmf/01_specRelationTest`, `02_sample` | Eclipse RMF | namespace of a ReqIF draft (`20101201`), identifiers that are no xsd:ID; written in the ReqIF 1.2 namespace |
