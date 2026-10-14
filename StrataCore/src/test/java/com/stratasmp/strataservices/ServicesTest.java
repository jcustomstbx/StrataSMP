package com.stratasmp.strataservices;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ServicesTest {
    private final List<String> warnings = new ArrayList<>();

    private List<ServiceDef> parse(String yaml) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        return ServiceDef.parseAll(cfg.getConfigurationSection("services"), warnings::add);
    }

    @Test
    void validServiceIsParsed() throws Exception {
        List<ServiceDef> defs = parse("services:\n  Keystone-1:\n    name: Keystone\n    icon: echo_shard\n    price: 500\n"
                + "    daily-limit: 3\n    commands: [\"keystone give %player% 1\"]\n");
        assertEquals(1, defs.size());
        ServiceDef def = defs.get(0);
        assertEquals("keystone-1", def.id());
        assertEquals(500, def.price());
        assertEquals(3, def.dailyLimit());
        assertEquals("", def.permission());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void brokenServicesAreSkippedWithAWarning() throws Exception {
        List<ServiceDef> defs = parse("services:\n"
                + "  badicon: {icon: NOPE, price: 5, commands: [x]}\n"
                + "  free: {icon: PAPER, price: 0, commands: [x]}\n"
                + "  negative: {icon: PAPER, price: -4, commands: [x]}\n"
                + "  nocmd: {icon: PAPER, price: 5}\n"
                + "  good: {icon: PAPER, price: 5, commands: [x]}\n");
        assertEquals(List.of("good"), defs.stream().map(ServiceDef::id).toList());
        assertEquals(4, warnings.size());
    }

    @Test
    void missingSectionGivesNothing() {
        assertTrue(ServiceDef.parseAll(null, warnings::add).isEmpty());
    }

    @Test
    void shippedConfigIsValid() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/modules/StrataServices/config.yml")) {
            cfg.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        List<ServiceDef> defs = ServiceDef.parseAll(cfg.getConfigurationSection("services"), warnings::add);
        assertFalse(defs.isEmpty());
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(defs.stream().allMatch(d -> d.price() > 0));
    }

    @Test
    void ledgerCountsPerDayAndResetsOnANewDate() {
        PurchaseLedger ledger = new PurchaseLedger();
        UUID player = UUID.randomUUID();
        LocalDate monday = LocalDate.of(2026, 10, 12);
        assertEquals(0, ledger.boughtToday(player, "a", monday));
        ledger.record(player, "a", monday);
        ledger.record(player, "a", monday);
        ledger.record(player, "b", monday);
        assertEquals(2, ledger.boughtToday(player, "a", monday));
        assertEquals(1, ledger.boughtToday(player, "b", monday));
        assertEquals(0, ledger.boughtToday(player, "a", monday.plusDays(1)), "a new day starts at zero");
        ledger.record(player, "a", monday.plusDays(1));
        assertEquals(1, ledger.boughtToday(player, "a", monday.plusDays(1)));
        assertEquals(0, ledger.boughtToday(player, "b", monday.plusDays(1)), "yesterday's other counts are dropped");
    }

    @Test
    void undoGivesTheCountBack() {
        PurchaseLedger ledger = new PurchaseLedger();
        UUID player = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 12);
        ledger.record(player, "a", day);
        ledger.undo(player, "a", day);
        assertEquals(0, ledger.boughtToday(player, "a", day));
        ledger.undo(player, "a", day);
        assertEquals(0, ledger.boughtToday(player, "a", day), "never negative");
    }

    @Test
    void ledgerSurvivesASaveAndLoadAndDropsOldDays() {
        PurchaseLedger ledger = new PurchaseLedger();
        UUID today = UUID.randomUUID();
        UUID old = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 12);
        ledger.record(today, "a", day);
        ledger.record(today, "a", day);
        ledger.record(old, "a", day.minusDays(1));
        YamlConfiguration saved = ledger.snapshot(day);
        assertNull(saved.get(old.toString()), "yesterday's entries aren't written");

        PurchaseLedger loaded = new PurchaseLedger();
        loaded.load(saved);
        assertEquals(2, loaded.boughtToday(today, "a", day));
    }

    @Test
    void damagedLedgerEntriesAreIgnored() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("not-a-uuid:\n  date: 2026-10-12\n" + UUID.randomUUID() + ":\n  date: nonsense\n");
        PurchaseLedger ledger = new PurchaseLedger();
        assertDoesNotThrow(() -> ledger.load(yaml));
    }
}
