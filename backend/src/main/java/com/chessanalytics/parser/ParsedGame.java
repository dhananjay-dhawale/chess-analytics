package com.chessanalytics.parser;

import com.chessanalytics.model.Color;
import com.chessanalytics.model.GameResult;
import com.chessanalytics.model.TimeControlCategory;
import java.time.LocalDateTime;

/**
 * Intermediate representation of a parsed game.
 * Contains all extracted data before entity creation.
 */
public class ParsedGame {
    private LocalDateTime playedAt;
    private GameResult result;
    private Color color;
    private String timeControlRaw;
    private TimeControlCategory timeControlCategory;
    private String ecoCode;
    private String openingName;
    private String opponent;
    private String pgnHash;

    // Getters and Setters

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }

    public GameResult getResult() {
        return result;
    }

    public void setResult(GameResult result) {
        this.result = result;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public String getTimeControlRaw() {
        return timeControlRaw;
    }

    public void setTimeControlRaw(String timeControlRaw) {
        this.timeControlRaw = timeControlRaw;
    }

    public TimeControlCategory getTimeControlCategory() {
        return timeControlCategory;
    }

    public void setTimeControlCategory(TimeControlCategory timeControlCategory) {
        this.timeControlCategory = timeControlCategory;
    }

    public String getEcoCode() {
        return ecoCode;
    }

    public void setEcoCode(String ecoCode) {
        this.ecoCode = ecoCode;
    }

    public String getOpeningName() {
        return openingName;
    }

    public void setOpeningName(String openingName) {
        this.openingName = openingName;
    }

    public String getOpponent() {
        return opponent;
    }

    public void setOpponent(String opponent) {
        this.opponent = opponent;
    }

    public String getPgnHash() {
        return pgnHash;
    }

    public void setPgnHash(String pgnHash) {
        this.pgnHash = pgnHash;
    }

    /**
     * Validates that all required fields are present.
     */
    public boolean isValid() {
        return playedAt != null && result != null && color != null && pgnHash != null;
    }
}
