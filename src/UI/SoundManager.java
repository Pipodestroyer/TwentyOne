package UI;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.nio.file.Files;
import java.nio.file.Path;

public class SoundManager {
    private static MediaPlayer bgmPlayer;
    private static double volume = 0.5;
    private static boolean initialized = false;

    private static Path resolveBgmPath() {
        Path[] candidates = {
                Path.of("BGM.mp3"),
                Path.of("twentyoneproject", "BGM.mp3"),
                Path.of("..", "BGM.mp3")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    public static synchronized void playBgm() {
        if (!initialized) {
            initialized = true;
            Path bgmPath = resolveBgmPath();
            if (bgmPath != null) {
                try {
                    Media media = new Media(bgmPath.toUri().toString());
                    MediaPlayer player = new MediaPlayer(media);
                    player.setCycleCount(MediaPlayer.INDEFINITE);
                    player.setVolume(volume);
                    player.setOnError(() -> {});
                    bgmPlayer = player;
                } catch (Throwable _) {
                    bgmPlayer = null;
                }
            }
        }
        if (bgmPlayer != null) {
            try {
                if (bgmPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                    bgmPlayer.setVolume(volume);
                    bgmPlayer.play();
                }
            } catch (Throwable _) {
                // Ignore audio playback errors in headless or unsupported environments
            }
        }
    }

    public static synchronized void setVolume(double newVolume) {
        double clamped = Math.max(0.0, Math.min(1.0, newVolume));
        volume = clamped;
        if (bgmPlayer != null) {
            try {
                bgmPlayer.setVolume(clamped);
            } catch (Throwable _) {
                // Ignore transient media player volume errors
            }
        }
    }

    public static synchronized double getVolume() {
        return volume;
    }
}
