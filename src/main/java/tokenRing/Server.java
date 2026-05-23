package tokenRing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

class Server extends Thread {
    Node node;

    Server(Node node) {
        this.node = node;
    }

    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(node.self.port)) {
            System.out.println("Node " + node.id + " listening on port " + node.self.port);

            while (true) {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(5000); // 5 second timeout
                new Thread(() -> handle(socket)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handle(Socket socket) {
        try (
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String json = in.readLine();
            if (json == null || json.trim().isEmpty()) return;

            Message m = Message.fromJson(json);
            if (m == null || m.from == null) return;

            node.lastSeen.put(m.from, System.currentTimeMillis());

            switch (m.type) {
                case "TOKEN" -> {
                    // When receiving TOKEN, send TOKEN_ACK back
                    sendTokenAck(m.from);
                    node.onReceiveToken();
                }
                case "DATA" -> node.handleData(m);
                case "ELECTION" -> node.handleElection(m);
                case "JOIN" -> node.handleJoin(m);
                case "LEAVE" -> node.handleLeave(m);
                case "UPDATE_RING" -> node.handleUpdatering(m);
                case "TOKEN_ACK" -> {
                    // Check if this ACK is for our sent token
                    if (m.from.equals(node.tokenSentTo)) {
                        node.tokenAckReceived = true;
                        System.out.println(node.id + " got TOKEN_ACK from " + m.from);
                    }
                }
                default -> System.out.println(node.id + " received unknown type: " + m.type);
            }

        } catch (Exception e) {
            System.err.println("Error handling message at " + node.id + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    void sendTokenAck(String fromNodeId) {
        try {
            NodeInfo sender = node.ring.stream()
                    .filter(n -> n.id.equals(fromNodeId))
                    .findFirst()
                    .orElse(null);
            
            if (sender != null) {
                Message ack = new Message();
                ack.type = "TOKEN_ACK";
                ack.from = node.id;
                ack.timestamp = System.currentTimeMillis();
                
                new Thread(() -> {
                    try {
                        Client.send(sender, ack);
                        System.out.println(node.id + " sent TOKEN_ACK to " + sender.id);
                    } catch (Exception e) {
                        System.err.println("Error sending TOKEN_ACK: " + e.getMessage());
                    }
                }).start();
            }
        } catch (Exception e) {
            System.err.println("Error in sendTokenAck: " + e.getMessage());
        }
    }
}