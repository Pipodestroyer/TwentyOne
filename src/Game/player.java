package Game;

import Game.TrumpAtribs.trumpType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class player {
    private String name;
    private byte chips;
    private boolean stood;
    private byte personalBet;
    private Map<trumpCard, Byte> trumpCards; // id, cantidad
    private ArrayList<trumpCard> trumpsInUse;
    private ArrayList<numberCard> numberCards;

    public player(String name, byte chips) {
        setName(name);
        setChips(chips);
        setStood(false);
        setPersonalBet((byte) 0);
        setNumberCards(new ArrayList<>());
        setTrumpCards(new LinkedHashMap<>());
        setTrumpsInUse(new ArrayList<>());
    }

    // Getters and Setters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public byte getChips() { return chips; }
    public void setChips(byte chips) { this.chips = chips; }

    public boolean isStood() { return stood; }
    public void setStood(boolean stood) { this.stood = stood; }

    public byte getPersonalBet() { return personalBet; }
    public void setPersonalBet(byte personalBet) { this.personalBet = personalBet; }

    public Map<trumpCard, Byte> getTrumpCards() { return trumpCards; }
    public void setTrumpCards(Map<trumpCard, Byte> trumpCards) { this.trumpCards = trumpCards; }

    public ArrayList<numberCard> getNumberCards() { return numberCards; }
    public void setNumberCards(ArrayList<numberCard> numberCards) { this.numberCards = numberCards; }

    public ArrayList<trumpCard> getTrumpsInUse() { return trumpsInUse; }
    public void setTrumpsInUse(ArrayList<trumpCard> trumpsInUse) { this.trumpsInUse = trumpsInUse; }

    public int getTotalNumberSum() {
        int sum = 0;
        for (numberCard nc : numberCards) {
            sum += nc.getNumber();
        }
        return sum;
    }

    public boolean isBusted() {
        return getTotalNumberSum() > config.getNumberCardScore();
    }

    public int getVisibleNumberSum(player viewer) {
        int sum = 0;
        for (numberCard nc : numberCards) {
            if (nc.getHiddenTo() != viewer) {
                sum += nc.getNumber();
            }
        }
        return sum;
    }

    // Methods for the inventory (Maps)

    public void addNumber(byte number) {
        numberCard.removeFromPool(number);
        numberCards.add(new numberCard(number));
    }

    public void addNumber(byte number, player ply) {
        numberCard.removeFromPool(number);
        numberCard nc = new numberCard(number);
        nc.setHidden(ply);
        numberCards.add(nc);
    }

    public void removeNumber(byte number) {
        numberCards.remove(new numberCard(number));
        numberCard.addToPool(number);
    }

    public boolean hasNumber(byte number) {
        return numberCards.contains(new numberCard(number));
    }

    public void addTrump(trumpCard trump) {
        if (trump == null) {
            return;
        }
        if (hasTrumpSaved(trump)) {
            trumpCards.put(trump, (byte) (trumpCards.get(trump) + 1));
        } else {
            trumpCards.put(trump, (byte) 1);
        }
    }

    public void removeTrump(trumpCard trump) { trumpCards.remove(trump); }

    public boolean hasTrumpSaved(trumpCard trump) { return trumpCards.containsKey(trump); }

    public void useTrump(trumpCard trump) {
        if (trump == null || !hasTrumpSaved(trump)) {
            return;
        }

        int remaining = trumpCards.get(trump) - 1;
        if (remaining <= 0) {
            removeTrump(trump);
        } else {
            trumpCards.put(trump, (byte) remaining);
        }

        player opponent = config.getOpponentOf(this);
        trump.getEffect().apply(this, opponent);
        config.recordPlayedTrump(this, trump);

        if (trump.getType() == trumpType.CONTINUOUS) {
            trumpsInUse.add(trump);
        }

        config.recalculateContinuousEffects();
        config.resetStoodState();
    }

    public boolean hasTrumpInUse(trumpCard trump) { return trumpsInUse.contains(trump); }

    public void removeTrumpInUse(trumpCard trump) { trumpsInUse.remove(trump); }

    public void drawNumber() {
        if (isBusted()) {
            config.setLastActionMessage(this.name + " ya se paso de " + config.getNumberCardScore() + " y no puede comer mas cartas.");
            return;
        }
        numberCard drawn = numberCard.getRandom();
        if (drawn != null) {
            if (trumpCard.trumpChance()) {
                trumpCard chosenTrump = trumpCard.getRandom();
                this.addTrump(chosenTrump);
            }
            this.addNumber(drawn.getNumber());
            config.setLastActionMessage(this.name + " robo el numero " + drawn.getNumber() + ".");
        } else {
            config.setLastActionMessage("No quedan numeros en la piscina.");
        }
        config.resetStoodState();
        config.changePlayersTurn();
    }

    public void keepHand() {
        this.stood = true;
        config.onPlayerStood(this);
    }
}
