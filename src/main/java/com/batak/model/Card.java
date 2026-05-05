package com.batak.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class Card {
    private final Suit suit;
    private final int rank; // 2..14 (11=J, 12=Q, 13=K, 14=A)

    public Card(Suit suit, int rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() {
        return suit;
    }

    public int getRank() {
        return rank;
    }

    @JsonProperty("rankLabel")
    public String getRankLabel() {
        switch (rank) {
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            case 14: return "A";
            default: return String.valueOf(rank);
        }
    }

    @JsonProperty("id")
    public String getId() {
        return suit.getCode() + "-" + rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card)) return false;
        Card c = (Card) o;
        return rank == c.rank && suit == c.suit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }

    @Override
    public String toString() {
        return getRankLabel() + suit.getCode();
    }
}
