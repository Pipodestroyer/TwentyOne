import Game.config;
import Game.numberCard;
import Game.player;
import Game.trumpCard;
import Net.RailwayRelayServer;
import UI.TwentyOneApp;
import javafx.application.Application;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean isServerArg = false;
        if (args != null) {
            for (String a : args) {
                if ("--server".equalsIgnoreCase(a)) {
                    isServerArg = true;
                    break;
                }
            }
        }
        if (isServerArg || System.getenv("RAILWAY_ENVIRONMENT") != null) {
            RailwayRelayServer.main(args);
        } else if (args != null && args.length > 0 && "--console".equalsIgnoreCase(args[0])) {
            runConsoleMode();
        } else {
            Application.launch(TwentyOneApp.class, args);
        }
    }

    private static void runConsoleMode() {
        Scanner sc = new Scanner(System.in);
        player j1 = new player("Daniel", (byte) 7);
        player j2 = new player("Galindo", (byte) 7);
        config.setConfiguration(
                (byte) 1,
                trumpCard.getPool(),
                j1,
                j2
        );

        config.startRound();

        while (config.isInGame()) {
            System.out.print("[");
            for (numberCard i : config.getNextPlayer().getNumberCards()) {
                if (i.getHiddenTo() == config.getCurrentPlayer()) {
                    System.out.print("X");
                    if (!(config.getNextPlayer().getNumberCards().getLast() == i)) {
                        System.out.print(", ");
                    }
                    continue;
                }
                System.out.print(i);
                if (!(config.getNextPlayer().getNumberCards().getLast() == i)) {
                    System.out.print(", ");
                }
            }
            System.out.println("]");
            System.out.println(config.getCurrentPlayer().getNumberCards());
            System.out.println("Eligid que hacer");
            System.out.println("(a) Comer carta");
            System.out.println("(b) Plantarse");
            System.out.println("(c) Mirar las trumps que tenes");
            System.out.println("(d) Acabar ronda");
            String option = sc.next();
            switch (option) {
                case "a" -> config.getCurrentPlayer().drawNumber();
                case "b" -> config.getCurrentPlayer().keepHand();
                case "c" -> System.out.println(config.getCurrentPlayer().getTrumpCards().toString());
                case "d" -> {
                    System.out.println(config.getCurrentPlayer().getName() + config.getCurrentPlayer().getNumberCards());
                    System.out.println(config.getNextPlayer().getName() + config.getNextPlayer().getNumberCards());
                    config.endRound();
                    System.out.println(config.getCurrentPlayer().getName() + config.getCurrentPlayer().getChips());
                    System.out.println(config.getNextPlayer().getName() + config.getNextPlayer().getChips());
                }
                default -> {
                    if (trumpCard.isNotInPool(option)) {
                        continue;
                    }
                    config.getCurrentPlayer().useTrump(trumpCard.getTrump(option));
                }
            }
        }
    }
}
