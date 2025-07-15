package org.jenkinsci.plugins.workflow.log.tee;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class TeeLogStorageTest extends TeeLogStorageTestBase {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File primaryFile;
    /**
     * By default, check all the log files have the same content
     */
    private boolean remoteCustomFileLogStorageContentSameAsPrimary = true;

    @Before
    public void primaryFile() throws Exception {
        primaryFile = tmp.newFile();
    }

    @Override
    protected LogStorage primaryStorage() {
        return FileLogStorage.forFile(primaryFile);
    }

    @Override
    public void remoting() throws Exception {
        remoteCustomFileLogStorageContentSameAsPrimary = false;
        super.remoting();
    }

    @After
    public void additional_checks() throws IOException {
        var primaryContent = getContent(primaryFile);
        var fileLogStorageContent = getContent(fileLogStorageFile);
        var remoteCustomFileLogStorageContent = getContent(remoteCustomFileLogStorageFile);
        assertThat(fileLogStorageContent, is(primaryContent));
        if (remoteCustomFileLogStorageContentSameAsPrimary) {
            assertThat(remoteCustomFileLogStorageContent, is(primaryContent));
        } else {
            assertThat(remoteCustomFileLogStorageContent, not(is(primaryContent)));
        }
    }

    private String getContent(File file) throws IOException {
        return Files.readString(file.toPath());
    }
}
