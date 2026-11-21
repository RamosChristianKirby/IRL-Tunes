package dev.irltunes.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs short external commands with a hard timeout. */
final class Processes {
    private Processes() {}

    /** @return stdout, or null if the command failed, timed out or does not exist. */
    static String run(List<String> command, long timeoutMs) {
        Process p;
        try {
            p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            return null; // not installed
        }
        try (InputStream in = p.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            byte[] buf = new byte[8192];
            while (true) {
                while (in.available() > 0) {
                    int n = in.read(buf);
                    if (n < 0) break;
                    out.write(buf, 0, n);
                }
                if (!p.isAlive()) {
                    in.transferTo(out);
                    break;
                }
                if (System.nanoTime() > deadline) {
                    p.destroyForcibly();
                    return null;
                }
                Thread.sleep(10);
            }
            return p.exitValue() == 0 ? out.toString(StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            p.destroyForcibly();
            return null;
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
