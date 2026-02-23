package events;

import com.fasterxml.jackson.databind.JsonNode;
import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.GameUnit;
import structures.basic.Card;
import structures.basic.Tile;

/**
 * CardClicked
 * ---------------------------------------------------------------------
 * Handles the logic when a player clicks a card in their hand.
 * Core functionalities include:
 * 1. Selecting/Deselecting a card.
 * 2. Highlighting valid summon tiles for units.
 * 3. Highlighting valid targets for spells and artifacts.
 * ---------------------------------------------------------------------
 */
public class CardClicked implements EventProcessor {

    // =================================================================================
    // 1. Main Event Processing Entry Point
    // =================================================================================

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        // 1. Debounce mechanism: 100ms cooldown to prevent rapid-fire clicks
        long currentTime = System.currentTimeMillis();
        if (currentTime - gameState.lastActionTime < 100) {
            return;
        }
        gameState.lastActionTime = currentTime;

        int handPosition = message.get("position").asInt();
        
        // Safety check for hand size
        if (handPosition > gameState.humanPlayer.getHand().size()) {
            return;
        }
        
        // Retrieve card from Hand
        Card clickedCard = gameState.humanPlayer.getHand().get(handPosition - 1);
        
        // Prevent interaction with empty slots (cards already played)
        if (clickedCard == null) {
            return;
        }

        // 2. Deselection Logic: If clicking the already selected card, clear selection
        if (gameState.selectionState.selectedHandPosition == handPosition) {
            BasicCommands.drawCard(out, clickedCard, handPosition, 0);
            clearAllHighlights(out, gameState);
            gameState.selectionState.clear();
            return;
        }

        // 3. Switch Selection Logic: Clear previous unit or card selection
        if (gameState.selectionState.selectedUnit != null) {
            removeHighlight(out, gameState, gameState.selectionState.selectedUnit);
            gameState.selectionState.selectedUnit = null;
        }
        
        // Reset the visual border of the previously selected card
        if (gameState.selectionState.selectedHandPosition != -1) {
            int oldPos = gameState.selectionState.selectedHandPosition;
            if (oldPos <= gameState.humanPlayer.getHand().size()) {
                Card oldCard = gameState.humanPlayer.getHand().get(oldPos - 1);
                // Only redraw if the card still exists in that slot
                if (oldCard != null) {
                    BasicCommands.drawCard(out, oldCard, oldPos, 0);
                }
            }
            clearAllHighlights(out, gameState);
        }
        
        // Update selection state in memory
        gameState.selectionState.selectedCard = clickedCard;
        gameState.selectionState.selectedHandPosition = handPosition;
        
        // Highlight the selected card on the UI
        BasicCommands.drawCard(out, clickedCard, handPosition, 1);

        // 4. Highlight Routing: Unit vs Spell
        if (isUnitCard(clickedCard.getCardname())) {
            highlightSummonableTiles(out, gameState);
        } else {
            highlightSpellTargets(out, gameState, clickedCard);
        }
    }

    // =================================================================================
    // 2. Highlighting & Targeting Logic
    // =================================================================================

    /**
     * Identifies if a card name represents a Unit or a Spell/Artifact.
     */
    private boolean isUnitCard(String name) {
        if (name.equals("Horn of the Forsaken") || name.equals("Dark Terminus") || 
            name.equals("Truestrike") || name.equals("Sundrop Elixir") || 
            name.equals("Beamshock") || name.equals("Wraithling Swarm")) {
            return false;
        }
        return true;
    }

    /**
     * Precise targeting logic for spells.
     * Highlights valid friendly (white) or enemy (red) units based on spell rules.
     */
    private void highlightSpellTargets(ActorRef out, GameState gameState, Card card) {
        String name = card.getCardname();
        
        // Handle "Summon-like" spells separately (they target empty tiles, not units)
        if (name.equals("Wraithling Swarm")) {
            highlightSummonableTiles(out, gameState);
            return;
        }

        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit unit = gameState.board.getUnitAt(x, y);
                if (unit == null) continue;

                boolean isMine = (unit.getOwner() == gameState.humanPlayer);
                boolean isAvatar = (unit.getBasicUnit().getId() == 1 || unit.getBasicUnit().getId() == 2);
                
                int mode = 0; // 0=None, 1=White (Friendly), 2=Red (Enemy)

                // --- Enemy-Targeting Spells ---
                if (!isMine) {
                    // Dark Terminus & Beamshock: Target non-avatar enemy units only
                    if (name.equals("Dark Terminus") || name.equals("Beamshock")) {
                        if (!isAvatar) {
                            mode = 2;
                        }
                    }
                    // Truestrike: Can target any enemy unit (including Avatar)
                    else if (name.equals("Truestrike")) {
                        mode = 2;
                    }
                }
                
                // --- Ally-Targeting Spells ---
                else {
                    // Sundrop Elixir: Can target any friendly unit
                    if (name.equals("Sundrop Elixir")) {
                        mode = 1;
                    }
                    // Horn of the Forsaken: Can only target your own Avatar
                    else if (name.equals("Horn of the Forsaken")) {
                        if (isAvatar) {
                            mode = 1;
                        }
                    }
                }

                // Execute the visual tile drawing
                if (mode != 0) {
                    BasicCommands.drawTile(out, gameState.board.getTile(x, y), mode);
                    try { Thread.sleep(5); } catch (InterruptedException e) {}
                }
            }
        }
    }

    /**
     * Highlights empty tiles adjacent to friendly units for summoning.
     */
    private void highlightSummonableTiles(ActorRef out, GameState gameState) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                Tile tile = gameState.board.getTile(x, y);
                GameUnit unit = gameState.board.getUnitAt(x, y);

                // Rule: Tile must be empty and adjacent to a friendly unit
                if (unit == null && hasFriendlyNeighbor(gameState, x, y)) {
                    BasicCommands.drawTile(out, tile, 1);
                    try { Thread.sleep(5); } catch (InterruptedException e) {}
                }
            }
        }
    }

    // =================================================================================
    // 3. Utility Methods
    // =================================================================================

    /**
     * Resets all tiles on the board to their default state (mode 0).
     */
    private void clearAllHighlights(ActorRef out, GameState gameState) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0);
                try { Thread.sleep(5); } catch (InterruptedException e) {}
            }
        }
    }

    /**
     * Scans the 8 tiles surrounding (x, y) for a unit belonging to the Human player.
     */
    private boolean hasFriendlyNeighbor(GameState gameState, int x, int y) {
        int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, { 0, -1}, { 0, 1}, { 1, -1}, { 1, 0}, { 1, 1}};
        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx >= 0 && nx < gameState.board.getXMax() && ny >= 0 && ny < gameState.board.getYMax()) {
                GameUnit u = gameState.board.getUnitAt(nx, ny);
                if (u != null && u.getOwner() == gameState.humanPlayer) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Removes the highlight from the specific tile occupied by a unit.
     */
    private void removeHighlight(ActorRef out, GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) {
                    BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0);
                    return;
                }
            }
        }
    }
}