package tokenRing;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {

        String id = args[0];
        String configPath = args.length > 1 ? args[1] : "nodes.config";

        // Load node configuration from file
        List<NodeInfo> ring = loadRingConfig(configPath);
        
        if (ring.isEmpty()) {
            System.err.println("No nodes configured in " + configPath);
            System.exit(1);
        }

        NodeInfo self = ring.stream()
                .filter(n -> n.id.equals(id))
                .findFirst()
                .orElse(null);

        if (self == null) {
            System.err.println("Node " + id + " not found in " + configPath);
            System.exit(1);
        }

        int myId = id.charAt(0); // A=65, B=66...

        Node node = new Node(id, myId, self, ring);
        node.start();

        if (id.equals("A")) {
            Thread.sleep(2000);
            node.onReceiveToken();
        }
    }

    static List<NodeInfo> loadRingConfig(String path) {
        List<NodeInfo> nodes = new ArrayList<>();
        try {
            Files.lines(Paths.get(path))
                    .filter(line -> !line.trim().isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        String[] parts = line.split(",");
                        if (parts.length == 3) {
                            nodes.add(new NodeInfo(
                                    parts[0].trim(),
                                    parts[1].trim(),
                                    Integer.parseInt(parts[2].trim())
                            ));
                        }
                    });
        } catch (Exception e) {
            System.err.println("Error reading config file: " + e.getMessage());
        }
        return nodes;
    }
}