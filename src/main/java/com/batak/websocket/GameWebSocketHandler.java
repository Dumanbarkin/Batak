package com.batak.websocket;

import com.batak.game.GameRoom;
import com.batak.model.Player;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    // Single room only
    private final GameRoom room = new GameRoom("batak123");

    // sessionId -> playerId
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    // playerId -> session
    private final Map<String, WebSocketSession> playerSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Wait for "join" message before adding to room
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.path("type").asText();

        switch (type) {
            case "join": {
                String name = node.path("name").asText("Player");
                String pwd = node.path("password").asText("");
                if (!room.checkPassword(pwd)) {
                    sendError(session, "Wrong password");
                    return;
                }
                if (room.isFull()) {
                    sendError(session, "Room is full (max 4 players). 5th player rejected.");
                    session.close(CloseStatus.NORMAL);
                    return;
                }
                String playerId = UUID.randomUUID().toString();
                Player p = room.addPlayer(playerId, name);
                if (p == null) {
                    sendError(session, "Could not join (room full).");
                    return;
                }
                sessionToPlayer.put(session.getId(), playerId);
                playerSessions.put(playerId, session);

                Map<String, Object> ack = new HashMap<>();
                ack.put("type", "joined");
                ack.put("playerId", playerId);
                ack.put("seat", p.getSeat());
                send(session, ack);
                broadcastState();
                break;
            }
            case "bid": {
                String pid = sessionToPlayer.get(session.getId());
                int amount = node.path("amount").asInt(0);
                String err = room.placeBid(pid, amount);
                if (err != null) sendError(session, err);
                broadcastState();
                break;
            }
            case "trump": {
                String pid = sessionToPlayer.get(session.getId());
                String suit = node.path("suit").asText();
                String err = room.selectTrump(pid, suit);
                if (err != null) sendError(session, err);
                broadcastState();
                break;
            }
            case "play": {
                String pid = sessionToPlayer.get(session.getId());
                String cardId = node.path("cardId").asText();
                String err = room.playCard(pid, cardId);
                if (err != null) sendError(session, err);
                broadcastState();
                break;
            }
            case "newHand": {
                room.newHand();
                broadcastState();
                break;
            }
            case "resetScores": {
                room.resetScores();
                broadcastState();
                break;
            }
            default:
                sendError(session, "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String pid = sessionToPlayer.remove(session.getId());
        if (pid != null) {
            playerSessions.remove(pid);
            room.removePlayer(pid);
            broadcastState();
        }
    }

    private void broadcastState() {
        for (Map.Entry<String, WebSocketSession> e : playerSessions.entrySet()) {
            String playerId = e.getKey();
            WebSocketSession session = e.getValue();
            if (!session.isOpen()) continue;
            try {
                Map<String, Object> snapshot = room.snapshotFor(playerId);
                Map<String, Object> msg = new HashMap<>();
                msg.put("type", "state");
                msg.put("state", snapshot);
                send(session, msg);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void send(WebSocketSession session, Map<String, Object> payload) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
            }
        }
    }

    private void sendError(WebSocketSession session, String msg) throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("type", "error");
        err.put("message", msg);
        send(session, err);
    }
}
