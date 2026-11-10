package net.yahuclient.mod.lyrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps now-playing.ps1 running while the lyrics mod is on and remembers the last song it reported. Windows only; elsewhere
 * nothing ever plays.
 */
public final class MediaWatcher {
	private volatile NowPlaying current;
	private volatile long lastReportMs;
	private Process process;
	private Thread reader;

	public NowPlaying current() {
		// a script that went quiet (crashed, killed) doesn't know anything any more
		return System.currentTimeMillis() - lastReportMs > 15_000 ? null : current;
	}

	public synchronized boolean isRunning() {
		return process != null && process.isAlive();
	}

	public synchronized void start() {
		if (isRunning() || !System.getProperty("os.name", "").toLowerCase().contains("win")) {
			return;
		}
		try (InputStream script = MediaWatcher.class.getResourceAsStream("/assets/yahu/now-playing.ps1")) {
			if (script == null) {
				return;
			}
			Path file = Files.createTempFile("yahu-now-playing", ".ps1");
			Files.copy(script, file, StandardCopyOption.REPLACE_EXISTING);
			file.toFile().deleteOnExit();
			process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", file.toString())
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
		} catch (IOException e) {
			process = null;
			return;
		}
		Process started = process;
		reader = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) {
					current = NowPlaying.parse(line);
					lastReportMs = System.currentTimeMillis();
				}
			} catch (IOException ignored) {
				// the script ended
			}
		}, "Yahu now playing");
		reader.setDaemon(true);
		reader.start();
	}

	public synchronized void stop() {
		if (process != null) {
			process.destroy();
			process = null;
		}
		current = null;
	}
}
