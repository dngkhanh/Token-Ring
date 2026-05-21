package tokenRing;

import java.util.List;
import com.google.gson.Gson;

class Message {
    String type; // TOKEN, DATA, ACK, HEARTBEAT, ELECTION, JOIN, LEAVE, UPDATE_RING
    String from;
    String to;
    String payload;
    String initiator;
    int electionId;
    long timestamp;

    static Gson gson = new Gson();

    String toJson() {
        return gson.toJson(this);
    }

    static Message fromJson(String json) {
        return gson.fromJson(json, Message.class);
    }

    NodeInfo node;
    List<NodeInfo> ring;
}