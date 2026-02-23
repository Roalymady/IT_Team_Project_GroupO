package structures;

import structures.basic.Player;
import structures.basic.Card;
import java.util.List;
import java.util.ArrayList;

// GamePlayer represents the logical data of a player, including deck, hand, and mana
public class GamePlayer {

    // Visual object reference for the frontend display
    private Player basicPlayer;
    
    // Current available mana points
    private int mana = 0;
    
    // Maximum mana capacity for the current turn
    private int maxMana = 2;
    
    // List of cards remaining in the player's draw pile
    private List<Card> deck;
    
    // List of cards currently held in the player's hand
    private List<Card> hand;

    // Constructor to initialize empty lists for deck and hand
    public GamePlayer() {
        this.deck = new ArrayList<>(); // Initialize deck list
        this.hand = new ArrayList<>(); // Initialize hand list
    }
    
    // Constructor to initialize with a specific basic player object
    public GamePlayer(Player basicPlayer) {
        this(); // Call default constructor
        this.basicPlayer = basicPlayer; // Assign basic player
    }

    // Returns the basic visual player object
    public Player getBasicPlayer() {
        return basicPlayer; // Return visual object
    }

    // Sets the basic visual player object
    public void setBasicPlayer(Player basicPlayer) {
        this.basicPlayer = basicPlayer; // Assign visual object
    }

    // Gets the current mana value
    public int getMana() {
        return mana; // Return current mana
    }

    // Sets the current mana value
    public void setMana(int mana) {
        this.mana = mana; // Update mana
    }
    
    // Gets the maximum mana capacity
    public int getMaxMana() {
        return maxMana; // Return max mana
    }
    
    // Sets the maximum mana capacity
    public void setMaxMana(int maxMana) {
        this.maxMana = maxMana; // Update max mana
    }

    // Returns the list of cards in the deck
    public List<Card> getDeck() {
        return deck; // Return deck list
    }

    // Sets the deck with a provided list of cards
    public void setDeck(List<Card> deck) {
        this.deck = deck; // Assign deck list
    }
    
    // Returns the list of cards in the hand
    public List<Card> getHand() {
        return hand; // Return hand list
    }

    // Sets the hand with a provided list of cards
    public void setHand(List<Card> hand) {
        this.hand = hand; // Assign hand list
    }
}