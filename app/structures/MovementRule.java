package structures;

import structures.basic.Tile;
import structures.Board;

/**
 * MovementRule
 * ---------------------------------------------------------------------
 * Defines the rules for unit movement and positioning.
 * Core responsibilities:
 * 1. Identifying units with the "Provoke" (Taunt) ability.
 * 2. Validating if a move to a target tile is legal.
 * 3. Checking for path obstructions and Provoke constraints.
 * ---------------------------------------------------------------------
 */
public class MovementRule {

    /**
     * Determines if a unit has the "Provoke" ability based on its name.
     */
    public static boolean isProvokeUnit(GameUnit unit) {
        if (unit == null || unit.getName() == null) return false;
        
        String name = unit.getName().trim().toLowerCase();
        
        // Covers all Provoke units for both Human and AI, including spelling variants
        return
            name.contains("rock pulveris") || name.contains("rock pulveriz") || 
            name.contains("ironcliff") || name.contains("silverguard knight") || 
            name.contains("swamp entangler");
    }

    /**
     * Validates if a unit can move to the specified target coordinates.
     * Logic includes stun checks, overlap prevention, flight logic, and path blocking.
     */
    public static boolean isValidMove(Board board, GameUnit unit, int targetX, int targetY) {
        
        // 0. Base Status Check
        if (unit.isStunned() || unit.hasMoved()) return false;

        // 1. Overlap Prevention
        // Target tile must be within bounds and empty.
        // Checked before flight logic to ensure flying units don't land on occupied tiles.
        if (targetX < 0 || targetX >= board.getXMax() || targetY < 0 || targetY >= board.getYMax()) return false;
        if (board.getUnitAt(targetX, targetY) != null) return false;

        // 2. Flight Logic (Young Flamewing)
        // If the target is empty, flying units can move anywhere (teleport logic)
        if (unit.getName() != null && unit.getName().equals("Young Flamewing")) return true; 

        // 3. Provoke Check
        // If the unit is currently adjacent to an enemy Provoke unit, it cannot move
        if (isProvoked(board, unit)) return false;

        // 4. Locate Starting Position
        int startX = -1;
        int startY = -1;
        boolean found = false;
        for (int x = 0; x < board.getXMax(); x++) {
            for (int y = 0; y < board.getYMax(); y++) {
                if (board.getUnitAt(x, y) == unit) {
                    startX = x;
                    startY = y;
                    found = true;
                    break;
                }
            }
            if (found) break;
        }
        if (!found) return false;

        // 5. Path Validation
        int diffX = targetX - startX;
        int diffY = targetY - startY;
        int distance = Math.abs(diffX) + Math.abs(diffY); // Manhattan distance

        // Distance must be 1 or 2
        if (distance == 0 || distance > 2) return false;

        // Distance 1: Direct move, no obstruction check needed (target is already confirmed empty)
        if (distance == 1) return true;

        // Distance 2: Check for obstructions on the intermediate path
        if (distance == 2) {
            // A. Straight Line Movement (2,0 or 0,2)
            if (diffX == 0 || diffY == 0) {
                int midX = startX + (diffX / 2);
                int midY = startY + (diffY / 2);
                return board.getUnitAt(midX, midY) == null;
            }
            // B. Diagonal Movement (1,1)
            else {
                // If either of the two possible paths to the diagonal tile is clear, the move is valid
                boolean path1Clear = (board.getUnitAt(startX + diffX, startY) == null);
                boolean path2Clear = (board.getUnitAt(startX, startY + diffY) == null);
                return path1Clear || path2Clear;
            }
        }

        return false;
    }

    /**
     * Checks if a unit is currently "Provoked" by an adjacent enemy unit.
     */
    public static boolean isProvoked(Board board, GameUnit unit) {
        int unitX = -1, unitY = -1;
        
        // Find unit position
        for (int x = 0; x < board.getXMax(); x++) {
            for (int y = 0; y < board.getYMax(); y++) {
                if (board.getUnitAt(x, y) == unit) {
                    unitX = x;
                    unitY = y;
                    break;
                }
            }
        }
        if (unitX == -1) return false;

        // Check all 8 neighboring tiles (including diagonals)
        int[][] directions = {
            {-1, -1}, {-1, 0}, {-1, 1},
            { 0, -1},          { 0, 1},
            { 1, -1}, { 1, 0}, { 1, 1}
        };

        for (int[] dir : directions) {
            int nx = unitX + dir[0];
            int ny = unitY + dir[1];
            
            if (nx >= 0 && nx < board.getXMax() && ny >= 0 && ny < board.getYMax()) {
                GameUnit neighbor = board.getUnitAt(nx, ny);
                // If the neighbor is an enemy and has the Provoke ability
                if (neighbor != null && neighbor.getOwner() != unit.getOwner() && isProvokeUnit(neighbor)) {
                    return true;
                }
            }
        }
        return false;
    }
}