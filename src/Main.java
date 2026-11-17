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
        } else {
            Application.launch(TwentyOneApp.class, args);
        }
    }
}