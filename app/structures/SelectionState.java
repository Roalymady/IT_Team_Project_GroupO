package structures;

import structures.basic.Card;

// SelectionState tracks the player's current interaction status
// It records which unit or card is currently selected on the UI
public class SelectionState {

    // Reference to the currently selected unit on the board
    public GameUnit selectedUnit;
    
    // Reference to the currently selected card from the hand
    public Card selectedCard;
    
    // The index of the selected card in the hand (ranges from 1 to 6)
    public int selectedHandPosition;
    
    // Constructor initializes the state with no active selection
    public SelectionState() {
        this.selectedUnit = null; // No unit selected by default
        this.selectedCard = null; // No card selected by default
        this.selectedHandPosition = -1; // Default position indicating no card
    }
    
    // Resets all selection fields to their default empty values
    public void clear() {
        this.selectedUnit = null; // Clear unit selection
        this.selectedCard = null; // Clear card selection
        this.selectedHandPosition = -1; // Reset hand position
    }
}