package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFParseException;

/**
 * Bug: parse failures called System.exit(1), killing the host JVM instead of
 * reporting an error. The parser was also vulnerable to XXE because external
 * entities and DOCTYPEs were allowed.
 */
class ParserHardeningTest {

	@Test
	void malformedXmlThrowsInsteadOfKillingTheJvm(@TempDir Path tempDir) throws Exception {
		Path file = TestFixtures.write(tempDir, "broken.reqif", "<REQ-IF><CORE-CONTENT>");

		// If System.exit were still called, the test JVM would die here.
		assertThrows(ReqIFParseException.class, () -> new ReqIF(file.toString()));
	}

	@Test
	void documentWithoutCoreContentThrows(@TempDir Path tempDir) throws Exception {
		Path file = TestFixtures.write(tempDir, "empty.reqif", "<?xml version=\"1.0\"?><REQ-IF></REQ-IF>");

		assertThrows(ReqIFParseException.class, () -> new ReqIF(file.toString()));
	}

	@Test
	void doctypeIsRejectedToPreventXxe(@TempDir Path tempDir) throws Exception {
		String xxe = """
				<?xml version="1.0"?>
				<!DOCTYPE REQ-IF [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				<REQ-IF><CORE-CONTENT>&xxe;</CORE-CONTENT></REQ-IF>
				""";
		Path file = TestFixtures.write(tempDir, "xxe.reqif", xxe);

		assertThrows(ReqIFParseException.class, () -> new ReqIF(file.toString()));
	}

	@Test
	void validFixtureStillParses(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());

		assertNotNull(reqif.getReqIFHeader());
		assertNotNull(reqif.getReqIFCoreContent());
	}
}
