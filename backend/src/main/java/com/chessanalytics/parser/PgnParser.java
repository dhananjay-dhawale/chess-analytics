package com.chessanalytics.parser;

import com.chessanalytics.model.Color;
import com.chessanalytics.model.GameResult;
import com.chessanalytics.model.TimeControlCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streaming PGN parser that processes games one at a time.
 * Memory-efficient for large files (30k+ games).
 *
 * PGN format overview:
 * - Headers in brackets: [Event "..."]
 * - Moves follow headers as text
 * - Empty line separates games
 */
@Component
public class PgnParser {

    private static final Logger log = LoggerFactory.getLogger(PgnParser.class);

    private static final Pattern HEADER_PATTERN = Pattern.compile("\\[([A-Za-z]+)\\s+\"([^\"]*)\"\\]");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Counts total games in a PGN file without full parsing.
     * Games are separated by "[Event" tags.
     */
    public int countGames(Path filePath) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[Event ")) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Parses a PGN file and invokes the callback for each game.
     * Streaming approach: only one game in memory at a time.
     *
     * @param filePath Path to PGN file
     * @param accountUsername Username to determine color and opponent
     * @param gameConsumer Callback for each parsed game
     * @return Number of games parsed
     */
    public int parse(Path filePath, String accountUsername, Consumer<ParsedGame> gameConsumer) throws IOException {
        int gamesProcessed = 0;
        Map<String, String> headers = new HashMap<>();
        StringBuilder moves = new StringBuilder();
        boolean inMoves = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    // Empty line can signal end of headers or end of game
                    if (!headers.isEmpty() && inMoves) {
                        // End of game - process it
                        ParsedGame game = buildGame(headers, moves.toString(), accountUsername);
                        if (game != null && game.isValid()) {
                            gameConsumer.accept(game);
                            gamesProcessed++;
                        }
                        headers.clear();
                        moves.setLength(0);
                        inMoves = false;
                    }
                    continue;
                }

                if (line.startsWith("[")) {
                    // Header line
                    if (inMoves && !headers.isEmpty()) {
                        // New game starting, process previous
                        ParsedGame game = buildGame(headers, moves.toString(), accountUsername);
                        if (game != null && game.isValid()) {
                            gameConsumer.accept(game);
                            gamesProcessed++;
                        }
                        headers.clear();
                        moves.setLength(0);
                        inMoves = false;
                    }

                    Matcher m = HEADER_PATTERN.matcher(line);
                    if (m.matches()) {
                        headers.put(m.group(1), m.group(2));
                    }
                } else {
                    // Move text
                    inMoves = true;
                    moves.append(line).append(" ");
                }
            }

            // Process last game if exists
            if (!headers.isEmpty()) {
                ParsedGame game = buildGame(headers, moves.toString(), accountUsername);
                if (game != null && game.isValid()) {
                    gameConsumer.accept(game);
                    gamesProcessed++;
                }
            }
        }

        return gamesProcessed;
    }

    /**
     * Parses a single PGN string into a ParsedGame.
     * Used for Chess.com API import where games come individually.
     *
     * @param pgnString Complete PGN for a single game
     * @param accountUsername Username to determine color and opponent
     * @return ParsedGame or null if invalid
     */
    public ParsedGame parseGameFromPgn(String pgnString, String accountUsername) {
        Map<String, String> headers = new HashMap<>();
        StringBuilder moves = new StringBuilder();

        String[] lines = pgnString.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[")) {
                Matcher m = HEADER_PATTERN.matcher(line);
                if (m.matches()) {
                    headers.put(m.group(1), m.group(2));
                }
            } else {
                moves.append(line).append(" ");
            }
        }

        return buildGame(headers, moves.toString(), accountUsername);
    }

    /**
     * Builds a ParsedGame from headers and moves.
     */
    ParsedGame buildGame(Map<String, String> headers, String moves, String accountUsername) {
        ParsedGame game = new ParsedGame();

        // Determine color and opponent based on username
        String white = headers.get("White");
        String black = headers.get("Black");

        if (white != null && white.equalsIgnoreCase(accountUsername)) {
            game.setColor(Color.WHITE);
            game.setOpponent(black);
        } else if (black != null && black.equalsIgnoreCase(accountUsername)) {
            game.setColor(Color.BLACK);
            game.setOpponent(white);
        } else {
            // Username not found in either - skip game or default to white
            log.debug("Username '{}' not found in game (White: {}, Black: {})",
                accountUsername, white, black);
            return null;
        }

        // Parse result from the player's perspective
        String resultStr = headers.get("Result");
        game.setResult(parseResult(resultStr, game.getColor()));

        // Parse date and time
        game.setPlayedAt(parseDateTime(headers));

        // Time control
        String timeControl = headers.get("TimeControl");
        game.setTimeControlRaw(timeControl);
        game.setTimeControlCategory(categorizeTimeControl(timeControl));

        // Opening info
        game.setEcoCode(headers.get("ECO"));
        game.setOpeningName(headers.get("Opening"));

        // Generate hash for duplicate detection
        game.setPgnHash(generateHash(headers, moves));

        return game;
    }

    /**
     * Parses game result from PGN result string.
     */
    private GameResult parseResult(String resultStr, Color playerColor) {
        if (resultStr == null) {
            return GameResult.DRAW; // Default
        }

        return switch (resultStr) {
            case "1-0" -> playerColor == Color.WHITE ? GameResult.WIN : GameResult.LOSS;
            case "0-1" -> playerColor == Color.BLACK ? GameResult.WIN : GameResult.LOSS;
            case "1/2-1/2" -> GameResult.DRAW;
            default -> GameResult.DRAW;
        };
    }

    /**
     * Parses date and optional time from headers.
     */
    private LocalDateTime parseDateTime(Map<String, String> headers) {
        String dateStr = headers.get("Date");
        String timeStr = headers.get("UTCTime"); // Chess.com uses this
        if (timeStr == null) {
            timeStr = headers.get("Time"); // Lichess uses this
        }

        LocalDate date = LocalDate.now();
        LocalTime time = LocalTime.MIDNIGHT;

        if (dateStr != null && !dateStr.contains("?")) {
            try {
                date = LocalDate.parse(dateStr, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                log.debug("Failed to parse date: {}", dateStr);
            }
        }

        if (timeStr != null) {
            try {
                time = LocalTime.parse(timeStr, TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                log.debug("Failed to parse time: {}", timeStr);
            }
        }

        return LocalDateTime.of(date, time);
    }

    /**
     * Categorizes raw time control string into standard categories.
     * Time control format: "base+increment" (in seconds)
     */
    private TimeControlCategory categorizeTimeControl(String timeControl) {
        if (timeControl == null || timeControl.equals("-")) {
            return TimeControlCategory.UNKNOWN;
        }

        // Handle correspondence
        if (timeControl.contains("/")) {
            return TimeControlCategory.CORRESPONDENCE;
        }

        try {
            // Parse base time (before + or /)
            String baseStr = timeControl.split("[+/]")[0];
            int baseSeconds = Integer.parseInt(baseStr);

            // Categorize based on total time
            // Standard: bullet < 180s, blitz < 600s, rapid < 1800s
            if (baseSeconds < 30) {
                return TimeControlCategory.ULTRABULLET;
            } else if (baseSeconds < 180) {
                return TimeControlCategory.BULLET;
            } else if (baseSeconds < 600) {
                return TimeControlCategory.BLITZ;
            } else if (baseSeconds < 1800) {
                return TimeControlCategory.RAPID;
            } else {
                return TimeControlCategory.CLASSICAL;
            }
        } catch (NumberFormatException e) {
            return TimeControlCategory.UNKNOWN;
        }
    }

    /**
     * Generates SHA-256 hash for duplicate detection.
     * Uses: date + white + black + result + first portion of moves
     */
    private String generateHash(Map<String, String> headers, String moves) {
        StringBuilder hashInput = new StringBuilder();
        hashInput.append(headers.getOrDefault("Date", ""));
        hashInput.append(headers.getOrDefault("White", ""));
        hashInput.append(headers.getOrDefault("Black", ""));
        hashInput.append(headers.getOrDefault("Result", ""));

        // Use first 200 chars of moves for hash (enough to be unique)
        String movesClean = moves.replaceAll("\\s+", " ").trim();
        if (movesClean.length() > 200) {
            movesClean = movesClean.substring(0, 200);
        }
        hashInput.append(movesClean);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hashInput.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Fallback: use simple string hash
            return Integer.toHexString(hashInput.toString().hashCode());
        }
    }
}
