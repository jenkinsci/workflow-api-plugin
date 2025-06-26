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
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TeeLogStorageTest extends TeeLogStorageTestBase {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File primaryFile;
    /**
     * By default, check all the log files have the same content
     */
    private boolean secondary1SameAsPrimary = true;

    private boolean secondary2SameAsPrimary = true;

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
        secondary2SameAsPrimary = false;
        super.remoting();
    }

    @Override
    @Test
    public void mangledLines() throws Exception {
        // primary lines are tweaked
        secondary1SameAsPrimary = false;
        secondary2SameAsPrimary = false;
        super.mangledLines();
    }

    @After
    public void additional_checks() throws IOException {
        if (secondary1SameAsPrimary) {
            assertThat(getContent(secondaryFile1), is(getContent(primaryFile)));
        } else {
            assertThat(getContent(secondaryFile1), not(is(getContent(primaryFile))));
        }
        if (secondary2SameAsPrimary) {
            assertThat(getContent(secondaryFile2), is(getContent(primaryFile)));
        } else {
            assertThat(getContent(secondaryFile2), not(is(getContent(primaryFile))));
        }
    }

    private String getContent(File file) throws IOException {
        return Files.readString(file.toPath());
    }
}
