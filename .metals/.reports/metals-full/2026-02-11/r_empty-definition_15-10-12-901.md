error id: file:///D:/Doneload/ITSD-DT2025-26-Template/app/events/EndTurnClicked.java:_empty_/Tile#getTiley#
file:///D:/Doneload/ITSD-DT2025-26-Template/app/events/EndTurnClicked.java
empty definition using pc, found symbol in pc: _empty_/Tile#getTiley#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 4551
uri: file:///D:/Doneload/ITSD-DT2025-26-Template/app/events/EndTurnClicked.java
text:
```scala
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

public class EndTurnClicked implements EventProcessor {

@Override
public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
    if (!gameState.gameInitalised) return;

    // ==================== 【在这里插入代码】 ====================
        
        // 1. 清除全屏高亮 (把所有格子画成模式 0)
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0);
                try { Thread.sleep(2); } catch (InterruptedException e) {}
            }
        }

        // 2. 视觉上取消手牌选中状态 (把选中的卡画成模式 0)
        if (gameState.selectionState.selectedHandPosition != -1) {
            int handPos = gameState.selectionState.selectedHandPosition;
            // 简单校验一下防止越界
            if (handPos <= gameState.humanPlayer.getHand().size()) {
                Card card = gameState.humanPlayer.getHand().get(handPos - 1);
                if (card != null) {
                    BasicCommands.drawCard(out, card, handPos, 0); 
                }
            }
        }

        // 3. 清空逻辑选中状态 (清除内存里的 selectedUnit 和 selectedCard)
        gameState.selectionState.clear();

        // ==================== 【插入结束】 ====================

    // 当玩家点击“结束回合”，说明被定身的惩罚已服刑完毕，现在可以清除了
    for (int x = 0; x < gameState.board.getXMax(); x++) {
        for (int y = 0; y < gameState.board.getYMax(); y++) {
            GameUnit u = gameState.board.getUnitAt(x, y);
            if (u != null && u.getOwner() == gameState.humanPlayer) {
                u.setStunned(false); 
            }
        }
    }
    

        // --- AI Turn ---
        BasicCommands.addPlayer1Notification(out, "Enemy Turn", 2);
        
        int currentAIMax = gameState.aiPlayer.getMaxMana();
        if (currentAIMax < 9) gameState.aiPlayer.setMaxMana(currentAIMax + 1);
        if (gameState.aiPlayer.getMaxMana() > 9) gameState.aiPlayer.setMaxMana(9);
        gameState.aiPlayer.setMana(gameState.aiPlayer.getMaxMana()); 
        
        gameState.aiPlayer.getBasicPlayer().setMana(gameState.aiPlayer.getMana());
        BasicCommands.setPlayer2Mana(out, gameState.aiPlayer.getBasicPlayer());

        executeAITurn(out, gameState);

        // --- Your Turn ---
        BasicCommands.addPlayer1Notification(out, "Your Turn!", 2);

        int currentHumanMax = gameState.humanPlayer.getMaxMana();
        if (currentHumanMax < 9) gameState.humanPlayer.setMaxMana(currentHumanMax + 1);
        if (gameState.humanPlayer.getMaxMana() > 9) gameState.humanPlayer.setMaxMana(9);
        gameState.humanPlayer.setMana(gameState.humanPlayer.getMaxMana());
        
        gameState.humanPlayer.getBasicPlayer().setMana(gameState.humanPlayer.getMana());
        BasicCommands.setPlayer1Mana(out, gameState.humanPlayer.getBasicPlayer());

        for (int i = 0; i < 2; i++) {
            drawCardWithLimit(out, gameState);
            try { Thread.sleep(300); } catch (InterruptedException e) {}
        }

        resetAllUnits(out, gameState);
    }

private void moveAIUnitVisually(ActorRef out, GameState gameState, GameUnit unit, Tile targetTile) {
    // 飞行单位直接飞，不需要分段
    if (unit.getName() != null && unit.getName().equals("Young Flamewing")) {
        BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
        return;
    }

    int startX = getUnitX(gameState, unit);
    int startY = getUnitY(gameState, unit);
    int diffX = targetTile.getTilex() - startX;
    int diffY = targetTile.getTiley() - startY;

    // 如果走 2 格，必须分段移动
    if (Math.abs(diffX) + Math.abs(diffY) == 2) {
        Tile intermediate = null;
        // 直线移动 (2,0) 或 (0,2)
        if (diffX == 0 || diffY == 0) {
            intermediate = gameState.board.getTile(startX + diffX / 2, startY + diffY / 2);
        } 
        // 斜向移动 (1,1)
        else {
            // 优先选择空的中间格
            Tile t1 = gameState.board.getTile(startX + diffX, startY);
            if (gameState.board.getUnitAt(t1.getTilex(), t1.getTiley@@()) == null) {
                intermediate = t1;
            } else {
                intermediate = gameState.board.getTile(startX, startY + diffY);
            }
        }

        if (intermediate != null) {
            BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), intermediate);
            try { Thread.sleep(800); } catch (InterruptedException e) {} // 给一点行走时间
            BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
        } else {
            BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
        }
    } else {
        // 走 1 格直接移
        BasicCommands.moveUnitToTile(out, unit.getBasicUnit(), targetTile);
    }
}


    private void executeAITurn(ActorRef out, GameState gameState) {
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        // Phase 1: Draw
        for (int k = 0; k < 1; k++) {
            if (!gameState.aiPlayer.getDeck().isEmpty()) {
                if (gameState.aiPlayer.getHand().size() < 6) {
                    gameState.aiPlayer.getHand().add(gameState.aiPlayer.getDeck().remove(0));
                } else {
                    gameState.aiPlayer.getDeck().remove(0); // Burn
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
                    if (castSmartAIAbility(out, gameState, card)) {
                        played = true;
                    }
                }

                if (played) {
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
                if (!isInAttackRange(gameState, unit, target)) {
                    moveAIUnitTowards(out, gameState, unit, target);
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                }
                
                if (isInAttackRange(gameState, unit, target) && !unit.hasAttacked()) {
                    AttackLogic.executeAttack(unit, target, out, gameState);
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                }
            }
        }
    }

    private void moveAIUnitTowards(ActorRef out, GameState gameState, GameUnit unit, GameUnit target) {
        if (unit.hasMoved()) return;
        int startX = getUnitX(gameState, unit); int startY = getUnitY(gameState, unit);
        int targetX = getUnitX(gameState, target); int targetY = getUnitY(gameState, target);
        
        Tile bestTile = null;
        int bestDist = Math.abs(startX - targetX) + Math.abs(startY - targetY);

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
            // 【核心防重叠】再次检查目标格是否为空
            if (gameState.board.getUnitAt(bestTile.getTilex(), bestTile.getTiley()) != null) {
                return;
            }

// 【修改这里】改用分段移动方法
            moveAIUnitVisually(out, gameState, unit, bestTile); 

            gameState.board.setUnitAt(startX, startY, null);
            gameState.board.setUnitAt(bestTile.getTilex(), bestTile.getTiley(), unit);
            unit.getBasicUnit().setPositionByTile(bestTile);
            unit.setHasMoved(true);
        }
    }

    // --- Helpers ---
    
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
            
            if (card.getCardname().equals("Saberspine Tiger")) {
                gameUnit.setHasMoved(false); gameUnit.setHasAttacked(false);
            } else {
                gameUnit.setHasMoved(true); gameUnit.setHasAttacked(true);
            }
            
            BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_buff.json"), tile);
            BasicCommands.addPlayer1Notification(out, "AI Summoned!", 2);

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

    private boolean castSmartAIAbility(ActorRef out, GameState gameState, Card card) {
        String name = card.getCardname();
        
        // 1. 【核心修复】增加对 Truestrike (无空格) 的支持
        if (name.equals("True Strike") || name.equals("Truestrike")) {
            GameUnit target = null;
            for (GameUnit e : getAllEnemyUnits(gameState)) {
                // 优先斩杀血量低的目标
                if (e.getCurrentHealth() <= 2) { target = e; break; }
            }
            if (target == null) target = getHero(gameState, gameState.humanPlayer);
            
            if (target != null) {
                BasicCommands.addPlayer1Notification(out, "AI uses True Strike!", 2);
                AttackLogic.dealDamage(target, 2, out, gameState);
                BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_inmolation.json"), 
                    gameState.board.getTile(getUnitX(gameState, target), getUnitY(gameState, target)));
                return true;
            }
        }

        // 2. 【逻辑微调】Sundrop Elixir 降低触发门槛 (掉 1 点血就让 AI 奶一下看看效果)
        if (name.equals("Sundrop Elixir")) {
            GameUnit target = null;
            int maxLost = 0;
            for (GameUnit f : getAllFriendlyUnits(gameState)) {
                int lost = f.getMaxHealth() - f.getCurrentHealth();
                // 修改为 lost >= 1，方便测试观察
                if (lost >= 1 && lost > maxLost) { 
                    maxLost = lost; target = f; 
                }
            }
            if (target != null) {
                BasicCommands.addPlayer1Notification(out, "AI Heals!", 2);
                int newHp = Math.min(target.getCurrentHealth() + 5, target.getMaxHealth());
                target.setCurrentHealth(newHp);
                BasicCommands.setUnitHealth(out, target.getBasicUnit(), newHp);
                
                // 同步英雄血量 UI
                if (isAvatar(target)) { 
                    if (target.getBasicUnit().getId() == 2) { // AI 英雄
                        gameState.aiPlayer.getBasicPlayer().setHealth(newHp);
                        BasicCommands.setPlayer2Health(out, gameState.aiPlayer.getBasicPlayer());
                    }
                }
                BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_buff.json"), 
                    gameState.board.getTile(getUnitX(gameState, target), getUnitY(gameState, target)));
                return true;
            }
        }
        if (name.equals("Beam Shock") || name.equals("Beamshock")) {
            GameUnit target = null;
            int maxAtk = -1;
            for (GameUnit e : getAllEnemyUnits(gameState)) {
                if (!isAvatar(e) && !e.isStunned() && e.getCurrentAttack() > maxAtk) {
                    maxAtk = e.getCurrentAttack(); target = e;
                }
            }
            if (target != null) {
                BasicCommands.addPlayer1Notification(out, "AI Stuns!", 2);
                target.setStunned(true);
                BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect("conf/gameconfs/effects/f1_martyrdom.json"), 
                    gameState.board.getTile(getUnitX(gameState, target), getUnitY(gameState, target)));
                return true;
            }
        }
        return false;
    }

private GameUnit findBestTarget(GameState gameState, GameUnit attacker) {
    // 1. 【核心修复】如果 AI 被嘲讽了，它必须从周围的嘲讽单位中选一个作为目标
    if (MovementRule.isProvoked(gameState.board, attacker)) {
        int ax = getUnitX(gameState, attacker);
        int ay = getUnitY(gameState, attacker);
        int[][] directions = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] dir : directions) {
            int nx = ax + dir[0]; int ny = ay + dir[1];
            if (nx>=0 && nx<9 && ny>=0 && ny<5) {
                GameUnit neighbor = gameState.board.getUnitAt(nx, ny);
                if (neighbor != null && neighbor.getOwner() != attacker.getOwner() && MovementRule.isProvokeUnit(neighbor)) {
                    return neighbor; // 强制锁定嘲讽者
                }
            }
        }
    }

    // 2. 没被嘲讽时，才按原逻辑优先打英雄
    GameUnit enemyHero = getHero(gameState, gameState.humanPlayer);
    if (isInAttackRange(gameState, attacker, enemyHero)) return enemyHero;
    
    GameUnit bestUnit = null;
    int minDistance = 999;
    for (GameUnit enemy : getAllEnemyUnits(gameState)) {
        // 【修改这里】传入 gameState，不要只传单位
        int dist = getDistance(gameState, attacker, enemy); 
        if (dist < minDistance) { minDistance = dist; bestUnit = enemy; }
    }
    return (bestUnit != null) ? bestUnit : enemyHero;
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
// 【修改这里】增加 GameState 参数，并传给 getUnitX/Y
private int getDistance(GameState gameState, GameUnit u1, GameUnit u2) {
    return Math.abs(getUnitX(gameState, u1) - getUnitX(gameState, u2)) 
         + Math.abs(getUnitY(gameState, u1) - getUnitY(gameState, u2));
}
    private Tile findValidSpawnTile(GameState gameState) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == null) {
                    if (hasFriendlyNeighbor(gameState, x, y, gameState.aiPlayer)) return gameState.board.getTile(x, y);
                }
            }
        }
        return null;
    }
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
// 1. 检查攻击范围（保持不变，它依赖下面两个方法）
    private boolean isInAttackRange(GameState gameState, GameUnit attacker, GameUnit defender) {
        int ax = getUnitX(gameState, attacker); 
        int ay = getUnitY(gameState, attacker);
        int dx = getUnitX(gameState, defender); 
        int dy = getUnitY(gameState, defender);
        // 距离计算逻辑正确
        return Math.abs(ax - dx) <= 1 && Math.abs(ay - dy) <= 1;
    }

    // 2. 【核心修改】X 搜索棋盘（你已经写对了）
    private int getUnitX(GameState gameState, GameUnit unit) {
        for (int x = 0; x < gameState.board.getXMax(); x++) {
            for (int y = 0; y < gameState.board.getYMax(); y++) {
                if (gameState.board.getUnitAt(x, y) == unit) return x;
            }
        }
        return -1;
    }

    // 3. 【必须修改】Y 也要搜索棋盘，不能直接 return getTiley()！
private int getUnitY(GameState gameState, GameUnit unit) {
    for (int x = 0; x < gameState.board.getXMax(); x++) {
        for (int y = 0; y < gameState.board.getYMax(); y++) {
            // 查棋盘最准！
            if (gameState.board.getUnitAt(x, y) == unit) return y; 
        }
    }
    return -1;
}

    // 4. 抽牌逻辑（这个逻辑是正确的，能正常处理手牌上限）
    private void drawCardWithLimit(ActorRef out, GameState gameState) {
        if (gameState.humanPlayer.getDeck().isEmpty()) { 
            BasicCommands.addPlayer1Notification(out, "Deck Empty!", 2); 
            return; 
        }
        int MAX_HAND_SIZE = 6;
        int currentHandCount = 0;
        for (Card c : gameState.humanPlayer.getHand()) { if (c != null) currentHandCount++; }
        
        if (currentHandCount < MAX_HAND_SIZE) {
            Card card = gameState.humanPlayer.getDeck().remove(0); // 简化写法
            int emptySlot = -1;
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
            gameState.humanPlayer.getDeck().remove(0); // 爆牌逻辑正确
            BasicCommands.addPlayer1Notification(out, "Hand Full! Burned!", 2);
        }
    }
private void resetAllUnits(ActorRef out, GameState gameState) {
    for (int x = 0; x < gameState.board.getXMax(); x++) {
        for (int y = 0; y < gameState.board.getYMax(); y++) {
            GameUnit unit = gameState.board.getUnitAt(x, y);
            if (unit != null) { 
                // 【注意】这里删掉了 if (unit.isStunned()) { unit.setStunned(false); }
                unit.resetTurn(); 
                BasicCommands.drawTile(out, gameState.board.getTile(x, y), 0); 
            }
        }
    }
}
    private boolean isUnitCard(String name) { 
        return !name.equals("Horn of the Forsaken") 
            && !name.equals("Truestrike") && !name.equals("True Strike") // 建议双重保险
            && !name.equals("Sundrop Elixir") 
            && !name.equals("Beam Shock") && !name.equals("Beamshock") // <--- 【关键修复】添加这个！
            && !name.equals("Wraithling Swarm") 
            && !name.equals("Dark Terminus"); 
    }    
    private String getUnitConfigByCardName(String cardName) {
        String name = cardName.trim();
        // 修正了 Ironcliff 的拼写，防止回退到 Golem
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
        // 修正了 Ironcliff 的拼写
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
    private boolean isAvatar(GameUnit unit) { return unit.getBasicUnit().getId() == 1 || unit.getBasicUnit().getId() == 2; }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/Tile#getTiley#