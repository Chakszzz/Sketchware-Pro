package ide.sketchware.utility;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link BinaryExecutor#execute()}.
 *
 * Tests focus on the behaviour introduced in this PR:
 *   - {@code redirectErrorStream(true)}: stderr is merged into stdout, so all
 *     output (including error output) is captured in the returned string.
 *   - {@code process.waitFor()}: the method blocks until the process terminates
 *     before returning; the complete output is therefore captured even for
 *     slow processes.
 *   - {@code finally { process.destroy() }}: the process handle is always
 *     cleaned up even when an exception occurs during reading.
 *   - When a command fails to start (e.g., an invalid binary), the stack trace
 *     is captured in the returned string instead of being swallowed silently.
 *
 * These tests use real OS processes (echo, sh) that are reliably available on
 * the Linux test host specified in the project's CI configuration.
 */
public class BinaryExecutorTest {

    private BinaryExecutor executor;

    @Before
    public void setUp() {
        executor = new BinaryExecutor();
    }

    private ArrayList<String> cmd(String... parts) {
        ArrayList<String> list = new ArrayList<>();
        for (String p : parts) list.add(p);
        return list;
    }

    // -----------------------------------------------------------------------
    // Basic output capture
    // -----------------------------------------------------------------------

    @Test
    public void execute_echoCommand_returnsOutputLine() {
        executor.setCommands(cmd("echo", "hello"));
        String output = executor.execute();
        assertTrue("Output should contain the echoed text",
                output.contains("hello"));
    }

    @Test
    public void execute_multiLineOutput_capturesAllLines() {
        // printf produces multiple lines; all must appear in the result
        executor.setCommands(cmd("sh", "-c", "echo line1; echo line2; echo line3"));
        String output = executor.execute();
        assertTrue(output.contains("line1"));
        assertTrue(output.contains("line2"));
        assertTrue(output.contains("line3"));
    }

    // -----------------------------------------------------------------------
    // redirectErrorStream(true): stderr merged into stdout
    // -----------------------------------------------------------------------

    @Test
    public void execute_stderrOutput_capturedViaRedirectErrorStream() {
        // Write to stderr only; with redirectErrorStream the result must contain it
        executor.setCommands(cmd("sh", "-c", "echo error-text >&2"));
        String output = executor.execute();
        assertTrue("Stderr output must be captured via redirectErrorStream",
                output.contains("error-text"));
    }

    @Test
    public void execute_mixedStdoutAndStderr_bothCaptured() {
        executor.setCommands(cmd("sh", "-c", "echo stdout-line; echo stderr-line >&2"));
        String output = executor.execute();
        assertTrue("stdout should be captured", output.contains("stdout-line"));
        assertTrue("stderr should be captured via merged stream", output.contains("stderr-line"));
    }

    // -----------------------------------------------------------------------
    // waitFor(): output is complete when execute() returns
    // -----------------------------------------------------------------------

    @Test
    public void execute_slowProcess_outputCompleteOnReturn() {
        // Process writes output after a short sleep; waitFor ensures we see it
        executor.setCommands(cmd("sh", "-c", "sleep 0.1; echo delayed-output"));
        String output = executor.execute();
        assertTrue("Output written after delay must still be captured",
                output.contains("delayed-output"));
    }

    // -----------------------------------------------------------------------
    // Invalid command: exception is captured, not re-thrown
    // -----------------------------------------------------------------------

    @Test
    public void execute_invalidCommand_doesNotThrow() {
        executor.setCommands(cmd("/nonexistent_binary_xyz_123456"));
        // Must not throw; the exception is written to the StringWriter
        String output = executor.execute();
        assertNotNull("Output must not be null even on exception", output);
    }

    @Test
    public void execute_invalidCommand_outputContainsExceptionInfo() {
        executor.setCommands(cmd("/nonexistent_binary_xyz_123456"));
        String output = executor.execute();
        // The stack trace or IOException message should be present
        assertFalse("Exception info should be captured, not produce empty output",
                output.isEmpty());
    }

    // -----------------------------------------------------------------------
    // getLog() == execute() return value
    // -----------------------------------------------------------------------

    @Test
    public void getLog_afterExecute_matchesReturnValue() {
        executor.setCommands(cmd("echo", "log-test"));
        String returned = executor.execute();
        String log = executor.getLog();
        // Both methods read from the same StringWriter
        assertEquals(returned, log);
    }

    // -----------------------------------------------------------------------
    // Empty output
    // -----------------------------------------------------------------------

    @Test
    public void execute_commandWithNoOutput_returnsEmptyOrWhitespaceString() {
        // `true` is a shell built-in that exits 0 with no output
        executor.setCommands(cmd("true"));
        String output = executor.execute();
        assertNotNull(output);
        assertEquals("Command with no output should return empty string",
                "", output.trim());
    }

    // -----------------------------------------------------------------------
    // Regression: process.destroy() is called in finally – resource cleanup
    // Test indirectly: a second execute() on a fresh executor must succeed
    // -----------------------------------------------------------------------

    @Test
    public void execute_calledTwiceOnSeparateInstances_bothSucceed() {
        BinaryExecutor e1 = new BinaryExecutor();
        e1.setCommands(cmd("echo", "first"));
        String out1 = e1.execute();

        BinaryExecutor e2 = new BinaryExecutor();
        e2.setCommands(cmd("echo", "second"));
        String out2 = e2.execute();

        assertTrue(out1.contains("first"));
        assertTrue(out2.contains("second"));
    }
}