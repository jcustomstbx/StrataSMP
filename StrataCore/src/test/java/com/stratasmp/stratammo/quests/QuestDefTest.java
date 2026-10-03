package com.stratasmp.stratammo.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stratasmp.stratammo.Skill;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestDefTest {

    private static QuestDef quest(ObjectiveType type, Set<String> targets) {
        return new QuestDef("q", "Quest", type, targets, 10, Skill.ALCHEMY, 500);
    }

    @Test
    void anyTargetMatchesEverythingOfThatType() {
        QuestDef quest = quest(ObjectiveType.CHOP_LOG, Set.of());
        assertTrue(quest.matches(ObjectiveType.CHOP_LOG, "OAK_LOG"));
        assertTrue(quest.matches(ObjectiveType.CHOP_LOG, "cherry_log"));
    }

    @Test
    void otherObjectiveTypesNeverMatch() {
        QuestDef quest = quest(ObjectiveType.MINE_BLOCK, Set.of());
        assertFalse(quest.matches(ObjectiveType.CHOP_LOG, "OAK_LOG"));
        assertFalse(quest.matches(ObjectiveType.KILL_MOB, "ZOMBIE"));
    }

    @Test
    void targetsMatchCaseInsensitively() {
        QuestDef quest = quest(ObjectiveType.KILL_MOB, Set.of("ZOMBIE", "HUSK"));
        assertTrue(quest.matches(ObjectiveType.KILL_MOB, "zombie"));
        assertTrue(quest.matches(ObjectiveType.KILL_MOB, "HUSK"));
        assertFalse(quest.matches(ObjectiveType.KILL_MOB, "SKELETON"));
    }

    @Test
    void potionQuestsCountStrongAndLongVariants() {
        QuestDef quest = quest(ObjectiveType.BREW_POTION, Set.of("SWIFTNESS"));
        assertTrue(quest.matches(ObjectiveType.BREW_POTION, "SWIFTNESS"));
        assertTrue(quest.matches(ObjectiveType.BREW_POTION, "STRONG_SWIFTNESS"));
        assertTrue(quest.matches(ObjectiveType.BREW_POTION, "LONG_SWIFTNESS"));
        assertFalse(quest.matches(ObjectiveType.BREW_POTION, "HEALING"));
    }

    @Test
    void variantStrippingOnlyAppliesToPotions() {
        QuestDef quest = quest(ObjectiveType.MINE_BLOCK, Set.of("STONE"));
        assertFalse(quest.matches(ObjectiveType.MINE_BLOCK, "STRONG_STONE"));
    }

    @Test
    void describeListsTargets() {
        assertEquals("Quest (swiftness)", quest(ObjectiveType.BREW_POTION, Set.of("SWIFTNESS")).describe());
        assertEquals("Quest", quest(ObjectiveType.BREW_POTION, Set.of()).describe());
    }
}
