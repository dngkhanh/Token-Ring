package tokenRing;

import java.io.PrintWriter;
import java.net.Socket;

class Client {
    static void send(NodeInfo node, Message msg) throws Exception {
        try (Socket socket = new Socket(node.host, node.port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(msg.toJson());
        }
    }
}