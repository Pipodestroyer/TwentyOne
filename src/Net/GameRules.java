package Net;

public class GameRules {
    public final String hostName;
    public final byte idJudge;
    public final byte betMode;
    public final byte initialChips;
    public final int turnTimeLimitSeconds;

    public GameRules(String hostName, byte idJudge, byte betMode, byte initialChips) {
        this(hostName, idJudge, betMode, initialChips, 45);
    }

    public GameRules(String hostName, byte idJudge, byte betMode, byte initialChips, int turnTimeLimitSeconds) {
        this.hostName = (hostName == null || hostName.isBlank()) ? "Jugador" : hostName.trim();
        this.idJudge = idJudge;
        this.betMode = betMode;
        this.initialChips = initialChips;
        this.turnTimeLimitSeconds = turnTimeLimitSeconds <= 0 ? 0 : Math.max(30, turnTimeLimitSeconds);
    }
}
