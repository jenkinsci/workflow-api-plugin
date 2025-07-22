package org.jenkinsci.plugins.workflow.log.tee;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

/**
 * Tests related to potential exceptions when using TeeLogStorage.
 */
public class TeeOutputStreamTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static LoggerRule logging = new LoggerRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File fileLogStorageFileA;
    private File fileLogStorageFileB;
    private File remoteCustomFileLogStorageFile;
    private static final String CONTENT = "Hello World";

    @Before
    public void before() throws Exception {
        fileLogStorageFileA = tmp.newFile();
        fileLogStorageFileB = tmp.newFile();
        remoteCustomFileLogStorageFile = tmp.newFile();
    }

    /**
     * Test {@link TeeOutputStream#write(int)}
     */
    @Test
    public void primary_fails_write_char() throws Exception {
        char content = 'a';
        var ls = primaryFails(new BufferedOutputStream(new FileOutputStream(remoteCustomFileLogStorageFile)) {
            @Override
            public void write(int b) throws IOException {
                throw new IOException();
            }
        });
        try (TeeBuildListener overall = (TeeBuildListener) ls.overallListener()) {
            overall.getLogger().write(content);
        } catch (IOException e) {
            fail();
        } finally {
            assertCustomFileEmpty(String.valueOf(content));
        }
    }

    /**
     *Test {@link TeeOutputStream#write(byte[], int, int)}
     */
    @Test
    public void primary_fails_write_string() throws Exception {
        var ls = primaryFails(new BufferedOutputStream(new FileOutputStream(remoteCustomFileLogStorageFile)) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException();
            }
        });
        try (TeeBuildListener overall = (TeeBuildListener) ls.overallListener()) {
            overall.getLogger().print(CONTENT);
        } catch (IOException e) {
            fail();
        } finally {
            assertCustomFileEmpty(CONTENT);
        }
    }

    /**
     * Test {@link TeeOutputStream#flush()}
     */
    @Test
    public void primary_fails_flush() throws Exception {
        var ls = primaryFails(new BufferedOutputStream(new FileOutputStream(remoteCustomFileLogStorageFile)) {
            @Override
            public void flush() throws IOException {
                throw new IOException("Exception for test");
            }
        });

        try (TeeBuildListener overall = (TeeBuildListener) ls.overallListener()) {
            overall.getLogger().print(CONTENT);
        } catch (IOException e) {
            assertThat(e.getMessage(), is("Exception for test"));
        } finally {
            assertCustomFileEmpty(CONTENT);
        }
    }

    /**
     * Test {@link TeeOutputStream#close()}
     */
    @Test
    public void primary_fails_close() throws Exception {
        var ls = primaryFails(new BufferedOutputStream(new FileOutputStream(remoteCustomFileLogStorageFile)) {
            @Override
            public void close() throws IOException {
                throw new IOException("Exception for test");
            }
        });
        try (TeeBuildListener overall = (TeeBuildListener) ls.overallListener()) {
            overall.getLogger().print(CONTENT);
        } catch (IOException e) {
            assertThat(e.getMessage(), is("Exception for test"));
        } finally {
            assertCustomFileEmpty(CONTENT);
        }
    }

    /**
     * Test {@link TeeOutputStream#write(int)}
     */
    @Test
    public void secondary_fails_write_char() throws Exception {
        char content = 'a';
        var ls = secondaryFails(new BufferedOutputStream(new FileOutputStream(remoteCustomFileLogStorageFile)) {
            @Override
            public void write(int b) throws IOException {
                throw new IOException();
            }
        });
        try (TeeBuildListener overall = (TeeBuildListener) ls.overallListener()) {
            overall.getLogger().write(content);
        } catch (IOException e) {
            fail();
        } finally {
            assertCustomFileEmpty(String.valueOf(content));
        }
    }

    /**
     *Test {@link TeeOutputStream#write(byte[], int, int)}
     */
    @Test
    public void secondary_fails_write_string() throws Exception {
        var ls = secondaryFails(new BufferedOutputStream(new FileOutputStream(remoteCustomFileLogStorageFile)) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException();
            }
        });
        try (TeeBuildListener overall = (TeeBuildListener) ls.overallListener()) {
            overall.getLogger().print(CONTENT);
        } catch (IOException e) {
            fail();
        } finally {
            assertCustomFileEmpty(CONTENT);
        }
    }

    /**
     * Test {@link TeeOutputStream#flush()}
     */
    @Test
    public void secondary_fails_flush() throws Exception {
        var ls = secondaryFails(new BufferedOutputStream(new FileOutputStream(remoteCustomFileLogStorageFile)) {
            @Override
            public void flush() throws IOException {
                throw new IOException("Exception for test");
            }
        });

        try (TeeBuildListener overall = (TeeBuildListener) ls.overallListener()) {
            overall.getLogger().print(CONTENT);
        } catch (IOException e) {
            assertThat(e.getMessage(), is("Exception for test"));
        } finally {
            assertCustomFileEmpty(CONTENT);
        }
    }

    /**
     * Test {@link TeeOutputStream#close()}
     */
    @Test
    public void secondary_fails_close() throws Exception {
        var ls = secondaryFails(new BufferedOutputStream(new FileOutputStream(remoteCustomFileLogStorageFile)) {
            @Override
            public void close() throws IOException {
                throw new IOException("Exception for test");
            }
        });

        try (TeeBuildListener overall = (TeeBuildListener) ls.overallListener()) {
            overall.getLogger().print(CONTENT);
        } catch (IOException e) {
            assertThat(e.getMessage(), is("Exception for test"));
        } finally {
            assertCustomFileEmpty(CONTENT);
        }
    }

    private TeeLogStorage primaryFails(OutputStream failingOutputStream) {
        return new TeeLogStorage(
                RemoteCustomFileLogStorage.forFile(remoteCustomFileLogStorageFile, failingOutputStream),
                FileLogStorage.forFile(fileLogStorageFileA),
                FileLogStorage.forFile(fileLogStorageFileB));
    }

    private TeeLogStorage secondaryFails(OutputStream failingOutputStream) {
        return new TeeLogStorage(
                FileLogStorage.forFile(fileLogStorageFileA),
                RemoteCustomFileLogStorage.forFile(remoteCustomFileLogStorageFile, failingOutputStream),
                FileLogStorage.forFile(fileLogStorageFileB));
    }

    private void assertCustomFileEmpty(String content) throws IOException {
        var remoteCustomFileLogStorageContent = getContent(remoteCustomFileLogStorageFile);
        var fileLogStorageFileAContent = getContent(fileLogStorageFileA);
        var fileLogStorageFileBContent = getContent(fileLogStorageFileB);

        assertThat(remoteCustomFileLogStorageContent, emptyString());
        assertThat(fileLogStorageFileAContent, is(content));
        assertThat(fileLogStorageFileBContent, is(content));
    }

    private String getContent(File file) throws IOException {
        return Files.readString(file.toPath());
    }
}
