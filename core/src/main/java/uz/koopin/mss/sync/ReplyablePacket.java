package uz.koopin.mss.sync;

public interface ReplyablePacket<R extends SyncPacket> extends SyncPacket {
    R handle();
}
