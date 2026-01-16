package com.chessanalytics.model;

/**
 * Supported chess platforms for account linking.
 */
public enum Platform {
    CHESS_COM("Chess.com"),
    LICHESS("Lichess"),
    OTHER("Other");

    private final String displayName;

    Platform(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
