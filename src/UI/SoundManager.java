package UI;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SoundManager {
    private static MediaPlayer bgmPlayer;
    private static Timeline fadeTimeline;
    private static double volume = 0.5;
    private static boolean initialized = false;

    private static Path resolveBgmPath() {
        List<Path> candidates = new ArrayList<>(List.of(
                Path.of("BGM.mp3"),
                Path.of("app", "BGM.mp3"),
                Path.of("twentyoneproject", "BGM.mp3"),
                Path.of("..", "BGM.mp3")
        ));
        String jpackageAppPath = System.getProperty("jpackage.app-path");
        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            Path exeDir = Path.of(jpackageAppPath).toAbsolutePath().getParent();
            if (exeDir != null) {
                candidates.add(exeDir.resolve("app").resolve("BGM.mp3"));
                candidates.add(exeDir.resolve("BGM.mp3"));
            }
        }
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
                if (fadeTimeline != null) {
                    fadeTimeline.stop();
                    fadeTimeline = null;
                }
                if (bgmPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                    bgmPlayer.setVolume(volume);
                    bgmPlayer.play();
                }
            } catch (Throwable _) {
                // Ignore audio playback errors in headless or unsupported environments
            }
        }
    }

    public static synchronized void fadeOutAndPause(double durationMs) {
        if (fadeTimeline != null) {
            fadeTimeline.stop();
            fadeTimeline = null;
        }
        if (bgmPlayer == null) {
            return;
        }
        try {
            if (bgmPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                return;
            }
            MediaPlayer targetPlayer = bgmPlayer;
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(targetPlayer.volumeProperty(), targetPlayer.getVolume())),
                    new KeyFrame(Duration.millis(Math.max(100, durationMs)), new KeyValue(targetPlayer.volumeProperty(), 0.0))
            );
            tl.setOnFinished(_ -> {
                try {
                    targetPlayer.pause();
                } catch (Throwable _) {
                    // Ignore transient media player errors
                }
            });
            fadeTimeline = tl;
            tl.play();
        } catch (Throwable _) {
            // Ignore audio playback errors in headless or unsupported environments
        }
    }

    public static synchronized void resumeAndFadeIn(double durationMs) {
        if (fadeTimeline != null) {
            fadeTimeline.stop();
            fadeTimeline = null;
        }
        playBgm();
        if (bgmPlayer == null) {
            return;
        }
        try {
            MediaPlayer targetPlayer = bgmPlayer;
            targetPlayer.setVolume(0.0);
            if (targetPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                targetPlayer.play();
            }
            Timeline tl = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(targetPlayer.volumeProperty(), 0.0)),
                    new KeyFrame(Duration.millis(Math.max(100, durationMs)), new KeyValue(targetPlayer.volumeProperty(), volume))
            );
            fadeTimeline = tl;
            tl.play();
        } catch (Throwable _) {
            // Ignore audio playback errors in headless or unsupported environments
        }
    }

    public static synchronized void setVolume(double newVolume) {
        double clamped = Math.max(0.0, Math.min(1.0, newVolume));
        volume = clamped;
        if (fadeTimeline != null) {
            fadeTimeline.stop();
            fadeTimeline = null;
        }
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
