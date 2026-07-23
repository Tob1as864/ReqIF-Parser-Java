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
