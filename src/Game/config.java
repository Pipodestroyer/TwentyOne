package Game;

import java.util.Random;
import java.util.Set;

public class config {
    public static final byte JUDGE_HOST = 1;
    public static final byte JUDGE_BANK = 2;
    public static final byte JUDGE_MAFIA = 3;

    public static final byte BET_SINGLE = 1;
    public static final byte BET_DOUBLE = 2;

    private static byte idJudge = JUDGE_HOST;
    private static byte betMode = BET_SINGLE;
    private static byte currentBet;
    private static byte originalBet; // Aka round number
    private static player player1;
    private static player player2;
    private static player nextPlayer;
    private static player currentPlayer;
    private static player lastLoser;
    private static byte numberCardScore; // Go for 21
    private static boolean inGame;
    private static boolean roundOver;
    private static String lastActionMessage = "";
    private static RoundSummary lastRoundSummary;

    public static class RoundSummary {
        public final String winnerName;
        public final String loserName;
        public final String reason;
        public final String p1Name;
        public final int p1Sum;
        public final int p1Chips;
        public final int p1Delta;
        public final String p2Name;
        public final int p2Sum;
        public final int p2Chips;
        public final int p2Delta;
        public final boolean blessTriggered;
        public final boolean gameOver;

        public RoundSummary(String winnerName, String loserName, String reason,
                            String p1Name, int p1Sum, int p1Chips, int p1Delta,
                            String p2Name, int p2Sum, int p2Chips, int p2Delta,
                            boolean blessTriggered, boolean gameOver) {
            this.winnerName = winnerName;
            this.loserName = loserName;
            this.reason = reason;
            this.p1Name = p1Name;
            this.p1Sum = p1Sum;
            this.p1Chips = p1Chips;
            this.p1Delta = p1Delta;
            this.p2Name = p2Name;
            this.p2Sum = p2Sum;
            this.p2Chips = p2Chips;
            this.p2Delta = p2Delta;
            this.blessTriggered = blessTriggered;
            this.gameOver = gameOver;
        }
    }

    private config() {}

    public static byte getJudgeBonusChips(byte idJudge) {
        return switch (idJudge) {
            case JUDGE_BANK -> (byte) 6;
            case JUDGE_MAFIA -> (byte) 2;
            default -> (byte) 0;
        };
    }

    public static String getJudgeName(byte idJudge) {
        return switch (idJudge) {
            case JUDGE_BANK -> "El Banco (+6 fichas, perdedor paga al juez)";
            case JUDGE_MAFIA -> "La Mafia (+2 fichas, comision 1 desde R2)";
            default -> "El Anfitrion (+0 fichas, ganador lleva todo)";
        };
    }

    public static void setConfiguration(byte idJudge, Set<trumpCard> trumpCardPool, player j1, player j2) {
        setConfiguration(idJudge, BET_SINGLE, trumpCardPool, j1, j2);
    }

    public static void setConfiguration(byte idJudge, byte betMode, Set<trumpCard> trumpCardPool, player j1, player j2) {
        config.idJudge = idJudge;
        config.betMode = betMode;
        config.player1 = j1;
        config.player2 = j2;
        config.lastLoser = null;
        originalBet = (byte) 0;
        currentBet = originalBet;
        numberCardScore = (byte) 21;
        roundOver = false;
        lastRoundSummary = null;
        lastActionMessage = "Partida iniciada.";
        Game.trumpCard.setPool(trumpCardPool);
        refillNumberPool();
        if (new Random().nextBoolean()) {
            nextPlayer = j1;
            currentPlayer = j2;
        } else {
            nextPlayer = j2;
            currentPlayer = j1;
        }
        inGame = true;
    }

    private static void refillNumberPool() {
        Game.numberCard.getPool().clear();
        for (int i = 1; i <= 11; i++) {
            Game.numberCard.addToPool((byte) i);
        }
    }

    // Getters and Setters

    public static byte getIdJudge() { return idJudge; }
    public static byte getBetMode() { return betMode; }

    public static player getPlayer1() { return player1; }
    public static player getPlayer2() { return player2; }

    public static player getOpponentOf(player p) {
        if (p == player1) return player2;
        if (p == player2) return player1;
        if (p == currentPlayer) return nextPlayer;
        return currentPlayer;
    }

    public static player getNextPlayer() { return nextPlayer; }
    public static void setNextPlayer(player nextPlayer) { config.nextPlayer = nextPlayer; }

    public static player getCurrentPlayer() { return currentPlayer; }
    public static void setCurrentPlayer(player currentPlayer) { config.currentPlayer = currentPlayer; }

    public static byte getCurrentBet() { return currentBet; }
    public static void setCurrentBet(byte currentBet) { config.currentBet = currentBet; }

    public static byte getOriginalBet() { return originalBet; }
    public static void setOriginalBet(byte originalBet) { config.originalBet = originalBet; }

    public static byte getNumberCardScore() { return numberCardScore; }
    public static void setNumberCardScore(byte numberCardScore) { config.numberCardScore = numberCardScore; }

    public static boolean isInGame() { return inGame; }
    public static boolean isRoundOver() { return roundOver; }

    public static String getLastActionMessage() { return lastActionMessage; }
    public static void setLastActionMessage(String msg) { lastActionMessage = msg != null ? msg : ""; }

    public static RoundSummary getLastRoundSummary() { return lastRoundSummary; }

    // Methods

    public static void changePlayersTurn() {
        player copy = currentPlayer;
        currentPlayer = nextPlayer;
        nextPlayer = copy;
    }

    public static void resetStoodState() {
        if (player1 != null) player1.setStood(false);
        if (player2 != null) player2.setStood(false);
    }

    public static void onPlayerStood(player whoStood) {
        if (player1 != null && player2 != null && player1.isStood() && player2.isStood()) {
            lastActionMessage = whoStood.getName() + " se planto. Fin de la ronda.";
            resolveRound();
        } else {
            lastActionMessage = whoStood.getName() + " se planto.";
            changePlayersTurn();
        }
    }

    public static void recalculateContinuousEffects() {
        byte target = (byte) 21;
        int sharedBet = originalBet;
        int p1Bet = originalBet;
        int p2Bet = originalBet;

        player[] both = {player1, player2};
        for (player owner : both) {
            if (owner == null) continue;
            for (trumpCard tc : owner.getTrumpsInUse()) {
                switch (tc.getName()) {
                    case "Go for 17" -> target = (byte) 17;
                    case "Go for 24" -> target = (byte) 24;
                    case "Go for 27" -> target = (byte) 27;
                    case "One-Up", "Bloodshed" -> {
                        if (betMode == BET_DOUBLE) {
                            if (owner == player1) p2Bet += 1;
                            else p1Bet += 1;
                        } else {
                            sharedBet += 1;
                        }
                    }
                    case "Two-Up" -> {
                        if (betMode == BET_DOUBLE) {
                            if (owner == player1) p2Bet += 2;
                            else p1Bet += 2;
                        } else {
                            sharedBet += 2;
                        }
                    }
                    case "Shield" -> {
                        if (betMode == BET_DOUBLE) {
                            if (owner == player1) p1Bet -= 1;
                            else p2Bet -= 1;
                        } else {
                            sharedBet -= 1;
                        }
                    }
                    case "Shield-Plus" -> {
                        if (betMode == BET_DOUBLE) {
                            if (owner == player1) p1Bet -= 2;
                            else p2Bet -= 2;
                        } else {
                            sharedBet -= 2;
                        }
                    }
                    default -> {}
                }
            }
        }

        numberCardScore = target;
        currentBet = (byte) Math.max(0, Math.min(127, sharedBet));
        if (player1 != null) player1.setPersonalBet((byte) Math.max(0, Math.min(127, p1Bet)));
        if (player2 != null) player2.setPersonalBet((byte) Math.max(0, Math.min(127, p2Bet)));
    }

    public static void startRound() {
        originalBet = (byte) (originalBet + 1);
        currentBet = originalBet;
        numberCardScore = (byte) 21;
        roundOver = false;
        lastRoundSummary = null;
        resetStoodState();
        if (player1 != null) player1.setPersonalBet(originalBet);
        if (player2 != null) player2.setPersonalBet(originalBet);
        refillNumberPool();

        getCurrentPlayer().addTrump(trumpCard.getRandom());
        getCurrentPlayer().addTrump(trumpCard.getRandom());
        getNextPlayer().addTrump(trumpCard.getRandom());
        getNextPlayer().addTrump(trumpCard.getRandom());

        numberCard c1 = numberCard.getRandom();
        if (c1 != null) getCurrentPlayer().addNumber(c1.getNumber(), getNextPlayer());
        numberCard c2 = numberCard.getRandom();
        if (c2 != null) getNextPlayer().addNumber(c2.getNumber(), getCurrentPlayer());

        numberCard c3 = numberCard.getRandom();
        if (c3 != null) getCurrentPlayer().addNumber(c3.getNumber());
        numberCard c4 = numberCard.getRandom();
        if (c4 != null) getNextPlayer().addNumber(c4.getNumber());

        lastActionMessage = "Ronda " + originalBet + " iniciada. Turno de " + currentPlayer.getName() + ".";
    }

    public static void resolveRound() {
        if (roundOver || player1 == null || player2 == null) {
            return;
        }
        recalculateContinuousEffects();

        int sum1 = player1.getTotalNumberSum();
        int sum2 = player2.getTotalNumberSum();
        int target = numberCardScore;
        int p1Before = player1.getChips();
        int p2Before = player2.getChips();

        player winner = null;
        player loser = null;
        String reason;

        if (sum1 == sum2) {
            reason = "Empate con " + sum1 + "/" + target;
        } else if (sum1 <= target && sum2 <= target) {
            if (target - sum1 < target - sum2) {
                winner = player1;
                loser = player2;
            } else {
                winner = player2;
                loser = player1;
            }
            reason = winner.getName() + " quedo mas cerca de " + target;
        } else if (sum1 > target && sum2 > target) {
            if (sum1 - target < sum2 - target) {
                winner = player1;
                loser = player2;
            } else {
                winner = player2;
                loser = player1;
            }
            reason = "Ambos se pasaron de " + target + "; " + winner.getName() + " se paso menos";
        } else {
            if (sum1 > target) {
                winner = player2;
                loser = player1;
            } else {
                winner = player1;
                loser = player2;
            }
            reason = loser.getName() + " se paso de " + target;
        }

        boolean blessTriggered = false;
        if (winner != null && loser != null) {
            int betToPay = (betMode == BET_DOUBLE) ? loser.getPersonalBet() : currentBet;
            trumpCard blessCard = trumpCard.getTrump("Bless");
            if (blessCard != null && loser.hasTrumpInUse(blessCard) && betToPay >= loser.getChips()) {
                blessTriggered = true;
                betToPay = 0;
            }

            int loserNewChips = Math.max(0, loser.getChips() - betToPay);
            loser.setChips((byte) loserNewChips);

            int winnerGain = switch (idJudge) {
                case JUDGE_BANK -> 0;
                case JUDGE_MAFIA -> (originalBet > 1 && betToPay > 0) ? (betToPay - 1) : betToPay;
                default -> betToPay;
            };
            int winnerNewChips = Math.min(127, winner.getChips() + winnerGain);
            winner.setChips((byte) winnerNewChips);
            lastLoser = loser;
        }

        inGame = (player1.getChips() > 0) && (player2.getChips() > 0);
        roundOver = true;

        int p1Delta = player1.getChips() - p1Before;
        int p2Delta = player2.getChips() - p2Before;
        String winnerName = (winner != null) ? winner.getName() : "EMPATE";
        String loserName = (loser != null) ? loser.getName() : "";

        lastRoundSummary = new RoundSummary(
                winnerName, loserName, reason,
                player1.getName(), sum1, player1.getChips(), p1Delta,
                player2.getName(), sum2, player2.getChips(), p2Delta,
                blessTriggered, !inGame
        );
        lastActionMessage = reason + (blessTriggered ? " (Bless evito la bancarrota)" : "");
    }

    public static void surrender(player surrenderingPlayer) {
        if (player1 == null || player2 == null) return;
        player winner = getOpponentOf(surrenderingPlayer);
        int p1Before = player1.getChips();
        int p2Before = player2.getChips();

        surrenderingPlayer.setChips((byte) 0);
        inGame = false;
        roundOver = true;

        lastRoundSummary = new RoundSummary(
                winner != null ? winner.getName() : "",
                surrenderingPlayer.getName(),
                surrenderingPlayer.getName() + " se rindio",
                player1.getName(), player1.getTotalNumberSum(), player1.getChips(), player1.getChips() - p1Before,
                player2.getName(), player2.getTotalNumberSum(), player2.getChips(), player2.getChips() - p2Before,
                false, true
        );
        lastActionMessage = surrenderingPlayer.getName() + " se rindio.";
    }

    public static void advanceToNextRound() {
        if (!inGame || player1 == null || player2 == null) {
            return;
        }
        player1.getTrumpsInUse().clear();
        player1.getNumberCards().clear();
        player2.getTrumpsInUse().clear();
        player2.getNumberCards().clear();
        resetStoodState();
        refillNumberPool();

        if (lastLoser != null) {
            currentPlayer = lastLoser;
            nextPlayer = getOpponentOf(lastLoser);
        }
        startRound();
    }

    public static void endRound() {
        resolveRound();
        if (inGame) {
            advanceToNextRound();
        }
    }
}
