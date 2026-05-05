package com.batak.game;

import com.batak.model.Card;
import com.batak.model.Deck;
import com.batak.model.Player;
import com.batak.model.Suit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GameRoom {

    public enum Phase { WAITING, BIDDING, SEVEN_ROUND, TRUMP_SELECTION, PLAYING, FINISHED }

    private final String password;
    private final List<Player> players = new ArrayList<>(); // index = seat
    private Phase phase = Phase.WAITING;

    // Bidding
    private int bidTurn = 0;
    private int highestBid = 0;
    private int highestBidderSeat = -1;
    private int passCount = 0;
    private final boolean[] hasPassed = new boolean[4];
    // 7'ye düşürülen tur — herkes 8'i pas geçtiyse sırayla 7'ye sorulur
    private int sevenTurn = 0;
    private int sevenStartSeat = 0;
    private int sevenAsked = 0; // kaç kişiye soruldu

    // Game
    private Suit trump = null;
    private int currentTurn = 0;
    private int leadSeat = 0;
    private Suit leadSuit = null;
    // Played cards in current trick: seat -> card
    private final Map<Integer, Card> trick = new LinkedHashMap<>();
    // Tricks won by team
    private final int[] tricksByTeam = new int[2];
    // Trick history (last finished trick) for UI display
    private Map<Integer, Card> lastTrick = new LinkedHashMap<>();
    private int lastTrickWinnerSeat = -1;

    // Toplam skor (eller arasında biriken)
    private final int[] totalScore = new int[2];
    // El sayacı
    private int handNumber = 0;
    // Zorunlu 7 sırası: kimse 8 vermezse, bu koltuktaki oyuncu mecburen 7 alır.
    // Her el sonrası saat yönünde döner (0,1,2,3,0,1,...)
    private int forcedSevenSeat = 0;

    private String message = "Waiting for players...";

    public GameRoom(String password) {
        this.password = password;
        for (int i = 0; i < 4; i++) hasPassed[i] = false;
    }

    public synchronized boolean checkPassword(String pwd) {
        if (password == null || password.isEmpty()) return true;
        return password.equals(pwd);
    }

    public synchronized boolean isFull() {
        return players.size() >= 4;
    }

    public synchronized Player addPlayer(String id, String name) {
        if (isFull()) return null;
        int seat = players.size();
        Player p = new Player(id, name, seat);
        players.add(p);
        message = p.getName() + " joined (seat " + seat + ")";
        if (players.size() == 4) {
            startNewHand();
        }
        return p;
    }

    public synchronized void removePlayer(String id) {
        players.removeIf(p -> p.getId().equals(id));
        // Reset seats
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setSeat(i);
        }
        if (players.size() < 4 && phase != Phase.WAITING) {
            phase = Phase.WAITING;
            message = "A player left. Waiting for 4 players...";
            resetState();
        }
    }

    private void resetState() {
        bidTurn = 0;
        highestBid = 0;
        highestBidderSeat = -1;
        passCount = 0;
        for (int i = 0; i < 4; i++) hasPassed[i] = false;
        sevenTurn = 0;
        sevenAsked = 0;
        sevenStartSeat = 0;
        trump = null;
        currentTurn = 0;
        leadSeat = 0;
        leadSuit = null;
        trick.clear();
        lastTrick.clear();
        lastTrickWinnerSeat = -1;
        tricksByTeam[0] = 0;
        tricksByTeam[1] = 0;
        for (Player p : players) {
            p.setBid(0);
            p.setDeclarer(false);
            p.setPartnerOfDeclarer(false);
            p.getHand().clear();
        }
    }

    private void startNewHand() {
        resetState();
        handNumber++;
        Deck deck = new Deck();
        deck.shuffle();
        List<List<Card>> hands = deck.deal(4);
        for (int i = 0; i < 4; i++) {
            players.get(i).setHand(hands.get(i));
        }
        // Zorunlu 7 koltuğu ve ihale baslangic koltugu — her el sonunda saat yonunde doner.
        // El 1 -> k0, El 2 -> k1, El 3 -> k2, El 4 -> k3, El 5 -> k0 ...
        forcedSevenSeat = (handNumber - 1) % 4;
        phase = Phase.BIDDING;
        bidTurn = forcedSevenSeat;
        message = "El " + handNumber + " — " + players.get(bidTurn).getName()
                + " ihaleyi acar (min 8, pas). Kimse 8 almazsa "
                + players.get(forcedSevenSeat).getName() + " (k" + forcedSevenSeat + ") 7 alir.";
    }

    // ========== BIDDING ==========

    public synchronized String placeBid(String playerId, int bid) {
        if (phase != Phase.BIDDING) return "Ihale zamani degil";
        Player p = findPlayer(playerId);
        if (p == null) return "Bilinmeyen oyuncu";
        if (p.getSeat() != bidTurn) return "Sira sizde degil";
        if (hasPassed[p.getSeat()]) return "Zaten pas gectiniz";

        if (bid <= 0) {
            // pass
            hasPassed[p.getSeat()] = true;
            passCount++;
            message = p.getName() + " pas gecti";
        } else {
            if (bid <= highestBid) return "Ihale " + highestBid + "'den yuksek olmali";
            if (bid < 8) return "Minimum ihale 8";
            if (bid > 13) return "Maksimum ihale 13";
            highestBid = bid;
            highestBidderSeat = p.getSeat();
            p.setBid(bid);
            message = p.getName() + " " + bid + " ihale aldi";
        }

        advanceBidTurn();
        return null;
    }

    private void advanceBidTurn() {
        // 3 pas ve biri ihale almışsa -> declarer
        if (passCount >= 3 && highestBidderSeat >= 0) {
            finalizeBidding();
            return;
        }
        // 4 pas ve kimse ihale almamış -> ZORUNLU 7
        // forcedSevenSeat'teki oyuncu otomatik olarak 7 alır
        if (passCount >= 4 && highestBidderSeat < 0) {
            assignForcedSeven();
            return;
        }
        // Pas geçmemiş bir sonraki oyuncuya geç
        for (int i = 0; i < 4; i++) {
            bidTurn = (bidTurn + 1) % 4;
            if (!hasPassed[bidTurn]) break;
        }
        message += " — sira " + players.get(bidTurn).getName();
    }

    private void assignForcedSeven() {
        Player forced = players.get(forcedSevenSeat);
        highestBid = 7;
        highestBidderSeat = forced.getSeat();
        forced.setBid(7);
        message = "Kimse 8 almadi. " + forced.getName() + " (koltuk " + forcedSevenSeat
                + ") MECBUREN 7 alir.";
        finalizeBidding();
    }

    // ========== ZORUNLU 7 ==========
    // Kimse 8 almazsa, forcedSevenSeat'teki oyuncu MECBUREN 7 alir.
    // forcedSevenSeat her el sonunda saat yönünde döner.
    // Yani: 1. el oyuncu 0, 2. el oyuncu 1, 3. el oyuncu 2, 4. el oyuncu 3, 5. el oyuncu 0...
    // (Yukarida advanceBidTurn -> assignForcedSeven mantigi calisir.)

    private void finalizeBidding() {
        Player declarer = players.get(highestBidderSeat);
        declarer.setDeclarer(true);
        // Partner is the player on the same team (seat +2 mod 4)
        int partnerSeat = (highestBidderSeat + 2) % 4;
        players.get(partnerSeat).setPartnerOfDeclarer(true);

        phase = Phase.TRUMP_SELECTION;
        message = declarer.getName() + " is declarer with bid " + highestBid + ". Choose trump suit.";
    }

    public synchronized String selectTrump(String playerId, String suitCode) {
        if (phase != Phase.TRUMP_SELECTION) return "Not trump selection phase";
        Player p = findPlayer(playerId);
        if (p == null || !p.isDeclarer()) return "Only declarer chooses trump";
        Suit s = null;
        for (Suit x : Suit.values()) {
            if (x.getCode().equalsIgnoreCase(suitCode)) { s = x; break; }
        }
        if (s == null) return "Invalid suit";
        this.trump = s;
        phase = Phase.PLAYING;
        leadSeat = highestBidderSeat;
        currentTurn = leadSeat;
        leadSuit = null;
        trick.clear();
        message = "Trump is " + s + ". " + players.get(currentTurn).getName() + " leads.";
        return null;
    }

    // ========== PLAYING ==========

    public synchronized String playCard(String playerId, String cardId) {
        if (phase != Phase.PLAYING) return "Oyun fazi degil";
        Player requester = findPlayer(playerId);
        if (requester == null) return "Bilinmeyen oyuncu";

        // Kim oynuyor? Sıradaki oyuncu
        Player p = players.get(currentTurn);

        // İstek atan oyuncu = sıradaki oyuncu OLMALI...
        // ...VEYA istek atan declarer ve sıradaki oyuncu onun partneri olmalı
        boolean isOwnTurn = requester.getSeat() == currentTurn;
        boolean declarerControllingPartner =
                requester.isDeclarer() && p.isPartnerOfDeclarer();

        if (!isOwnTurn && !declarerControllingPartner) {
            return "Sira sizde degil";
        }

        // Find card in the actual seated player's hand (could be partner)
        Card target = null;
        for (Card c : p.getHand()) {
            if (c.getId().equals(cardId)) { target = c; break; }
        }
        if (target == null) return "Bu kart elde yok";

        // Must follow suit if possible
        if (leadSuit != null && target.getSuit() != leadSuit && p.hasSuit(leadSuit)) {
            return "Yer suitini takip etmek zorundasin (" + leadSuit + ")";
        }

        // If you don't have the lead suit but you DO have trump, you MUST play trump
        if (leadSuit != null && !p.hasSuit(leadSuit) && trump != null
                && leadSuit != trump
                && target.getSuit() != trump
                && p.hasSuit(trump)) {
            return "Yer kartin yok ama kozun var, koz oynamak zorundasin (" + trump + ")";
        }

        // Üstten atma kuralı: yer suitinden oynayacaksan, ortada koz YOKSA
        // (yani henüz çakılma olmadıysa) ve elinde yerdeki en yüksek karttan büyük
        // bir kart varsa, ondan büyük bir kart atmak zorundasın.
        if (leadSuit != null && target.getSuit() == leadSuit) {
            // Şu ana kadar oynanan kartlardaki en yüksek lead-suit kartı
            int highestOnTable = 0;
            boolean anyTrumpOnTable = false;
            for (Card c : trick.values()) {
                if (trump != null && c.getSuit() == trump && leadSuit != trump) {
                    anyTrumpOnTable = true;
                }
                if (c.getSuit() == leadSuit && c.getRank() > highestOnTable) {
                    highestOnTable = c.getRank();
                }
            }
            // Çakılma yoksa (ortada koz atan yoksa) üstten atma kuralı geçerli
            if (!anyTrumpOnTable && highestOnTable > 0 && target.getRank() < highestOnTable) {
                // Elimde yerdeki en yüksek karttan büyük lead-suit kart var mı?
                boolean hasBigger = false;
                for (Card c : p.getHand()) {
                    if (c.getSuit() == leadSuit && c.getRank() > highestOnTable) {
                        hasBigger = true; break;
                    }
                }
                if (hasBigger) {
                    return "Ustten atmak zorundasin (yerdeki en buyuk: " + highestOnTable + ")";
                }
            }
        }

        // Remove from hand
        p.removeCard(cardId);

        // First card of trick sets the lead suit
        if (trick.isEmpty()) {
            leadSuit = target.getSuit();
            leadSeat = p.getSeat();
        }
        trick.put(p.getSeat(), target);
        message = p.getName() + " played " + target;

        // Next turn or resolve
        if (trick.size() == 4) {
            resolveTrick();
        } else {
            currentTurn = (currentTurn + 1) % 4;
        }
        return null;
    }

    private void resolveTrick() {
        int winnerSeat = leadSeat;
        Card winningCard = trick.get(leadSeat);

        for (Map.Entry<Integer, Card> e : trick.entrySet()) {
            int seat = e.getKey();
            Card c = e.getValue();
            if (c == winningCard) continue;

            boolean winnerIsTrump = winningCard.getSuit() == trump;
            boolean cIsTrump = c.getSuit() == trump;

            if (cIsTrump && !winnerIsTrump) {
                winningCard = c; winnerSeat = seat;
            } else if (cIsTrump && winnerIsTrump) {
                if (c.getRank() > winningCard.getRank()) {
                    winningCard = c; winnerSeat = seat;
                }
            } else if (!cIsTrump && !winnerIsTrump) {
                if (c.getSuit() == leadSuit && c.getRank() > winningCard.getRank()) {
                    winningCard = c; winnerSeat = seat;
                }
            }
            // if c is not trump and winner is trump -> stays
        }

        int winnerTeam = winnerSeat % 2;
        tricksByTeam[winnerTeam]++;
        lastTrick = new LinkedHashMap<>(trick);
        lastTrickWinnerSeat = winnerSeat;
        message = players.get(winnerSeat).getName() + " wins the trick";
        trick.clear();
        leadSuit = null;
        leadSeat = winnerSeat;
        currentTurn = winnerSeat;

        // Hand finished?
        if (players.get(0).getHand().isEmpty()) {
            finishHand();
        }
    }

    private void finishHand() {
        phase = Phase.FINISHED;
        int declarerTeam = players.get(highestBidderSeat).getTeam();
        int otherTeam = 1 - declarerTeam;
        int declarerTricks = tricksByTeam[declarerTeam];
        int otherTricks = tricksByTeam[otherTeam];

        // Skor:
        //  - Declarer takim ihaleyi tutarsa: aldigi el sayisi kadar +puan
        //  - Tutamazsa (battiysa): -ihale puani (kayip)
        //  - Karsi takim: aldigi el sayisi kadar +puan
        //  - CIKAMAMA: Karsi takim 2 elden AZ aldiysa (yani 0 veya 1 el):
        //    karsi takim ekstra -ihale puani daha alir.
        StringBuilder sb = new StringBuilder();
        sb.append("El bitti. Takim ").append(declarerTeam).append(": ").append(declarerTricks).append(" el");
        sb.append(", Takim ").append(otherTeam).append(": ").append(otherTricks).append(" el. ");

        if (declarerTricks >= highestBid) {
            totalScore[declarerTeam] += declarerTricks;
            totalScore[otherTeam] += otherTricks;
            sb.append("Declarer takim ihaleyi TUTTU (").append(highestBid).append("). +")
              .append(declarerTricks).append(" puan.");
        } else {
            totalScore[declarerTeam] -= highestBid;
            totalScore[otherTeam] += otherTricks;
            sb.append("Declarer takim ihaleyi BATTI (").append(highestBid).append("). -")
              .append(highestBid).append(" puan.");
        }

        // CIKAMAMA cezasi: karsi takim 2 elden az aldiysa
        if (otherTricks < 2) {
            totalScore[otherTeam] -= highestBid;
            sb.append(" Takim ").append(otherTeam).append(" CIKAMADI (sadece ")
              .append(otherTricks).append(" el)! Ekstra -")
              .append(highestBid).append(" ceza.");
        }

        sb.append(" Yeni el icin 'Yeni El' tikla.");
        message = sb.toString();
    }

    public synchronized void newHand() {
        if (phase == Phase.FINISHED && players.size() == 4) {
            startNewHand();
        }
    }

    public synchronized void resetScores() {
        totalScore[0] = 0;
        totalScore[1] = 0;
        handNumber = 0;
        message = "Skor sifirlandi.";
    }

    // ========== Helpers ==========

    private Player findPlayer(String id) {
        return players.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

    public synchronized Map<String, Object> snapshotFor(String playerId) {
        Map<String, Object> out = new HashMap<>();
        out.put("phase", phase.name());
        out.put("message", message);
        out.put("trump", trump == null ? null : trump.name());
        out.put("currentTurn", currentTurn);
        out.put("bidTurn", bidTurn);
        out.put("highestBid", highestBid);
        out.put("highestBidderSeat", highestBidderSeat);
        out.put("tricksTeam0", tricksByTeam[0]);
        out.put("tricksTeam1", tricksByTeam[1]);
        out.put("totalTeam0", totalScore[0]);
        out.put("totalTeam1", totalScore[1]);
        out.put("handNumber", handNumber);
        out.put("sevenTurn", sevenTurn);
        out.put("forcedSevenSeat", forcedSevenSeat);
        out.put("leadSuit", leadSuit == null ? null : leadSuit.name());

        // Played cards (current trick)
        List<Map<String, Object>> played = new ArrayList<>();
        for (Map.Entry<Integer, Card> e : trick.entrySet()) {
            Map<String, Object> m = new HashMap<>();
            m.put("seat", e.getKey());
            m.put("card", e.getValue());
            played.add(m);
        }
        out.put("trick", played);

        // Last trick
        if (lastTrickWinnerSeat >= 0) {
            List<Map<String, Object>> last = new ArrayList<>();
            for (Map.Entry<Integer, Card> e : lastTrick.entrySet()) {
                Map<String, Object> m = new HashMap<>();
                m.put("seat", e.getKey());
                m.put("card", e.getValue());
                last.add(m);
            }
            out.put("lastTrick", last);
            out.put("lastTrickWinner", lastTrickWinnerSeat);
        }

        // Players (with hand visibility rules)
        List<Map<String, Object>> ps = new ArrayList<>();
        Player me = findPlayer(playerId);
        for (Player p : players) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("seat", p.getSeat());
            pm.put("name", p.getName());
            pm.put("team", p.getTeam());
            pm.put("bid", p.getBid());
            pm.put("declarer", p.isDeclarer());
            pm.put("partnerOfDeclarer", p.isPartnerOfDeclarer());
            pm.put("handSize", p.getHand().size());
            pm.put("passed", hasPassed[p.getSeat()]);

            // Visibility:
            //  - Her zaman kendi elin
            //  - Declarer partnerinin eli SADECE koz seçildikten sonra (PLAYING/FINISHED)
            //    herkese açık olur. TRUMP_SELECTION sırasında hâlâ kapalı.
            boolean isMe = (me != null && p.getId().equals(me.getId()));
            boolean partnerOpen = p.isPartnerOfDeclarer()
                    && (phase == Phase.PLAYING || phase == Phase.FINISHED);
            boolean visible = isMe || partnerOpen;
            if (visible) {
                pm.put("hand", p.getHand());
            }
            ps.add(pm);
        }
        out.put("players", ps);

        // Tell the receiver who they are
        if (me != null) {
            out.put("yourSeat", me.getSeat());
            out.put("yourId", me.getId());
        }
        return out;
    }

    public synchronized List<Player> getPlayers() {
        return new ArrayList<>(players);
    }
}
