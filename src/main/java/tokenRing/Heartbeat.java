package tokenRing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

class Heartbeat extends Thread {
    Node node;
    Map<String, Long> heartbeatSent = new ConcurrentHashMap<>();

    Heartbeat(Node node) {
        this.node = node;
    }

    @Override
    public void run() {
        while (true) {
            long now = System.currentTimeMillis();
            
            // Send heartbeat to all alive nodes
            for (NodeInfo n : node.ring) {
                if (!n.id.equals(node.id)) {
                    Message hb = new Message();
                    hb.type = "HEARTBEAT";
                    hb.from = node.id;
                    hb.timestamp = now;

                    heartbeatSent.put(n.id, now);
                    node.send(n, hb);
                    
                    // Check if we got ACK response within 3 seconds
                    if (now - node.lastSeen.getOrDefault(n.id, 0L) > 3000) {
                        System.out.println(node.id + " no heartbeat ACK from " + n.id + " → marking dead");
                    }
                }
            }

            try { Thread.sleep(1000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}