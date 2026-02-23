package structures;

import structures.basic.Unit;

/**
 * GameUnit
 * ---------------------------------------------------------------------
 * A logic wrapper class for game units.
 * It manages health, attack power, action states, ownership, 
 * artifact equipment, and status effects like stun.
 * ---------------------------------------------------------------------
 */
public class GameUnit {

    // Core attributes
    private Unit basicUnit;
    private int currentHealth;
    private int maxHealth;
    private int currentAttack;

    // Action state flags
    private boolean hasMoved = false;
    private boolean hasAttacked = false;
    private boolean hasCounterAttacked = false;
    
    // Status effects
    private boolean isStunned = false;
    
    // Artifact properties (primarily for Hero units)
    private String artifactName = null; // Name of the equipped artifact
    private int artifactDurability = 0; // Remaining durability of the artifact
    
    // Ownership and Identification
    private GamePlayer owner;
    private String name; // Unit name used for skill/logic identification

    /**
     * Constructor to initialize a game unit with its basic stats.
     * Sets default action states to false.
     */
    public GameUnit(Unit unit, int health, int attack) {
        this.basicUnit = unit;
        this.maxHealth = health;
        this.currentHealth = health;
        this.currentAttack = attack;
        
        // Default state: Unit cannot act immediately upon initialization
        this.hasMoved = false;
        this.hasAttacked = false;
        this.hasCounterAttacked = false;
        this.isStunned = false;
    }

    // =================================================================================
    // 1. Core Logic Methods
    // =================================================================================

    /**
     * Reduces the current health of the unit by the specified damage amount.
     * Ensures health does not drop below zero.
     */
    public void takeDamage(int damage) {
        this.currentHealth -= damage;
        if (this.currentHealth < 0) {
            this.currentHealth = 0;
        }
    }

    /**
     * Resets action flags at the start of a new turn.
     * If stunned, the unit remains in an "acted" state for the duration of the turn.
     */
    public void resetTurn() {
        if (this.isStunned) {
            // If stunned, force "acted" status but keep flag for UI display purposes
            this.hasMoved = true;
            this.hasAttacked = true;
        } else {
            // Normal units regain their ability to move and attack
            this.hasMoved = false;
            this.hasAttacked = false;
        }
        this.hasCounterAttacked = false;
    }

    // =================================================================================
    // 2. Getters & Setters
    // =================================================================================

    // Name and Identification
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // Ownership
    public GamePlayer getOwner() { return owner; }
    public void setOwner(GamePlayer owner) { this.owner = owner; }

    // Basic Object Reference
    public Unit getBasicUnit() { return basicUnit; }
    
    // Health Management
    public int getCurrentHealth() { return currentHealth; }
    public void setCurrentHealth(int h) { this.currentHealth = h; }
    
    public int getMaxHealth() { return maxHealth; }
    public void setMaxHealth(int h) { this.maxHealth = h; }
    
    // Attack Management
    public int getCurrentAttack() { return currentAttack; }
    public void setCurrentAttack(int a) { this.currentAttack = a; }
    
    // Action State Flags
    public boolean hasMoved() { return hasMoved; }
    public void setHasMoved(boolean hasMoved) { this.hasMoved = hasMoved; }

    public boolean hasAttacked() { return hasAttacked; }
    public void setHasAttacked(boolean hasAttacked) { this.hasAttacked = hasAttacked; }

    public boolean hasCounterAttacked() { return hasCounterAttacked; }
    public void setHasCounterAttacked(boolean hasCounterAttacked) { this.hasCounterAttacked = hasCounterAttacked; }

    /**
     * Gets the stun status of the unit.
     */
    public boolean isStunned() { return isStunned; }

    /**
     * Sets the stun status. If set to true, the unit immediately loses its 
     * ability to move or attack for the current turn.
     */
    public void setStunned(boolean stunned) {
        this.isStunned = stunned;
        if (stunned) {
            this.hasMoved = true;
            this.hasAttacked = true;
        }
    }

    // Artifact Management
    public String getArtifactName() { return artifactName; }
    public void setArtifactName(String artifactName) { this.artifactName = artifactName; }
    
    public int getArtifactDurability() { return artifactDurability; }
    public void setArtifactDurability(int durability) { this.artifactDurability = durability; }
}