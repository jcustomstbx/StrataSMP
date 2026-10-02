package com.stratasmp.strataeconomy.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public surface for other Strata plugins (StrataElections uses balance ops,
 * the price table and the auction house). Grab it with {@link #get()}.
 */
public interface StrataApi {

    long getBalance(UUID uuid);

    boolean has(UUID uuid, long amount);

    void deposit(UUID uuid, long amount);

    /** @return false if the account didn't have enough. */
    boolean withdraw(UUID uuid, long amount);

    void set(UUID uuid, long amount);

    List<Map.Entry<UUID, Long>> top(int limit);

    String format(long amount);

    Prices prices();

    Auctions auctions();

    static StrataApi get() {
        RegisteredServiceProvider<StrataApi> rsp =
                Bukkit.getServicesManager().getRegistration(StrataApi.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
