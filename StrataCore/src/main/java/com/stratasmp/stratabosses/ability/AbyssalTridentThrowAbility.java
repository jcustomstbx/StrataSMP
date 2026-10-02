package com.stratasmp.stratabosses.ability;

import com.stratasmp.stratabosses.StrataBosses;
import org.bukkit.entity.LivingEntity;
import com.stratasmp.stratacore.StrataModule;

public final class AbyssalTridentThrowAbility implements BossAbility {
    @Override
    public void execute(StrataModule plugin, LivingEntity boss) {
        if (plugin instanceof StrataBosses bosses) bosses.throwTrident(boss);
    }
}
