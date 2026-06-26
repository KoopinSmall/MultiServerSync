package uz.koopin.mss.storage;

public record SyncUser(String projectName, String playerName, String proxy, String previousServer, String server) {}