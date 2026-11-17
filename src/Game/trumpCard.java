package Game;

import Game.TrumpAtribs.trumpEffect;
import Game.TrumpAtribs.trumpType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class trumpCard {
    private final String name;
    private final String description;
    private final trumpType type;
    private final trumpEffect effect;
    private static Set<trumpCard> pool = new LinkedHashSet<>();

    trumpCard(String name, String description, trumpType type, trumpEffect effect) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.effect = effect;
    }

    trumpCard(String name) {
        this.name = name;
        this.description = null;
        this.type = null;
        this.effect = null;
    }

    public static Set<trumpCard> getPool() { return pool; }
    public static void setPool(Set<trumpCard> pool) { trumpCard.pool = pool; }

    public static boolean isNotInPool(String trump) {
        var tc = new trumpCard(trump);
        return !pool.contains(tc);
    }

    public static trumpCard idInPool(int i) {
        if (i < 0 || i >= pool.size()) { return null; }
        return (trumpCard) pool.toArray()[i];
    }

    public static trumpCard getTrump(String name) {
        if (name == null || isNotInPool(name)) { return null; }
        for (trumpCard tc : pool) {
            if (Objects.equals(tc.getName(), name)) {
                return tc;
            }
        }
        return null;
    }

    public static trumpCard getRandom() {
        if (pool.isEmpty()) { return null; }
        int chosenNumber = new Random().nextInt(pool.size());
        return (trumpCard) pool.toArray()[chosenNumber];
    }

    public static boolean trumpChance() {
        int chance = new Random().nextInt(5);
        return chance == 0;
    }

    // Getters
    public String getName() { return name; }
    public trumpType getType() { return type; }
    public String getDescription() { return description; }
    public trumpEffect getEffect() { return effect; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        trumpCard object = (trumpCard) o;
        return Objects.equals(object.getName(), getName());
    }

    @Override
    public int hashCode() { return Objects.hash(name); }

    @Override
    public String toString() { return name + ": " + description; }

    private static void removeGoForCards(player user, player op) {
        String[] goForNames = {"Go for 17", "Go for 24", "Go for 27"};
        for (String goForName : goForNames) {
            trumpCard tc = getTrump(goForName);
            if (tc != null) {
                if (user != null) user.removeTrumpInUse(tc);
                if (op != null) op.removeTrumpInUse(tc);
            }
        }
    }

    private static void drawSpecificNumber(player user, byte num) {
        if (numberCard.isInPool(num)) {
            user.addNumber(num);
            config.setLastActionMessage(user.getName() + " uso " + num + "-Card y obtuvo el " + num + ".");
        } else {
            config.setLastActionMessage(user.getName() + " uso " + num + "-Card, pero el " + num + " no estaba en la piscina.");
        }
    }
    static {
        pool.add(new trumpCard(
                "Bless",
                "Previene perder fichas si la apuesta te dejaria en bancarrota.",
                trumpType.CONTINUOUS,
                (user, _) -> config.setLastActionMessage(user.getName() + " coloco Bless en la mesa.")
        ));
        pool.add(new trumpCard(
                "2-Card",
                "Robas el numero 2 si esta disponible en la piscina.",
                trumpType.INSTANTANEOUS,
                (user, _) -> drawSpecificNumber(user, (byte) 2)
        ));
        pool.add(new trumpCard(
                "3-Card",
                "Robas el numero 3 si esta disponible en la piscina.",
                trumpType.INSTANTANEOUS,
                (user, _) -> drawSpecificNumber(user, (byte) 3)
        ));
        pool.add(new trumpCard(
                "4-Card",
                "Robas el numero 4 si esta disponible en la piscina.",
                trumpType.INSTANTANEOUS,
                (user, _) -> drawSpecificNumber(user, (byte) 4)
        ));
        pool.add(new trumpCard(
                "5-Card",
                "Robas el numero 5 si esta disponible en la piscina.",
                trumpType.INSTANTANEOUS,
                (user, _) -> drawSpecificNumber(user, (byte) 5)
        ));
        pool.add(new trumpCard(
                "6-Card",
                "Robas el numero 6 si esta disponible en la piscina.",
                trumpType.INSTANTANEOUS,
                (user, _) -> drawSpecificNumber(user, (byte) 6)
        ));
        pool.add(new trumpCard(
                "7-Card",
                "Robas el numero 7 si esta disponible en la piscina.",
                trumpType.INSTANTANEOUS,
                (user, _) -> drawSpecificNumber(user, (byte) 7)
        ));
        pool.add(new trumpCard(
                "Go for 17",
                "Cambia el numero objetivo de la ronda a 17.",
                trumpType.CONTINUOUS,
                (user, op) -> {
                    removeGoForCards(user, op);
                    config.setLastActionMessage(user.getName() + " cambio el objetivo a 17.");
                }
        ));
        pool.add(new trumpCard(
                "Go for 24",
                "Cambia el numero objetivo de la ronda a 24.",
                trumpType.CONTINUOUS,
                (user, op) -> {
                    removeGoForCards(user, op);
                    config.setLastActionMessage(user.getName() + " cambio el objetivo a 24.");
                }
        ));
        pool.add(new trumpCard(
                "Go for 27",
                "Cambia el numero objetivo de la ronda a 27.",
                trumpType.CONTINUOUS,
                (user, op) -> {
                    removeGoForCards(user, op);
                    config.setLastActionMessage(user.getName() + " cambio el objetivo a 27.");
                }
        ));
        pool.add(new trumpCard(
                "One-Up",
                "Sube la apuesta por 1 (en Individual, sube solo la del oponente).",
                trumpType.CONTINUOUS,
                (user, op) -> config.setLastActionMessage(user.getName() + " coloco One-Up (+1 apuesta"
                        + (config.getBetMode() == config.BET_DOUBLE && op != null ? " a " + op.getName() : "") + ").")
        ));
        pool.add(new trumpCard(
                "Two-Up",
                "Sube la apuesta por 2 (en Individual, sube solo la del oponente).",
                trumpType.CONTINUOUS,
                (user, op) -> config.setLastActionMessage(user.getName() + " coloco Two-Up (+2 apuesta"
                        + (config.getBetMode() == config.BET_DOUBLE && op != null ? " a " + op.getName() : "") + ").")
        ));
        pool.add(new trumpCard(
                "Shield",
                "Baja la apuesta por 1 (en Individual, baja solo tu propia apuesta).",
                trumpType.CONTINUOUS,
                (user, _) -> config.setLastActionMessage(user.getName() + " coloco Shield (-1 apuesta"
                        + (config.getBetMode() == config.BET_DOUBLE ? " propia" : "") + ").")
        ));
        pool.add(new trumpCard(
                "Shield-Plus",
                "Baja la apuesta por 2 (en Individual, baja solo tu propia apuesta).",
                trumpType.CONTINUOUS,
                (user, _) -> config.setLastActionMessage(user.getName() + " coloco Shield-Plus (-2 apuesta"
                        + (config.getBetMode() == config.BET_DOUBLE ? " propia" : "") + ").")
        ));
        pool.add(new trumpCard(
                "Bloodshed",
                "Sube la apuesta por 1 (al oponente en Individual) y robas una carta especial.",
                trumpType.CONTINUOUS,
                (user, op) -> {
                    user.addTrump(trumpCard.getRandom());
                    config.setLastActionMessage(user.getName() + " coloco Bloodshed (+1 apuesta"
                            + (config.getBetMode() == config.BET_DOUBLE && op != null ? " a " + op.getName() : "")
                            + " y robo comodin).");
                }
        ));
        pool.add(new trumpCard(
                "Destroy",
                "Destruye la carta especial continua mas reciente del oponente.",
                trumpType.INSTANTANEOUS,
                (user, op) -> {
                    if (op != null && !op.getTrumpsInUse().isEmpty()) {
                        trumpCard removed = op.getTrumpsInUse().getLast();
                        op.removeTrumpInUse(removed);
                        config.setLastActionMessage(user.getName() + " destruyo " + removed.getName() + " de " + op.getName() + ".");
                    } else {
                        config.setLastActionMessage(user.getName() + " uso Destroy, pero el oponente no tenia cartas continuas.");
                    }
                }
        ));
        pool.add(new trumpCard(
                "Friendship",
                "Ambos jugadores roban dos cartas especiales.",
                trumpType.INSTANTANEOUS,
                (user, op) -> {
                    user.addTrump(trumpCard.getRandom());
                    user.addTrump(trumpCard.getRandom());
                    if (op != null) {
                        op.addTrump(trumpCard.getRandom());
                        op.addTrump(trumpCard.getRandom());
                    }
                    config.setLastActionMessage(user.getName() + " uso Friendship (+2 comodines para ambos).");
                }
        ));
        pool.add(new trumpCard(
                "Reincarnation",
                "Destruye la carta continua mas reciente del oponente y robas una carta especial.",
                trumpType.INSTANTANEOUS,
                (user, op) -> {
                    if (op != null && !op.getTrumpsInUse().isEmpty()) {
                        trumpCard removed = op.getTrumpsInUse().getLast();
                        op.removeTrumpInUse(removed);
                        user.addTrump(trumpCard.getRandom());
                        config.setLastActionMessage(user.getName() + " uso Reincarnation: destruyo " + removed.getName() + " y robo un comodin.");
                    } else {
                        config.setLastActionMessage(user.getName() + " uso Reincarnation, pero el oponente no tenia cartas continuas.");
                    }
                }
        ));
        pool.add(new trumpCard(
                "Refresh",
                "Devuelves tus numeros a la piscina y empiezas con dos cartas nuevas.",
                trumpType.INSTANTANEOUS,
                (user, op) -> {
                    boolean hadSkull = user.hasSkullCard();
                    List<numberCard> currentCopy = new ArrayList<>(user.getNumberCards());
                    for (numberCard nc : currentCopy) {
                        if (nc.getNumber() != numberCard.SKULL_CARD) {
                            user.removeNumber(nc.getNumber());
                        }
                    }
                    numberCard first = numberCard.getRandom();
                    if (first != null) {
                        user.addNumber(first.getNumber(), op);
                    }
                    numberCard second = numberCard.getRandom();
                    if (second != null) {
                        user.addNumber(second.getNumber());
                    }
                    if (hadSkull && !user.hasSkullCard()) {
                        user.getNumberCards().add(new numberCard(numberCard.SKULL_CARD));
                    }
                    config.setLastActionMessage(user.getName() + " uso Refresh y reinicio su mano.");
                }
        ));
        pool.add(new trumpCard(
                "Remove",
                "Remueve el ultimo numero del oponente devolviendolo a la piscina.",
                trumpType.INSTANTANEOUS,
                (user, op) -> {
                    if (op != null && op.getNumberCards().size() > 1) {
                        numberCard targetCard = null;
                        for (int idx = op.getNumberCards().size() - 1; idx >= 1; idx--) {
                            if (op.getNumberCards().get(idx).getNumber() != numberCard.SKULL_CARD) {
                                targetCard = op.getNumberCards().get(idx);
                                break;
                            }
                        }
                        if (targetCard != null) {
                            byte removed = targetCard.getNumber();
                            op.removeNumber(removed);
                            config.setLastActionMessage(user.getName() + " removio la ultima carta de " + op.getName() + ".");
                        } else {
                            config.setLastActionMessage(user.getName() + " uso Remove, pero la Calavera no puede quitarse.");
                        }
                    } else {
                        config.setLastActionMessage(user.getName() + " uso Remove, pero no puede quitar la carta oculta.");
                    }
                }
        ));
        pool.add(new trumpCard(
                "Return",
                "Devuelves tu ultimo numero a la piscina.",
                trumpType.INSTANTANEOUS,
                (user, _) -> {
                    if (user.getNumberCards().size() > 1) {
                        numberCard targetCard = null;
                        for (int idx = user.getNumberCards().size() - 1; idx >= 1; idx--) {
                            if (user.getNumberCards().get(idx).getNumber() != numberCard.SKULL_CARD) {
                                targetCard = user.getNumberCards().get(idx);
                                break;
                            }
                        }
                        if (targetCard != null) {
                            byte removed = targetCard.getNumber();
                            user.removeNumber(removed);
                            config.setLastActionMessage(user.getName() + " devolvio su ultima carta (" + removed + ") a la piscina.");
                        } else {
                            config.setLastActionMessage(user.getName() + " uso Return, pero la Calavera no puede devolverse.");
                        }
                    } else {
                        config.setLastActionMessage(user.getName() + " uso Return, pero no puede devolver su carta oculta.");
                    }
                }
        ));
        pool.add(new trumpCard(
                "Exchange",
                "Intercambia los ultimos numeros entre ambos jugadores.",
                trumpType.INSTANTANEOUS,
                (user, op) -> {
                    if (op != null && !user.getNumberCards().isEmpty() && !op.getNumberCards().isEmpty()) {
                        numberCard userLast = user.getNumberCards().getLast();
                        numberCard opLast = op.getNumberCards().getLast();
                        if (userLast.getNumber() == numberCard.SKULL_CARD || opLast.getNumber() == numberCard.SKULL_CARD) {
                            config.setLastActionMessage("La Calavera (∞) no puede intercambiarse.");
                            return;
                        }
                        byte numYou = userLast.getNumber();
                        byte numOp = opLast.getNumber();
                        boolean userSlotHidden = userLast.getHiddenTo() != null;
                        boolean opSlotHidden = opLast.getHiddenTo() != null;

                        user.removeNumber(numYou);
                        op.removeNumber(numOp);

                        if (userSlotHidden) {
                            user.addNumber(numOp, op);
                        } else {
                            user.addNumber(numOp);
                        }
                        if (opSlotHidden) {
                            op.addNumber(numYou, user);
                        } else {
                            op.addNumber(numYou);
                        }
                        config.setLastActionMessage(user.getName() + " intercambio su ultima carta con " + op.getName() + ".");
                    } else {
                        config.setLastActionMessage("No hay suficientes cartas para intercambiar.");
                    }
                }
        ));
        pool.add(new trumpCard(
                "Disservice",
                "Hace robar un numero aleatorio al oponente.",
                trumpType.INSTANTANEOUS,
                (user, op) -> {
                    numberCard drawn = numberCard.getRandom();
                    if (op != null && drawn != null) {
                        op.addNumber(drawn.getNumber());
                        config.setLastActionMessage(user.getName() + " uso Disservice: " + op.getName() + " recibio el " + drawn.getNumber() + ".");
                    } else {
                        config.setLastActionMessage("No quedan numeros en la piscina.");
                    }
                }
        ));
        pool.add(new trumpCard(
                "Perfect Draw",
                "Robas el numero disponible que mas te acerque al objetivo sin pasarte.",
                trumpType.INSTANTANEOUS,
                (user, _) -> {
                    if (numberCard.getPool().isEmpty()) {
                        config.setLastActionMessage("No quedan numeros en la piscina.");
                        return;
                    }
                    if (config.getCurrentPlayer().isBusted()) {
                        config.setLastActionMessage("Estas sobre el objetivo.");
                        return;
                    }
                    int currentSum = user.getTotalNumberSum();
                    int target = config.getNumberCardScore();
                    byte bestUnder = -1;
                    int bestUnderSum = Integer.MIN_VALUE;
                    byte smallestOverall = Byte.MAX_VALUE;

                    for (numberCard candidate : numberCard.getPool()) {
                        byte val = candidate.getNumber();
                        if (val < smallestOverall) {
                            smallestOverall = val;
                        }
                        int newSum = currentSum + val;
                        if (newSum <= target && newSum > bestUnderSum) {
                            bestUnderSum = newSum;
                            bestUnder = val;
                        }
                    }

                    byte chosen = (bestUnder != -1) ? bestUnder : smallestOverall;
                    user.addNumber(chosen);
                    config.setLastActionMessage(user.getName() + " uso Perfect Draw y obtuvo el " + chosen + ".");
                }
        ));
        pool.add(new trumpCard(
                "Hush",
                "Robas un numero de la piscina que queda oculto para el oponente.",
                trumpType.INSTANTANEOUS,
                (user, op) -> {
                    numberCard drawn = numberCard.getRandom();
                    if (drawn != null) {
                        user.addNumber(drawn.getNumber(), op);
                        config.setLastActionMessage(user.getName() + " uso Hush y robo una carta oculta.");
                    } else {
                        config.setLastActionMessage("No quedan numeros en la piscina.");
                    }
                }
        ));
    }
}
