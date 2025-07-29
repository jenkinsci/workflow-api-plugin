package org.jenkinsci.plugins.workflow.log.tee;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;
import org.junit.Test;

/**
 * Tests related to potential exceptions when using TeeLogStorage.
 */
public class TeeOutputStreamTest {

    private static final Logger LOGGER = Logger.getLogger(TeeOutputStreamTest.class.getName());

    private File fileLogStorageFileA;
    private File fileLogStorageFileB;
    private File remoteCustomFileLogStorageFile;
    private static final String CONTENT = "Hello World";

    /**
     * Test {@link TeeOutputStream#write(int)}
     */
    @Test
    public void fails_write_char() throws Exception {
        var out = multipleFails(new BufferedOutputStream(OutputStream.nullOutputStream()) {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Exception for test");
            }
        });
        try {
            out.write(0);
            fail();
        } catch (IOException e) {
            assertException(e);
        }
    }

    /**
     *Test {@link TeeOutputStream#write(byte[], int, int)}
     */
    @Test
    public void fails_write_string() throws Exception {
        var out = multipleFails(new BufferedOutputStream(OutputStream.nullOutputStream()) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("Exception for test");
            }
        });
        try {
            out.write(new byte[] {0}, 0, 1);
            fail();
        } catch (IOException e) {
            assertException(e);
        }
    }

    /**
     * Test {@link TeeOutputStream#flush()}
     */
    @Test
    public void fails_flush() throws Exception {
        var out = multipleFails(new BufferedOutputStream(OutputStream.nullOutputStream()) {
            @Override
            public void flush() throws IOException {
                throw new IOException("Exception for test");
            }
        });
        try {
            out.flush();
            fail();
        } catch (IOException e) {
            assertException(e);
        }
    }

    /**
     * Test {@link TeeOutputStream#close()}
     */
    @Test
    public void fails_close() throws Exception {
        var out = multipleFails(new BufferedOutputStream(OutputStream.nullOutputStream()) {
            @Override
            public void close() throws IOException {
                throw new IOException("Exception for test");
            }
        });
        try {
            out.close();
            fail();
        } catch (IOException e) {
            assertException(e);
        }
    }

    private TeeOutputStream multipleFails(OutputStream failingOutputStream) {
        return new TeeOutputStream(
                failingOutputStream,
                new OutputStream[] {failingOutputStream, OutputStream.nullOutputStream()});
    }
    
    private void assertException(IOException e) {
        assertThat(e.getMessage(), is("Exception for test"));
        assertThat(e.getSuppressed().length, is(1));
        assertThat(e.getSuppressed()[0].getMessage(), is("Exception for test"));
    }
}
