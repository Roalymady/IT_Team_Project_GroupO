package structures;

import structures.basic.Card;

/**
 * GameState
 * ---------------------------------------------------------------------
 * Global controller state for the game.
 * It acts as a central repository for the board, player information, 
 * selection states, and global counters.
 * ---------------------------------------------------------------------
 */
public class GameState {

    // Flag to check if the game has been fully initialized
    public boolean gameInitalised = false;
    
    // The logical game board
    public Board board = null;
    
    // Logical player objects for Human and AI
    public GamePlayer humanPlayer = null;
    public GamePlayer aiPlayer = null;
    
    // Cooldown timer to prevent rapid-click (debounce) issues
    public long lastActionTime = 0;
    
    // Global ID counter for units.
    // ID 1 is reserved for Human Hero, 2 for AI Hero, so new units start at 3
    public int unitIdCounter = 3;

    // Current selection status of cards or units
    public SelectionState selectionState = new SelectionState();

    // Human starts on turn 1, AI is also prepared for turn 1.
    public int humanTurnNumber = 1;
    public int aiTurnNumber = 1;

    public int manaForTurn(int turnNumber) {
        return Math.min(turnNumber + 1, 9);
    }

    
    //Inner class to manage the selection state of units and cards.
    public class SelectionState {
        
        public GameUnit selectedUnit;
        public Card selectedCard;
        public int selectedHandPosition; // Valid range: 1-6
        
        
        //Constructor to initialize selection state with null/default values.
        public SelectionState() {
            selectedUnit = null;
            selectedCard = null;
            selectedHandPosition = -1;
        }
        
        
        //Clears all current selections in the UI and memory.
        public void clear() {
            selectedUnit = null;
            selectedCard = null;
            selectedHandPosition = -1;
        }
    }
}