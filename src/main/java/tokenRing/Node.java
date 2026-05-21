package tokenRing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class Node {
    String id;
    int myId;

    NodeInfo self;
    List<NodeInfo> ring = new CopyOnWriteArrayList<>();

    volatile boolean hasToken = false;
    volatile long lastTokenTime = 0;
    volatile boolean electionInProgress = false;
    volatile long tokenSentTime = 0;
    volatile String tokenSentTo = null;
    volatile boolean tokenAckReceived = false;

    Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    public Node(String id, int myId, NodeInfo self, List<NodeInfo> ring) {
        this.id = id;
        this.myId = myId;
        this.self = self;
        this.ring.addAll(ring);
        // Khởi tạo lastSeen cho chính mình để tránh null
        this.lastSeen.put(id, System.currentTimeMillis());
    }

    void start() {
        new Server(this).start();
        new UdpHeartbeat(this).start();
        new UdpServer(this).start();
        new FailureDetector(this).start();
    }

    // ================= TOKEN =================
    void onReceiveToken() {
        hasToken = true;
        lastTokenTime = System.currentTimeMillis();

        System.out.println(id + " got TOKEN");

        // Check if we still have quorum before using token
        if (!hasQuorum()) {
            System.out.println(id + " ⚠️  NO QUORUM while holding token - forwarding immediately");
            forwardToken();
            return;
        }

        if (Math.random() < 0.6) {
            System.out.println(id + " using resource...");
            try { Thread.sleep(1000); } catch (Exception e) {}
        }

        sendDataIfNeeded();
        forwardToken();
    }

    void forwardToken() {
        NodeInfo next = findNextAlive();

        // Nếu không còn ai khác alive, giữ token và đợi
        if (next == null || next.id.equals(id)) {
            System.out.println(id + " no other alive node, keeping token");
            return; 
        }

        Message token = new Message();
        token.type = "TOKEN";
        token.from = id;
        token.timestamp = System.currentTimeMillis();

        sendWithAckTimeout(next, token, 3000); // 3 second timeout for token ACK
        hasToken = false;
    }

    // ================= DATA =================
    void sendDataIfNeeded() {
        if (Math.random() < 0.3) {
            List<NodeInfo> aliveOthers = ring.stream()
                    .filter(n -> !n.id.equals(id) && isAlive(n))
                    .toList();
            if (aliveOthers.isEmpty()) return;

            NodeInfo target = aliveOthers.get(new Random().nextInt(aliveOthers.size()));

            Message m = new Message();
            m.type = "DATA";
            m.from = id;
            m.to = target.id;
            m.payload = "Hello from " + id;

            send(target, m);
        }
    }

    void handleData(Message m) {
        if (m.to.equals(id)) {
            System.out.println(id + " received DATA: " + m.payload);
        } else if (isAlive(findNodeById(m.to))) {
            forward(m);
        } else {
            System.out.println(id + " cannot forward to " + m.to + ", destination not alive");
        }
    }

    // ================= HEARTBEAT =================
    boolean isAlive(NodeInfo n) {
        if (n == null) return false;
        if (n.id.equals(id)) return true; // Luôn coi mình alive
        return System.currentTimeMillis() - lastSeen.getOrDefault(n.id, 0L) < 3000;
    }

    NodeInfo findNodeById(String nodeId) {
        return ring.stream()
                .filter(n -> n.id.equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    NodeInfo findNextAlive() {
        List<NodeInfo> sorted = ring.stream()
                .sorted(Comparator.comparing(n -> n.id))
                .toList();

        // Tìm node tiếp theo trong vòng tròn (wrap-around)
        boolean foundSelf = false;
        for (NodeInfo n : sorted) {
            if (n.id.equals(id)) {
                foundSelf = true;
                continue;
            }
            if (foundSelf && isAlive(n)) {
                return n;
            }
        }
        // Nếu chưa tìm được, quay lại từ đầu đến trước self
        for (NodeInfo n : sorted) {
            if (n.id.equals(id)) break;
            if (isAlive(n)) return n;
        }
        return null; 
    }

    // ================= QUORUM/PARTITION DETECTION =================
    int getQuorumSize() {
        return ring.size() / 2 + 1;
    }

    int getAliveNodeCount() {
        return (int) ring.stream()
                .filter(this::isAlive)
                .count();
    }

    boolean hasQuorum() {
        int alive = getAliveNodeCount();
        int quorum = getQuorumSize();
        boolean result = alive >= quorum;
        if (!result) {
            System.out.println(id + " ⚠️  NO QUORUM: " + alive + " alive < " + quorum + " needed");
        }
        return result;
    }

    // ================= ACK =================
    void sendWithAck(NodeInfo node, Message msg) {
        try {
            Client.send(node, msg);
        } catch (Exception e) {
            System.out.println("Node " + node.id + " unreachable → skipping");
        }
    }

    void send(NodeInfo node, Message msg) {
        try {
            Client.send(node, msg);
        } catch (Exception e) {
            System.out.println("Send fail to " + node.id);
        }
    }
    void sendWithAckTimeout(NodeInfo node, Message msg, long timeoutMs) {
        new Thread(() -> {
            try {
                // Mark that we're sending token to this node and waiting for ACK
                tokenSentTo = node.id;
                tokenAckReceived = false;
                long startTime = System.currentTimeMillis();

                // Send token message
                Client.send(node, msg);
                System.out.println(id + " sent " + msg.type + " to " + node.id + ", waiting for TOKEN_ACK");
                tokenSentTime = System.currentTimeMillis();

                // Wait for TOKEN_ACK within timeout
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (tokenAckReceived) {
                        System.out.println(id + " received TOKEN_ACK from " + node.id);
                        return; // Success - token delivered
                    }
                    Thread.sleep(50); // Check every 50ms
                }

                // Timeout - token not acknowledged
                System.out.println(id + " TOKEN_ACK timeout from " + node.id + ", trying next node");
                NodeInfo nextNode = findNextAlive();
                if (nextNode != null && !nextNode.id.equals(id) && !nextNode.id.equals(node.id)) {
                    sendWithAckTimeout(nextNode, msg, timeoutMs);
                } else {
                    System.out.println(id + " no other alive node, keeping token");
                    hasToken = true;
                }

            } catch (Exception e) {
                System.out.println(id + " error sending " + msg.type + " to " + node.id + ": " + e.getMessage());
                // Try next alive node
                NodeInfo nextNode = findNextAlive();
                if (nextNode != null && !nextNode.id.equals(id)) {
                    sendWithAckTimeout(nextNode, msg, timeoutMs);
                } else {
                    System.out.println(id + " no other alive node, keeping token");
                    hasToken = true;
                }
            }
        }).start();
    }
    void forward(Message m) {
        NodeInfo next = findNextAlive();
        if (next == null) {
            System.out.println(id + " cannot forward, no alive node");
            return;
        }
        send(next, m);
    }

    // ================= ELECTION =================
    void startElection() {
        synchronized (this) {
            if (electionInProgress) return;
            electionInProgress = true;
        }

        // Check if we have quorum before running election
        if (!hasQuorum()) {
            System.out.println(id + " cannot start election - no quorum (minority partition detected)");
            synchronized (this) {
                electionInProgress = false;
            }
            return;
        }
        
        Message m = new Message();
        m.type = "ELECTION";
        m.electionId = myId;
        m.from = id;
        m.initiator = id;
        m.timestamp = System.currentTimeMillis();

        NodeInfo next = findNextAlive();
        if (next == null) {
            System.out.println(id + " alone in ring + has quorum → wins election");
            synchronized (this) {
                electionInProgress = false;
                if (hasQuorum()) {  // Double-check quorum
                    onReceiveToken();
                } else {
                    System.out.println(id + " lost quorum during election - aborting");
                }
            }
            return;
        }
        send(next, m);
    }

    void handleElection(Message m) {
        if (m.initiator != null && m.initiator.equals(id) && m.electionId == myId) {
            synchronized (this) {
                if (hasQuorum()) {  // Check quorum before creating token
                    System.err.println(id + " wins election + has quorum → create TOKEN");
                    electionInProgress = false;
                    onReceiveToken();
                } else {
                    System.err.println(id + " won election but NO QUORUM - cannot create token");
                    electionInProgress = false;
                }
            }
            return;
        }

        if (m.electionId < myId) {
            m.electionId = myId;
            m.initiator = id;
            System.err.println(id + " forwards election with higher ID: " + myId);
        }

        NodeInfo next = findNextAlive();
        if (next == null) {
            System.out.println(id + " no next node, election halts");
            return;
        }
        send(next, m);
    }

    // ================= JOIN =================
    void join(NodeInfo bootstrap) {
        Message m = new Message();
        m.type = "JOIN";
        m.node = self;
        m.from = id;

        send(bootstrap, m);
    }

    void handleJoin(Message m) {
        NodeInfo newNode = m.node;
        if (newNode == null || newNode.id == null) return;
        if (ring.stream().anyMatch(n -> n.id.equals(newNode.id))) {
            return; // already in ring
        }

        System.out.println("Node " + newNode.id + " joined the ring");
        ring.add(newNode);
        lastSeen.put(newNode.id, System.currentTimeMillis());

        Message updateMsg = new Message();
        updateMsg.type = "UPDATE_RING";
        updateMsg.ring = new ArrayList<>(ring);
        updateMsg.from = id;
        send(newNode, updateMsg);

        broadcastRing();
    }

    // ================= LEAVE =================
    void leave() {
        if (hasToken) {
            System.out.println(id + " leaving while holding token → forward first");
            hasToken = false;
            Message token = new Message();
            token.type = "TOKEN";
            token.from = id;
            token.timestamp = System.currentTimeMillis();
            NodeInfo next = findNextAlive();
            if (next != null) {
                send(next, token);
            }
        }

        Message m = new Message();
        m.type = "LEAVE";
        m.node = self;
        m.from = id;

        broadcast(m);
        ring.removeIf(n -> n.id.equals(id));
    }

    void handleLeave(Message m) {
        NodeInfo leaving = m.node;
        if (leaving == null || leaving.id == null) return;
        System.out.println("Node " + leaving.id + " left the ring");
        
        // If leaving node was holding token, trigger election
        if (leaving.id.equals(id)) {
            System.out.println(id + " detected own leave message");
            return;
        }
        
        ring.removeIf(n -> n.id.equals(leaving.id));
        lastSeen.remove(leaving.id);
        
        // If token was with leaving node, trigger election
        if (!hasToken && System.currentTimeMillis() - lastTokenTime > 2000) {
            System.out.println(id + " node " + leaving.id + " left while holding token → election");
            startElection();
        }
        
        broadcastRing();
    }

    // ================= UPDATING =================
    void broadcastRing() {
        Message m = new Message();
        m.type = "UPDATE_RING";
        m.ring = new ArrayList<>(ring);
        m.from = id;

        broadcast(m);
    }

    void handleUpdatering(Message m) {
        if (m.ring == null) return;
        System.out.println(id + " updating ring...");
        ring.clear();
        ring.addAll(m.ring);

        for (NodeInfo n : ring) {
            if (!n.id.equals(id) && !lastSeen.containsKey(n.id)) {
                lastSeen.put(n.id, System.currentTimeMillis());
            }
        }
    }

    // ================= BROADCAST =================
    void broadcast(Message m) {
        for (NodeInfo n : ring) {
            if (!n.id.equals(id)) {
                send(n, m);
            }
        }
    }
}