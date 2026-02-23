package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.UnitAnimationType;

/**
 * AttackLogic
 * ---------------------------------------------------------------------
 * Handles all combat-related calculations and animations.
 * Core responsibilities:
 * 1. Handling attacks and counter-attacks.
 * 2. Processing damage and HP synchronization.
 * 3. Managing unit death and associated triggers (Deathwatch, Zeal).
 * 4. Handling special artifact effects like Horn of the Forsaken.
 * ---------------------------------------------------------------------
 */
public class AttackLogic {

    // =================================================================================
    // 1. Primary Combat Execution
    // =================================================================================

    /**
     * Executes a full combat sequence between an attacker and a defender.
     * Includes Provoke checks, animations, damage calculation, and counter-attacks.
     */
    public static void executeAttack(GameUnit attacker, GameUnit defender, ActorRef out, GameState gameState) {
        // Prevent action if unit is stunned
        if (attacker.isStunned()) return;

        // 1. Provoke (Taunt) Check
        if (MovementRule.isProvoked(gameState.board, attacker)) {
            if (!MovementRule.isProvokeUnit(defender)) {
                BasicCommands.addPlayer1Notification(out, "Must attack Provoker!", 2);
                return;
            }
        }

        // Send UI notifications based on ownership
        if (attacker.getOwner() == gameState.humanPlayer) {
            BasicCommands.addPlayer1Notification(out, "You Attack!", 2);
        } else {
            BasicCommands.addPlayer1Notification(out, "Enemy Attacking!", 2);
        }

        // Play attack animation
        BasicCommands.playUnitAnimation(out, attacker.getBasicUnit(), UnitAnimationType.attack);
        try { Thread.sleep(500); } catch (InterruptedException e) {}

        // 2. Resolve initial attack damage
        dealDamage(defender, attacker.getCurrentAttack(), out, gameState);

        // 3. Counter-attack Logic
        // Triggers only if both units survive and are adjacent
        if (defender.getCurrentHealth() > 0 && attacker.getCurrentHealth() > 0) {
            int ax = getUnitX(gameState, attacker);
            int ay = getUnitY(gameState, attacker);
            int dx = getUnitX(gameState, defender);
            int dy = getUnitY(gameState, defender);

            // Check adjacency (Chebyshev distance <= 1)
            if (Math.abs(ax - dx) <= 1 && Math.abs(ay - dy) <= 1) {
                try { Thread.sleep(500); } catch (InterruptedException e) {}

                if (defender.getOwner() == gameState.humanPlayer) {
                    BasicCommands.addPlayer1Notification(out, "You Counter!", 2);
                } else {
                    BasicCommands.addPlayer1Notification(out, "Enemy Counters!", 2);
                }

                BasicCommands.playUnitAnimation(out, defender.getBasicUnit(), UnitAnimationType.attack);
                try { Thread.sleep(500); } catch (InterruptedException e) {}

                // Resolve counter damage
                dealDamage(attacker, defender.getCurrentAttack(), out, gameState);
            }
        }
        
        // Handle "Horn of the Forsaken" artifact effect
        if (attacker.getCurrentHealth() > 0 &&
            attacker.getArtifactName() != null &&
            attacker.getArtifactName().equals("Horn of the Forsaken")) {
            
            BasicCommands.addPlayer1Notification(out, "Horn Summons!", 2);
            summonWraithlingNearby(out, gameState, getUnitX(gameState, attacker), getUnitY(gameState, attacker));
        }

        // Post-combat cleanup
        if (attacker.getCurrentHealth() <= 0) {
            gameState.selectionState.clear();
            clearAllHighlights(out, gameState);
        }

        // Update attacker action state
        if (attacker.getCurrentHealth() > 0) {
            attacker.setHasAttacked(true);
            attacker.setHasMoved(true);
        }

        // Clear selection if the target died
        if (gameState.selectionState.selectedUnit == defender && defender.getCurrentHealth() <= 0) {
            gameState.selectionState.clear();
            clearAllHighlights(out, gameState);
        }
    }

    // =================================================================================
    // 2. Damage & HP Management
    // =================================================================================

    /**
     * Deals damage to a target unit, syncs health UI, and checks for death.
     */
    public static void dealDamage(GameUnit target, int damage, ActorRef out, GameState gameState) {
        
        target.takeDamage(damage);
        BasicCommands.playUnitAnimation(out, target.getBasicUnit(), UnitAnimationType.hit);
        BasicCommands.setUnitHealth(out, target.getBasicUnit(), target.getCurrentHealth());

        // Sync Player Health UI if an Avatar is hit
        int unitId = target.getBasicUnit().getId();
        if (unitId == 1) { 
            gameState.humanPlayer.getBasicPlayer().setHealth(target.getCurrentHealth());
            BasicCommands.setPlayer1Health(out, gameState.humanPlayer.getBasicPlayer());
        } else if (unitId == 2) { 
            gameState.aiPlayer.getBasicPlayer().setHealth(target.getCurrentHealth());
            BasicCommands.setPlayer2Health(out, gameState.aiPlayer.getBasicPlayer());
        }

        // Reduce Artifact durability if the owner is hit
        if (target.getArtifactName() != null) {
            target.setArtifactDurability(target.getArtifactDurability() - 1);
            if (target.getArtifactDurability() <= 0) {
                BasicCommands.addPlayer1Notification(out, "Artifact Broken!", 2);
                target.setArtifactName(null);
            }
        }

        // Trigger "Zeal" effect (Silverguard Knight) if a friendly Avatar took damage
        if (isAvatar(target)) {
            triggerSilverguardKnightZeal(out, gameState, target.getOwner());
        }

        // Check for unit death
        if (target.getCurrentHealth() <= 0) {
            handleUnitDeath(out, gameState, target);
        }
    }

    /**
     * Handles the logic when a unit's HP drops to zero.
     */
    private static void handleUnitDeath(ActorRef out, GameState gameState, GameUnit unit) {
        try { Thread.sleep(300); } catch (InterruptedException e) {}
        BasicCommands.playUnitAnimation(out, unit.getBasicUnit(), UnitAnimationType.death);
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        // Remove from logical board and visual UI
        gameState.board.removeUnit(unit);
        BasicCommands.deleteUnit(out, unit.getBasicUnit());
        
        // Trigger global "Deathwatch" effects
        triggerDeathwatch(out, gameState);

        // Win/Loss Condition Check
        int unitId = unit.getBasicUnit().getId();
        if (unitId == 1) {
            BasicCommands.addPlayer1Notification(out, "GAME OVER", 100);
            gameState.gameInitalised = false;
        } else if (unitId == 2) {
            BasicCommands.addPlayer1Notification(out, "VICTORY!", 100);
            gameState.gameInitalised = false;
        }
    }
    
    // =================================================================================
    // 3. Global Buffs & Triggers
    // =================================================================================

    /**
     * Scans the board for units with the "Deathwatch" ability and triggers them.
     */
    private static void triggerDeathwatch(ActorRef out, GameState gameState) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit u = gameState.board.getUnitAt(x, y);
                if (u != null && u.getName() != null && u.getCurrentHealth() > 0) {
                    
                    // 1. Bad Omen (+1 Attack)
                    if (u.getName().equals("Bad Omen")) {
                        BasicCommands.addPlayer1Notification(out, "Bad Omen Feeds!", 2);
                        playBuff(out, gameState.board.getTile(x, y));
                        u.setCurrentAttack(u.getCurrentAttack() + 1);
                        BasicCommands.setUnitAttack(out, u.getBasicUnit(), u.getCurrentAttack());
                    }
                    
                    // 2. Shadow Watcher (+1/+1)
                    if (u.getName().equals("Shadow Watcher")) {
                        BasicCommands.addPlayer1Notification(out, "Shadow Watcher Grows!", 2);
                        playBuff(out, gameState.board.getTile(x, y));
                        u.setCurrentAttack(u.getCurrentAttack() + 1);
                        u.setCurrentHealth(u.getCurrentHealth() + 1);
                        u.setMaxHealth(u.getMaxHealth() + 1); 
                        BasicCommands.setUnitAttack(out, u.getBasicUnit(), u.getCurrentAttack());
                        BasicCommands.setUnitHealth(out, u.getBasicUnit(), u.getCurrentHealth());
                    }
                    
                    // 3. Bloodmoon Priestess (Summon Wraithling nearby)
                    if (u.getName().equals("Bloodmoon Priestess")) {
                        BasicCommands.addPlayer1Notification(out, "Bloodmoon Rise!", 2);
                        summonWraithlingNearby(out, gameState, x, y);
                    }

                    // 4. Shadowdancer (Drain Life)
                    if (u.getName().equals("Shadowdancer")) {
                        BasicCommands.addPlayer1Notification(out, "Soul Steal!", 2);
                        playBuff(out, gameState.board.getTile(x, y));
                        
                        // Deal 1 damage to enemy hero
                        GameUnit enemyHero = getAvatar(gameState, (u.getOwner() == gameState.humanPlayer) ? gameState.aiPlayer : gameState.humanPlayer);
                        if (enemyHero != null) {
                            dealDamage(enemyHero, 1, out, gameState);
                        }

                        // Heal friendly hero by 1
                        GameUnit myHero = getAvatar(gameState, u.getOwner());
                        if (myHero != null && myHero.getCurrentHealth() < myHero.getMaxHealth()) { 
                            int newHp = myHero.getCurrentHealth() + 1;
                            myHero.setCurrentHealth(newHp);
                            BasicCommands.setUnitHealth(out, myHero.getBasicUnit(), newHp);
                            
                            // Sync Player Health UI
                            if (myHero.getBasicUnit().getId() == 1) {
                                gameState.humanPlayer.getBasicPlayer().setHealth(newHp);
                                BasicCommands.setPlayer1Health(out, gameState.humanPlayer.getBasicPlayer());
                            } else if (myHero.getBasicUnit().getId() == 2) {
                                gameState.aiPlayer.getBasicPlayer().setHealth(newHp);
                                BasicCommands.setPlayer2Health(out, gameState.aiPlayer.getBasicPlayer());
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Scans the board for Silverguard Knights belonging to the owner and grants them Attack.
     */
    private static void triggerSilverguardKnightZeal(ActorRef out, GameState gameState, GamePlayer owner) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit u = gameState.board.getUnitAt(x, y);
                if (u != null && u.getOwner() == owner && "Silverguard Knight".equals(u.getName())) {
                    BasicCommands.addPlayer1Notification(out, "Knight Enraged!", 2);
                    playBuff(out, gameState.board.getTile(x, y));
                    u.setCurrentAttack(u.getCurrentAttack() + 2);
                    BasicCommands.setUnitAttack(out, u.getBasicUnit(), u.getCurrentAttack());
                }
            }
        }
    }

    // =================================================================================
    // 4. Utility & Helper Methods
    // =================================================================================

    private static boolean isAvatar(GameUnit unit) {
        return unit.getBasicUnit().getId() == 1 || unit.getBasicUnit().getId() == 2;
    }

    private static GameUnit getAvatar(GameState gameState, GamePlayer player) {
        int targetId = (player == gameState.humanPlayer) ? 1 : 2;
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit u = gameState.board.getUnitAt(x, y);
                if (u != null && u.getBasicUnit().getId() == targetId) return u;
            }
        }
        return null;
    }
    
    /**
     * Summons a Wraithling on an empty adjacent tile near the specified coordinates.
     */
    private static void summonWraithlingNearby(ActorRef out, GameState gameState, int x, int y) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}; 
        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx >= 0 && nx < gameState.board.getXMax() && ny >= 0 && ny < gameState.board.getYMax()) {
                if (gameState.board.getUnitAt(nx, ny) == null) {
                    events.TileClicked.summonWraithlingAt(out, gameState, gameState.board.getTile(nx, ny));
                    return;
                }
            }
        }
    }
    
    private static void playBuff(ActorRef out, structures.basic.Tile tile) {
        BasicCommands.playEffectAnimation(out, utils.BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_buff.json"), tile);
    }
    
    private static void clearAllHighlights(ActorRef out, GameState gameState) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0);
            }
        }
    }
    
    private static int getUnitX(GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) return x;
            }
        }
        return -1;
    }

    private static int getUnitY(GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) return y;
            }
        }
        return -1;
    }
}