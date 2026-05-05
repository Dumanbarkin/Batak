package com.batak.model;

public enum Suit {
    SPADES("S"),    // Maca
    HEARTS("H"),    // Kupa
    DIAMONDS("D"),  // Karo
    CLUBS("C");     // Sinek

    private final String code;

    Suit(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
