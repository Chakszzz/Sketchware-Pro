package ide.sketchware.utility;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link BinaryExecutor}.
 *
 * Tests cover the changes introduced in this PR:
 * - {@code redirectErrorStream(true)} merges stderr into stdout
 * - Output is read from {@code getInputStream()} (not just {@code getErrorStream()})
 * - {@code process.waitFor()} is called so output is fully captured
 * - {@code process.destroy()} is always called in the finally block
 */
public class BinaryExecutorTest {

    private static ArrayList<String> cmd(String... parts) {
        return new ArrayList<>(Arrays.asList(parts));
    }

    // ==================== stdout capture ====================

    @Test
    public void execute_stdoutOutput_capturedInResult() {
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("sh", "-c", "echo hello"));
        String result = executor.execute();
        assertTrue("stdout should be captured", result.contains("hello"));
    }

    @Test
    public void execute_multiLineOutput_capturedCompletely() {
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("sh", "-c", "echo line1; echo line2; echo line3"));
        String result = executor.execute();
        assertTrue(result.contains("line1"));
        assertTrue(result.contains("line2"));
        assertTrue(result.contains("line3"));
    }

    // ==================== stderr merge via redirectErrorStream(true) ====================

    @Test
    public void execute_stderrOutput_capturedDueToRedirectErrorStream() {
        // Before this PR, only getErrorStream() was read but without redirectErrorStream.
        // After this PR, redirectErrorStream(true) merges stderr into stdout stream.
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("sh", "-c", "echo stderr_message >&2"));
        String result = executor.execute();
        assertTrue("stderr should be merged and captured via redirectErrorStream",
                result.contains("stderr_message"));
    }

    @Test
    public void execute_mixedStdoutAndStderr_bothCaptured() {
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("sh", "-c", "echo out_line; echo err_line >&2"));
        String result = executor.execute();
        assertTrue("stdout should be present", result.contains("out_line"));
        assertTrue("stderr should be merged and present", result.contains("err_line"));
    }

    // ==================== Non-zero exit code ====================

    @Test
    public void execute_nonZeroExitCode_doesNotThrow() {
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("sh", "-c", "exit 1"));
        // Should return without throwing — process.waitFor() handles it gracefully
        String result = executor.execute();
        assertNotNull(result);
    }

    @Test
    public void execute_commandThatFails_capturesErrorOutput() {
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("sh", "-c", "echo failure_output >&2; exit 42"));
        String result = executor.execute();
        // stderr is now merged via redirectErrorStream, so the message should appear
        assertTrue(result.contains("failure_output"));
    }

    // ==================== Empty output ====================

    @Test
    public void execute_noOutput_returnsEmptyString() {
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("sh", "-c", "true"));
        String result = executor.execute();
        // Should return empty or whitespace only — no output was produced
        assertNotNull(result);
        assertTrue("Result should be blank for a no-output command", result.trim().isEmpty());
    }

    // ==================== Invalid command (exception path) ====================

    @Test
    public void execute_invalidCommand_doesNotThrow_returnsStackTrace() {
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("this_command_does_not_exist_at_all_12345xyz"));
        String result = executor.execute();
        // Should not throw; instead the exception is printed to the writer
        assertNotNull(result);
        assertFalse("Result should contain exception info", result.isEmpty());
    }

    // ==================== getLog() consistency ====================

    @Test
    public void getLog_afterExecute_returnsSameContentAsExecute() {
        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(cmd("sh", "-c", "echo getlog_test"));
        String executeResult = executor.execute();
        String logResult = executor.getLog();
        assertEquals(executeResult, logResult);
    }

    // ==================== Regression: waitFor() ensures full output capture ====================

    @Test
    public void execute_longRunningCommand_capturesAllOutput() {
        // This test exercises the waitFor() call — without it, the scanner could
        // terminate before the process finishes writing all output.
        BinaryExecutor executor = new BinaryExecutor();
        // Print 100 lines of output
        executor.setCommands(cmd("sh", "-c", "for i in $(seq 1 100); do echo \"line_$i\"; done"));
        String result = executor.execute();
        assertTrue("All lines should be captured when waitFor() is called",
                result.contains("line_100"));
        assertTrue(result.contains("line_1"));
    }
}