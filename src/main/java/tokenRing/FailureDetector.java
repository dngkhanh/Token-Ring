package tokenRing;

class FailureDetector extends Thread {
    Node node;
    private volatile boolean electionTriggered = false;

    FailureDetector(Node node) {
        this.node = node;
    }

    public void run() {
        while (true) {
            long now = System.currentTimeMillis();

            // Detect token lost (no token for 5 seconds)
            if (!node.hasToken && node.lastTokenTime != 0 && now - node.lastTokenTime > 5000) {
                if (!electionTriggered && !node.electionInProgress) {
                    System.out.println(node.id + " detects token lost → election");
                    electionTriggered = true;
                    node.startElection();
                    node.lastTokenTime = now; // Reset timer
                }
            } else if (node.hasToken) {
                electionTriggered = false; // Reset when we get token back
            }

            try { Thread.sleep(1000); } catch (Exception e) {}
        }
    }
}