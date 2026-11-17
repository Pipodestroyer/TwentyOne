package UI;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class AssetManager {
    private static final int CARD_W = 39;
    private static final int CARD_H = 55;

    private static Path assetsRoot;
    private static final Map<String, Image> cache = new HashMap<>();
    private static List<Image> menuCarouselCards;

    private static final Map<String, String> TRUMP_ASSET_MAP = Map.ofEntries(
            Map.entry("Bless", "Cards/Jokers/black_joker.png"),
            Map.entry("2-Card", "Cards/Clubs/Green Variant/2_of_clubs.png"),
            Map.entry("3-Card", "Cards/Clubs/Green Variant/3_of_clubs.png"),
            Map.entry("4-Card", "Cards/Clubs/Green Variant/4_of_clubs.png"),
            Map.entry("5-Card", "Cards/Clubs/Green Variant/5_of_clubs.png"),
            Map.entry("6-Card", "Cards/Clubs/Green Variant/6_of_clubs.png"),
            Map.entry("7-Card", "Cards/Clubs/Green Variant/7_of_clubs.png"),
            Map.entry("Go for 17", "Cards/Decks/deck_4_green.png"),
            Map.entry("Go for 24", "Cards/Decks/deck_5_yellow.png"),
            Map.entry("Go for 27", "Cards/Decks/deck_6_black.png"),
            Map.entry("One-Up", "Cards/Clubs/jack_of_clubs.png"),
            Map.entry("Two-Up", "Cards/Clubs/king_of_clubs.png"),
            Map.entry("Shield", "Cards/Diamonds/queen_of_diamonds.png"),
            Map.entry("Shield-Plus", "Cards/Diamonds/king_of_diamonds.png"),
            Map.entry("Bloodshed", "Cards/Jokers/red_joker.png"),
            Map.entry("Destroy", "Cards/Spades/king_of_spades.png"),
            Map.entry("Friendship", "Cards/Hearts/queen_of_hearts.png"),
            Map.entry("Reincarnation", "Cards/Spades/queen_of_spades.png"),
            Map.entry("Refresh", "Cards/Decks/deck_10_yellow.png"),
            Map.entry("Remove", "Cards/Hearts/king_of_hearts.png"),
            Map.entry("Return", "Cards/Clubs/queen_of_clubs.png"),
            Map.entry("Exchange", "Cards/Diamonds/jack_of_diamonds.png"),
            Map.entry("Disservice", "Cards/Clubs/Green Variant/jack_of_clubs.png"),
            Map.entry("Perfect Draw", "Cards/Decks/deck_12_yellow.png"),
            Map.entry("Hush", "Cards/Decks/deck_13_black.png")
    );

    // 3x5 pixel bitmap glyphs for crisp pixel-art numbers & symbols
    private static final Map<Character, int[]> GLYPHS_3X5 = Map.ofEntries(
            Map.entry('0', new int[]{0b111, 0b101, 0b101, 0b101, 0b111}),
            Map.entry('1', new int[]{0b010, 0b110, 0b010, 0b010, 0b111}),
            Map.entry('2', new int[]{0b111, 0b001, 0b111, 0b100, 0b111}),
            Map.entry('3', new int[]{0b111, 0b001, 0b111, 0b001, 0b111}),
            Map.entry('4', new int[]{0b101, 0b101, 0b111, 0b001, 0b001}),
            Map.entry('5', new int[]{0b111, 0b100, 0b111, 0b001, 0b111}),
            Map.entry('6', new int[]{0b111, 0b100, 0b111, 0b101, 0b111}),
            Map.entry('7', new int[]{0b111, 0b001, 0b001, 0b010, 0b010}),
            Map.entry('8', new int[]{0b111, 0b101, 0b111, 0b101, 0b111}),
            Map.entry('9', new int[]{0b111, 0b101, 0b111, 0b001, 0b111}),
            Map.entry('?', new int[]{0b111, 0b001, 0b011, 0b000, 0b010}),
            Map.entry('+', new int[]{0b000, 0b010, 0b111, 0b010, 0b000}),
            Map.entry('-', new int[]{0b000, 0b000, 0b111, 0b000, 0b000})
    );

    public static synchronized Path getAssetsRoot() {
        if (assetsRoot != null) {
            return assetsRoot;
        }
        List<Path> candidates = new ArrayList<>(List.of(
                Path.of("Snoblin_Deck_of_Playing_Cards"),
                Path.of("app", "Snoblin_Deck_of_Playing_Cards"),
                Path.of("twentyoneproject", "Snoblin_Deck_of_Playing_Cards"),
                Path.of("..", "Snoblin_Deck_of_Playing_Cards")
        ));
        String jpackageAppPath = System.getProperty("jpackage.app-path");
        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            Path exeDir = Path.of(jpackageAppPath).toAbsolutePath().getParent();
            if (exeDir != null) {
                candidates.add(exeDir.resolve("app").resolve("Snoblin_Deck_of_Playing_Cards"));
                candidates.add(exeDir.resolve("Snoblin_Deck_of_Playing_Cards"));
            }
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                assetsRoot = candidate.toAbsolutePath().normalize();
                return assetsRoot;
            }
        }
        assetsRoot = Path.of("Snoblin_Deck_of_Playing_Cards").toAbsolutePath();
        return assetsRoot;
    }

    private static Image loadRaw(String relativePath) {
        Path fullPath = getAssetsRoot().resolve(relativePath);
        if (!Files.exists(fullPath)) {
            return createFallbackCard();
        }
        return new Image(fullPath.toUri().toString(), false);
    }

    private static Image createFallbackCard() {
        WritableImage img = new WritableImage(CARD_W, CARD_H);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < CARD_H; y++) {
            for (int x = 0; x < CARD_W; x++) {
                boolean border = x == 0 || y == 0 || x == CARD_W - 1 || y == CARD_H - 1;
                pw.setColor(x, y, border ? Color.BLACK : Color.WHITE);
            }
        }
        return img;
    }

    public static Image toMonochrome(Image src) {
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        WritableImage out = new WritableImage(w, h);
        PixelReader pr = src.getPixelReader();
        PixelWriter pw = out.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = pr.getColor(x, y);
                if (c.getOpacity() < 0.05) {
                    pw.setColor(x, y, Color.TRANSPARENT);
                    continue;
                }
                double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
                Color mono;
                if (lum < 0.35) {
                    mono = Color.rgb(16, 16, 16, c.getOpacity());
                } else if (lum < 0.62) {
                    mono = Color.rgb(75, 75, 75, c.getOpacity());
                } else if (lum < 0.85) {
                    mono = Color.rgb(210, 210, 210, c.getOpacity());
                } else {
                    mono = Color.rgb(255, 255, 255, c.getOpacity());
                }
                pw.setColor(x, y, mono);
            }
        }
        return out;
    }

    public static Image scaleNearestNeighbor(Image src, int scale) {
        if (scale <= 1) return src;
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        WritableImage out = new WritableImage(w * scale, h * scale);
        PixelReader pr = src.getPixelReader();
        PixelWriter pw = out.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = pr.getColor(x, y);
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        pw.setColor(x * scale + dx, y * scale + dy, c);
                    }
                }
            }
        }
        return out;
    }

    private static WritableImage copyToWritable(Image src) {
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        WritableImage out = new WritableImage(w, h);
        PixelReader pr = src.getPixelReader();
        PixelWriter pw = out.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pw.setColor(x, y, pr.getColor(x, y));
            }
        }
        return out;
    }

    private static void drawPixelText(PixelWriter pw, int x, int y, String text, Color color, int pixelScale) {
        int cursorX = x;
        for (int i = 0; i < text.length(); i++) {
            int[] glyph = GLYPHS_3X5.get(text.charAt(i));
            if (glyph != null) {
                for (int row = 0; row < 5; row++) {
                    int bits = glyph[row];
                    for (int col = 0; col < 3; col++) {
                        if ((bits & (1 << (2 - col))) != 0) {
                            for (int sy = 0; sy < pixelScale; sy++) {
                                for (int sx = 0; sx < pixelScale; sx++) {
                                    int px = cursorX + col * pixelScale + sx;
                                    int py = y + row * pixelScale + sy;
                                    if (px >= 0 && py >= 0) {
                                        pw.setColor(px, py, color);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            cursorX += 4 * pixelScale;
        }
    }

    public static Image getMenuAce21(int scale) {
        String key = "menu_ace_21_" + scale;
        return cache.computeIfAbsent(key, _ -> {
            Image base = toMonochrome(loadRaw("Cards/Spades/ace_of_spades.png"));
            WritableImage canvas = copyToWritable(base);
            PixelWriter pw = canvas.getPixelWriter();
            drawPixelText(pw, 16, 24, "21", Color.WHITE, 1);
            return scaleNearestNeighbor(canvas, scale);
        });
    }

    public static Image getPlayerVisibleNumberCard(int number, int scale) {
        String key = "num_player_blue_" + number + "_" + scale;
        return cache.computeIfAbsent(key, _ -> {
            String rel = switch (number) {
                case 1 -> "Cards/Diamonds/Blue Variant/ace_of_diamonds.png";
                case 11 -> "Cards/Diamonds/Blue Variant/jack_of_diamonds.png";
                default -> "Cards/Diamonds/Blue Variant/" + number + "_of_diamonds.png";
            };
            Image raw = loadRaw(rel);
            if (number == 11) {
                return scaleNearestNeighbor(overlayElevenOnJack(raw, Color.rgb(25, 73, 138)), scale);
            }
            return scaleNearestNeighbor(raw, scale);
        });
    }

    public static Image getEnemyVisibleNumberCard(int number, int scale) {
        String key = "num_enemy_red_" + number + "_" + scale;
        return cache.computeIfAbsent(key, _ -> {
            String rel = switch (number) {
                case 1 -> "Cards/Hearts/ace_of_hearts.png";
                case 11 -> "Cards/Hearts/jack_of_hearts.png";
                default -> "Cards/Hearts/" + number + "_of_hearts.png";
            };
            Image raw = loadRaw(rel);
            if (number == 11) {
                return scaleNearestNeighbor(overlayElevenOnJack(raw, Color.rgb(184, 37, 53)), scale);
            }
            return scaleNearestNeighbor(raw, scale);
        });
    }

    private static Image overlayElevenOnJack(Image raw, Color inkColor) {
        WritableImage canvas = copyToWritable(raw);
        PixelReader pr = raw.getPixelReader();
        PixelWriter pw = canvas.getPixelWriter();
        Color bg = pr.getColor(2, 2);
        for (int y = 3; y <= 8; y++) {
            for (int x = 2; x <= 8; x++) {
                pw.setColor(x, y, bg);
            }
        }
        drawPixelText(pw, 2, 4, "11", inkColor, 1);
        return canvas;
    }

    public static Image getVisibleNumberCard(int number, int scale) {
        return getPlayerVisibleNumberCard(number, scale);
    }

    public static Image getPlayerHiddenNumberCard(int number, int scale) {
        if (number == Game.numberCard.SKULL_CARD) {
            return getSkullCard(scale);
        }
        String key = "num_player_mono_" + number + "_" + scale;
        return cache.computeIfAbsent(key, _ -> {
            String rel = switch (number) {
                case 1 -> "Cards/Spades/ace_of_spades.png";
                case 11 -> "Cards/Spades/jack_of_spades.png";
                default -> "Cards/Spades/" + number + "_of_spades.png";
            };
            Image mono = toMonochrome(loadRaw(rel));
            if (number == 11) {
                return scaleNearestNeighbor(overlayElevenOnJack(mono, Color.rgb(16, 16, 16)), scale);
            }
            return scaleNearestNeighbor(mono, scale);
        });
    }

    public static Image getSkullCard(int scale) {
        String key = "skull_card_" + scale;
        return cache.computeIfAbsent(key, _ -> {
            Image base = toMonochrome(loadRaw("Cards/Decks/deck_1_black.png"));
            WritableImage canvas = copyToWritable(base);
            PixelWriter pw = canvas.getPixelWriter();
            for (int y = 14; y <= 40; y++) {
                for (int x = 8; x <= 30; x++) {
                    boolean border = x == 8 || x == 30 || y == 14 || y == 40;
                    pw.setColor(x, y, border ? Color.WHITE : Color.rgb(16, 16, 16));
                }
            }
            String[] skull = {
                    "  #########  ",
                    " ########### ",
                    "#############",
                    "#############",
                    "##   ###   ##",
                    "##   ###   ##",
                    "##   ###   ##",
                    "#############",
                    " ##### ##### ",
                    " ####   #### ",
                    "  #########  ",
                    "   #######   ",
                    "   # # # #   ",
                    "   # # # #   "
            };
            int startX = 13;
            int startY = 20;
            for (int r = 0; r < skull.length; r++) {
                String row = skull[r];
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) == '#') {
                        pw.setColor(startX + c, startY + r, Color.WHITE);
                    }
                }
            }
            return scaleNearestNeighbor(canvas, scale);
        });
    }

    public static Image getEnemyHiddenCard(int scale) {
        String key = "enemy_hidden_" + scale;
        return cache.computeIfAbsent(key, _ -> {
            Image base = toMonochrome(loadRaw("Cards/Decks/deck_1_black.png"));
            WritableImage canvas = copyToWritable(base);
            PixelWriter pw = canvas.getPixelWriter();
            for (int y = 14; y <= 40; y++) {
                for (int x = 8; x <= 30; x++) {
                    boolean border = x == 8 || x == 30 || y == 14 || y == 40;
                    pw.setColor(x, y, border ? Color.WHITE : Color.rgb(16, 16, 16));
                }
            }
            drawPixelText(pw, 16, 22, "?", Color.WHITE, 2);
            return scaleNearestNeighbor(canvas, scale);
        });
    }

    public static Image getTrumpCardImage(String trumpName, int scale) {
        String key = "trump_color_" + trumpName + "_" + scale;
        return cache.computeIfAbsent(key, _ -> {
            String rel = TRUMP_ASSET_MAP.getOrDefault(trumpName, "Cards/Jokers/black_joker.png");
            return scaleNearestNeighbor(loadRaw(rel), scale);
        });
    }

    public static Image getPlayerChipImage(int scale) {
        String key = "chip_blue_" + scale;
        return cache.computeIfAbsent(key, _ -> scaleNearestNeighbor(loadRaw("Chips/blue_chip_1.png"), scale));
    }

    public static Image getEnemyChipImage(int scale) {
        String key = "chip_red_" + scale;
        return cache.computeIfAbsent(key, _ -> scaleNearestNeighbor(loadRaw("Chips/red_chip_1.png"), scale));
    }

    public static Image getChipImage(boolean dark, int scale) {
        return dark ? getPlayerChipImage(scale) : getEnemyChipImage(scale);
    }

    public static synchronized List<Image> getAllPackCardsForMenu(int scale) {
        if (menuCarouselCards != null) {
            return menuCarouselCards;
        }
        List<Image> list = new ArrayList<>();
        Path cardsDir = getAssetsRoot().resolve("Cards");
        if (Files.isDirectory(cardsDir)) {
            try (Stream<Path> walk = Files.walk(cardsDir)) {
                List<Path> pngs = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                        .sorted()
                        .toList();
                for (Path png : pngs) {
                    Image raw = new Image(png.toUri().toString(), false);
                    list.add(scaleNearestNeighbor(raw, scale));
                }
            } catch (IOException _) {
                // Fallback below if walk fails
            }
        }
        if (list.isEmpty()) {
            for (int i = 1; i <= 11; i++) {
                list.add(getPlayerVisibleNumberCard(i, scale));
            }
        }
        menuCarouselCards = list;
        return menuCarouselCards;
    }
}
