error id: file:///D:/Doneload/ITSD-DT2025-26-Template/app/structures/AttackLogic.java:java/lang/InterruptedException#
file:///D:/Doneload/ITSD-DT2025-26-Template/app/structures/AttackLogic.java
empty definition using pc, found symbol in pc: java/lang/InterruptedException#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1650
uri: file:///D:/Doneload/ITSD-DT2025-26-Template/app/structures/AttackLogic.java
text:
```scala
package structures;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.basic.UnitAnimationType;

public class AttackLogic {

    public static void executeAttack(GameUnit attacker, GameUnit defender, ActorRef out, GameState gameState) {
        if (attacker.isStunned()) return;

        // 1. 嘲讽检查
        if (MovementRule.isProvoked(gameState.board, attacker)) {
            if (!MovementRule.isProvokeUnit(defender)) {
                BasicCommands.addPlayer1Notification(out, "Must attack Provoker!", 2);
                return; 
            }
        }

        BasicCommands.addPlayer1Notification(out, "Attacking!", 2);
        BasicCommands.playUnitAnimation(out, attacker.getBasicUnit(), UnitAnimationType.attack);
        try { Thread.sleep(500); } catch (InterruptedException e) {}

        // 2. 结算攻击伤害
        dealDamage(defender, attacker.getCurrentAttack(), out, gameState);

        // 3. 反击逻辑 (Counter-Attack)
        if (defender.getCurrentHealth() > 0 && attacker.getCurrentHealth() > 0) {
            int ax = getUnitX(gameState, attacker);
            int ay = getUnitY(gameState, attacker);
            int dx = getUnitX(gameState, defender);
            int dy = getUnitY(gameState, defender);

            // 只有相邻才能反击
            if (Math.abs(ax - dx) <= 1 && Math.abs(ay - dy) <= 1) {
                try { Thread.sleep(500); } catch (InterruptedException e) {}
                BasicCommands.addPlayer1Notification(out, "Counter Attack!", 2);
                BasicCommands.playUnitAnimation(out, defender.getBasicUnit(), UnitAnimationType.attack);
                try { Thread.sleep(500); } catch (@@InterruptedException e) {}

                // 结算反击伤害
                dealDamage(attacker, defender.getCurrentAttack(), out, gameState);
            }
        }

        // --- 【核心修复】检查攻击者是否阵亡 ---
        // 如果攻击者死了，它已经被移除出棋盘，普通的 removeHighlight 找不到它。
        // 所以必须在这里强制清除全场高亮。
        if (attacker.getCurrentHealth() <= 0) {
            gameState.selectionState.clear();
            clearAllHighlights(out, gameState);
        }
        // ------------------------------------

        // Horn of the Forsaken 特效 (只有活着才能发动)
        if (attacker.getCurrentHealth() > 0 && 
            attacker.getArtifactName() != null && 
            attacker.getArtifactName().equals("Horn of the Forsaken")) {
            BasicCommands.addPlayer1Notification(out, "Horn Summons!", 2);
            summonWraithlingNearby(out, gameState, getUnitX(gameState, attacker), getUnitY(gameState, attacker));
        }

        // 标记状态
        if (attacker.getCurrentHealth() > 0) {
            attacker.setHasAttacked(true);
            attacker.setHasMoved(true);
        }
        
        // 如果防御者死了，也顺手清理一下（虽然通常 TileClicked 会处理）
        if (gameState.selectionState.selectedUnit == defender && defender.getCurrentHealth() <= 0) {
            gameState.selectionState.clear();
            clearAllHighlights(out, gameState);
        }
    }

    public static void dealDamage(GameUnit target, int damage, ActorRef out, GameState gameState) {
        
        target.takeDamage(damage);
        BasicCommands.playUnitAnimation(out, target.getBasicUnit(), UnitAnimationType.hit);
        BasicCommands.setUnitHealth(out, target.getBasicUnit(), target.getCurrentHealth());

        // 同步 UI 血量
        int unitId = target.getBasicUnit().getId();
        if (unitId == 1) { 
            gameState.humanPlayer.getBasicPlayer().setHealth(target.getCurrentHealth());
            BasicCommands.setPlayer1Health(out, gameState.humanPlayer.getBasicPlayer());
        } else if (unitId == 2) { 
            gameState.aiPlayer.getBasicPlayer().setHealth(target.getCurrentHealth());
            BasicCommands.setPlayer2Health(out, gameState.aiPlayer.getBasicPlayer());
        }

        // 神器耐久扣除
        if (target.getArtifactName() != null) {
            target.setArtifactDurability(target.getArtifactDurability() - 1);
            if (target.getArtifactDurability() <= 0) {
                BasicCommands.addPlayer1Notification(out, "Artifact Broken!", 2);
                target.setArtifactName(null); 
            }
        }

        // 触发 Zeal (如果受伤的是英雄)
        if (isAvatar(target)) {
            triggerSilverguardKnightZeal(out, gameState, target.getOwner());
        }

        // 死亡判定
        if (target.getCurrentHealth() <= 0) {
            handleUnitDeath(out, gameState, target);
        }
    }

    private static void handleUnitDeath(ActorRef out, GameState gameState, GameUnit unit) {
        try { Thread.sleep(300); } catch (InterruptedException e) {}
        BasicCommands.playUnitAnimation(out, unit.getBasicUnit(), UnitAnimationType.death);
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        gameState.board.removeUnit(unit);
        BasicCommands.deleteUnit(out, unit.getBasicUnit());
        
        // 触发亡语
        triggerDeathwatch(out, gameState);

        // 胜负判定
        int unitId = unit.getBasicUnit().getId();
        if (unitId == 1) { 
            BasicCommands.addPlayer1Notification(out, "GAME OVER", 100);
            gameState.gameInitalised = false; 
        } else if (unitId == 2) { 
            BasicCommands.addPlayer1Notification(out, "VICTORY!", 100);
            gameState.gameInitalised = false; 
        }
    }
    
    // --- 【核心修复】Deathwatch 逻辑 ---
    private static void triggerDeathwatch(ActorRef out, GameState gameState) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                GameUnit u = gameState.board.getUnitAt(x, y);
                if (u != null && u.getName() != null && u.getCurrentHealth() > 0) {
                    
                    // 1. Bad Omen (+1 攻)
                    if (u.getName().equals("Bad Omen")) {
                        BasicCommands.addPlayer1Notification(out, "Bad Omen Feeds!", 2);
                        playBuff(out, gameState.board.getTile(x, y)); // 恢复特效
                        u.setCurrentAttack(u.getCurrentAttack() + 1);
                        BasicCommands.setUnitAttack(out, u.getBasicUnit(), u.getCurrentAttack());
                    }
                    
                    // 2. Shadow Watcher (+1/+1)
                    if (u.getName().equals("Shadow Watcher")) {
                        BasicCommands.addPlayer1Notification(out, "Shadow Watcher Grows!", 2);
                        playBuff(out, gameState.board.getTile(x, y)); // 恢复特效
                        u.setCurrentAttack(u.getCurrentAttack() + 1);
                        u.setCurrentHealth(u.getCurrentHealth() + 1);
                        u.setMaxHealth(u.getMaxHealth() + 1); 
                        BasicCommands.setUnitAttack(out, u.getBasicUnit(), u.getCurrentAttack());
                        BasicCommands.setUnitHealth(out, u.getBasicUnit(), u.getCurrentHealth());
                    }
                    
                    // 3. Bloodmoon Priestess (召唤 Wraithling)
                    if (u.getName().equals("Bloodmoon Priestess")) {
                        BasicCommands.addPlayer1Notification(out, "Bloodmoon Rise!", 2);
                        summonWraithlingNearby(out, gameState, x, y);
                    }

                    // 4. Shadowdancer (吸血)
                    if (u.getName().equals("Shadowdancer")) {
                        BasicCommands.addPlayer1Notification(out, "Soul Steal!", 2);
                        playBuff(out, gameState.board.getTile(x, y)); // 恢复特效
                        
                        // 对敌方英雄造成伤害
                        GameUnit enemyHero = getAvatar(gameState, (u.getOwner() == gameState.humanPlayer) ? gameState.aiPlayer : gameState.humanPlayer);
                        if (enemyHero != null) {
                            dealDamage(enemyHero, 1, out, gameState);
                        }

                        // 己方英雄回血
                        GameUnit myHero = getAvatar(gameState, u.getOwner());
                        if (myHero != null && myHero.getCurrentHealth() < myHero.getMaxHealth()) { 
                            int newHp = myHero.getCurrentHealth() + 1;
                            myHero.setCurrentHealth(newHp);
                            BasicCommands.setUnitHealth(out, myHero.getBasicUnit(), newHp);
                            
                            // 同步回血 UI
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

    private static boolean isAvatar(GameUnit unit) {
        return unit.getBasicUnit().getId() == 1 || unit.getBasicUnit().getId() == 2;
    }

    private static GameUnit getAvatar(GameState gameState, GamePlayer player) {
        int targetId = (player == gameState.humanPlayer) ? 1 : 2;
        for(int x=0; x<gameState.board.getXMax(); x++){
            for(int y=0; y<gameState.board.getYMax(); y++){
                GameUnit u = gameState.board.getUnitAt(x, y);
                if(u != null && u.getBasicUnit().getId() == targetId) return u;
            }
        }
        return null;
    }
    
    private static void summonWraithlingNearby(ActorRef out, GameState gameState, int x, int y) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1,1}, {1,-1}, {-1,1}, {-1,-1}}; 
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
    
    // --- 恢复 playBuff 辅助方法 ---
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
            for (int y = 0; y < gameState.board.getYMax(); y++) { if (gameState.board.getUnitAt(x, y) == unit) return x; }
        } return -1;
    }
    private static int getUnitY(GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) { if (gameState.board.getUnitAt(x, y) == unit) return y; }
        } return -1;
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: java/lang/InterruptedException#