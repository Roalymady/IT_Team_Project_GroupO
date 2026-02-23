package events;

import com.fasterxml.jackson.databind.JsonNode;
import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.GameUnit;
import structures.AttackLogic;
import structures.MovementRule;
import structures.basic.Card;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;
import java.util.ArrayList;
import java.util.Collections;

/**
 * TileClicked
 * ---------------------------------------------------------------------
 * Handles all interaction logic when a player clicks a tile on the board.
 * Core functionalities include:
 * 1. Selecting/Deselecting units
 * 2. Moving units
 * 3. Attacking enemies
 * 4. Playing cards (Summoning units, Casting spells, Equipping artifacts)
 * ---------------------------------------------------------------------
 */
public class TileClicked implements EventProcessor {

    // =================================================================================
    // 1. Main Event Processing Entry Point
    // =================================================================================

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {

        // 0. Game initialization check
        if (!gameState.gameInitalised) {
            return;
        }

        // 1. Debounce mechanism: 200ms cooldown to prevent front-end duplicate events
        long currentTime = System.currentTimeMillis();
        if (currentTime - gameState.lastActionTime < 200) {
            return;
        }
        gameState.lastActionTime = currentTime;

        // 2. Parse click data
        int tilex = message.get("tilex").asInt();
        int tiley = message.get("tiley").asInt();
        
        Tile clickedTile = gameState.board.getTile(tilex, tiley);
        GameUnit unitOnTile = gameState.board.getUnitAt(tilex, tiley);

        // Get current selection state (Unit or Card)
        GameUnit selectedUnit = gameState.selectionState.selectedUnit;
        Card selectedCard = gameState.selectionState.selectedCard;

        // --------------------------------------------------------
        // Branch A: Unit Clicked
        // --------------------------------------------------------
        if (unitOnTile != null) {

            // A1. A unit is currently selected -> Try to attack or switch target
            if (selectedUnit != null) {
                
                // Case 1: Clicked on self -> Deselect
                if (selectedUnit == unitOnTile) {
                    deselect(out, gameState); 
                } 
                else {
                    // Case 2: Target is an enemy -> Attempt to attack
                    if (selectedUnit.getOwner() != unitOnTile.getOwner()) {
                        
                        // Calculate Chebyshev distance (allows diagonal attacks)
                        int dist = Math.max(
                            Math.abs(tilex - getUnitX(gameState, selectedUnit)),
                            Math.abs(tiley - getUnitY(gameState, selectedUnit))
                        );

                        // Check condition: Distance <= 1 AND hasn't attacked this turn
                        if (dist <= 1 && !selectedUnit.hasAttacked()) {
                            // Clear highlights before attack to prevent visual artifacts
                            clearSummonHighlights(out, gameState);
                            
                            // Execute attack logic
                            AttackLogic.executeAttack(selectedUnit, unitOnTile, out, gameState);
                            
                            // Deselect after attack and update action time
                            deselect(out, gameState);
                            gameState.lastActionTime = System.currentTimeMillis();
                        } else {
                            BasicCommands.addPlayer1Notification(out, "Out of range / Already attacked!", 2);
                        }
                    } 
                    // Case 3: Target is an ally -> Switch selection
                    else {
                        deselect(out, gameState);
                        selectUnit(out, gameState, unitOnTile, clickedTile);
                    }
                }
            }
            // A2. A card is currently selected -> Try to cast spell on unit
            else if (selectedCard != null) {
                // Attempt to execute targeted spell (Heal, Damage, Buff)
                if (castSpellOrArtifact(out, gameState, unitOnTile, selectedCard)) {
                    gameState.selectionState.clear();
                    clearSummonHighlights(out, gameState);
                }
            }
            // A3. No selection -> Try to select a unit
            else {
                // Can only select own units
                if (unitOnTile.getOwner() == gameState.humanPlayer) {
                    selectUnit(out, gameState, unitOnTile, clickedTile);
                } else {
                    BasicCommands.addPlayer1Notification(out, "That's an enemy unit!", 2);
                }
            }

        }
        // --------------------------------------------------------
        // Branch B: Empty Tile Clicked
        // --------------------------------------------------------
        else {

            // B1. A card is currently selected -> Try to summon or cast area spell
            if (selectedCard != null) {
                
                // Special Spell: Wraithling Swarm (Summon spell, requires empty tile)
                if (selectedCard.getCardname().equals("Wraithling Swarm")) {
                    if (checkMana(out, gameState, selectedCard)) {
                        // Must be cast near an ally
                        if (hasFriendlyNeighbor(gameState, tilex, tiley)) {
                            BasicCommands.addPlayer1Notification(out, "Swarm!", 2);
                            summonSwarm(out, gameState, tilex, tiley);
                            
                            payManaAndRemoveCard(out, gameState, selectedCard);
                            gameState.selectionState.clear();
                            clearSummonHighlights(out, gameState);
                        } else {
                            BasicCommands.addPlayer1Notification(out, "Cast near ally!", 2);
                        }
                    }
                    return;
                }

                // Normal Unit Summon Logic
                if (isUnitCard(selectedCard.getCardname())) {
                    // Rule: Can only summon adjacent to friendly units
                    if (hasFriendlyNeighbor(gameState, tilex, tiley)) {
                        if (checkMana(out, gameState, selectedCard)) {
                            summonUnit(out, gameState, clickedTile, selectedCard);
                        }
                    } else {
                        BasicCommands.addPlayer1Notification(out, "Summon near ally!", 2);
                    }
                } else {
                    // Clicked empty tile but card is not a unit or Swarm -> Invalid target
                    BasicCommands.addPlayer1Notification(out, "Cannot play here!", 2);
                }
                return;
            }

            // B2. A unit is currently selected -> Try to move
            if (selectedUnit != null) {
                // Security check: Can only move own units
                if (selectedUnit.getOwner() != gameState.humanPlayer) {
                    deselect(out, gameState);
                    return;
                }

                // Status check: Stunned or already moved
                if (selectedUnit.isStunned()) {
                    BasicCommands.addPlayer1Notification(out, "Unit is Stunned!", 2);
                    return;
                }
                if (selectedUnit.hasMoved()) {
                    BasicCommands.addPlayer1Notification(out, "Unit has moved!", 2);
                    return;
                }

                // Execute move
                if (MovementRule.isValidMove(gameState.board, selectedUnit, tilex, tiley)) {
                    // [Crucial] Clear highlights before move animation starts
                    clearSummonHighlights(out, gameState);

                    moveUnitVisually(out, gameState, selectedUnit, clickedTile);
                    updateBoardPosition(gameState, selectedUnit, tilex, tiley);

                    selectedUnit.setHasMoved(true);
                    gameState.selectionState.clear();
                    gameState.lastActionTime = System.currentTimeMillis();
                } else {
                    BasicCommands.addPlayer1Notification(out, "Invalid Move!", 2);
                }
            }
        }
    }

    // =================================================================================
    // 2. Summoning & Spells Logic
    // =================================================================================

    /**
     * Core method to summon a unit, handling mana deduction, unit instantiation,
     * and "Opening Gambit" (Battlecry) effects.
     */
    private void summonUnit(ActorRef out, GameState gameState, Tile tile, Card card) {
        try {
            String unitConf = getUnitConfigByCardName(card.getCardname());
            int[] stats = getUnitStatsByCardName(card.getCardname());

            // Deduct mana and remove card from hand
            payManaAndRemoveCard(out, gameState, card);

            // Create logical unit object
            int unitId = gameState.unitIdCounter++;
            Unit unitObj = BasicObjectBuilders.loadUnit(unitConf, unitId, Unit.class);
            unitObj.setPositionByTile(tile);

            // Create GameState unit wrapper
            GameUnit gameUnit = new GameUnit(unitObj, stats[1], stats[0]);
            gameUnit.setOwner(gameState.humanPlayer);
            gameUnit.setName(card.getCardname());

            // Update board data
            gameState.board.setUnitAt(tile.getTilex(), tile.getTiley(), gameUnit);

            // Frontend: Draw unit first, delay slightly, then set stats to avoid UI jumps
            BasicCommands.drawUnit(out, unitObj, tile);
            try { Thread.sleep(100); } catch (InterruptedException e) { }
            BasicCommands.setUnitAttack(out, unitObj, stats[0]);
            BasicCommands.setUnitHealth(out, unitObj, stats[1]);

            // ---------------------------------------------------
            // Handle "Opening Gambit" effects
            // ---------------------------------------------------

            // 1. Nightsorrow Assassin: Destroy nearby weak unit
            if (card.getCardname().equals("Nightsorrow Assassin")) {
                GameUnit target = findNearbyWeakEnemy(gameState, tile.getTilex(), tile.getTiley());
                if (target != null) {
                    BasicCommands.addPlayer1Notification(out, "Assassination!", 2);
                    playEffect(out, gameState, target, "f1_inmolation");
                    AttackLogic.dealDamage(target, 999, out, gameState);
                }
            }

            // 2. Gloom Chaser: Summon a Wraithling behind
            if (card.getCardname().equals("Gloom Chaser")) {
                int targetX = tile.getTilex() - 1;
                int targetY = tile.getTiley();
                // Ensure target position is within bounds and empty
                if (targetX >= 0 && gameState.board.getUnitAt(targetX, targetY) == null) {
                    BasicCommands.addPlayer1Notification(out, "Opening Gambit!", 2);
                    summonWraithlingAt(out, gameState, gameState.board.getTile(targetX, targetY));
                }
            }

            // 3. Silverguard Squire: Buff adjacent allies
            if (card.getCardname().equals("Silverguard Squire")) {
                BasicCommands.addPlayer1Notification(out, "Squire Buff!", 2);
                GameUnit avatar = getAvatar(gameState, gameState.humanPlayer);
                
                if (avatar != null) {
                    int ax = getUnitX(gameState, avatar);
                    int ay = getUnitY(gameState, avatar);
                    int[][] checkPos = { {ax - 1, ay}, {ax + 1, ay} };
                    
                    for (int[] pos : checkPos) {
                        if (pos[0] >= 0 && pos[0] < gameState.board.getXMax()) {
                            GameUnit u = gameState.board.getUnitAt(pos[0], pos[1]);
                            // Only buff own non-avatar units
                            if (u != null && u.getOwner() == gameState.humanPlayer && !isAvatar(u)) {
                                u.setCurrentAttack(u.getCurrentAttack() + 1);
                                u.setCurrentHealth(u.getCurrentHealth() + 1);
                                u.setMaxHealth(u.getMaxHealth() + 1);
                                
                                playEffect(out, gameState, u, "f1_buff");
                                BasicCommands.setUnitAttack(out, u.getBasicUnit(), u.getCurrentAttack());
                                BasicCommands.setUnitHealth(out, u.getBasicUnit(), u.getCurrentHealth());
                            }
                        }
                    }
                }
            }

            // Set action status
            if (card.getCardname().equals("Saberspine Tiger")) {
                // Rush: Can act immediately
                gameUnit.setHasMoved(false);
                gameUnit.setHasAttacked(false);
            } else {
                // Normal units: Cannot act on summon turn
                gameUnit.setHasMoved(true);
                gameUnit.setHasAttacked(true);
            }

            // Clean up state
            gameState.selectionState.clear();
            clearSummonHighlights(out, gameState);
            BasicCommands.addPlayer1Notification(out, "Summoned!", 2);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the casting of targeted spells or equipping artifacts.
     */
    private boolean castSpellOrArtifact(ActorRef out, GameState gameState, GameUnit targetUnit, Card card) {
        String cardName = card.getCardname();

        // 1. Horn of the Forsaken (Artifact)
        if (cardName.equals("Horn of the Forsaken")) {
            if (targetUnit.getOwner() == gameState.humanPlayer && isAvatar(targetUnit)) {
                if (!checkMana(out, gameState, card)) return false;
                
                payManaAndRemoveCard(out, gameState, card);
                BasicCommands.addPlayer1Notification(out, "Artifact Equipped!", 2);
                playEffect(out, gameState, targetUnit, "f1_buff");
                
                targetUnit.setArtifactName("Horn of the Forsaken");
                targetUnit.setArtifactDurability(3);
                return true;
            } else {
                BasicCommands.addPlayer1Notification(out, "Target YOUR Avatar!", 2);
                return false;
            }
        }

        // 2. Dark Terminus (Destroy non-avatar unit)
        if (cardName.equals("Dark Terminus")) {
            if (targetUnit.getOwner() != gameState.humanPlayer && !isAvatar(targetUnit)) {
                if (!checkMana(out, gameState, card)) return false;
                
                payManaAndRemoveCard(out, gameState, card);
                BasicCommands.addPlayer1Notification(out, "Terminated!", 2);
                playEffect(out, gameState, targetUnit, "f1_inmolation");
                
                Tile t = gameState.board.getTile(getUnitX(gameState, targetUnit), getUnitY(gameState, targetUnit));
                AttackLogic.dealDamage(targetUnit, 999, out, gameState);
                // Summon Wraithling after death
                summonWraithlingAt(out, gameState, t);
                return true;
            } else {
                BasicCommands.addPlayer1Notification(out, "Target Enemy Unit!", 2);
                return false;
            }
        }

        // 3. Sundrop Elixir (Heal)
        if (cardName.equals("Sundrop Elixir")) {
            if (targetUnit.getOwner() == gameState.humanPlayer) {
                if (!checkMana(out, gameState, card)) return false;
                
                payManaAndRemoveCard(out, gameState, card);
                playEffect(out, gameState, targetUnit, "f1_buff");
                
                int newHp = Math.min(targetUnit.getCurrentHealth() + 5, targetUnit.getMaxHealth());
                targetUnit.setCurrentHealth(newHp);
                BasicCommands.setUnitHealth(out, targetUnit.getBasicUnit(), newHp);
                
                // If Avatar, sync player health display
                if (isAvatar(targetUnit)) {
                    gameState.humanPlayer.getBasicPlayer().setHealth(newHp);
                    BasicCommands.setPlayer1Health(out, gameState.humanPlayer.getBasicPlayer());
                }
                return true;
            } else {
                BasicCommands.addPlayer1Notification(out, "Target YOUR unit!", 2);
                return false;
            }
        }

        // 4. Truestrike (Direct Damage)
        if (cardName.equals("Truestrike")) {
            if (targetUnit.getOwner() != gameState.humanPlayer) {
                if (!checkMana(out, gameState, card)) return false;
                
                payManaAndRemoveCard(out, gameState, card);
                playEffect(out, gameState, targetUnit, "f1_inmolation");
                AttackLogic.dealDamage(targetUnit, 2, out, gameState);
                return true;
            } else {
                BasicCommands.addPlayer1Notification(out, "Target ENEMY!", 2);
                return false;
            }
        }
        return false;
    }

    /**
     * Helper method: Summon a Wraithling (1/1 token) at a specific tile.
     */
    public static void summonWraithlingAt(ActorRef out, GameState gameState, Tile tile) {
        try {
            String unitConf = "conf/gameconfs/units/wraithling.json";
            int unitId = gameState.unitIdCounter++;
            
            Unit unitObj = BasicObjectBuilders.loadUnit(unitConf, unitId, Unit.class);
            unitObj.setPositionByTile(tile);
            
            GameUnit gameUnit = new GameUnit(unitObj, 1, 1);
            gameUnit.setOwner(gameState.humanPlayer);
            gameUnit.setName("Wraithling");
            
            gameState.board.setUnitAt(tile.getTilex(), tile.getTiley(), gameUnit);
            
            BasicCommands.drawUnit(out, unitObj, tile);
            try { Thread.sleep(100); } catch (InterruptedException e) { }
            BasicCommands.setUnitAttack(out, unitObj, 1);
            BasicCommands.setUnitHealth(out, unitObj, 1);
            
            // Tokens cannot move on the turn they are summoned
            gameUnit.setHasMoved(true);
            gameUnit.setHasAttacked(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method: Implement Wraithling Swarm logic (Randomly summon around center).
     */
    private void summonSwarm(ActorRef out, GameState gameState, int centerX, int centerY) {
        // Summon the first one at the center
        summonWraithlingAt(out, gameState, gameState.board.getTile(centerX, centerY));
        
        // Find empty neighbor tiles
        ArrayList<Tile> neighbors = new ArrayList<>();
        int[][] directions = { {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1} };
        
        for (int[] dir : directions) {
            int nx = centerX + dir[0];
            int ny = centerY + dir[1];
            if (nx >= 0 && nx < gameState.board.getXMax() && ny >= 0 && ny < gameState.board.getYMax()) {
                if (gameState.board.getUnitAt(nx, ny) == null) {
                    neighbors.add(gameState.board.getTile(nx, ny));
                }
            }
        }
        
        // Randomly select positions for remaining units
        Collections.shuffle(neighbors);
        int count = 0;
        for (Tile t : neighbors) {
            if (count >= 2) break; // Summon at most 2 extra
            summonWraithlingAt(out, gameState, t);
            count++;
        }
    }

    // =================================================================================
    // 3. Selection & Visual Control Logic
    // =================================================================================

    // Select a unit and highlight relevant tiles
    private void selectUnit(ActorRef out, GameState gameState, GameUnit unit, Tile tile) {
        gameState.selectionState.selectedUnit = unit;
        BasicCommands.drawTile(out, tile, 1);
        BasicCommands.addPlayer1Notification(out, "Unit Selected", 2);

        // --- Feature: Show movement range and attack targets ---
        highlightValidMoves(out, gameState, unit);
        highlightAttackTargets(out, gameState, unit);
    }

    // Clear current selection state and visuals
    private void deselect(ActorRef out, GameState gameState) {
        // If a card was selected, redraw the hand border
        if (gameState.selectionState.selectedCard != null) {
            BasicCommands.drawCard(out, gameState.selectionState.selectedCard, gameState.selectionState.selectedHandPosition, 0);
        }
        clearSummonHighlights(out, gameState);
        gameState.selectionState.clear();
    }

    /**
     * Handle unit movement visual animation (including L-shape pathing).
     */
    private void moveUnitVisually(ActorRef out, GameState gameState, GameUnit unit, Tile targetTile) {
        // Flying units teleport
        if (unit.getName() != null && unit.getName().equals("Young Flamewing")) {
            BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
            return;
        }
        
        // Normal unit movement animation
        int startX = getUnitX(gameState, unit);
        int startY = getUnitY(gameState, unit);
        int diffX = targetTile.getTilex() - startX;
        int diffY = targetTile.getTiley() - startY;
        int distance = Math.abs(diffX) + Math.abs(diffY);

        // If Manhattan distance is 2, it might be an L-shape move
        if (distance == 2) {
            Tile intermediate = null;
            
            // Straight line move
            if (diffX == 0 || diffY == 0) {
                intermediate = gameState.board.getTile(startX + diffX / 2, startY + diffY / 2);
            } 
            // Diagonal move (check for obstacles)
            else {
                Tile t1 = gameState.board.getTile(startX + diffX, startY);
                if (gameState.board.getUnitAt(t1.getTilex(), t1.getTiley()) == null) {
                    intermediate = t1;
                } else {
                    intermediate = gameState.board.getTile(startX, startY + diffY);
                }
            }
            
            // Execute two-step move
            if (intermediate != null) {
                BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), intermediate);
                try { Thread.sleep(1000); } catch (InterruptedException e) { }
                BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
            } else {
                BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
            }
        } else {
            BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
        }
    }

    // Highlight logic: White tiles for movement
    private void highlightValidMoves(ActorRef out, GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (MovementRule.isValidMove(gameState.board, unit, x, y)) {
                    BasicCommands.drawTile(out, gameState.board.getTile(x, y), 1);
                    // [Note] Delay is necessary to prevent frontend memory overflow
                    try { Thread.sleep(2); } catch (InterruptedException e) { }
                }
            }
        }
    }

    // Highlight logic: Red tiles for attack targets
    private void highlightAttackTargets(ActorRef out, GameState gameState, GameUnit unit) {
        if (unit.hasAttacked()) return;

        int[][] directions = { {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1} };
        int ux = getUnitX(gameState, unit);
        int uy = getUnitY(gameState, unit);

        for (int[] dir : directions) {
            int nx = ux + dir[0];
            int ny = uy + dir[1];
            if (nx >= 0 && nx < gameState.board.getXMax() && ny >= 0 && ny < gameState.board.getYMax()) {
                GameUnit enemy = gameState.board.getUnitAt(nx, ny);
                if (enemy != null && enemy.getOwner() != gameState.humanPlayer) {
                    BasicCommands.drawTile(out, gameState.board.getTile(nx, ny), 2);
                }
            }
        }
    }

    // =================================================================================
    // 4. Helper & Utility Methods
    // =================================================================================

    // Deduct mana and remove the used card from hand
    private void payManaAndRemoveCard(ActorRef out, GameState gameState, Card card) {
        int cost = card.getManacost();
        int newMana = gameState.humanPlayer.getMana() - cost;
        
        gameState.humanPlayer.setMana(newMana);
        gameState.humanPlayer.getBasicPlayer().setMana(newMana);
        BasicCommands.setPlayer1Mana(out, gameState.humanPlayer.getBasicPlayer());

        int handPos = gameState.selectionState.selectedHandPosition;
        BasicCommands.deleteCard(out, handPos);
        
        if (handPos - 1 >= 0 && handPos - 1 < gameState.humanPlayer.getHand().size()) {
            gameState.humanPlayer.getHand().set(handPos - 1, null);
        }
    }

    // Check if player has enough mana for the card
    private boolean checkMana(ActorRef out, GameState gameState, Card card) {
        if (gameState.humanPlayer.getMana() < card.getManacost()) {
            BasicCommands.addPlayer1Notification(out, "Not enough Mana!", 2);
            return false;
        }
        return true;
    }

    // Clear all tile highlights (White/Red)
    private void clearSummonHighlights(ActorRef out, GameState gameState) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0);
                try { Thread.sleep(2); } catch (InterruptedException e) { }
            }
        }
    }

    // Remove highlight for a specific unit's position
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

    // Update the unit's position in the board array
    private void updateBoardPosition(GameState gameState, GameUnit unit, int newX, int newY) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) {
                    gameState.board.setUnitAt(x, y, null);
                }
            }
        }
        gameState.board.setUnitAt(newX, newY, unit);
    }

    // Check if there is a friendly unit adjacent to (x, y)
    private boolean hasFriendlyNeighbor(GameState gameState, int x, int y) {
        int[][] directions = { {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1} };
        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx >= 0 && nx < gameState.board.getXMax() && ny >= 0 && ny < gameState.board.getYMax()) {
                GameUnit u = gameState.board.getUnitAt(nx, ny);
                if (u != null && u.getOwner() == gameState.humanPlayer) return true;
            }
        }
        return false;
    }

    // Find a damaged non-avatar enemy nearby (for Nightsorrow Assassin)
    private GameUnit findNearbyWeakEnemy(GameState gameState, int x, int y) {
        int[][] directions = { {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1} };
        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx >= 0 && nx < gameState.board.getXMax() && ny >= 0 && ny < gameState.board.getYMax()) {
                GameUnit u = gameState.board.getUnitAt(nx, ny);
                // Search: Enemy, Damaged, Non-Avatar
                if (u != null && u.getOwner() != gameState.humanPlayer
                    && u.getCurrentHealth() < u.getMaxHealth() && !isAvatar(u)) {
                    return u;
                }
            }
        }
        return null;
    }

    // --- ID & Config Helpers ---

    // Check if the card name corresponds to a Unit (not Spell/Artifact)
    private boolean isUnitCard(String name) {
        return !name.equals("Horn of the Forsaken") && !name.equals("Truestrike") 
            && !name.equals("Sundrop Elixir") && !name.equals("Beamshock") 
            && !name.equals("Wraithling Swarm") && !name.equals("Dark Terminus");
    }

    // Check if the unit is an Avatar (Hero)
    private boolean isAvatar(GameUnit unit) {
        return unit.getBasicUnit().getId() == 1 || unit.getBasicUnit().getId() == 2;
    }

    // Get the Avatar unit for a specific player
    private GameUnit getAvatar(GameState gameState, structures.GamePlayer player) {
        int id = (player == gameState.humanPlayer) ? 1 : 2;
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit u = gameState.board.getUnitAt(x, y);
                if (u != null && u.getBasicUnit().getId() == id) return u;
            }
        }
        return null;
    }

    // Get X coordinate of a unit
    private int getUnitX(GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) return x;
            }
        }
        return -1;
    }

    // Get Y coordinate of a unit
    private int getUnitY(GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) return y;
            }
        }
        return -1;
    }

    // Play visual effect animation
    private void playEffect(ActorRef out, GameState gameState, GameUnit unit, String effect) {
        BasicCommands.playEffectAnimation(out,
            BasicObjectBuilders.loadEffect("conf/gameconfs/effects/" + effect + ".json"),
            gameState.board.getTile(getUnitX(gameState, unit), getUnitY(gameState, unit)));
    }

    // Get unit configuration file path by card name
    private String getUnitConfigByCardName(String cardName) {
        String name = cardName.trim();
        
        if (name.equals("Bad Omen"))                return "conf/gameconfs/units/bad_omen.json";
        if (name.equals("Gloom Chaser"))            return "conf/gameconfs/units/gloom_chaser.json";
        if (name.equals("Shadow Watcher"))          return "conf/gameconfs/units/shadow_watcher.json";
        if (name.equals("Nightsorrow Assassin"))    return "conf/gameconfs/units/nightsorrow_assassin.json";
        if (name.equals("Bloodmoon Priestess"))     return "conf/gameconfs/units/bloodmoon_priestess.json";
        if (name.equals("Shadowdancer"))            return "conf/gameconfs/units/shadowdancer.json";
        if (name.equals("Rock Pulveriser"))         return "conf/gameconfs/units/rock_pulveriser.json";
        
        if (name.equals("Silverguard Knight"))      return "conf/gameconfs/units/silverguard_knight.json";
        if (name.equals("Ironcliff Guardian"))      return "conf/gameconfs/units/ironcliff_guardian.json";
        if (name.equals("Saberspine Tiger"))        return "conf/gameconfs/units/saberspine_tiger.json";
        if (name.equals("Young Flamewing"))         return "conf/gameconfs/units/young_flamewing.json";
        
        if (name.equals("Wraithling") || name.equals("Wraithling Swarm"))
                                                    return "conf/gameconfs/units/wraithling.json";

        if (name.equals("Skyrock Golem"))           return "conf/gameconfs/units/skyrock_golem.json";
        if (name.equals("Swamp Entangler"))         return "conf/gameconfs/units/swamp_entangler.json";
        if (name.equals("Silverguard Squire"))      return "conf/gameconfs/units/silverguard_squire.json";

        return StaticConfFiles.humanAvatar;
    }

    // Get unit stats (Attack/Health) by card name
    private int[] getUnitStatsByCardName(String cardName) {
        String name = cardName.trim();
        
        if (name.equals("Bad Omen"))                return new int[]{0, 1};
        if (name.equals("Gloom Chaser"))            return new int[]{3, 1};
        if (name.equals("Shadow Watcher"))          return new int[]{3, 2};
        if (name.equals("Nightsorrow Assassin"))    return new int[]{4, 2};
        if (name.equals("Bloodmoon Priestess"))     return new int[]{3, 3};
        if (name.equals("Shadowdancer"))            return new int[]{5, 4};
        if (name.equals("Rock Pulveriser"))         return new int[]{1, 4};

        if (name.equals("Silverguard Knight"))      return new int[]{1, 5};
        if (name.equals("Ironcliff Guardian"))      return new int[]{3, 10};
        if (name.equals("Saberspine Tiger"))        return new int[]{3, 2};
        if (name.equals("Young Flamewing"))         return new int[]{5, 4};
        
        if (name.equals("Wraithling") || name.equals("Wraithling Swarm"))
                                                    return new int[]{1, 1};

        if (name.equals("Skyrock Golem"))           return new int[]{4, 2};
        if (name.equals("Swamp Entangler"))         return new int[]{0, 3};
        if (name.equals("Silverguard Squire"))      return new int[]{1, 1};

        return new int[]{2, 2}; // Default
    }
}