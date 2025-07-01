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

    @After
    public void additional_checks() throws IOException {
        var primary = getContent(primaryFile);
        var secondary1 = getContent(secondaryFile1);
        var secondary2 = getContent(secondaryFile2);
        if (secondary1SameAsPrimary) {
            assertThat(secondary1, is(primary));
        } else {
            assertThat(secondary1, not(is(primary)));
        }
        if (secondary2SameAsPrimary) {
            assertThat(secondary2, is(primary));
        } else {
            assertThat(secondary2, not(is(primary)));
        }
    }

    private String getContent(File file) throws IOException {
        return Files.readString(file.toPath());
    }
}
