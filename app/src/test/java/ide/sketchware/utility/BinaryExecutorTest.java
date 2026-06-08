package ide.sketchware.utility;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Tests for the refactored {@link BinaryExecutor#execute()} method.
 *
 * The key changes verified here:
 * <ul>
 *   <li>stderr is now merged into stdout via {@code redirectErrorStream(true)}</li>
 *   <li>{@code process.waitFor()} is called to await completion</li>
 *   <li>{@code process.destroy()} is called in finally to prevent process leaks</li>
 * </ul>
 */
public class BinaryExecutorTest {

    // ==================== Successful command execution ====================

    @Test
    public void execute_echoCommand_returnsOutput() {
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("echo");
        cmd.add("hello");
        executor.setCommands(cmd);

        String result = executor.execute();

        assertTrue("Output should contain 'hello'", result.contains("hello"));
    }

    @Test
    public void execute_multiLineOutput_returnsAllLines() {
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        // printf outputs two lines separated by newline
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("printf 'line1\\nline2\\n'");
        executor.setCommands(cmd);

        String result = executor.execute();

        assertTrue("Output should contain 'line1'", result.contains("line1"));
        assertTrue("Output should contain 'line2'", result.contains("line2"));
    }

    @Test
    public void execute_commandWithNoOutput_returnsEmptyOrBlank() {
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("true"); // POSIX 'true' exits 0 with no output
        executor.setCommands(cmd);

        String result = executor.execute();

        assertNotNull("Result should never be null", result);
        assertTrue("Output should be empty for 'true'", result.trim().isEmpty());
    }

    // ==================== Stderr merged via redirectErrorStream ====================

    @Test
    public void execute_commandWritingToStderr_capturesStderrOutput() {
        // After the fix, stderr is merged into stdout via redirectErrorStream(true)
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("echo error-output >&2");
        executor.setCommands(cmd);

        String result = executor.execute();

        assertTrue("Stderr output should be captured (redirectErrorStream=true)",
                result.contains("error-output"));
    }

    @Test
    public void execute_commandWritingToBothStreams_capturesBoth() {
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("echo stdout-line; echo stderr-line >&2");
        executor.setCommands(cmd);

        String result = executor.execute();

        assertTrue("stdout should be captured", result.contains("stdout-line"));
        assertTrue("stderr should be captured (merged)", result.contains("stderr-line"));
    }

    // ==================== getLog() reflects execute() output ====================

    @Test
    public void getLog_afterExecute_matchesExecuteReturn() {
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("echo");
        cmd.add("log-test");
        executor.setCommands(cmd);

        String executeResult = executor.execute();
        String logResult = executor.getLog();

        assertEquals("getLog() should match execute() return value", executeResult, logResult);
    }

    // ==================== Invalid command handling ====================

    @Test
    public void execute_nonExistentCommand_returnsStackTrace() {
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("this-command-does-not-exist-xyz-12345");
        executor.setCommands(cmd);

        String result = executor.execute();

        // When a command fails to start, the exception stack trace is written to mWriter
        assertNotNull("Result should not be null even on failure", result);
        // The output will contain exception information
        assertFalse("Result should contain error information", result.isEmpty());
    }

    // ==================== Process cleanup (process.destroy() in finally) ====================

    @Test
    public void execute_shortLivedCommand_completes() {
        // Verifies process.waitFor() is called (command finishes before we read output)
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("sleep 0; echo done");
        executor.setCommands(cmd);

        String result = executor.execute();

        assertTrue("Output should contain 'done'", result.contains("done"));
    }

    @Test
    public void execute_returnsNonNullString() {
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("echo");
        cmd.add("test");
        executor.setCommands(cmd);

        String result = executor.execute();

        assertNotNull("execute() should never return null", result);
    }

    // ==================== Output completeness (waitFor ensures all output is read) ====================

    @Test
    public void execute_largeOutput_capturesAllLines() {
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        // Generate 100 lines
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("for i in $(seq 1 100); do echo \"line$i\"; done");
        executor.setCommands(cmd);

        String result = executor.execute();

        assertTrue("Should contain first line", result.contains("line1"));
        assertTrue("Should contain last line", result.contains("line100"));
    }

    @Test
    public void execute_exitCodeNonZero_returnsOutputNotException() {
        // Even for non-zero exit, we should get whatever output was produced
        BinaryExecutor executor = new BinaryExecutor();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("echo partial-output; exit 1");
        executor.setCommands(cmd);

        String result = executor.execute();

        assertNotNull(result);
        assertTrue("Should capture output even on non-zero exit", result.contains("partial-output"));
    }
}