package org.jenkinsci.plugins.workflow.log.tee;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.workflow.log.FileLogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.jenkinsci.plugins.workflow.log.LogStorageTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class TeeLogStorageTest extends LogStorageTestBase {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File primaryFile;
    private File fileLogStorageFile;
    private File remoteCustomFileLogStorageFile;
    /**
     * For the remoteCustomFileLogStorage, the logs are tranfromed in uppercase, meaning the case should be different
     */
    private boolean remoteCase = false;
    /**
     * For the mangled case we simply check the sequences are properly in order, we check all lowercase alphabet, uppercase alphabet and digits are in the correct seauence
     */
    private boolean mangledCase = false;

    @Before
    public void before() throws Exception {
        primaryFile = tmp.newFile();
        fileLogStorageFile = tmp.newFile();
        remoteCustomFileLogStorageFile = tmp.newFile();
    }

    @Override
    protected LogStorage createStorage() {
        return new TeeLogStorage(
            FileLogStorage.forFile(primaryFile),
            FileLogStorage.forFile(fileLogStorageFile),
            RemoteCustomFileLogStorage.forFile(remoteCustomFileLogStorageFile));
    }

    @Override
    public void mangledLines() throws Exception {
        mangledCase = true;
        super.mangledLines();
    }

    @Override
    public void remoting() throws Exception {
        remoteCase = true;
        super.remoting();
    }

    @After
    public void additional_checks() throws IOException {
        var primaryContent = getContent(primaryFile);
        var fileLogStorageContent = getContent(fileLogStorageFile);
        var remoteCustomFileLogStorageContent = getContent(remoteCustomFileLogStorageFile);

        if (mangledCase) {
            checkMangledLines(primaryContent, fileLogStorageContent, remoteCustomFileLogStorageContent);
            return;
        }

        assertThat(fileLogStorageContent, is(primaryContent));
        if (remoteCase) {
            assertThat(remoteCustomFileLogStorageContent, equalToIgnoringCase(primaryContent));
        } else {
            assertThat(remoteCustomFileLogStorageContent, is(primaryContent));
        }
    }

    private String getContent(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    private void checkMangledLines(
            String primaryContent, String fileLogStorageContent, String remoteCustomFileLogStorageContent) {
        for (Pattern pattern : List.of(Pattern.compile("[a-z]"), Pattern.compile("[A-Z]"), Pattern.compile("[0-9]"))) {
            var primaryExtract = extractCharacters(pattern, primaryContent);
            var fileLogStorageExtract = extractCharacters(pattern, fileLogStorageContent);
            var remoteCustomFileLogStorageExtract = extractCharacters(pattern, remoteCustomFileLogStorageContent);
            assertThat(primaryExtract, is(fileLogStorageExtract));
            assertThat(primaryExtract, is(remoteCustomFileLogStorageExtract));
        }
    }

    private String extractCharacters(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            sb.append(matcher.group());
        }
        return sb.toString();
    }
}
