# Ist-/Soll-Analyse reqif4j: Weg zum vollständigen ReqIF-Import und -Export

Stand: 2026-10-11 · Basis: Branch `claude/dreamy-goldberg-muhi8j` (= `master` @ `972ae90`, Version `1.1.2-SNAPSHOT`)

## 0. Umsetzungsstand

| Phase | Stand | Ergebnis |
|---|---|---|
| **1.1** Schemaprüfung | ✅ erledigt | `validateAgainstSchema` löst die http-Importe des offiziellen XSD lokal auf und geht nie online. Fehlende Importe werden mit Namen und URL gemeldet. Neue Variante für Dateien auf der Platte mit Zeilennummern. Die Tests laden das Schema einmalig herunter (in der CI gecacht) und prüfen gegen das echte OMG-XSD (`OfficialReqIFSchema.assertSchemaValid`). |
| **1.2** `Identifiable` | ✅ erledigt | Gemeinsame Basisklasse für alle 9 Elementarten. `LAST-CHANGE`, `DESC`, `LONG-NAME` (auch an Spec-Objekten, Relationen und Hierarchieknoten) und `ALTERNATIVE-ID` werden gelesen und geschrieben. Fehlender `LONG-NAME` führt nicht mehr zur NPE, fehlender `IDENTIFIER` zu einer klaren `ReqIFParseException`. Das Lesen der Attributwerte liegt jetzt in einer Klasse (`AttributeValueReader`). |

Beim Umsetzen von Phase 1 fielen vier weitere Writer-Fehler auf. Sie wurden behoben, weil sie einen
schemagültigen Round-Trip verhinderten:

- **`ALTERNATIVE-ID` hatte die falsche Form.** Das Schema verschachtelt das Element
  (`<ALTERNATIVE-ID><ALTERNATIVE-ID IDENTIFIER=".."/></ALTERNATIVE-ID>`). reqif4j las und schrieb die flache
  Form, las also die Alternative-IDs echter Dateien nicht und schrieb ungültiges XML. Jetzt wird die
  verschachtelte Form geschrieben, gelesen werden beide.
- **`SPEC-RELATION-GROUPS` stand vor `SPECIFICATIONS`.** `REQ-IF-CONTENT` ist eine `xsd:sequence`.
  Jede Datei mit Relationsgruppen war deshalb ungültig.
- **Leeres `<THE-VALUE/>` für XHTML-Attribute ohne Inhalt.** Das ist ungültig, solche Werte werden jetzt ausgelassen.
- **Werte unbenannter Attributdefinitionen überschrieben sich.** Ohne `LONG-NAME` dient jetzt die ID als
  Schlüssel. Die eigentliche Umstellung auf IDs bleibt Arbeitspaket 3.1.

| Phase | Stand | Ergebnis |
|---|---|---|
| **5.1** Testkorpus | ✅ erledigt (ohne Zielwerkzeuge) | 25 echte Exporte (IBM DOORS, Siemens Polarion, ReqIF Studio, Enterprise Architect, Capella, StrictDoc) und die Referenzdateien des ReqIF Implementor Forum aus dem Apache-2.0-lizenzierten Projekt *reqif* (`src/test/resources/corpus`, Herkunft im README dort). `CorpusTest` prüft für jede Datei: Lesen ohne Abbruch, Zurückschreiben wie gelesen (sonst mit dokumentiertem Grund), zweites Schreiben ohne Änderung, Validator und offizielles Schema stimmen überein. Archive werden mit allen Einträgen neu gepackt. Gefunden und behoben: Werte von Elementen mit fehlendem Typ, von unbekannten Definitionen und von unpassender Art (Polarion schreibt XHTML-`ReqIF.Text` als `ATTRIBUTE-VALUE-STRING`) gingen verloren oder wurden umgedeutet. Sie bleiben jetzt als `UninterpretedAttributeValue` unverändert erhalten. Ebenso Attributdefinitionen ohne Datentyp. `xsi:schemaLocation` ging verloren. Relationen dürfen Objekte eines anderen Dokuments im Archiv referenzieren (`GLOBAL-REF`, Testfall TC1700), dafür gibt es `ReqIFValidator.validate(ReqIFz)`. Offen bleibt der Import in ReqIF Studio und Zielwerkzeuge: Das braucht die Werkzeuge und ist ein manueller Schritt. |
| **5.2** Implementation Guide | ✅ erledigt | `ImplementationGuideCheck` prüft Datentypen der Standardattribute (aus den Exporten abgeleitet: Datumsattribute DATE, `ReqIF.ForeignDeleted` BOOLEAN, `ReqIF.Text` XHTML, Namen STRING oder XHTML), eingebettete Objekte (MIME-Typ, relativer Pfad, PNG-Vorschau für OLE und andere Formate, auch in XHTML-Defaults), `IS-SIMPLIFIED` zusammen mit `THE-ORIGINAL-VALUE` sowie bei Archiven, ob referenzierte Dateien vorhanden sind (relativ zum Dokument, Percent-Encoding aufgelöst). Die Referenzdateien des Implementor Forum ergeben keinen Befund. |
| **5.3** Große Dateien | ✅ gemessen und beschleunigt, StAX zurückgestellt | Messung mit der 19-MB-Referenzdatei TC4000 (10.000 Objekte): Lesen 2,5 s → 0,5 s, Schreiben 0,3 s, Validieren 0,2 s, 99 MB Heap (läuft mit `-Xmx160m`). Behoben wurden quadratische Stellen: `NodeList.item(i)` in verschachtelten Schleifen, Löschen mit Nachrücken, Neunummerierung beim Anhängen, Kopieren der ID-Menge im Builder, Suche nach Hierarchieknoten (jetzt ein inkrementell gepflegter Index), dazu eine Factory pro XHTML-Wert. `LargeDocumentTest` sichert das mit 20.000 Objekten ab. Ein StAX-Reader/-Writer würde den Speicher etwa halbieren (kein zweites DOM beim Schreiben), lohnt den Aufwand bei linear ~10 KB Heap pro Objekt aber erst für Exporte mit deutlich über 100.000 Objekten. |

Ergebnis von Phase 5: Die Bibliothek ist gegen reale Exporte und die Referenzdateien des Implementor Forum
getestet, prüft die Konventionen des Implementation Guide und verarbeitet große Dateien in linearer Zeit.
Der Interop-Nachweis im Zielwerkzeug (Abnahmekriterium 6) braucht die Werkzeuge selbst. Er bleibt ein
manueller Schritt: Die von `CorpusTest` geschriebenen Dateien in ReqIF Studio, DOORS oder Polarion
importieren.

| Phase | Stand | Ergebnis |
|---|---|---|
| **4.1** Mutations-API | ✅ erledigt | Werte per Definitions-ID setzen (`set`, `setXhtml`, `setEnum`, `removeValue`), Hierarchieknoten einfügen, verschieben und löschen (`addChild`, `addSpecHierarchy`, `moveSpecHierarchy`, `removeSpecHierarchy`), Relationen und Relationsgruppen umhängen. Löschen hält Referenzen konsistent: Ein entferntes Spec-Objekt nimmt seine Relationen mit, die Kinder seiner Knoten rücken nach. Entfernte Relationen verlassen ihre Gruppen, entfernte Spezifikationen nehmen ihre Gruppen mit. Typen und Datentypen in Verwendung werden nicht gelöscht. IDs müssen dokumentweit frei sein. Neue und geänderte Elemente bekommen beim Schreiben den Schreibzeitpunkt als `LAST-CHANGE`, der nach erfolgreicher Ausgabe ins Modell übernommen wird. |
| **4.2** Builder | ✅ erledigt | Relationsgruppen und ihre Typen, `with(id, …)` für `LONG-NAME`/`DESC`/`LAST-CHANGE`/`ALTERNATIVE-ID` jedes Elements, `lang`, Tool-Extensions (nur Elemente eines eigenen Namespace, wie das Schema verlangt), `IS-EDITABLE` an Attributdefinitionen und Knoten, `IS-TABLE-INTERNAL`, `EDITABLE-ATTS`, XHTML mit `THE-ORIGINAL-VALUE`/`IS-SIMPLIFIED`. Der Validator prüft jetzt auch `EDITABLE-ATTS`. |

Ergebnis von Phase 4: „Import → Änderung → Export“ funktioniert über das Modell, ohne dass Aufrufer
Referenzen selbst nachziehen müssen. Alles, was der Parser liest, lässt sich auch erzeugen.

API-Änderungen in Phase 4: Die Maps von `ReqIFCoreContent` (`getSpecObjects()` …) sind schreibgeschützt,
geändert wird über `add…`/`remove…`. `add…` lehnt vergebene IDs ab, statt still zu ersetzen.
`getAllSpecHierarchies()` liefert eine schreibgeschützte Liste. Ein Knoten lässt sich nur an einer Stelle
einhängen (verschieben mit `moveSpecHierarchy`). Aus Werten erzeugte Elemente gelten als geändert, bis
der Builder (oder `clearModified()`) das zurücksetzt. `ReqIFBuildException` ist eine
`IllegalArgumentException`.

| Phase | Stand | Ergebnis |
|---|---|---|
| **3.1/3.2** Werte | ✅ erledigt | Werte liegen nach Definitions-ID in Dokumentreihenfolge vor (`getValues()`), Defaults werden nicht mehr als Werte geschrieben. `getAttributes()` bleibt als schreibgeschützte Sicht mit Defaults nach `LONG-NAME`, `setValue`/`removeValue` ändern Werte. Gemeinsame Basisklasse `SpecElementWithAttributes`. |
| **3.3** Zahlen | ✅ erledigt | Integer ohne Größenbeschränkung (`getBigInteger()`), REAL-Grenzen und XHTML-Defaults schon in Phase 2. |
| **3.4** Metadaten | ✅ erledigt | `xml:lang`, `REPOSITORY-ID`, `IS-EDITABLE`, `IS-TABLE-INTERNAL`, `EDITABLE-ATTS`, `THE-ORIGINAL-VALUE`, `IS-SIMPLIFIED`, `p`-Inhalt statt `div`. Laut Schemaabgleich fehlt dem Code kein Element und kein Attribut des XSD mehr. |
| **3.5** `.reqifz` | ✅ erledigt | Alle Anhänge bleiben erhalten (OLE, PDF, GIF …), Neupacken ohne Speicherladen, `InputStream`-Konstruktor, Entpacken in ein Temp-Verzeichnis, das `close()` löscht. |
| **3.6** toleranter Import | ✅ erledigt | Statt Abstürzen gibt es `ReqIFDocument.getParseIssues()`. Unpassende Werte bleiben als Rohtext erhalten, nicht auflösbare Referenzen werden mitgeschrieben, nur Unverwendbares wird übersprungen. Der Validator meldet das als Warnungen. |
| **3.7** Round-Trip-Test | ✅ erledigt | `LosslessRoundTripTest` mit einer schemagültigen Datei, die alle Elemente und Attribute nutzt. Der kanonische XML-Vergleich ignoriert nur, was das Schema offenlässt. |

Ergebnis von Phase 3: Import und Export sind im Sinne des Zielbilds vollständig. Was eine schemagültige
Datei enthält, wird gelesen und inhaltlich unverändert geschrieben. Lexikalisch normalisiert werden nur
typisierte Werte (`1` → `true`, `1e3` → `1000.0`). Kaputte Dateien werden gelesen, soweit es geht, und die
Probleme gemeldet.

API-Änderungen in Phase 3: `getAttributes()` ist schreibgeschützt (Änderungen über `setValue`/`removeValue`),
`ReqIFz` entpackt nicht mehr neben die Quelldatei, und ein XHTML-Default gilt jetzt als effektiver Wert. Bei
attributbasierter Klassifizierung kann sich dadurch die Kategorie von Objekten ohne eigenen Wert ändern.

Eine API-Änderung: `getName()` liefert für Elemente ohne `LONG-NAME` jetzt `null` (vorher Absturz,
bei Enum-Werten `""`). `DatatypeEnumeration.getEnumValueName()` liefert weiterhin `""`.

| Phase | Stand | Ergebnis |
|---|---|---|
| **2.1** `LAST-CHANGE`-Default | ✅ erledigt | Der Writer setzt fehlendes `LAST-CHANGE` auf die `CREATION-TIME` des Headers bzw. den Schreibzeitpunkt (Clock injizierbar). Das Modell bleibt unverändert. |
| **2.2** Pflichtattribute | ✅ erledigt | `MULTI-VALUED` und `OTHER-CONTENT` werden immer geschrieben, REAL behält `ACCURACY`/`MIN`/`MAX` (Defaults 15 / voller double-Bereich), Header-Pflichtfelder bekommen Defaults, der Builder vergibt eine freie Header-ID. Unendlich wird als `INF`/`-INF` geschrieben und gelesen, XHTML-Defaults werden als Markup geschrieben. |
| **2.3** Modellvalidator | ✅ erledigt | Fehler bei allem, was die Ausgabe schemaungültig machen würde: ungültige `xsd:ID`, kein `xsd:dateTime` (`LAST-CHANGE`, `CREATION-TIME`, DATE-Werte), `REQ-IF-VERSION` ≠ `1.0`, nicht ganzzahliger Enum-`KEY`, Defaults nicht in Schema-Notation, fehlender Header. Warnungen bei Verstößen gegen `MAX-LENGTH` und `MIN`/`MAX`. Außerdem Fehler bei Zeichen, die XML nicht aufnehmen kann, Enum-Defaults als Text und Relationsgruppen ohne Typ/Quelle/Ziel. Vertrag: Ein Dokument ohne Fehler wird schemagültig geschrieben, mit Ausnahme des XHTML-Vokabulars (z. B. `font`, `img` sind in ReqIF nicht erlaubt), das nur `validateAgainstSchema` prüft (siehe 5.2). |

Ergebnis von Phase 2: Mit dem Builder erzeugte Dokumente (das README-Beispiel) und der Round-Trip einer
schemagültigen Datei mit REAL-Grenzen, `MULTI-VALUED="false"` und leerem `OTHER-CONTENT` bestehen das
offizielle OMG-Schema. Die Tests prüfen das in der CI gegen das echte XSD.

Weitere Funde der Review-Runden in Phase 2: Der Parser übernahm auch verschachtelte `TOOL-EXTENSIONS`
aus Vendor-Erweiterungen (jetzt nur direkte Kinder von `REQ-IF`). Unendliche REAL-Werte konnte die
Bibliothek nicht mehr einlesen, nachdem sie als `INF` geschrieben wurden (jetzt in beide Richtungen
xsd-Notation). XHTML-Defaults wurden als ungültiges Attribut geschrieben und nicht gelesen (jetzt beides,
das Lesen ist aus 3.3 vorgezogen). Ein Boolean-Wert `1` (gültiges xsd:boolean) wurde als `false` gelesen.
Offen für 3.3 bleibt: Integer-Werte außerhalb des int-Bereichs.

Bekannte Grenzen: IDs mit Nicht-ASCII-Zeichen prüft der Validator mit dem Schemavalidator des JDK, damit
beide übereinstimmen. Ein reines Datum (`2026-01-01`) in DATE-Werten liest der Parser weiterhin tolerant
ein, der Validator meldet es als Fehler.

## 1. Zusammenfassung

**Zielbild:** Jede gültige ReqIF-1.2-Datei (`.reqif`/`.reqifz`) wird verlustfrei eingelesen, lässt
sich über eine API bearbeiten und wird wieder als **schemakonforme** Datei ausgegeben, die
gängige Werkzeuge (DOORS, Polarion, ReqIF Studio, Enterprise Architect …) wieder importieren können.

**Ist-Stand in einem Satz:** Der *Import* deckt die fachlichen Kerninhalte gut ab und ist
weitgehend robust. Der *Export* ist strukturell vollständig, erzeugt aber **in keinem Fall eine
XSD-gültige Datei** und verliert beim Round-Trip eine Reihe von Informationen.

| Bereich | Reifegrad | Kernaussage |
|---|---|---|
| Import `.reqif` | 🟡 gut | Alle Datentypen, Spec-Typen, Objekte, Relationen, Relationsgruppen, Hierarchien, XHTML, Tool-Extensions. Es fehlen Metadaten (`LAST-CHANGE`, `DESC`, `LONG-NAME` von Objekten …). Bei einigen gültigen Dateien bricht der Import ab. |
| Import `.reqifz` | 🟡 eingeschränkt | Nur Anhänge vom Typ `png`/`jpg`/`jpeg`. OLE-, GIF-, SVG- und PDF-Anhänge gehen verloren. |
| Export `.reqif` | 🔴 nicht schemakonform | `LAST-CHANGE` und weitere Pflichtattribute fehlen. Jede geschriebene Datei verletzt das OMG-XSD. |
| Export `.reqifz` | 🟡 eingeschränkt | Funktioniert, übernimmt aber nur die Bildanhänge, die der Import kennt. |
| Erzeugen (`ReqIFBuilder`) | 🟡 gut | Komfortabel, prüft Referenzen. Relationsgruppen, Metadaten und Pflicht-Header-Felder werden nicht unterstützt bzw. nicht erzwungen. |
| Validierung | 🟡 Lücke | Die Modellprüfung meldet „gültig“, obwohl das XSD 17 bis 23 Fehler findet. Die Schemaprüfung kann das offizielle XSD nicht laden. |
| Bearbeiten geparster Dokumente | 🔴 fehlt | Es gibt keine Mutations-API (Werte, Hierarchie, Relationen). |
| Tests/CI | 🟢 solide Basis | 154 Tests, alle grün (`mvn verify`). Es fehlen XSD-Konformitätstests und echte Tool-Exporte als Testdaten. |

## 2. Vorgehen

1. Code-Review der Kernklassen unter `src/main/java/de/uni_stuttgart/ils/reqif4j` (83 Dateien)
   mit Fokus auf Parser, Writer, Builder und Validator.
2. `mvn verify` ausgeführt: **154 Tests, 0 Fehler**.
3. Das offizielle OMG-Schema `http://www.omg.org/spec/ReqIF/20110401/reqif.xsd` samt XHTML-Modulen
   heruntergeladen und lokal aufgelöst.
4. Experimente gegen den aktuellen Build:
   - **Builder → Writer → XSD:** das Beispiel aus dem README, mit dem Schema geprüft.
   - **Round-Trip:** eine Datei, die alle optionalen ReqIF-Merkmale nutzt, einlesen, schreiben,
     die Ausgabe vergleichen und gegen das XSD prüfen.
   - **Robustheit:** fehlender `LONG-NAME`, doppelte Attributnamen, unbekannte Typreferenz,
   `.reqifz` mit OLE-, GIF- und PNG-Anhängen.

Alle unten als „nachgewiesen“ markierten Befunde stammen aus diesen Experimenten.

## 3. Ist-Stand

### 3.1 Architektur

| Paket | Aufgabe | Bemerkung |
|---|---|---|
| `reqif` | Einstieg: `ReqIF` (.reqif), `ReqIFz` (.reqifz), `ReqIFDocument`, `ReqIFHeader`, `ReqIFCoreContent` | DOM-basiert, liest die ganze Datei in den Speicher |
| `datatypes` | `Datatype*` (Boolean, Integer, String, Real→„Double“, Date, XHTML, Enumeration) | Unbekannte Datentypen werden generisch erhalten |
| `attributes` | `AttributeDefinition*`, `AttributeValue*`, XHTML-Werte | Werte sind nach `LONG-NAME` der Definition abgelegt, nicht nach ID |
| `specification` | `SpecType`, `SpecObject`, `SpecRelation`, `Specification`, `SpecHierarchy`, `RelationGroup`, Klassifizierer | Inhaltskategorien (REQ/HEADLINE/…) sind parsereigene Erweiterungen |
| `xhtml` | Typisierter XHTML-Knotenbaum, Token-Liste, Rendering | Gut getestet |
| `write` | `ReqIFWriter`, `ReqIFzWriter` | Schreibt aus dem Objektmodell, nicht aus dem Quell-DOM |
| `build` | `ReqIFBuilder`, `SpecTypeBuilder`, `ValuesBuilder` | Fluent-API zum Neuerzeugen von Dokumenten |
| `validate` | `ReqIFValidator` (Modell + optional XSD) | |
| `util` | `SecureXml` (XXE-Schutz), `XmlUtils`, `XhtmlParser` | Zip-Slip-Schutz in `ReqIFz` |

Positiv: Prefix-unabhängiges Lesen (`rif:`, `xhtml:`, `reqif-xhtml:`), sichere XML-Konfiguration,
Erhalt unbekannter Datentyp-, Attribut- und Spec-Typ-Arten, Tool-Extensions werden 1:1
durchgereicht, Multiselect-Enums, XHTML-Rendering mit korrektem Escaping, deterministische Ausgabe.

### 3.2 Abdeckungsmatrix (ReqIF 1.2 gegen reqif4j)

Legende: ✅ unterstützt · ⚠️ teilweise · ❌ fehlt · — nicht anwendbar

| ReqIF-Element / -Attribut | Import | Export | Builder |
|---|---|---|---|
| `REQ-IF` `xml:lang` | ✅ *(3.4)* | ✅ *(3.4)* | ⚠️ per Setter |
| Header: `COMMENT`, `CREATION-TIME`, `REQ-IF-TOOL-ID`, `REQ-IF-VERSION`, `SOURCE-TOOL-ID`, `TITLE` | ✅ | ✅ Pflichtfelder mit Default *(2.2)* | ✅ freie Header-ID als Default *(2.2)* |
| Header: `REPOSITORY-ID` | ✅ *(3.4)* | ✅ *(3.4)* | ✅ *(3.4)* |
| `TOOL-EXTENSIONS` | ✅ (roh) | ✅ (roh) | ❌ |
| Identifiable: `IDENTIFIER` | ✅ | ✅ | ✅ |
| Identifiable: `LAST-CHANGE` (**Pflicht**) | ✅ *(1.2)* | ✅ mit Default *(2.1)* | ⚠️ nur per Setter, sonst Default |
| Identifiable: `DESC` | ✅ *(1.2)* | ✅ *(1.2)* | ⚠️ nur per Setter |
| Identifiable: `LONG-NAME` | ✅ alle Elemente, optional *(1.2)* | ✅ *(1.2)* | ⚠️ nicht für Spec-Objekte |
| `ALTERNATIVE-ID` | ✅ verschachtelte und flache Form *(1.2)* | ✅ verschachtelt *(1.2)* | ⚠️ nur per Setter |
| `DATATYPE-DEFINITION-BOOLEAN` / `-DATE` / `-XHTML` | ✅ | ✅ | ✅ |
| `DATATYPE-DEFINITION-STRING` `MAX-LENGTH` | ✅ | ✅ | ✅ |
| `DATATYPE-DEFINITION-INTEGER` `MIN`/`MAX` | ✅ (long) | ✅ | ✅ |
| `DATATYPE-DEFINITION-REAL` `ACCURACY`/`MIN`/`MAX` (**Pflicht**) | ✅ *(2.2)* | ✅ *(2.2)* | ✅ *(2.2)* |
| `DATATYPE-DEFINITION-ENUMERATION`, `ENUM-VALUE`, `KEY` | ✅ | ✅ | ✅ |
| `EMBEDDED-VALUE` `OTHER-CONTENT` (**Pflicht**) | ✅ | ✅ auch leer *(2.2)* | ✅ |
| `SPEC-OBJECT-TYPE`, `SPECIFICATION-TYPE`, `SPEC-RELATION-TYPE` | ✅ | ✅ | ✅ |
| `RELATION-GROUP-TYPE` | ✅ | ✅ | ✅ *(4.2)* |
| `ATTRIBUTE-DEFINITION-*` | ✅ | ✅ | ✅ |
| `ATTRIBUTE-DEFINITION-*` `IS-EDITABLE` | ✅ *(3.4)* | ✅ *(3.4)* | ✅ *(4.2)* |
| `ATTRIBUTE-DEFINITION-ENUMERATION` `MULTI-VALUED` (**Pflicht**) | ✅ | ✅ auch `false` *(2.2)* | ✅ |
| `DEFAULT-VALUE` (einfache Typen, Enum) | ✅ | ✅ | ✅ |
| `DEFAULT-VALUE` (XHTML) | ✅ *(2.2, aus 3.3 vorgezogen)* | ✅ als Markup *(2.2)* | ✅ über `attribute(...)` |
| `ATTRIBUTE-VALUE-*` (alle 7 Arten) | ✅ | ✅ | ✅ |
| `ATTRIBUTE-VALUE-INTEGER` außerhalb des int-Bereichs | ✅ unbegrenzt *(3.3)* | ✅ *(3.3)* | ✅ *(3.3)* |
| `ATTRIBUTE-VALUE-XHTML` `THE-ORIGINAL-VALUE` / `IS-SIMPLIFIED` | ✅ *(3.4)* | ✅ *(3.4)* | ✅ *(4.2)* |
| `SPEC-OBJECT` | ✅ | ✅ | ✅ |
| `SPEC-RELATION` (+ Attribute) | ✅ | ✅ | ✅ |
| `SPECIFICATION` (+ Attribute) | ✅ | ✅ | ✅ |
| `SPEC-HIERARCHY` | ✅ | ✅ | ✅ |
| `SPEC-HIERARCHY` `IS-TABLE-INTERNAL`, `IS-EDITABLE`, `EDITABLE-ATTS` | ✅ *(3.4)* | ✅ *(3.4)* | ✅ *(4.2)* |
| `RELATION-GROUP` | ✅ | ✅ | ✅ *(4.2)* |
| `.reqifz` mit mehreren `.reqif` | ✅ | ✅ | — |
| `.reqifz`-Anhänge `png`/`jpg`/`jpeg` | ✅ | ✅ | ✅ |
| `.reqifz`-Anhänge OLE, GIF, SVG, PDF, RTF … | ✅ *(3.5)* | ✅ *(3.5)* | ✅ über `Map<String, byte[]>` |

## 4. Befunde: Was zum Zielstand fehlt

Die Befunde sind nach Auswirkung auf das Zielbild priorisiert:
**P0** blockiert jeden produktiven Export, **P1** verursacht Datenverlust oder Abbrüche bei realen Dateien,
**P2** betrifft Vollständigkeit, API und Betrieb.

### P0: Export ist nicht schemakonform

**4.1 `LAST-CHANGE` fehlt vollständig (nachgewiesen).**
Das XSD verlangt `LAST-CHANGE` (`xsd:dateTime`, `use="required"`) auf **jedem** Identifiable:
Datentypen, Enum-Werte, Spec-Typen, Attributdefinitionen, Spec-Objekte, Relationen,
Spezifikationen, Hierarchieknoten und Relationsgruppen. Der Parser liest das Attribut nicht,
das Modell hat kein Feld dafür und der Writer schreibt es nicht. Ergebnis: **Jede** exportierte
Datei ist ungültig. Bei einem Werkzeug wie DOORS geht damit außerdem die Änderungsverfolgung
beim Re-Import verloren.

**4.2 Weitere Pflichtattribute werden nicht geschrieben (nachgewiesen).**
- `MULTI-VALUED` auf `ATTRIBUTE-DEFINITION-ENUMERATION` wird nur bei `true` gesetzt (`ReqIFWriter.attributeDefinition`).
- `OTHER-CONTENT` auf `EMBEDDED-VALUE` entfällt, wenn der Wert leer ist.
- `ACCURACY`, `MIN` und `MAX` auf `DATATYPE-DEFINITION-REAL` werden weder gelesen noch geschrieben
  (`DatatypeDouble` hat keine Felder).
- Die Pflichtelemente des Headers (`CREATION-TIME`, `REQ-IF-TOOL-ID`, `REQ-IF-VERSION`,
  `SOURCE-TOOL-ID`, `TITLE`) werden bei leerem Wert weggelassen. Der Builder setzt keine Defaults.
  Das README-Beispiel erzeugt so einen ungültigen Header.

XSD-Prüfung des README-Beispiels: **17 unterschiedliche Fehlermeldungen**.
Round-Trip der Testdatei: **23 unterschiedliche Fehlermeldungen**.

**4.3 Die Modellvalidierung gibt falsche Sicherheit (nachgewiesen).**
`ReqIFValidator.validate()` meldet für dasselbe Builder-Dokument `isValid() == true`.
Geprüft werden Referenzen und Eindeutigkeit, aber keine Pflichtattribute und Wertebereiche
(`MAX-LENGTH`, `MIN`/`MAX`, `ACCURACY`).

**4.4 `validateAgainstSchema` kann das offizielle XSD nicht laden (nachgewiesen).**
`reqif.xsd` importiert `xml.xsd` und `driver.xsd` (XHTML-Module) per HTTP. `SecureXml` erlaubt
nur `file:`-Zugriffe. Die Prüfung endet daher mit
`Failed to read schema document 'xml.xsd', because 'http' access is not allowed`.
Nötig sind ein `LSResourceResolver` oder ein XML-Katalog mit lokal abgelegten Schemata
(im Test-Classpath oder als Download-Anleitung). Erst dann kann die Schemakonformität in der CI
geprüft werden.

### P1: Datenverlust und Abbrüche beim Import und im Round-Trip

**4.5 Metadaten gehen verloren (nachgewiesen).** Folgende Angaben werden nicht modelliert und
fehlen nach `parse → write`:
`DESC` (alle Elemente), `LONG-NAME` von `SPEC-OBJECT`/`SPEC-RELATION`/`SPEC-HIERARCHY`,
`REPOSITORY-ID`, `xml:lang`, `IS-EDITABLE`, `IS-TABLE-INTERNAL`, `EDITABLE-ATTS`,
`ACCURACY`/`MIN`/`MAX` (Real) sowie XHTML-Defaultwerte.

**4.6 XHTML: `THE-ORIGINAL-VALUE` und `IS-SIMPLIFIED` fehlen (nachgewiesen).**
Werkzeuge wie DOORS legen den vollständigen Inhalt (z. B. eingebettete OLE-Objekte mit
Vorschaubild) in `THE-ORIGINAL-VALUE` ab und eine vereinfachte Fassung in `THE-VALUE`.
reqif4j liest nur `THE-VALUE`. **Eingebettete OLE-Objekte verschwinden** beim Round-Trip.

**4.7 Nicht gesetzte Werte werden zu explizit gesetzten Werten (nachgewiesen).**
Beim Import füllt `SpecObject`/`Specification` fehlende Werte mit dem Default auf. Fehlt auch
der Default, bleibt Boolean `false`, Integer `0` und Real `0.0`. Der Writer schreibt diese
Werte als echte `ATTRIBUTE-VALUE-*`. In der Testdatei hatte das Objekt kein `Flag` und kein `Real`.
Nach dem Export trägt es `THE-VALUE="false"` und `THE-VALUE="0.0"`.
Das ändert die Semantik: „nicht gesetzt“ ist nicht dasselbe wie „false“. Das Modell muss
*explizite Werte* und *effektive Werte (inkl. Default)* trennen.

**4.8 Werte sind nach `LONG-NAME` statt nach Definitions-ID abgelegt (nachgewiesen).**
`attributeValues` ist eine `HashMap<LONG-NAME, Wert>`. Tragen zwei Attributdefinitionen eines Typs
denselben Namen (bei DOORS-Exporten mit Modul- und Systemattributen keine Seltenheit), überschreibt
der zweite Wert den ersten. Im Test kam von `A` und `B` nur `B` an, und es wurde nur ein Wert
geschrieben. Die Reihenfolge der Werte geht ebenfalls verloren.

**4.9 Integer-Werte außerhalb des `int`-Bereichs brechen den Import ab (nachgewiesen).**
`DatatypeInteger` arbeitet mit `long`, `AttributeValueInteger` aber mit `Integer.parseInt`.
`THE-VALUE="99999999999"` führt zur `NumberFormatException`, und das ganze Dokument wird
nicht geladen. ReqIF verwendet `xsd:integer` (unbeschränkt). Richtig wäre `long` mit Fallback
auf `BigInteger`.

**4.10 Optionale Angaben führen zu `NullPointerException` (nachgewiesen).**
- `LONG-NAME` ist im XSD optional. `Datatype`-, `SpecType`-, `AttributeDefinition`- und
  `Specification`-Konstruktoren rufen aber `getNamedItem(LONG_NAME).getTextContent()` ohne
  Null-Prüfung auf.
- Ein `SPEC-OBJECT` mit unbekannter Typreferenz bricht in `readAttributeValues` ab (`specType` ist `null`).
- `SpecHierarchy` mit nicht auflösbarem `OBJECT`: `getSpecObjectID()` wirft eine NPE, und der Writer
  lässt das Pflichtelement `OBJECT` weg.

Bei einem fehlertoleranten Import gehören solche Fälle in `ValidationResult`-Meldungen, nicht in Abstürze.

**4.11 `.reqifz`: Anhänge außer `png`/`jpg`/`jpeg` gehen verloren (nachgewiesen).**
`ReqIFz` registriert nur diese drei Endungen, `ReqIFzWriter.write(ReqIFz, …)` packt nur sie
wieder ein. Aus einem Archiv mit `obj.ole`, `a.gif` und `b.png` blieb nach dem Neupacken nur
`b.png` übrig. Für einen vollständigen Export müssen **alle** Nicht-ReqIF-Einträge erhalten
bleiben, unabhängig von der Endung. Dasselbe gilt für den Bilderordner neben einer `.reqif`-Datei.

**4.12 Doppelte Identifier werden stillschweigend überschrieben.**
Das ist im README dokumentiert, aber ungelöst. Der Parser sollte Duplikate beim Einlesen erkennen und melden.

### P2: Vollständigkeit, API und Betrieb

**4.13 Keine API zum Bearbeiten geparster Dokumente.** *(behoben in 4.1)*
`AttributeValue` hat keine Setter, Hierarchien und Relationen lassen sich nicht umhängen,
Objekte nicht konsistent löschen (inklusive Referenzen in Hierarchien und Relationen).
Der Builder erzeugt nur *neue* Dokumente. Für „Import → Änderung → Export“ fehlt eine
Bearbeitungsschicht, die auch `LAST-CHANGE` automatisch fortschreibt.

**4.14 Builder unvollständig.** *(behoben in 2.2, 3.4 und 4.2)*
Es fehlen `RELATION-GROUP` und `RELATION-GROUP-TYPE`, `ALTERNATIVE-ID`, `DESC`, `LAST-CHANGE`,
`LONG-NAME` für Objekte, `REPOSITORY-ID`, `IS-EDITABLE`, Real-Attribute (`ACCURACY` …),
Tool-Extensions und Defaults für Pflicht-Header-Felder.

**4.15 Doppelte Parse-Logik.**
Das Einlesen der Attributwerte ist in `SpecObject.readAttributeValues` und im Konstruktor von
`Specification` doppelt vorhanden (ca. 100 Zeilen) und schon jetzt uneinheitlich,
z. B. `REAL` im Wert-Switch gegenüber `DOUBLE` im Default-Switch. Jede Erweiterung aus 4.5 bis 4.9
müsste doppelt umgesetzt werden. Vorher sollte eine gemeinsame Basisklasse
`Identifiable`/`SpecElementWithAttributes` (wie im ReqIF-Metamodell) eingeführt werden.

**4.16 Speicher und Dateisystem.** *(Dateisystem in 3.5, Laufzeit in 5.3; StAX zurückgestellt)*
- Rein DOM-basiert: Große Exporte (z. B. 100.000 Objekte mit XHTML) liegen vollständig im Speicher,
  der Writer baut zusätzlich ein zweites DOM auf. Für große Dateien wären StAX zum Lesen und
  Streaming zum Schreiben sinnvoll.
- `ReqIFz` entpackt ungefragt in ein Verzeichnis `<name>_unzipped` neben der Quelldatei (nachgewiesen).
  Das scheitert auf schreibgeschützten Laufwerken und wird nie aufgeräumt.
  Eine `InputStream`-Variante für `.reqifz` fehlt.
- Bild-`InputStream`s sind nur einmal lesbar. Hier wäre `byte[]` bzw. `Path`-Zugriff besser.

**4.17 Interoperabilität und Profile.** *(Prüfungen in 5.2)*
Die Klassifizierung nach Implementation Guide existiert. Es fehlen aber Prüfungen und Hilfen für die
Regeln des *ReqIF Implementation Guide* (ProSTEP iViP), z. B. die erlaubte XHTML-Teilmenge,
OLE-Konventionen (`object` mit Vorschaubild), `ReqIF.ForeignID`/`ReqIF.ChapterName` und den
Umgang mit `IS-SIMPLIFIED`.

**4.18 Tests und Dokumentation.**
- Es gibt keinen Test, der Writer-Ausgaben gegen das XSD prüft. Die 154 Tests vergleichen das Modell
  nach Round-Trip, nicht die XML-Vollständigkeit. Deshalb fielen 4.1 bis 4.11 bisher nicht auf.
- Es fehlen echte, anonymisierte Exporte (DOORS Classic/Next, Polarion, Jama, EA, ReqIF Studio)
  als Testdaten.
- Das README nennt Version `1.1.0`, die `pom.xml` steht auf `1.1.2-SNAPSHOT`. Der Writer ist
  noch als *experimental* gekennzeichnet.

## 5. Soll-Bild: Abnahmekriterien „vollständiger Import und Export“

1. **Schemakonformität:** Jede vom Writer erzeugte Datei besteht die Validierung gegen das
   OMG-XSD (ReqIF 1.2, `20110401`). Das wird in der CI geprüft.
2. **Verlustfreier Round-Trip:** `parse → write → parse` ergibt ein inhaltlich identisches Modell, und
   `parse → write` ergibt ein XML, das bis auf Formatierung und Attributreihenfolge dem
   Original entspricht (kanonischer XML-Vergleich). Getestet wird das mit einer „Alle-Merkmale“-Datei
   und mit echten Tool-Exporten.
3. **Fehlertoleranter Import:** Gültige, aber ungewöhnliche Dateien (optionale Attribute fehlen,
   große Zahlen, unbekannte Referenzen) werden geladen. Probleme landen als `ValidationIssue`
   statt als Exception.
4. **Vollständige Archive:** `.reqifz` behält alle Einträge (Dokumente und beliebige Anhänge),
   auch beim Neupacken.
5. **Bearbeitbarkeit:** Geparste Dokumente lassen sich über eine API ändern. `LAST-CHANGE` wird dabei
   automatisch gepflegt, und Referenzen bleiben konsistent.
6. **Interop-Nachweis:** Exportierte Dateien lassen sich in mindestens zwei Zielwerkzeugen
   (z. B. ReqIF Studio und DOORS oder Polarion) importieren.

## 6. Empfohlene Umsetzungsschritte

Aufwand: S ≈ bis 1 Tag, M ≈ 2–4 Tage, L ≈ 1–2 Wochen (eine Person, inklusive Tests).

| # | Arbeitspaket | Befunde | Aufwand |
|---|---|---|---|
| **Phase 1: Fundament und Messbarkeit** ||||
| 1.1 | XSD und XHTML-Module lokal einbinden (Resolver/Katalog), Testhelfer `assertSchemaValid(doc)`, CI-Test „Builder-Beispiel ist XSD-gültig“ (zunächst rot) | 4.4, 4.18 | S |
| 1.2 | Gemeinsame Basisklasse `Identifiable` (ID, LONG-NAME optional, DESC, LAST-CHANGE, ALTERNATIVE-ID) für alle Elemente. Wert-Parsing in einer Klasse zusammenführen | 4.5, 4.10, 4.15 | M |
| **Phase 2: Schemakonformer Export** ||||
| 2.1 | `LAST-CHANGE` lesen, modellieren und schreiben (Default: Zeitpunkt des Exports bzw. Erzeugungszeit) | 4.1 | S |
| 2.2 | Pflichtattribute: `MULTI-VALUED` immer, `OTHER-CONTENT` immer, Real `ACCURACY`/`MIN`/`MAX`, Header-Pflichtfelder mit sinnvollen Defaults | 4.2 | S |
| 2.3 | `ReqIFValidator` um Pflichtfelder und Wertebereiche erweitern | 4.3 | S–M |
| **Phase 3: Verlustfreier Import und Round-Trip** ||||
| 3.1 | Werte nach Definitions-ID statt nach LONG-NAME ablegen (Namenszugriff als Komfortmethode beibehalten), Reihenfolge erhalten | 4.8 | M (API-Bruch → Major-Version) |
| 3.2 | Explizite und effektive Werte trennen, keine Default-Werte mehr materialisieren | 4.7 | M |
| 3.3 | Integer als `long`/`BigInteger`, Real-Grenzen, XHTML-Defaults | 4.9, 4.5 | S |
| 3.4 | `THE-ORIGINAL-VALUE`/`IS-SIMPLIFIED`, `IS-EDITABLE`, `IS-TABLE-INTERNAL`, `EDITABLE-ATTS`, `REPOSITORY-ID`, `xml:lang` | 4.5, 4.6 | M |
| 3.5 | `.reqifz`: alle Einträge erhalten, `InputStream`-Konstruktor, Entpacken ins Temp-Verzeichnis oder in den Speicher | 4.11, 4.16 | M |
| 3.6 | Fehlertoleranter Import: NPE-Stellen in `ValidationIssue`s umwandeln, Duplikate melden | 4.10, 4.12 | M |
| 3.7 | Round-Trip-Test mit „Alle-Merkmale“-Datei und kanonischem XML-Vergleich | 4.18 | S |
| **Phase 4: Bearbeitung und Komfort** ||||
| 4.1 | Mutations-API (Werte setzen und entfernen, Objekte und Hierarchieknoten einfügen, verschieben und löschen, Relationen pflegen) mit automatischem `LAST-CHANGE` | 4.13 | L |
| 4.2 | Builder vervollständigen (Relationsgruppen, Metadaten, Real-Attribute, Tool-Extensions) | 4.14 | M |
| **Phase 5: Interoperabilität und Skalierung** ||||
| 5.1 | Echte Tool-Exporte als Testkorpus, Import-Tests in ReqIF Studio und Zielwerkzeugen | 4.17, 4.18 | M |
| 5.2 | Implementation-Guide-Prüfungen (XHTML-Teilmenge, OLE-Konventionen, Standardattribute) | 4.17 | M |
| 5.3 | Optional: StAX-basierter Reader/Writer für sehr große Dateien | 4.16 | L |

**Schnellster Weg zu einem brauchbaren Export:** Phase 1 und Phase 2 (ca. 1–1,5 Wochen) machen den
Export schemakonform. Mit Phase 3 (ca. 3–4 Wochen) wird der Round-Trip verlustfrei. Danach ist
das Zielbild „vollständiger Import und Export“ im engeren Sinn erreicht. Phase 4 und 5 machen die
Bibliothek bearbeitungsfähig und belegen die Interoperabilität mit realen Werkzeugen.

## Anhang: Reproduktion der Experimente

Das XSD ist nicht im Repository enthalten (Lizenz). Zum Nachstellen:

1. `reqif.xsd` von `https://www.omg.org/spec/ReqIF/20110401/reqif.xsd` laden und die
   `schemaLocation`-Importe (`xml.xsd`, `driver.xsd` und die XHTML-Module) lokal auflösen.
2. Das README-Beispiel (`ReqIFBuilder` … `build()`) mit
   `new ReqIFValidator().validateAgainstSchema(doc, lokalesXsd)` prüfen. Ergebnis:
   Fehler zu `LAST-CHANGE`, `MULTI-VALUED`, `OTHER-CONTENT` und zum Header.
3. Eine Datei mit `LAST-CHANGE`, `DESC`, `LONG-NAME` am `SPEC-OBJECT`, `REPOSITORY-ID`, `xml:lang`,
   `IS-EDITABLE`, `ACCURACY`, `THE-ORIGINAL-VALUE`, `IS-TABLE-INTERNAL`, `EDITABLE-ATTS` und
   ungesetzten Boolean- und Real-Attributen einlesen und mit `ReqIFWriter` schreiben. Die Ausgabe
   enthält keines dieser Merkmale, dafür aber `THE-VALUE="false"` und `THE-VALUE="0.0"`.
4. `THE-VALUE="99999999999"` in einem Integer-Attribut führt zur `NumberFormatException`.
   Ein fehlender `LONG-NAME` an einem Datentyp oder eine unbekannte `SPEC-OBJECT-TYPE-REF`
   führt zur `NullPointerException`.
5. Ein `.reqifz` mit `files/obj.ole`, `files/a.gif` und `files/b.png` einlesen und mit
   `ReqIFzWriter.write(ReqIFz, …)` neu packen: Das Ergebnis enthält nur `doc.reqif` und `files/b.png`.
