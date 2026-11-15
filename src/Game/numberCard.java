package Game;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class numberCard {
    private final byte number;
    private player hiddenTo;
    private static Set<numberCard> pool = new HashSet<>();

    numberCard(byte number) {
        this.number = number;
        hiddenTo = null;
    }

    public byte getNumber() {return number;}
    public void setHidden(player ply) {
        hiddenTo = ply;
    }
    public player getHiddenTo() {return hiddenTo;}

    public static Set<numberCard> getPool() {
        return pool;
    }

    public static void removeFromPool(byte number) {
        numberCard nc = new numberCard(number);
        pool.remove(nc);
    }
    public static void addToPool(byte number) {
        numberCard nc = new numberCard(number);
        pool.add(nc);
    }

    public static boolean isInPool(byte number) {
        numberCard nc = new numberCard(number);
        return pool.contains(nc);
    }

    public static numberCard getRandom() {
        if (pool.isEmpty()) {
            return null;
        }
        int chosenNumber = new Random().nextInt(pool.size());
        return (numberCard) pool.toArray()[chosenNumber];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        numberCard object = (numberCard) o;
        return object.getNumber() == getNumber();
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    @Override
    public String toString() {
        if (hiddenTo == null) {return String.valueOf(number);}
        return number+" hidden to "+hiddenTo.getName();
    }

    static {
        new numberCard((byte)1);
        new numberCard((byte)2);
        new numberCard((byte)3);
        new numberCard((byte)4);
        new numberCard((byte)5);
        new numberCard((byte)6);
        new numberCard((byte)7);
        new numberCard((byte)8);
        new numberCard((byte)9);
        new numberCard((byte)10);
        new numberCard((byte)11);
    }
}
