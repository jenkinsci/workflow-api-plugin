package org.jenkinsci.plugins.workflow.log.tee;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.File;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TeeLogStorageTest extends LogStorageTestBase {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File primaryFile;
    private File secondaryFile1;
    private File secondaryFile2;
    private boolean primarySameLength = true;

    @Before
    public void setup() throws Exception {
        primaryFile = tmp.newFile();
        secondaryFile1 = tmp.newFile();
        secondaryFile2 = tmp.newFile();
    }

    @Override
    protected LogStorage createStorage() {
        return new TeeLogStorage(
                FileLogStorage.forFile(primaryFile),
                FileLogStorage.forFile(secondaryFile1),
                FileLogStorage.forFile(secondaryFile2));
    }

    @Override
    @Test
    public void mangledLines() throws Exception {
        primarySameLength = false;
        super.mangledLines();
    }

    @After
    public void additional_checks() {
        if (primarySameLength) {
            assertThat(primaryFile.length(), is(secondaryFile1.length()));
            assertThat(primaryFile.length(), is(secondaryFile2.length()));
        } else {
            assertThat(primaryFile.length(), not(is(secondaryFile1.length())));
            assertThat(primaryFile.length(), not(is(secondaryFile2.length())));
        }
        assertThat(secondaryFile1.length(), is(secondaryFile2.length()));
    }
}
