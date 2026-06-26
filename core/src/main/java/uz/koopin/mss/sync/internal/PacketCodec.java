package uz.koopin.mss.sync.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import uz.koopin.mss.sync.SyncPacket;

public final class PacketCodec {

    static final String FIELD_CLASS = "__class";
    static final String FIELD_ORIGIN = "__origin";
    static final String FIELD_CORR = "__corr";
    static final String FIELD_REPLY = "__reply";

    private final Gson gson;

    public PacketCodec(Gson gson) {
        this.gson = gson;
    }

    public String encode(SyncPacket packet, String origin, String corrId, boolean isReply) {
        JsonElement tree = gson.toJsonTree(packet);
        JsonObject obj = tree.isJsonObject() ? tree.getAsJsonObject() : new JsonObject();
        obj.addProperty(FIELD_CLASS, packet.getClass().getName());
        obj.addProperty(FIELD_ORIGIN, origin);
        if (corrId != null) {
            obj.addProperty(FIELD_CORR, corrId);
        }
        if (isReply) {
            obj.addProperty(FIELD_REPLY, true);
        }
        return obj.toString();
    }

    public Decoded decode(String json) throws ClassNotFoundException {
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        if (obj == null || !obj.has(FIELD_CLASS)) {
            throw new IllegalArgumentException("Missing __class in payload: " + json);
        }

        String fqcn = obj.remove(FIELD_CLASS).getAsString();
        String origin = removeString(obj, FIELD_ORIGIN);
        String corrId = removeString(obj, FIELD_CORR);
        boolean isReply = obj.has(FIELD_REPLY) && obj.get(FIELD_REPLY).getAsBoolean();
        obj.remove(FIELD_REPLY);

        Class<?> cls = Class.forName(fqcn);
        if (!SyncPacket.class.isAssignableFrom(cls)) {
            throw new IllegalArgumentException(fqcn + " is not a SyncPacket");
        }

        SyncPacket packet = (SyncPacket) gson.fromJson(obj, cls);
        return new Decoded(packet, origin, corrId, isReply);
    }

    private static String removeString(JsonObject obj, String field) {
        if (!obj.has(field)) return null;
        JsonElement el = obj.remove(field);
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }

    public record Decoded(SyncPacket packet, String origin, String corrId, boolean isReply) { }
}
