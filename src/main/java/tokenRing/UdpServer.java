package tokenRing;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

class UdpServer extends Thread {
    Node node;

    UdpServer(Node node) {
        this.node = node;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(node.self.port + 1000)) {
            System.out.println("Node " + node.id + " UDP listening on port " + (node.self.port + 1000));

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String json = new String(packet.getData(), 0, packet.getLength());
                Message m = Message.fromJson(json);

                if (m != null && m.from != null && m.type.equals("HEARTBEAT")) {
                    node.lastSeen.put(m.from, System.currentTimeMillis());
                    System.out.println(node.id + " ❤️  heartbeat from " + m.from);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
