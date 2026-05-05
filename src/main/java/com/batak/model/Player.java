package com.batak.model;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private final String id;
    private String name;
    private int seat;       // 0..3
    private int team;       // 0 or 1 (seats 0,2 -> team 0 ; seats 1,3 -> team 1)
    private List<Card> hand = new ArrayList<>();
    private int bid = 0;
    private boolean declarer = false;
    private boolean partnerOfDeclarer = false;

    public Player(String id, String name, int seat) {
        this.id = id;
        this.name = name;
        this.seat = seat;
        this.team = seat % 2;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSeat() { return seat; }
    public void setSeat(int seat) { this.seat = seat; this.team = seat % 2; }
    public int getTeam() { return team; }
    public List<Card> getHand() { return hand; }
    public void setHand(List<Card> hand) { this.hand = hand; }
    public int getBid() { return bid; }
    public void setBid(int bid) { this.bid = bid; }
    public boolean isDeclarer() { return declarer; }
    public void setDeclarer(boolean declarer) { this.declarer = declarer; }
    public boolean isPartnerOfDeclarer() { return partnerOfDeclarer; }
    public void setPartnerOfDeclarer(boolean partnerOfDeclarer) { this.partnerOfDeclarer = partnerOfDeclarer; }

    public boolean hasSuit(Suit s) {
        return hand.stream().anyMatch(c -> c.getSuit() == s);
    }

    public Card removeCard(String cardId) {
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).getId().equals(cardId)) {
                return hand.remove(i);
            }
        }
        return null;
    }
}
