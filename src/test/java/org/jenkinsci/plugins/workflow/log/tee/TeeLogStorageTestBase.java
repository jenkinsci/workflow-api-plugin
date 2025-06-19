package org.jenkinsci.plugins.workflow.log.tee;

import java.io.File;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageTestBase;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

/**
 * Foundation for compliance tests of {@link TeeLogStorage} implementations.
 */
public abstract class TeeLogStorageTestBase extends LogStorageTestBase {

    @ClassRule
    public static TemporaryFolder tmp = new TemporaryFolder();

    protected File secondaryFile1;
    protected File secondaryFile2;

    @Before
    public void secondaryFiles() throws Exception {
        secondaryFile1 = tmp.newFile();
        secondaryFile2 = tmp.newFile();
    }

    /**
     * Create the primary new tee storage implementation, but potentially reusing any data initialized in the last {@link Before} setup.
     */
    protected abstract LogStorage primaryStorage();

    @Override
    protected LogStorage createStorage() {
        return new TeeLogStorage(
                primaryStorage(), FileLogStorage.forFile(secondaryFile1), FileLogStorage.forFile(secondaryFile2));
    }
}
