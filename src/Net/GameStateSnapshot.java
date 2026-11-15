package Net;

import Game.config;
import Game.numberCard;
import Game.player;
import Game.trumpCard;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class GameStateSnapshot {
    public static class CardView {
        public final int number; // -1 if hidden from viewer
        public final boolean hiddenCard;

        public CardView(int number, boolean hiddenCard) {
            this.number = number;
            this.hiddenCard = hiddenCard;
        }
    }

    public static class TrumpView {
        public final String name;
        public final String description;
        public final String type;
        public final int count;

        public TrumpView(String name, String description, String type, int count) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.count = count;
        }
    }

    public String myName;
    public String enemyName;
    public int myChips;
    public int enemyChips;
    public int myBet;
    public int enemyBet;
    public int sharedBet;
    public int betMode;
    public int idJudge;
    public int targetScore;
    public int roundNumber;
    public boolean myTurn;
    public boolean myStood;
    public boolean enemyStood;
    public boolean roundOver;
    public boolean gameOver;
    public String lastActionMessage;
    public List<CardView> myCards = new ArrayList<>();
    public List<CardView> enemyCards = new ArrayList<>();
    public List<TrumpView> myTrumps = new ArrayList<>();
    public List<String> myContinuousTrumps = new ArrayList<>();
    public List<String> enemyContinuousTrumps = new ArrayList<>();
    public String summaryWinner = "";
    public String summaryReason = "";
    public int summaryMyDelta;
    public int summaryEnemyDelta;
    public boolean summaryBless;

    public int getMyTotalSum() {
        int s = 0;
        for (CardView cv : myCards) {
            if (cv.number > 0) s += cv.number;
        }
        return s;
    }

    public int getEnemyVisibleSum() {
        int s = 0;
        for (CardView cv : enemyCards) {
            if (cv.number > 0) s += cv.number;
        }
        return s;
    }

    public int getEnemyHiddenCount() {
        int c = 0;
        for (CardView cv : enemyCards) {
            if (cv.number < 0) c++;
        }
        return c;
    }

    public static GameStateSnapshot fromGameFor(player viewer) {
        player enemy = config.getOpponentOf(viewer);
        GameStateSnapshot s = new GameStateSnapshot();
        s.myName = viewer.getName();
        s.enemyName = enemy != null ? enemy.getName() : "Oponente";
        s.myChips = viewer.getChips();
        s.enemyChips = enemy != null ? enemy.getChips() : 0;
        s.myBet = viewer.getPersonalBet();
        s.enemyBet = enemy != null ? enemy.getPersonalBet() : 0;
        s.sharedBet = config.getCurrentBet();
        s.betMode = config.getBetMode();
        s.idJudge = config.getIdJudge();
        s.targetScore = config.getNumberCardScore();
        s.roundNumber = config.getOriginalBet();
        s.myTurn = (config.getCurrentPlayer() == viewer) && !config.isRoundOver() && config.isInGame();
        s.myStood = viewer.isStood();
        s.enemyStood = enemy != null && enemy.isStood();
        s.roundOver = config.isRoundOver();
        s.gameOver = !config.isInGame();
        s.lastActionMessage = config.getLastActionMessage();

        for (numberCard nc : viewer.getNumberCards()) {
            boolean isBlack = (nc.getHiddenTo() != null);
            s.myCards.add(new CardView(nc.getNumber(), isBlack));
        }

        if (enemy != null) {
            for (numberCard nc : enemy.getNumberCards()) {
                boolean hiddenToMe = (nc.getHiddenTo() == viewer);
                if (hiddenToMe && !config.isRoundOver()) {
                    s.enemyCards.add(new CardView(-1, true));
                } else {
                    s.enemyCards.add(new CardView(nc.getNumber(), hiddenToMe));
                }
            }
            for (trumpCard tc : enemy.getTrumpsInUse()) {
                s.enemyContinuousTrumps.add(tc.getName());
            }
        }

        for (Map.Entry<trumpCard, Byte> entry : viewer.getTrumpCards().entrySet()) {
            trumpCard tc = entry.getKey();
            s.myTrumps.add(new TrumpView(tc.getName(), tc.getDescription(), tc.getType().name(), entry.getValue()));
        }

        for (trumpCard tc : viewer.getTrumpsInUse()) {
            s.myContinuousTrumps.add(tc.getName());
        }

        config.RoundSummary rs = config.getLastRoundSummary();
        if (rs != null) {
            s.summaryWinner = rs.winnerName != null ? rs.winnerName : "";
            s.summaryReason = rs.reason != null ? rs.reason : "";
            s.summaryBless = rs.blessTriggered;
            if (viewer == config.getPlayer1()) {
                s.summaryMyDelta = rs.p1Delta;
                s.summaryEnemyDelta = rs.p2Delta;
            } else {
                s.summaryMyDelta = rs.p2Delta;
                s.summaryEnemyDelta = rs.p1Delta;
            }
        }
        return s;
    }

    private static String b64(String str) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((str == null ? "" : str).getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String token) {
        if (token == null || token.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(b64(myName)).append('|')
          .append(b64(enemyName)).append('|')
          .append(myChips).append('|')
          .append(enemyChips).append('|')
          .append(myBet).append('|')
          .append(enemyBet).append('|')
          .append(sharedBet).append('|')
          .append(betMode).append('|')
          .append(idJudge).append('|')
          .append(targetScore).append('|')
          .append(roundNumber).append('|')
          .append(myTurn).append('|')
          .append(myStood).append('|')
          .append(enemyStood).append('|')
          .append(roundOver).append('|')
          .append(gameOver).append('|')
          .append(b64(lastActionMessage)).append('|');

        sb.append(encodeCards(myCards)).append('|');
        sb.append(encodeCards(enemyCards)).append('|');

        StringBuilder trumpsSb = new StringBuilder();
        for (int i = 0; i < myTrumps.size(); i++) {
            if (i > 0) trumpsSb.append(';');
            TrumpView tv = myTrumps.get(i);
            trumpsSb.append(b64(tv.name)).append(',')
                    .append(b64(tv.description)).append(',')
                    .append(b64(tv.type)).append(',')
                    .append(tv.count);
        }
        sb.append(trumpsSb).append('|');
        sb.append(encodeStrings(myContinuousTrumps)).append('|');
        sb.append(encodeStrings(enemyContinuousTrumps)).append('|');
        sb.append(b64(summaryWinner)).append('|');
        sb.append(b64(summaryReason)).append('|');
        sb.append(summaryMyDelta).append('|');
        sb.append(summaryEnemyDelta).append('|');
        sb.append(summaryBless);
        return sb.toString();
    }

    private static String encodeCards(List<CardView> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(';');
            CardView cv = list.get(i);
            sb.append(cv.number).append(',').append(cv.hiddenCard);
        }
        return sb.toString();
    }

    private static List<CardView> decodeCards(String raw) {
        List<CardView> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;
        for (String item : raw.split(";")) {
            if (item.isEmpty()) continue;
            String[] p = item.split(",");
            list.add(new CardView(Integer.parseInt(p[0]), Boolean.parseBoolean(p[1])));
        }
        return list;
    }

    private static String encodeStrings(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(b64(list.get(i)));
        }
        return sb.toString();
    }

    private static List<String> decodeStrings(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;
        for (String item : raw.split(";")) {
            if (!item.isEmpty()) list.add(unb64(item));
        }
        return list;
    }

    public static GameStateSnapshot deserialize(String line) {
        String[] parts = line.split("\\|", -1);
        GameStateSnapshot s = new GameStateSnapshot();
        int idx = 0;
        s.myName = unb64(parts[idx++]);
        s.enemyName = unb64(parts[idx++]);
        s.myChips = Integer.parseInt(parts[idx++]);
        s.enemyChips = Integer.parseInt(parts[idx++]);
        s.myBet = Integer.parseInt(parts[idx++]);
        s.enemyBet = Integer.parseInt(parts[idx++]);
        s.sharedBet = Integer.parseInt(parts[idx++]);
        s.betMode = Integer.parseInt(parts[idx++]);
        s.idJudge = Integer.parseInt(parts[idx++]);
        s.targetScore = Integer.parseInt(parts[idx++]);
        s.roundNumber = Integer.parseInt(parts[idx++]);
        s.myTurn = Boolean.parseBoolean(parts[idx++]);
        s.myStood = Boolean.parseBoolean(parts[idx++]);
        s.enemyStood = Boolean.parseBoolean(parts[idx++]);
        s.roundOver = Boolean.parseBoolean(parts[idx++]);
        s.gameOver = Boolean.parseBoolean(parts[idx++]);
        s.lastActionMessage = unb64(parts[idx++]);
        s.myCards = decodeCards(parts[idx++]);
        s.enemyCards = decodeCards(parts[idx++]);

        String trumpsRaw = parts[idx++];
        if (!trumpsRaw.isEmpty()) {
            for (String item : trumpsRaw.split(";")) {
                if (item.isEmpty()) continue;
                String[] tp = item.split(",");
                s.myTrumps.add(new TrumpView(unb64(tp[0]), unb64(tp[1]), unb64(tp[2]), Integer.parseInt(tp[3])));
            }
        }
        s.myContinuousTrumps = decodeStrings(parts[idx++]);
        s.enemyContinuousTrumps = decodeStrings(parts[idx++]);
        s.summaryWinner = unb64(parts[idx++]);
        s.summaryReason = unb64(parts[idx++]);
        s.summaryMyDelta = Integer.parseInt(parts[idx++]);
        s.summaryEnemyDelta = Integer.parseInt(parts[idx++]);
        s.summaryBless = Boolean.parseBoolean(parts[idx]);
        return s;
    }
}
