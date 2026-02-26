package events;

import com.fasterxml.jackson.databind.JsonNode;
import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.GameUnit;
import structures.MovementRule;
import structures.AttackLogic;
import structures.basic.Card;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;

import java.util.ArrayList;
import java.util.List;

/**
 * EndTurnClicked
 * ---------------------------------------------------------------------
 * Handles the logic when the "End Turn" button is clicked.
 * It manages the transition between the Human player's turn and the AI's turn.
 * Key responsibilities:
 * 1. Clean up Human turn state (highlights, selection, stunned status).
 * 2. Execute the AI's full turn (Draw, Play Cards, Move, Attack).
 * 3. Start the Human's next turn (Mana, Draw, Reset units).
 * ---------------------------------------------------------------------
 */
public class EndTurnClicked implements EventProcessor {

    // =================================================================================
    // 1. Main Event Processing Entry Point
    // =================================================================================

    @Override
    public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
        if (!gameState.gameInitalised) return;

        // --------------------------------------------------------
        // Step 1: Human Turn Cleanup
        // --------------------------------------------------------
        
        // 1. Clear full-board highlighting (Set all tiles to mode 0)
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0);
                try { Thread.sleep(2); } catch (InterruptedException e) {}
            }
        }

        // 2. Visually deselect hand card (Set selected card to mode 0)
        if (gameState.selectionState.selectedHandPosition != -1) {
            int handPos = gameState.selectionState.selectedHandPosition;
            // Simple validation to prevent index out of bounds
            if (handPos <= gameState.humanPlayer.getHand().size()) {
                Card card = gameState.humanPlayer.getHand().get(handPos - 1);
                if (card != null) {
                    BasicCommands.drawCard(out, card, handPos, 0);
                }
            }
        }

        // 3. Clear logical selection state (Clear selectedUnit and selectedCard in memory)
        gameState.selectionState.clear();

        // 3b. Visually and logically clear Human player's mana at end of turn
        gameState.humanPlayer.setMana(0);
        gameState.humanPlayer.getBasicPlayer().setMana(0);
        BasicCommands.setPlayer1Mana(out, gameState.humanPlayer.getBasicPlayer());

        // 4. Remove Stunned status from Human units
        // (If a unit was stunned, the penalty is considered paid at the end of the turn)
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit u = gameState.board.getUnitAt(x, y);
                if (u != null && u.getOwner() == gameState.humanPlayer) {
                    u.setStunned(false);
                }
            }
        }
    
        // --------------------------------------------------------
        // Step 2: AI Turn Execution
        // --------------------------------------------------------
        BasicCommands.addPlayer1Notification(out, "Enemy Turn", 2);
        
        // Update AI Mana (Cap at 9)
        int currentAIMax = gameState.aiPlayer.getMaxMana();
        if (currentAIMax < 9) gameState.aiPlayer.setMaxMana(currentAIMax + 1);
        if (gameState.aiPlayer.getMaxMana() > 9) gameState.aiPlayer.setMaxMana(9);
        gameState.aiPlayer.setMana(gameState.aiPlayer.getMaxMana());
        
        // Sync AI Mana with frontend (Player 2)
        gameState.aiPlayer.getBasicPlayer().setMana(gameState.aiPlayer.getMana());
        BasicCommands.setPlayer2Mana(out, gameState.aiPlayer.getBasicPlayer());

        // Run the main AI logic
        executeAITurn(out, gameState);

        gameState.aiPlayer.setMana(0);
        gameState.aiPlayer.getBasicPlayer().setMana(0);
        BasicCommands.setPlayer2Mana(out, gameState.aiPlayer.getBasicPlayer());

        // --------------------------------------------------------
        // Step 3: Start Human Turn
        // --------------------------------------------------------
        BasicCommands.addPlayer1Notification(out, "Your Turn!", 2);

        // Update Human Mana (Cap at 9)
        int currentHumanMax = gameState.humanPlayer.getMaxMana();
        if (currentHumanMax < 9) gameState.humanPlayer.setMaxMana(currentHumanMax + 1);
        if (gameState.humanPlayer.getMaxMana() > 9) gameState.humanPlayer.setMaxMana(9);
        gameState.humanPlayer.setMana(gameState.humanPlayer.getMaxMana());
        
        // Sync Human Mana with frontend (Player 1)
        gameState.humanPlayer.getBasicPlayer().setMana(gameState.humanPlayer.getMana());
        BasicCommands.setPlayer1Mana(out, gameState.humanPlayer.getBasicPlayer());

        // Draw card for Human player
        for (int i = 0; i < 1; i++) {
            drawCardWithLimit(out, gameState);
            try { Thread.sleep(300); } catch (InterruptedException e) {}
        }

        // Reset movement/attack status for all units
        resetAllUnits(out, gameState);
    }

    // =================================================================================
    // 2. AI Movement & Logic
    // =================================================================================

    /**
     * Handles the visual movement of AI units.
     * Includes logic for "Flying" units and segmented movement for L-shaped paths.
     */
    private void moveAIUnitVisually(ActorRef out, GameState gameState, GameUnit unit, Tile targetTile) {
        // Flying units move directly without pathing logic
        if (unit.getName() != null && unit.getName().equals("Young Flamewing")) {
            BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
            return;
        }

        int startX = getUnitX(gameState, unit);
        int startY = getUnitY(gameState, unit);
        int diffX = targetTile.getTilex() - startX;
        int diffY = targetTile.getTiley() - startY;

        // If moving 2 squares (Manhattan distance), we must split the movement
        if (Math.abs(diffX) + Math.abs(diffY) == 2) {
            Tile intermediate = null;
            // Straight line move (2,0) or (0,2)
            if (diffX == 0 || diffY == 0) {
                intermediate = gameState.board.getTile(startX + diffX / 2, startY + diffY / 2);
            } 
            // Diagonal move (1,1)
            else {
                // Prefer an empty intermediate tile
                Tile t1 = gameState.board.getTile(startX + diffX, startY);
                if (gameState.board.getUnitAt(t1.getTilex(), t1.getTiley()) == null) {
                    intermediate = t1;
                } else {
                    intermediate = gameState.board.getTile(startX, startY + diffY);
                }
            }

            if (intermediate != null) {
                BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), intermediate);
                try { Thread.sleep(800); } catch (InterruptedException e) {} // Wait for animation
                BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
            } else {
                BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
            }
        } else {
            // Moving 1 square, direct move
            BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
        }
    }

    /**
     * Main orchestration method for the AI's turn.
     * Phases: 1. Draw Card -> 2. Play Cards -> 3. Move & Attack.
     */
    private void executeAITurn(ActorRef out, GameState gameState) {
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        // Phase 1: Draw
        for (int k = 0; k < 1; k++) {
            if (!gameState.aiPlayer.getDeck().isEmpty()) {
                if (gameState.aiPlayer.getHand().size() < 6) {
                    gameState.aiPlayer.getHand().add(gameState.aiPlayer.getDeck().remove(0));
                } else {
                    gameState.aiPlayer.getDeck().remove(0); // Burn card if hand is full
                }
            }
        }
        try { Thread.sleep(1500); } catch (InterruptedException e) {}

        // Phase 2: Play Cards
        for (int i = gameState.aiPlayer.getHand().size() - 1; i >= 0; i--) {
            Card card = gameState.aiPlayer.getHand().get(i);
            
            if (gameState.aiPlayer.getMana() >= card.getManacost()) {
                boolean played = false;

                if (isUnitCard(card.getCardname())) {
                    Tile spawnTile = findValidSpawnTile(gameState);
                    if (spawnTile != null) {
                        summonAIUnit(out, gameState, card, spawnTile);
                        played = true;
                    }
                } else {
                    // Try to cast spell if condition is met
                    if (castSmartAIAbility(out, gameState, card)) {
                        played = true;
                    }
                }

                if (played) {
                    // Update Mana and UI
                    gameState.aiPlayer.setMana(gameState.aiPlayer.getMana() - card.getManacost());
                    gameState.aiPlayer.getBasicPlayer().setMana(gameState.aiPlayer.getMana());
                    BasicCommands.setPlayer2Mana(out, gameState.aiPlayer.getBasicPlayer());
                    gameState.aiPlayer.getHand().remove(i);
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                }
            }
        }

        // Phase 3: Move & Attack
        List<GameUnit> aiUnits = new ArrayList<>();
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit unit = gameState.board.getUnitAt(x, y);
                if (unit != null && unit.getOwner() == gameState.aiPlayer) {
                    aiUnits.add(unit);
                }
            }
        }

        for (GameUnit unit : aiUnits) {
            if (unit.getCurrentHealth() <= 0) continue;

            GameUnit target = findBestTarget(gameState, unit);
            if (target != null) {
                // 1. Movement Logic
                // Even 0-attack units move to block/provoke enemies
                if (!isInAttackRange(gameState, unit, target)) {
                    moveAIUnitTowards(out, gameState, unit, target);
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                }
                
                // 2. Attack Logic
                // Condition added: unit.getCurrentAttack() > 0
                // Explanation: Only attack if unit has damage. Units like Swamp Entangler (0 atk)
                // will move to the enemy (via logic above) and stop, effectively provoking them.
                if (isInAttackRange(gameState, unit, target) && !unit.hasAttacked() && unit.getCurrentAttack() > 0) {
                    
                    AttackLogic.executeAttack(unit, target, out, gameState);
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                }
            }
        }
    }

    /**
     * Moves an AI unit towards a target unit using simple pathfinding.
     */
    private void moveAIUnitTowards(ActorRef out, GameState gameState, GameUnit unit, GameUnit target) {
        if (unit.hasMoved()) return;
        int startX = getUnitX(gameState, unit); int startY = getUnitY(gameState, unit);
        int targetX = getUnitX(gameState, target); int targetY = getUnitY(gameState, target);
        
        Tile bestTile = null;
        int bestDist = Math.abs(startX - targetX) + Math.abs(startY - targetY);

        // Search in a 2-tile radius (max movement)
        for (int x = startX - 2; x <= startX + 2; x++) {
            for (int y = startY - 2; y <= startY + 2; y++) {
                if (x < 0 || x >= gameState.board.getXMax() || y < 0 || y >= gameState.board.getYMax()) continue;
                if (MovementRule.isValidMove(gameState.board, unit, x, y)) {
                    int dist = Math.abs(x - targetX) + Math.abs(y - targetY);
                    if (dist < bestDist) { bestDist = dist; bestTile = gameState.board.getTile(x, y); }
                }
            }
        }

        if (bestTile != null) {
            // [Crucial] Double check if the target tile is occupied
            if (gameState.board.getUnitAt(bestTile.getTilex(), bestTile.getTiley()) != null) {
                return;
            }

            // Use visual movement method
            moveAIUnitVisually(out, gameState, unit, bestTile);

            // Update logical position
            gameState.board.setUnitAt(startX, startY, null);
            gameState.board.setUnitAt(bestTile.getTilex(), bestTile.getTiley(), unit);
            unit.getBasicUnit().setPositionByTile(bestTile);
            unit.setHasMoved(true);
        }
    }

    // =================================================================================
    // 3. Summoning & Spells Helpers
    // =================================================================================
    
    /**
     * Summons an AI unit onto the board and applies Opening Gambit effects.
     */
    private void summonAIUnit(ActorRef out, GameState gameState, Card card, Tile tile) {
        try {
            String unitConf = getUnitConfigByCardName(card.getCardname());
            int[] stats = getUnitStatsByCardName(card.getCardname());
            int unitId = gameState.unitIdCounter++;
            Unit unitObj = BasicObjectBuilders.loadUnit(unitConf, unitId, Unit.class);
            unitObj.setPositionByTile(tile);
            
            GameUnit gameUnit = new GameUnit(unitObj, stats[1], stats[0]);
            gameUnit.setOwner(gameState.aiPlayer);
            gameUnit.setName(card.getCardname());
            
            gameState.board.setUnitAt(tile.getTilex(), tile.getTiley(), gameUnit);
            BasicCommands.drawUnit(out, unitObj, tile);
            
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            
            BasicCommands.setUnitAttack(out, unitObj, stats[0]);
            BasicCommands.setUnitHealth(out, unitObj, stats[1]);
            
            // Set action status (Rush check)
            if (card.getCardname().equals("Saberspine Tiger")) {
                gameUnit.setHasMoved(false); gameUnit.setHasAttacked(false);
            } else {
                gameUnit.setHasMoved(true); gameUnit.setHasAttacked(true);
            }
            
            BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_buff.json"), tile);
            BasicCommands.addPlayer1Notification(out, "AI Summoned!", 2);

            // Handle Silverguard Squire Effect
            if (card.getCardname().equals("Silverguard Squire")) {
                GameUnit aiAvatar = getHero(gameState, gameState.aiPlayer);
                if (aiAvatar != null) {
                    int ax = getUnitX(gameState, aiAvatar);
                    int ay = getUnitY(gameState, aiAvatar);
                    int[][] checkPos = {{ax - 1, ay}, {ax + 1, ay}};
                    for (int[] pos : checkPos) {
                        if (pos[0] >= 0 && pos[0] < gameState.board.getXMax()) {
                            GameUnit u = gameState.board.getUnitAt(pos[0], pos[1]);
                            if (u != null && u.getOwner() == gameState.aiPlayer && !isAvatar(u)) {
                                // Buff stats
                                u.setCurrentAttack(u.getCurrentAttack() + 1);
                                u.setCurrentHealth(u.getCurrentHealth() + 1);
                                u.setMaxHealth(u.getMaxHealth() + 1);
                                BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_buff.json"), gameState.board.getTile(pos[0], pos[1]));
                                BasicCommands.setUnitAttack(out, u.getBasicUnit(), u.getCurrentAttack());
                                BasicCommands.setUnitHealth(out, u.getBasicUnit(), u.getCurrentHealth());
                            }
                        }
                    }
                }
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Determines if a spell card should be cast by the AI and executes it.
     */
    private boolean castSmartAIAbility(ActorRef out, GameState gameState, Card card) {
        String name = card.getCardname();
        
        // --- True Strike (Deal 2 damage) ---
        if (name.equals("True Strike") || name.equals("Truestrike")) {
            GameUnit target = null;
            double bestVal = -1;

            for (GameUnit e : getAllEnemyUnits(gameState)) {
                double val = 0;
                // Lethal Logic: High value if 2 damage can kill
                if (e.getCurrentHealth() <= 2) val += 100;
                if (isAvatar(e) && e.getCurrentHealth() <= 2) val += 1000; // Lethal on Hero
                
                // Damage Logic: Worth more on high attack units
                val += e.getCurrentAttack();
                
                // Base value for hitting face
                if (isAvatar(e)) val += 5;

                if (val > bestVal) { bestVal = val; target = e; }
            }

            if (target != null) {
                BasicCommands.addPlayer1Notification(out, "AI uses True Strike!", 2);
                AttackLogic.dealDamage(target, 2, out, gameState);
                BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_inmolation.json"), 
                    gameState.board.getTile(getUnitX(gameState, target), getUnitY(gameState, target)));
                return true;
            }
        }

        // --- Sundrop Elixir (Heal 5) ---
        if (name.equals("Sundrop Elixir")) {
            GameUnit target = null;
            int maxLost = 0;
            
            for (GameUnit f : getAllFriendlyUnits(gameState)) {
                int lost = f.getMaxHealth() - f.getCurrentHealth();
                if (lost > 0) { // Only consider damaged units
                    // Slightly higher priority for healing Hero
                    if (isAvatar(f)) lost += 2;
                    
                    if (lost > maxLost) { maxLost = lost; target = f; }
                }
            }

            if (target != null) {
                BasicCommands.addPlayer1Notification(out, "AI Heals!", 2);
                int newHp = Math.min(target.getCurrentHealth() + 5, target.getMaxHealth());
                target.setCurrentHealth(newHp);
                BasicCommands.setUnitHealth(out, target.getBasicUnit(), newHp);
                if (isAvatar(target)) {
                    gameState.aiPlayer.getBasicPlayer().setHealth(newHp);
                    BasicCommands.setPlayer2Health(out, gameState.aiPlayer.getBasicPlayer());
                }
                BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_buff.json"), 
                    gameState.board.getTile(getUnitX(gameState, target), getUnitY(gameState, target)));
                return true;
            }
        }
        
        // --- Beam Shock (Stun) ---
        if (name.equals("Beam Shock") || name.equals("Beamshock")) {
            GameUnit target = null;
            int maxAtk = -1;
            
            for (GameUnit e : getAllEnemyUnits(gameState)) {
                // Do not stun Avatar (useless as they can still summon/cast)
                // Do not stun already stunned units
                if (!isAvatar(e) && !e.isStunned()) {
                    if (e.getCurrentAttack() > maxAtk) {
                        maxAtk = e.getCurrentAttack();
                        target = e;
                    }
                }
            }
            
            // Only stun if the threat (attack) is significant (>2)
            if (target != null && maxAtk > 2) {
                BasicCommands.addPlayer1Notification(out, "AI Stuns!", 2);
                target.setStunned(true);
                BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_martyrdom.json"), 
                    gameState.board.getTile(getUnitX(gameState, target), getUnitY(gameState, target)));
                return true;
            }
        }
        
        return false;
    }

    // =================================================================================
    // 4. Targeting & Scoring Logic
    // =================================================================================

    /**
     * Determines the best enemy target for an AI unit to attack based on a scoring system.
     */
    private GameUnit findBestTarget(GameState gameState, GameUnit attacker) {
        // 1. Hard Check for Provoke (Taunt)
        if (MovementRule.isProvoked(gameState.board, attacker)) {
            int ax = getUnitX(gameState, attacker); int ay = getUnitY(gameState, attacker);
            int[][] directions = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
            for (int[] dir : directions) {
                int nx = ax + dir[0]; int ny = ay + dir[1];
                if (nx>=0 && nx<gameState.board.getXMax() && ny>=0 && ny<gameState.board.getYMax()) {
                    GameUnit neighbor = gameState.board.getUnitAt(nx, ny);
                    if (neighbor != null && neighbor.getOwner() != attacker.getOwner() && MovementRule.isProvokeUnit(neighbor)) {
                        return neighbor;
                    }
                }
            }
        }

        GameUnit bestTarget = null;
        double bestScore = -99999.0;
        
        List<GameUnit> targets = getAllEnemyUnits(gameState);
        
        // Attacker Stats
        int myAtk = attacker.getCurrentAttack();
        int myHp = attacker.getCurrentHealth();
        boolean amIHero = isAvatar(attacker);

        for (GameUnit enemy : targets) {
            if (enemy.getCurrentHealth() <= 0) continue;

            double score = 0;
            int enemyHp = enemy.getCurrentHealth();
            int enemyAtk = enemy.getCurrentAttack();
            boolean isEnemyHero = isAvatar(enemy);
            int distance = getDistance(gameState, attacker, enemy);

            // --- Core Game Theory Logic ---

            // A. Lethal Check
            boolean canKill = myAtk >= enemyHp;
            if (canKill) {
                score += 150.0;
                if (isEnemyHero) score += 100000.0; // Killing Hero = Win
            }

            // B. Survival Check
            // Rule: If enemy is not killed and not stunned, they will counter-attack
            boolean willBeCountered = !canKill && !enemy.isStunned();
            boolean willDie = willBeCountered && (enemyAtk >= myHp);

            if (willDie) {
                // If this attack results in death
                if (amIHero) {
                    // If I am the Hero and I will die, forbid attack!
                    score -= 100000.0;
                } else {
                    // Normal unit: severe penalty, unless trading for high value
                    score -= 100.0;
                    // If I only have 1 HP, I'm dying anyway, so trading damage is acceptable
                    if (myHp == 1) score += 50.0;
                }
            } else if (!willBeCountered) {
                // "Free Trade": Attacking without taking damage (Enemy dies or is stunned)
                score += 40.0;
            }

            // C. Value Trade
            // Attacking high attack units is valuable (removing threats)
            score += enemyAtk * 5.0;
            
            // D. Overkill Penalty
            // If I have 10 Attack and enemy has 1 HP, it's wasteful.
            if (canKill && (myAtk - enemyHp > 3) && !isEnemyHero) {
                score -= 10.0;
            }

            // E. Hero Bias
            if (isEnemyHero) score += 35.0; // Always biased towards hitting face

            // F. Distance Penalty
            score -= (distance * 3.0);
            if (distance <= 1) score += 15.0; // Priority for adjacent enemies

            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy;
            }
        }

        // If the best score is extremely negative (suicide mission), hold position
        if (bestScore < -500.0) return null;

        return bestTarget;
    }

    private List<GameUnit> getAllEnemyUnits(GameState gs) {
        List<GameUnit> list = new ArrayList<>();
        for (int x=0; x<gs.board.getXMax(); x++) for (int y=0; y<gs.board.getYMax(); y++) {
            GameUnit u = gs.board.getUnitAt(x, y);
            if (u!=null && u.getOwner() == gs.humanPlayer) list.add(u);
        }
        return list;
    }

    private List<GameUnit> getAllFriendlyUnits(GameState gs) {
        List<GameUnit> list = new ArrayList<>();
        for (int x=0; x<gs.board.getXMax(); x++) for (int y=0; y<gs.board.getYMax(); y++) {
            GameUnit u = gs.board.getUnitAt(x, y);
            if (u!=null && u.getOwner() == gs.aiPlayer) list.add(u);
        }
        return list;
    }

    // Calculate Manhattan distance between two units
    private int getDistance(GameState gameState, GameUnit u1, GameUnit u2) {
        return Math.abs(getUnitX(gameState, u1) - getUnitX(gameState, u2))
            + Math.abs(getUnitY(gameState, u1) - getUnitY(gameState, u2));
    }

    // =================================================================================
    // 5. Board Scanning & Utilities
    // =================================================================================

    /**
     * Finds a valid tile for the AI to summon a unit.
     * Considers Defensive (near Avatar) vs Offensive (near Enemy) positioning.
     */
    private Tile findValidSpawnTile(GameState gameState) {
        Tile bestTile = null;
        
        GameUnit aiHero = getHero(gameState, gameState.aiPlayer);
        GameUnit humanHero = getHero(gameState, gameState.humanPlayer);
        
        if (aiHero == null || humanHero == null) return null;

        int aiX = getUnitX(gameState, aiHero);
        int aiY = getUnitY(gameState, aiHero);
        int humanX = getUnitX(gameState, humanHero);
        int humanY = getUnitY(gameState, humanHero);

        // Check Situation: If HP is low, enter Defensive Mode
        boolean defensiveMode = aiHero.getCurrentHealth() <= 10;
        
        // Initialize comparison metric
        double bestDistanceMetric = 9999.0;

        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                // Must be an empty tile
                if (gameState.board.getUnitAt(x, y) != null) continue;
                
                // Must be adjacent to a friendly unit (Game Rule)
                if (!hasFriendlyNeighbor(gameState, x, y, gameState.aiPlayer)) continue;

                double distToEnemy = Math.sqrt(Math.pow(x - humanX, 2) + Math.pow(y - humanY, 2));
                double distToMe = Math.sqrt(Math.pow(x - aiX, 2) + Math.pow(y - aiY, 2));

                if (defensiveMode) {
                    // [Defensive Mode]: Closer to self is better, but slightly towards enemy (Shielding)
                    // Score: Closer to self (High weight) + Closer to enemy (Low weight)
                    double score = (distToMe * 2) + (distToEnemy * 0.5);
                    
                    if (score < bestDistanceMetric) {
                        bestDistanceMetric = score;
                        bestTile = gameState.board.getTile(x, y);
                    }
                } else {
                    // [Offensive Mode]: Closer to enemy is better
                    if (distToEnemy < bestDistanceMetric) {
                        bestDistanceMetric = distToEnemy;
                        bestTile = gameState.board.getTile(x, y);
                    }
                }
            }
        }
        return bestTile;
    }

    // Check if a tile has a neighbor belonging to the owner
    private boolean hasFriendlyNeighbor(GameState gameState, int x, int y, structures.GamePlayer owner) {
        int[][] directions = {{-1, -1}, {-1, 0}, {-1, 1}, { 0, -1}, { 0, 1}, { 1, -1}, { 1, 0}, { 1, 1}};
        for (int[] dir : directions) {
            int nx = x + dir[0]; int ny = y + dir[1];
            if (nx >= 0 && nx < gameState.board.getXMax() && ny >= 0 && ny < gameState.board.getYMax()) {
                GameUnit u = gameState.board.getUnitAt(nx, ny);
                if (u != null && u.getOwner() == owner) return true;
            }
        }
        return false;
    }

    // Check if defender is within attack range (1 tile)
    private boolean isInAttackRange(GameState gameState, GameUnit attacker, GameUnit defender) {
        int ax = getUnitX(gameState, attacker);
        int ay = getUnitY(gameState, attacker);
        int dx = getUnitX(gameState, defender);
        int dy = getUnitY(gameState, defender);
        return Math.abs(ax - dx) <= 1 && Math.abs(ay - dy) <= 1;
    }

    // Scan board to get Unit X coordinate
    private int getUnitX(GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) return x;
            }
        }
        return -1;
    }

    // Scan board to get Unit Y coordinate (Corrected: scans board instead of returning internal Y)
    private int getUnitY(GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) return y;
            }
        }
        return -1;
    }

    /**
     * Logic for Human player drawing a card, handling hand size limit (Burn card).
     */
    private void drawCardWithLimit(ActorRef out, GameState gameState) {
        if (gameState.humanPlayer.getDeck().isEmpty()) {
            BasicCommands.addPlayer1Notification(out, "Deck Empty!", 2);
            return;
        }
        int MAX_HAND_SIZE = 6;
        int currentHandCount = 0;
        for (Card c : gameState.humanPlayer.getHand()) { if (c != null) currentHandCount++; }
        
        if (currentHandCount < MAX_HAND_SIZE) {
            Card card = gameState.humanPlayer.getDeck().remove(0);
            int emptySlot = -1;
            // Find first empty slot in hand
            for (int i = 0; i < gameState.humanPlayer.getHand().size(); i++) {
                if (gameState.humanPlayer.getHand().get(i) == null) { emptySlot = i; break; } 
            }
            if (emptySlot != -1) {
                gameState.humanPlayer.getHand().set(emptySlot, card);
                BasicCommands.drawCard(out, card, emptySlot + 1, 0);
            } else {
                gameState.humanPlayer.getHand().add(card);
                BasicCommands.drawCard(out, card, gameState.humanPlayer.getHand().size(), 0);
            }
        } else {
            // Hand full, burn card
            gameState.humanPlayer.getDeck().remove(0);
            BasicCommands.addPlayer1Notification(out, "Hand Full! Burned!", 2);
        }
    }

    // Reset units for the new turn (Restore movement points)
    private void resetAllUnits(ActorRef out, GameState gameState) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit unit = gameState.board.getUnitAt(x, y);
                if (unit != null) {
                    // Note: Stun removal logic is handled in processEvent, not here
                    unit.resetTurn();
                    BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0);
                    try { Thread.sleep(2); } catch (InterruptedException e) {}
                }
            }
        }
    }

    // --- Identification Helpers ---

    private boolean isUnitCard(String name) {
        return !name.equals("Horn of the Forsaken")
            && !name.equals("Truestrike") && !name.equals("True Strike") // Double check spelling
            && !name.equals("Sundrop Elixir")
            && !name.equals("Beam Shock") && !name.equals("Beamshock") // Added Beamshock
            && !name.equals("Wraithling Swarm")
            && !name.equals("Dark Terminus");
    }

    private String getUnitConfigByCardName(String cardName) {
        String name = cardName.trim();
        if (name.equals("Swamp Entangler")) return "conf/gameconfs/units/swamp_entangler.json";
        if (name.equals("Silverguard Squire")) return "conf/gameconfs/units/silverguard_squire.json";
        if (name.equals("Skyrock Golem")) return "conf/gameconfs/units/skyrock_golem.json";
        if (name.equals("Saberspine Tiger")) return "conf/gameconfs/units/saberspine_tiger.json";
        if (name.equals("Silverguard Knight")) return "conf/gameconfs/units/silverguard_knight.json";
        if (name.equals("Young Flamewing")) return "conf/gameconfs/units/young_flamewing.json";
        if (name.equals("Ironcliff Guardian")) return "conf/gameconfs/units/ironcliff_guardian.json";
        return "conf/gameconfs/units/skyrock_golem.json";
    }

    private int[] getUnitStatsByCardName(String cardName) {
        String name = cardName.trim();
        if (name.equals("Swamp Entangler")) return new int[]{0, 3};
        if (name.equals("Silverguard Squire")) return new int[]{1, 1};
        if (name.equals("Skyrock Golem")) return new int[]{4, 2};
        if (name.equals("Saberspine Tiger")) return new int[]{3, 2};
        if (name.equals("Silverguard Knight")) return new int[]{1, 5};
        if (name.equals("Young Flamewing")) return new int[]{5, 4};
        if (name.equals("Ironcliff Guardian")) return new int[]{3, 10};
        return new int[]{2, 2};
    }
    
    private GameUnit getHero(GameState gameState, structures.GamePlayer player) {
        int id = (player == gameState.humanPlayer) ? 1 : 2;
        for (int x=0; x<gameState.board.getXMax(); x++) for (int y=0; y<gameState.board.getYMax(); y++) {
            GameUnit u = gameState.board.getUnitAt(x, y);
            if (u!=null && u.getBasicUnit().getId() == id) return u;
        }
        return null;
    }

    private boolean isAvatar(GameUnit unit) {
        return unit.getBasicUnit().getId() == 1 || unit.getBasicUnit().getId() == 2;
    }
}
