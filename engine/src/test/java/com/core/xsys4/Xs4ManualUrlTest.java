package com.core.xsys4;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class Xs4ManualUrlTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File base;
    private File manual;
    private File secret;
    private File manualX;

    @Before public void setUp() throws IOException {
        base = temp.newFolder("game").getCanonicalFile();
        manual = new File(base, "Manual");
        assertTrue(new File(manual, "sub").mkdirs());
        write(new File(manual, "index.html"));
        write(new File(manual, "sub/page.html"));
        secret = write(new File(base, "secret.html"));
        manualX = new File(base, "ManualX");
        assertTrue(manualX.mkdirs());
        write(new File(manualX, "index.html"));
    }

    @Test public void allowsIndexAndNestedPagesInsideManual() {
        assertTrue(allowed(fileUrl(new File(manual, "index.html"))));
        assertTrue(allowed(fileUrl(new File(manual, "sub/page.html"))));
        assertTrue(allowed("file://" + new File(manual, "index.html").getPath() + "?anchor#top"));
    }

    @Test public void rejectsNonFileSchemesAndRelativeOrEmptyUrls() {
        String inside = new File(manual, "index.html").getPath();
        assertFalse(allowed("http://127.0.0.1/Manual/index.html"));
        assertFalse(allowed("https://example.com/" + inside));
        assertFalse(allowed("content://com.example/" + inside));
        assertFalse(allowed("about:blank"));
        assertFalse(allowed("javascript:void(0)"));
        assertFalse(allowed("data:text/html,hi"));
        assertFalse(allowed("index.html"));
        assertFalse(allowed(""));
        assertFalse(Xs4ManualActivity.isAllowedManualUrl(manual, null));
        assertFalse(Xs4ManualActivity.isAllowedManualUrl(null, fileUrl(new File(manual, "index.html"))));
    }

    @Test public void rejectsFileUrlsWithAuthority() {
        assertFalse(allowed("file://localhost" + new File(manual, "index.html").getPath()));
        assertFalse(allowed("file://evil.example" + new File(manual, "index.html").getPath()));
    }

    @Test public void rejectsDotDotTraversalIncludingPercentEncoded() {
        assertFalse(allowed(fileUrl(manual) + "/../secret.html"));
        assertFalse(allowed(fileUrl(manual) + "/%2e%2e/secret.html"));
        assertFalse(allowed(fileUrl(manual) + "/..%2fsecret.html"));
    }

    @Test public void rejectsManualDirectoryItselfAndOutsideFiles() {
        assertFalse(allowed(fileUrl(manual)));
        assertFalse(allowed(fileUrl(secret)));
    }

    @Test public void rejectsSimilarPrefixDirectory() {
        assertFalse(allowed(fileUrl(new File(manualX, "index.html"))));
    }

    @Test public void rejectsSymlinkedFileEscapingManual() throws IOException {
        Files.createSymbolicLink(new File(manual, "escape.html").toPath(), secret.toPath());
        assertFalse(allowed(fileUrl(new File(manual, "escape.html"))));
    }

    @Test public void rejectsSymlinkedDirectoryEscapingManual() throws IOException {
        Files.createSymbolicLink(new File(manual, "out").toPath(), base.toPath());
        assertFalse(allowed(fileUrl(new File(manual, "out/secret.html"))));
    }

    @Test public void allowsSymlinkThatStaysInsideManual() throws IOException {
        Files.createSymbolicLink(new File(manual, "alias.html").toPath(),
                new File(manual, "index.html").toPath());
        assertTrue(allowed(fileUrl(new File(manual, "alias.html"))));
    }

    @Test public void caseVariantIsAllowedOnCaseInsensitiveFilesystem() {
        Assume.assumeTrue("需要大小写不敏感文件系统", caseInsensitiveFilesystem());
        File variant = new File(base, "manual/index.html");
        assertTrue(allowed(fileUrl(variant)));
    }

    @Test public void caseVariantIsRejectedOnCaseSensitiveFilesystem() {
        Assume.assumeFalse("需要大小写敏感文件系统", caseInsensitiveFilesystem());
        File variant = new File(base, "manual/index.html");
        assertFalse(variant.exists());
        assertFalse(allowed(fileUrl(variant)));
    }

    @Test public void isInsideManualDirRejectsRootAndAcceptsDescendant() throws IOException {
        assertFalse(Xs4ManualActivity.isInsideManualDir(manual, manual));
        assertTrue(Xs4ManualActivity.isInsideManualDir(manual, new File(manual, "index.html")));
    }

    private boolean allowed(String url) {
        return Xs4ManualActivity.isAllowedManualUrl(manual, url);
    }

    /** 以已存在的 Manual 目录的大小写变体探测：能找到即为大小写不敏感。 */
    private boolean caseInsensitiveFilesystem() {
        return new File(base, "manual/index.html").exists();
    }

    private static String fileUrl(File file) {
        return "file://" + file.getAbsolutePath();
    }

    private static File write(File file) throws IOException {
        Files.write(file.toPath(), new byte[] { 1 });
        return file;
    }
}
