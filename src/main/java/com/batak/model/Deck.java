package com.batak.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    public Deck() {
        for (Suit s : Suit.values()) {
            for (int r = 2; r <= 14; r++) {
                cards.add(new Card(s, r));
            }
        }
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }

    /**
     * Distribute cards equally across the given number of players.
     * Returns a list of hands (List<Card>) — one hand per player.
     */
    public List<List<Card>> deal(int players) {
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            hands.add(new ArrayList<>());
        }
        for (int i = 0; i < cards.size(); i++) {
            hands.get(i % players).add(cards.get(i));
        }
        // Sort each hand: Kupa (H) -> Maca (S) -> Karo (D) -> Sinek (C)
        java.util.Map<Suit, Integer> order = new java.util.HashMap<>();
        order.put(Suit.HEARTS, 0);
        order.put(Suit.SPADES, 1);
        order.put(Suit.DIAMONDS, 2);
        order.put(Suit.CLUBS, 3);
        for (List<Card> h : hands) {
            h.sort((a, b) -> {
                int s = Integer.compare(order.get(a.getSuit()), order.get(b.getSuit()));
                if (s != 0) return s;
                return Integer.compare(a.getRank(), b.getRank());
            });
        }
        return hands;
    }
}
