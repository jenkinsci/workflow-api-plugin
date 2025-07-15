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

    protected File fileLogStorageFile;
    protected File remoteCustomFileLogStorageFile;

    @Before
    public void secondaryFiles() throws Exception {
        fileLogStorageFile = tmp.newFile();
        remoteCustomFileLogStorageFile = tmp.newFile();
    }

    /**
     * Create the primary new tee storage implementation, but potentially reusing any data initialized in the last {@link Before} setup.
     */
    protected abstract LogStorage primaryStorage();

    @Override
    protected LogStorage createStorage() {
        return new TeeLogStorage(
                primaryStorage(),
                FileLogStorage.forFile(fileLogStorageFile),
                RemoteCustomFileLogStorage.forFile(remoteCustomFileLogStorageFile));
    }
}
