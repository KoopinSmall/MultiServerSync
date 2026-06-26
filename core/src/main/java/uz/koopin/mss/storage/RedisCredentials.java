package uz.koopin.mss.storage;

public record RedisCredentials(String project, String host, int port, String password) { }