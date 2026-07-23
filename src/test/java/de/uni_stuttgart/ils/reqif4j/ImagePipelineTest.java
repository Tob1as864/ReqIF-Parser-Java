package de.uni_stuttgart.ils.reqif4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTML;
import de.uni_stuttgart.ils.reqif4j.attributes.AttributeValueXHTMLElementList;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIF;
import de.uni_stuttgart.ils.reqif4j.reqif.ReqIFz;
import de.uni_stuttgart.ils.reqif4j.specification.SpecObject;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLElementObject;
import de.uni_stuttgart.ils.reqif4j.xhtml.XHTMLNode;

/**
 * Bugs: xhtml object elements were rendered without a data attribute name
 * (invalid markup), URI slashes were replaced by the platform separator, a
 * missing data attribute caused an NPE, the picture lookup keys never matched,
 * and zip extraction was vulnerable to zip-slip.
 */
class ImagePipelineTest {

	private XHTMLElementObject parseObjectFromFixture(Path tempDir, String fixture) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.write(tempDir, "test.reqif", fixture).toString());
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");
		AttributeValueXHTML description = (AttributeValueXHTML) so1.getAttributes().get("Description");
		for (XHTMLNode node : description.getDivValue().getChildren()) {
			if (node instanceof XHTMLElementObject) {
				return (XHTMLElementObject) node;
			}
		}
		return null;
	}

	@Test
	void objectToStringProducesValidDataAttribute(@TempDir Path tempDir) throws Exception {
		XHTMLElementObject object = parseObjectFromFixture(tempDir, TestFixtures.REQIF_FIXTURE);

		assertNotNull(object);
		String html = object.toString();
		assertTrue(html.contains("data=\"files/image.png\""),
				"object output must contain a valid data attribute, got: " + html);
		assertTrue(html.contains("type=\"image/png\""),
				"object output must keep the type attribute, got: " + html);
		assertFalse(html.contains("\\"), "URI must keep forward slashes on every platform");
	}

	@Test
	void objectDataKeepsUriSlashes(@TempDir Path tempDir) throws Exception {
		XHTMLElementObject object = parseObjectFromFixture(tempDir, TestFixtures.REQIF_FIXTURE);

		assertEquals("files/image.png", object.getData(),
				"getData must return the original URI with forward slashes");
	}

	@Test
	void objectWithoutDataAttributeDoesNotThrow(@TempDir Path tempDir) throws Exception {
		String fixture = TestFixtures.REQIF_FIXTURE.replace(
				" data=\"files/image.png\" type=\"image/png\"", "");

		XHTMLElementObject object = parseObjectFromFixture(tempDir, fixture);

		assertNotNull(object);
		assertNull(object.getData());
		assertNotNull(object.toString());
	}

	@Test
	void deconstructedElementListContainsImagePath(@TempDir Path tempDir) throws Exception {
		ReqIF reqif = new ReqIF(TestFixtures.writeDefaultFixture(tempDir).toString());
		SpecObject so1 = reqif.getReqIFCoreContent().getSpecObject("so-1");
		AttributeValueXHTML description = (AttributeValueXHTML) so1.getAttributes().get("Description");

		AttributeValueXHTMLElementList elements = description.getElementList();
		boolean foundImage = false;
		for (int i = 0; i < elements.size(); i++) {
			if (elements.getElementType(i).equals("OBJ")) {
				assertEquals("files/image.png", elements.getElementContentList(i).get(0));
				foundImage = true;
			}
		}
		assertTrue(foundImage, "element list must contain the OBJ entry with the image path");
	}

	@Test
	void reqifzPicturesAreAssociatedWithTheirDocument(@TempDir Path tempDir) throws Exception {
		Path zipFile = createReqIFz(tempDir);

		try (ReqIFz reqifz = new ReqIFz(zipFile.toString())) {
			assertEquals(1, reqifz.getReqIFDocumentsCount());

			Map<String, InputStream> pictures = reqifz.getPicturesInputStreams("test.reqif");
			assertNotNull(pictures, "picture lookup by document name must work");
			assertTrue(pictures.containsKey("files/image.png"), "pictures must be keyed like the object data URI");

			// lookup exactly as an xhtml object references the image
			assertNotNull(reqifz.getPictureInputStream("test.reqif", "files/image.png"));
			// legacy lookup without extension must also work
			assertNotNull(reqifz.getPicturesInputStreams("test"));
		}
	}

	@Test
	void zipSlipEntriesAreRejected(@TempDir Path tempDir) throws Exception {
		Path zipFile = tempDir.resolve("evil.reqifz");
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
			zos.putNextEntry(new ZipEntry("../evil.png"));
			zos.write(new byte[] {1, 2, 3});
			zos.closeEntry();
		}

		assertThrows(IOException.class, () -> new ReqIFz(zipFile.toString()),
				"entries escaping the extraction directory must be rejected");
	}

	private static Path createReqIFz(Path dir) throws IOException {
		Path zipFile = dir.resolve("archive.reqifz");
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
			zos.putNextEntry(new ZipEntry("test.reqif"));
			zos.write(TestFixtures.REQIF_FIXTURE.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry("files/image.png"));
			zos.write(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
			zos.closeEntry();
		}
		return zipFile;
	}
}
