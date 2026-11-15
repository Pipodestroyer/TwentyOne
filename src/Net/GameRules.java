package Net;

public class GameRules {
    public final String hostName;
    public final byte idJudge;
    public final byte betMode;
    public final byte initialChips;

    public GameRules(String hostName, byte idJudge, byte betMode, byte initialChips) {
        this.hostName = (hostName == null || hostName.isBlank()) ? "Jugador" : hostName.trim();
        this.idJudge = idJudge;
        this.betMode = betMode;
        this.initialChips = initialChips;
    }
}
