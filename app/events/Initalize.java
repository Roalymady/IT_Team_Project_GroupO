package events;

import com.fasterxml.jackson.databind.JsonNode;
import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.GameUnit;
import structures.GamePlayer;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.Card;
import utils.BasicObjectBuilders;
import utils.OrderedCardLoader;
import utils.StaticConfFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Initalize
 * ---------------------------------------------------------------------
 * Handles the startup phase of the game.
 * It is responsible for:
 * 1. Loading the board tiles.
 * 2. Setting up Player and AI states (Mana, Health, Decks).
 * 3. Dealing starting hands.
 * 4. Summoning the Avatar units (Heroes) for both players.
 * ---------------------------------------------------------------------
 */
public class Initalize implements EventProcessor {

    // =================================================================================
    // Card Configuration Registry
    // =================================================================================
    
    // Define all 20 card configuration files
    private static final String[] ALL_CARD_FILES = {
        // === Group A (Human Player): Indices 0-9 ===
        "conf/gameconfs/cards/1_1_c_u_bad_omen.json",
        "conf/gameconfs/cards/1_2_c_s_hornoftheforsaken.json",
        "conf/gameconfs/cards/1_3_c_u_gloom_chaser.json",
        "conf/gameconfs/cards/1_4_c_u_shadow_watcher.json",
        "conf/gameconfs/cards/1_5_c_s_wraithling_swarm.json",
        "conf/gameconfs/cards/1_6_c_u_nightsorrow_assassin.json",
        "conf/gameconfs/cards/1_7_c_u_rock_pulveriser.json",
        "conf/gameconfs/cards/1_8_c_s_dark_terminus.json",
        "conf/gameconfs/cards/1_9_c_u_bloodmoon_priestess.json",
        "conf/gameconfs/cards/1_a1_c_u_shadowdancer.json",

        // === Group B (AI Player): Indices 10-19 ===
        "conf/gameconfs/cards/2_1_c_u_skyrock_golem.json",
        "conf/gameconfs/cards/2_2_c_u_swamp_entangler.json",
        "conf/gameconfs/cards/2_3_c_u_silverguard_knight.json",
        "conf/gameconfs/cards/2_4_c_u_saberspine_tiger.json",
        "conf/gameconfs/cards/2_5_c_s_beamshock.json",
        "conf/gameconfs/cards/2_6_c_u_young_flamewing.json",
        "conf/gameconfs/cards/2_7_c_u_silverguard_squire.json",
        "conf/gameconfs/cards/2_8_c_u_ironcliff_guardian.json",
        "conf/gameconfs/cards/2_9_c_s_sundrop_elixir.json",
        "conf/gameconfs/cards/2_a1_c_s_truestrike.json"
    };

    // =================================================================================
    // Main Initialization Logic
    // =================================================================================

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        gameState.gameInitalised = true;

        try {
            // Small delay to ensure frontend is ready
            Thread.sleep(500);

            // --------------------------------------------------------
            // 1. Initialize the Board
            // --------------------------------------------------------
            gameState.board = new structures.Board();
            for (int x = 0; x < gameState.board.getXMax(); x++) {
                for (int y = 0; y < gameState.board.getYMax(); y++) {
                    // Draw basic grid with no highlighting (mode 0)
                    BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0);
                    try { Thread.sleep(2); } catch (InterruptedException e) {}
                }
            }
            Thread.sleep(100);
            BasicCommands.addPlayer1Notification(out, "Board Loaded", 2);

            // --------------------------------------------------------
            // 2. Initialize Logical Players
            // --------------------------------------------------------
            
            // Human Player Setup
            gameState.humanPlayer = new GamePlayer();
            gameState.humanPlayer.setMana(3);
            gameState.humanPlayer.setMaxMana(3);
            gameState.humanPlayer.setDeck(OrderedCardLoader.getPlayer1Cards(3));

            // AI Player Setup
            gameState.aiPlayer = new GamePlayer();
            gameState.aiPlayer.setMana(3);
            gameState.aiPlayer.setMaxMana(3);
            gameState.aiPlayer.setDeck(OrderedCardLoader.getPlayer2Cards(2));

            // --------------------------------------------------------
            // 3. Initialize Visual Player Objects (Avatars on UI)
            // --------------------------------------------------------
            
            // Set Player 1 (Human) Stats UI
            Player humanAvatarObj = new Player(20, 3);
            gameState.humanPlayer.setBasicPlayer(humanAvatarObj);
            BasicCommands.setPlayer1Health(out, gameState.humanPlayer.getBasicPlayer());
            BasicCommands.setPlayer1Mana(out, gameState.humanPlayer.getBasicPlayer());

            // Set Player 2 (AI) Stats UI
            Player aiAvatarObj = new Player(20, 3);
            gameState.aiPlayer.setBasicPlayer(aiAvatarObj);
            BasicCommands.setPlayer2Health(out, gameState.aiPlayer.getBasicPlayer());
            BasicCommands.setPlayer2Mana(out, gameState.aiPlayer.getBasicPlayer());

            // --------------------------------------------------------
            // 4. Deal Starting Hands
            // --------------------------------------------------------
            
            // Draw 3 cards for Human (Visual + Logical)
            drawStartingHand(out, gameState.humanPlayer, 3);
            

            // Draw 3 cards for AI (Logical only, not shown on UI)
            drawStartingHand(null, gameState.aiPlayer, 3);

            

            // Draw 3 cards for AI (Logical only, not shown on UI)
            // for (int i = 0; i < 3; i++) {
            //     if (!gameState.aiPlayer.getDeck().isEmpty()) {
            //         gameState.aiPlayer.getHand().add(gameState.aiPlayer.getDeck().remove(0));
            //     }
            // }

            // --------------------------------------------------------
            // 5. Summon Hero Units (Avatars) on the board
            // --------------------------------------------------------

            // Summon Human Hero at (1, 2)
            Unit humanUnitObj = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, 1, Unit.class);
            Tile humanStartTile = gameState.board.getTile(1, 2);
            humanUnitObj.setPositionByTile(humanStartTile);
            
            GameUnit humanHero = new GameUnit(humanUnitObj, 20, 2);
            humanHero.setOwner(gameState.humanPlayer);
            gameState.board.setUnitAt(1, 2, humanHero);
            
            BasicCommands.drawUnit(out, humanUnitObj, humanStartTile);
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            BasicCommands.setUnitAttack(out, humanUnitObj, humanHero.getCurrentAttack());
            BasicCommands.setUnitHealth(out, humanUnitObj, humanHero.getCurrentHealth());

            // Summon AI Hero at (7, 2)
            Unit aiUnitObj = BasicObjectBuilders.loadUnit(StaticConfFiles.aiAvatar, 2, Unit.class);
            Tile aiStartTile = gameState.board.getTile(7, 2);
            aiUnitObj.setPositionByTile(aiStartTile);
            
            GameUnit aiHero = new GameUnit(aiUnitObj, 20, 2);
            aiHero.setOwner(gameState.aiPlayer);
            gameState.board.setUnitAt(7, 2, aiHero);
            
            BasicCommands.drawUnit(out, aiUnitObj, aiStartTile);
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            BasicCommands.setUnitAttack(out, aiUnitObj, aiHero.getCurrentAttack());
            BasicCommands.setUnitHealth(out, aiUnitObj, aiHero.getCurrentHealth());

            BasicCommands.addPlayer1Notification(out, "Battle Start!", 2);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =================================================================================
    // Helper Methods
    // =================================================================================

    // /**
    //  * Loads the Human Player's deck with 20 cards (2 copies of 10 types).
    //  */
    // private List<Card> loadHumanDeck() {
    //     List<Card> deck = new ArrayList<>();
    //     int cardId = 100;
        
    //     // Loop 2 times to add 2 copies of each card
    //     for (int k = 0; k < 2; k++) {
    //         for (int i = 0; i < 10; i++) { // Indices 0-9 are Human cards
    //             Card c = BasicObjectBuilders.loadCard(ALL_CARD_FILES[i], cardId++, Card.class);
    //             deck.add(c);
    //         }
    //     }
    //     Collections.shuffle(deck); // Randomize
    //     return deck;
    // }

    // /**
    //  * Loads the AI Player's deck with 20 cards (2 copies of 10 types).
    //  */
    // private List<Card> loadAIDeck() {
    //     List<Card> deck = new ArrayList<>();
    //     int cardId = 200;
        
    //     // Loop 2 times to add 2 copies of each card
    //     for (int k = 0; k < 2; k++) {
    //         for (int i = 10; i < 20; i++) { // Indices 10-19 are AI cards
    //             Card c = BasicObjectBuilders.loadCard(ALL_CARD_FILES[i], cardId++, Card.class);
    //             deck.add(c);
    //         }
    //     }
    //     Collections.shuffle(deck); // Randomize
    //     return deck;
    // }

    /**
     * Helper to draw a specific number of cards for the Human and Ai player
     * If statements controls if it shows up on UI or not
     */



    private void drawStartingHand(ActorRef out, GamePlayer player, int count) {
        for (int i = 0; i < count; i++) {
            if (!player.getDeck().isEmpty()) {
                Card card = player.getDeck().remove(0);
                player.getHand().add(card);

                if (out == out) {
                BasicCommands.drawCard(out, card, player.getHand().size(), 0);
                try { Thread.sleep(200); } catch (InterruptedException e) {}
                }
            }
        }
    }
}