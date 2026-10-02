package com.stratasmp.stratavotereward;

import com.stratasmp.stratacore.StrataModule;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Durable at-most-once receipt ledger for authenticated Votifier events. */
final class ProcessedVotes {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private final StrataModule plugin;
    private final File file;
    private final Set<String> processed = new HashSet<>();
    private final Set<String> inFlight = new HashSet<>();
    private boolean healthy = true;

    ProcessedVotes(StrataModule plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "processed-votes.log");
        load();
    }

    synchronized BeginResult begin(String voteId) {
        if (!healthy) {
            return BeginResult.UNAVAILABLE;
        }
        if (processed.contains(voteId) || !inFlight.add(voteId)) {
            return BeginResult.DUPLICATE;
        }
        return BeginResult.NEW;
    }

    /** Persist and force the receipt before issuing any reward. */
    synchronized void commit(String voteId) throws IOException {
        if (!inFlight.contains(voteId)) {
            throw new IOException("Vote receipt was not reserved");
        }
        byte[] record = (voteId + "\n").getBytes(StandardCharsets.US_ASCII);
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            ByteBuffer buffer = ByteBuffer.wrap(record);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
            processed.add(voteId);
            inFlight.remove(voteId);
        } catch (IOException failure) {
            healthy = false;
            inFlight.remove(voteId);
            throw failure;
        }
    }

    synchronized void release(String voteId) {
        inFlight.remove(voteId);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.US_ASCII)) {
                String value = line.trim();
                if (HASH.matcher(value).matches()) {
                    processed.add(value);
                } else if (!value.isEmpty()) {
                    plugin.getLogger().warning("Ignoring an invalid entry in processed-votes.log.");
                }
            }
            plugin.getLogger().info("Loaded " + processed.size() + " durable vote receipt(s).");
        } catch (IOException failure) {
            healthy = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Could not read processed-votes.log; vote payouts are disabled to avoid duplicate rewards.", failure);
        }
    }

    enum BeginResult {
        NEW,
        DUPLICATE,
        UNAVAILABLE
    }
}
