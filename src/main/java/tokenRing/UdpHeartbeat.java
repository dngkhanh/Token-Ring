package tokenRing;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

class UdpHeartbeat extends Thread {
    Node node;

    UdpHeartbeat(Node node) {
        this.node = node;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            System.out.println(node.id + " UDP Heartbeat started");

            while (true) {
                long now = System.currentTimeMillis();

                // Send heartbeat to each node
                for (NodeInfo n : node.ring) {
                    if (!n.id.equals(node.id)) {
                        Message hb = new Message();
                        hb.type = "HEARTBEAT";
                        hb.from = node.id;
                        hb.timestamp = now;

                        String json = hb.toJson();
                        byte[] data = json.getBytes();

                        try {
                            InetAddress address = InetAddress.getByName(n.host);
                            int udpPort = n.port + 1000;
                            DatagramPacket packet = new DatagramPacket(data, data.length, address, udpPort);
                            socket.send(packet);
                        } catch (Exception e) {
                            System.err.println(node.id + " UDP send error to " + n.id + ": " + e.getMessage());
                        }
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
