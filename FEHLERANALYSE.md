# Fehleranalyse ReqIF-Parser (reqif4j)

Stand: 2026-07-23 · Analysierte Dateien: alle 62 Java-Klassen unter `de/uni_stuttgart/ils/reqif4j/`

Die Fehler sind nach Schweregrad gruppiert. Datei- und Zeilenangaben beziehen sich auf den aktuellen Stand des Branches.

---

## 1. Kritisch: Multiselect-Enumerationen (vom Nutzer gemeldet)

### 1.1 Nur der erste Enum-Wert wird gelesen
- **`specification/SpecObject.java:173`** und identisch **`specification/Specification.java`** (ENUMERATION-Case):
  ```java
  String enumValueRef = ((Element)attribute).getElementsByTagName(ReqIFConst.VALUES)
                            .item(0).getChildNodes().item(1).getTextContent();
  ```
  Es wird ausschließlich `item(1)` gelesen — also der **erste** `ENUM-VALUE-REF`. Bei Multiselect-Enums (mehrere `<ENUM-VALUE-REF>`-Kindelemente unter `<VALUES>`) werden alle weiteren Werte **stillschweigend verworfen**. Das bestätigt auch der TODO-Kommentar in Zeile 174 („check how it is behaving if more than one enum value exits").

### 1.2 Datenmodell kann nur einen Wert speichern
- **`attributes/AttributeValueEnumeration.java`**: Die Klasse speichert den Wert als einzelnen `String` (geerbt von `AttributeValue`). Für Multiselect müsste hier eine `List<String>` (bzw. Liste von Enum-Wert-Objekten mit Name/Key/Other-Content) vorgehalten werden. Es gibt außerdem keinen Zugriff auf `KEY`/`OTHER-CONTENT` des aufgelösten Wertes.

### 1.3 Default-Werte von Enum-Attributen gehen verloren
- **`attributes/AttributeDefinition.java:51–58`**: Default-Werte werden nur über das XML-Attribut `THE-VALUE` gelesen. Bei Enumerationen besteht der `DEFAULT-VALUE` laut ReqIF-Spezifikation aber aus einem eingebetteten `ATTRIBUTE-VALUE-ENUMERATION` mit `ENUM-VALUE-REF`-Kindern → `defaultValue` bleibt `null`. Fehlt der Wert am SpecObject, entsteht eine `AttributeValueEnumeration` mit `value == null`.

### 1.4 Fehlende Null-Checks in der Enum-Auflösung (NPE-Gefahr)
- **`specification/SpecType.java:63–91`**: `getEnumValueKey()` und `getEnumValueOtherContent()` prüfen — anders als `getEnumValueName()` (Zeile 49) — **nicht** auf `attributeDefinition.getDataType() == null` → `NullPointerException`, sobald eine Attributdefinition einen unbekannten Datentyp referenziert.
- **`datatypes/DatatypeEnumeration.java:35–41`**: `getEnumValueKey()`/`getEnumValueOtherContent()` werfen NPE bei unbekannter ID; nur `getEnumValueName()` ist abgesichert.
- **`specification/SpecType.java:45–61`**: Bei nicht auflösbarem `enumValueRef` wird stillschweigend `""` geliefert — Fehler werden verschluckt statt gemeldet.

---

## 2. Kritisch: Bild-/Objekt-Konvertierung (vom Nutzer gemeldet)

### 2.1 `toString()` erzeugt ungültiges XHTML
- **`xhtml/XHTMLElementObject.java:28–34`**:
  ```java
  sb.append('<').append(tagName).append(" ").append(data).append('>');
  ```
  Ausgabe ist z. B. `<xhtml:object files\image.png>…</xhtml:object>` — es fehlen Attributname und Anführungszeichen (`data="…"`). Auch `type`, `width`, `height` werden nicht übernommen. Das Ergebnis ist kein gültiges (X)HTML und kann von keinem Renderer als Bild dargestellt werden.

### 2.2 Pfad-Umschreibung zerstört URIs
- **`xhtml/XHTMLElementObject.java:18, 24`** und **`attributes/AttributeValueXHTML.java:144`**: `data.replace("/", System.getProperty("file.separator"))` wandelt den URI-Pfad des `data`-Attributs in einen OS-Pfad um. Unter Windows wird aus `files/image.png` → `files\image.png`. Im XHTML-Output (2.1) sind Backslashes falsch, und der Schlüssel passt nicht mehr zu den Bild-Maps (siehe 2.5), die mit `/`-Pfaden aus dem Zip befüllt werden.

### 2.3 NPE bei `object` ohne `data`-Attribut; kein Fallback-Handling
- **`xhtml/XHTMLElementObject.java:18, 24`**: `getNamedItem("data")` wird ohne Null-Check dereferenziert. Die ReqIF-Spezifikation schreibt zudem für Nicht-PNG-Objekte ein verschachteltes Fallback-`object` (PNG-Alternative) vor — dieses wird nicht ausgewertet.

### 2.4 Namespace-Präfixe: Bilder (und alles andere XHTML) werden gar nicht erst erkannt
- **`xhtml/XHTMLElement.java:52–103`**: Der `switch` vergleicht `getNodeName()` (nach Entfernen von Ziffern) mit **unpräfixierten** Konstanten (`"object"`, `"p"`, `"div"` …). Reale ReqIF-Dateien serialisieren XHTML aber mit Namespace-Präfix (`reqif-xhtml:object`, `xhtml:p` …). Da der `DocumentBuilderFactory` (in `ReqIFDocument`) **nicht namespace-aware** konfiguriert ist, enthält `getNodeName()` das Präfix → kein Case matcht → alle Elemente landen im `default`-Zweig als generische `XHTMLNode`, deren `toString()` nur ein leeres Tag-Paar ausgibt. **Bilder und Textinhalte gehen komplett verloren.**
- Gleiches Problem in **`attributes/AttributeValueXHTML.java:27, 48`**: `getElementsByTagName(XHTML.DIV)` („div") findet `xhtml:div` nicht → `item(0) == null` → NPE im Konstruktor von `XHTMLElementDiv`.

### 2.5 Bild-Zuordnung über Dateinamen ist inkonsistent und liefert `null`
- **`reqif/ReqIFFile.java:44–46`**: `getPicturesInputStreams(name)` sucht mit `name.split("\\.")[0]` (z. B. `"spec"`), die Map wird aber sowohl in `ReqIF.java:52` als auch in `ReqIFz.java:64–66` mit dem **vollen Dateinamen** (`"spec.reqif"`) befüllt → Lookup liefert **immer `null`**.
- **`reqif/ReqIFz.java:53`**: `reqifBaseName` wird berechnet, aber nie verwendet; stattdessen bekommen **alle** ReqIF-Dokumente im Archiv dieselbe komplette Bild-Map zugeordnet — keine echte Zuordnung Bild ↔ Dokument.

### 2.6 Fragile Pfad-Logik beim Bilderordner
- **`reqif/ReqIF.java:37`**: `this.path.split("\\.")[0]` soll die Dateierweiterung entfernen, bricht aber bei jedem Punkt im Pfad (z. B. `./data/v1.2/spec.reqif` → `"/data/v1"`) — der Bilderordner wird dann nicht gefunden.
- **`reqif/ReqIF.java:31`**, **`reqif/ReqIFDocument.java:50, 74`**: Dateiname wird per `lastIndexOf(System.getProperty("file.separator"))` extrahiert — schlägt fehl bei `/`-Pfaden unter Windows bzw. gemischten Separatoren.

### 2.7 Ressourcen-Leaks und Zip-Sicherheit
- **`reqif/ReqIF.java:47`**, **`reqif/ReqIFz.java:56`**: Für jedes Bild werden `FileInputStream`s geöffnet und **nie geschlossen** (Leak; unter Windows bleiben Dateien gesperrt).
- **`reqif/ReqIFz.java:30`**: `new File(destDir, zipEntry.getName())` ohne Kanonisierungs-Check → **Zip-Slip-Schwachstelle** (Einträge wie `../../x` schreiben außerhalb des Zielordners).

### 2.8 Tote Bild-Extraktion in der „Deconstruction"
- **`attributes/AttributeValueXHTML.java:44–69, 141–145`**: `deconstructXHTML()` übergibt rohe Node-Namen (`"xhtml:p"`, `"xhtml:object"` …), `decostructXHTMLElement()` vergleicht aber mit `"P"`, `"TBL"`, `"L"`, `"H"`, `"OBJ"`. Der Code, der die Namen auf diese Token abbildete, ist **auskommentiert** (Zeilen 54–66) → der `OBJ`-Zweig (Bildpfad-Extraktion) und alle anderen Zweige werden **nie** erreicht; die Elementliste enthält nur leere Inhaltslisten.

---

## 3. Hoch: Crashes / Datenverlust bei validen ReqIF-Dateien

1. **`reqif/ReqIFCoreContent.java:133`**: Im `default`-Zweig wird `this.dataTypes.put(null, …)` aufgerufen — Key ist `null` statt `dataTypeID`. Unbekannte Datentypen sind damit nicht referenzierbar; Folge sind `ExceptionSpecObject`/NPEs an anderer Stelle.
2. **`reqif/ReqIFCoreContent.java:105–111`**: `MIN`/`MAX` (Integer) und `MAX-LENGTH` (String) werden ohne Null-Check gelesen — laut ReqIF-Spezifikation sind diese Attribute optional → NPE.
3. **`datatypes/DatatypeInteger.java:27–28`**: `Integer.parseInt()` für `MIN`/`MAX` — ReqIF erlaubt `xsd:integer` (beliebig groß); DOORS exportiert häufig Werte im Long-Bereich → `NumberFormatException`.
4. **`attributes/AttributeValueInteger.java:11`** / **`AttributeValueDouble.java:10`**: `parseInt("")`/`parseDouble("")` werfen `NumberFormatException`. `SpecObject` übergibt bei fehlendem `THE-VALUE` genau `""` (INTEGER-Case, Zeile 160). Der REAL-Case nutzt dagegen `"0.0"` — inkonsistent; der INTEGER-Pfad crasht.
5. **`specification/SpecHierarchy.java:59–66`**: `getXHTMLContent()` castet `attributeValue.getValue()` auf `AttributeValueXHTMLElementList`. `AttributeValueXHTML.getValue()` ist aber überschrieben und liefert einen **String** → garantierte `ClassCastException`, sobald die Methode aufgerufen wird.
6. **`specification/Specification.java`**: 
   - Der Werte-Switch kennt keine `DATE`-, `REAL`-, `DOUBLE`-Cases (anders als `SpecObject`) → diese Attributwerte werden ignoriert; der Default-Werte-Switch kennt ebenfalls kein `DATE`/`DOUBLE` → Attribute fehlen anschließend komplett.
   - Im Default-Werte-Zweig fehlt der Null-Check auf `getDataType()` (in `SpecObject:212` vorhanden) → NPE statt aussagekräftiger `ExceptionSpecObject`.
7. **`reqif/ReqIFDocument.java:65–68, 86–89, 109–112`**: Bei Parse-Fehlern wird `System.exit(1)` aufgerufen — eine Bibliothek darf die JVM des Aufrufers nicht beenden; Fehler werden zudem nicht propagiert (deklariertes `FileNotFoundException` wird nie geworfen).
8. **Systemisch — Whitespace-Index-Annahme `item(1)`**: An vielen Stellen wird das zweite Kind (`.getChildNodes().item(1)`) als „erstes Element" angenommen. Das funktioniert nur bei pretty-printed XML mit Whitespace-Textknoten; bei minifizierten Dateien wird das falsche Kind gelesen oder es kommt zur NPE. Betroffen u. a.: `DatatypeEnumeration.java:52, 59, 61`, `AttributeDefinition.java:48, 54`, `SpecObject.java:142, 173`, `Specification.java` (DEFINITION/ENUM-Refs), `SpecType.java:103–105`, `AttributeValueXHTML.java:235` (`decontructTable`).
9. **`reqif/ReqIFHeader.java:70`**: `split("Created by: ")[1]` → `ArrayIndexOutOfBoundsException`, wenn der `COMMENT` diesen (tool-spezifischen) Text nicht enthält. Zeile 73–74: `date[2]` → AIOOBE bei unerwartetem `CREATION-TIME`-Format.

---

## 4. Mittel: Logik- und Robustheitsfehler

1. **`xhtml/XHTMLElement.java:15–17`**: `hasChildren()` ist invertiert — gibt `children.isEmpty()` zurück statt `!children.isEmpty()`.
2. **`specification/SpecObject.java:117–130`**: Typklassifizierung (REQ/SUB-REQ/HEADLINE/TEXT) per Substring-Heuristik auf dem `LONG-NAME` des SpecTypes — sprach- und toolabhängig, liefert bei fremden Profilen falsche Ergebnisse. Ebenso `isReq()`/`isSubReq()` (Namens-Heuristik auf Attributnamen).
3. **`specification/SpecRelation.java:22`**: `this.type` wird mit der **Referenz-ID** des Relationstyps überschrieben, obwohl `type` in der Basisklasse die Semantik REQ/HEADLINE/TEXT hat (`getType()`/`isReq()` etc. liefern damit Unsinn). Attributwerte der Relation werden gar nicht geparst.
4. **`specification/SpecObject.java:46`** / **`Specification.java` (`getAttribute`, `getDescription`)**: NPE bei unbekanntem Attributnamen; `getDescription()` setzt hart ein Attribut „Description" voraus.
5. **`reqif/ReqIFDocument.java`**: `DocumentBuilderFactory` ohne Schutz gegen XXE/Entity-Expansion (Sicherheitslücke bei fremden Dateien) und ohne `setNamespaceAware(true)` (Ursache von 2.4).
6. **`attributes/AttributeValueXHTML.java:181–184`** (`list()`): Im `var`-Zweig wird versehentlich `child` (das `li`) statt `listChild` (das `var`) ausgewertet; verschachtelte Listen erzeugen zudem unbalancierte `"/L"`-Marker (nur schließend, nie öffnend im Ergebnis).
7. **`attributes/AttributeValueXHTML.java:209–245`** (`decontructTable`): Nur `item(1)` einer Zelle wird gelesen — Zellen mit mehreren Kindelementen (Formatierung, Bilder, Umbrüche) verlieren Inhalt; `thead`/`th` werden nicht von `tbody`/`td` unterschieden.
8. **`xhtml/XHTMLNode.toString()` / alle Element-`toString()`**: Sämtliche XML-Attribute (`style`, `colspan`, `align` …) gehen bei der Ausgabe verloren; `XHTMLElementText` escaped Sonderzeichen (`<`, `&`) nicht; `<br>` wird als `<br></br>` ausgegeben.
9. **`xhtml/XHTMLElement.java:52`**: `replaceAll("[0-9]","")` soll `h1`–`h6` auf `h` mappen, entfernt aber Ziffern aus **allen** Tag-Namen — ein Tag wie `h2` in Kombination mit Präfixen oder exotischen Namen wird falsch klassifiziert.
10. **`reqif/ReqIFz.java:53`**: `zipEntry.getName().split("\\.")[0]` bricht bei Punkten im Verzeichnis-/Dateinamen (gleiches Muster wie 2.6).

---

## 5. Niedrig: Code-Qualität / Kosmetik

1. **`datatypes/DatatypeBoolean.java` / `DatatypeXHTML.java` / `DatatypeEnumeration.java`**: private Felder `id`/`name` verschatten die Basisklasse (inkl. redundanter Getter).
2. **`datatypes/DatatypeDate.java:5`**: unbenutzter (und sinnfreier) Import `javax.xml.crypto.Data`.
3. **`specification/ExceptionSpecObject.java`**: Meldungstext ohne Trennzeichen/Zeilenumbrüche zwischen ID/Name/Typ.
4. **`reqif/ReqIFCoreContent.java:128`**: `case A, B ->`-Syntax (Java 14+) inmitten von sonst altem Stil — Build bricht auf älteren Toolchains.
5. **`attributes/AttributeValueDate.java`**: Datum wird als roher String gespeichert, keine Validierung/Parsing (`getValue()` castet ggf. `null`).
6. **`reqif/ReqIFHeader.java:77`**: `replace("_Template", "")` — tool-spezifischer Hack im generischen Parser.
7. Große Mengen auskommentierten Codes (`AttributeValueXHTML`, `Specification`, `ReqIFHeader`) und Tippfehler in Methodennamen (`decostructXHTMLElement`, `decontructTable`).

---

## Empfohlene Reihenfolge für die Behebung

1. **Namespace-Handling** (2.4 + 5.5): `setNamespaceAware(true)` + Vergleich über `getLocalName()` — Voraussetzung dafür, dass XHTML/Bilder überhaupt ankommen.
2. **Multiselect-Enums** (1.1–1.3): Schleife über alle `ENUM-VALUE-REF`s, `AttributeValueEnumeration` auf Listen umstellen, Enum-Default-Werte korrekt lesen.
3. **Bild-Pipeline** (2.1–2.3, 2.5–2.7): korrektes `data="…"`-Attribut, keine Separator-Ersetzung, konsistente Map-Keys, Streams schließen, Zip-Slip-Check.
4. **Crash-Fixes** (Abschnitt 3): Null-Checks, `put(null, …)`-Fix, leere Strings beim Zahlparsen, `System.exit` entfernen, `getXHTMLContent()`-Cast.
5. Danach die Punkte aus Abschnitt 4/5.
