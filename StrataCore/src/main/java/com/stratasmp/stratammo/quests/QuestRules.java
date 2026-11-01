package com.stratasmp.stratammo.quests;

import com.stratasmp.stratammo.Skill;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Pure rules for weekly resets and story-chain progress, kept free of Bukkit so they can be tested directly. */
public final class QuestRules {

    private QuestRules() {
    }

    /** The date the current week started: the most recent {@code resetDay} on or before {@code today}. */
    public static String weekKey(LocalDate today, DayOfWeek resetDay) {
        return today.with(TemporalAdjusters.previousOrSame(resetDay)).toString();
    }

    /**
     * Picks up to {@code count} weekly quests from the pool, preferring a different skill for each so a week
     * is not three mining quests, and only repeating a skill when the pool has fewer skills than {@code count}.
     */
    public static List<QuestDef> pickWeekly(List<QuestDef> pool, int count, Random random) {
        List<QuestDef> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        Set<Skill> usedSkills = new HashSet<>();
        List<QuestDef> picked = new ArrayList<>();
        for (QuestDef def : shuffled) {
            if (picked.size() < count && usedSkills.add(def.skill())) {
                picked.add(def);
            }
        }
        for (QuestDef def : shuffled) {
            if (picked.size() < count && !picked.contains(def)) {
                picked.add(def);
            }
        }
        return picked;
    }

    public enum Result { NONE, STEP_DONE, CHAIN_DONE }

    /** Where a player is in one story chain. A finished chain never moves again, so rewards can't repeat. */
    public static final class ChainState {
        private int step;
        private int progress;
        private boolean done;

        public ChainState() {
        }

        public ChainState(int step, int progress, boolean done) {
            this.step = Math.max(0, step);
            this.progress = Math.max(0, progress);
            this.done = done;
        }

        public int step() { return step; }
        public int progress() { return progress; }
        public boolean done() { return done; }

        /**
         * Adds progress to the current step.
         *
         * @param stepAmount amount the current step needs
         * @param stepCount  number of steps in the chain
         */
        public Result add(int delta, int stepAmount, int stepCount) {
            if (done || delta <= 0 || stepAmount <= 0 || stepCount <= 0) {
                return Result.NONE;
            }
            progress = Math.min(stepAmount, progress + delta);
            if (progress < stepAmount) {
                return Result.NONE;
            }
            if (step + 1 >= stepCount) {
                done = true;
                return Result.CHAIN_DONE;
            }
            step++;
            progress = 0;
            return Result.STEP_DONE;
        }
    }
}
