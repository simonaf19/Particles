import javax.swing.*;
import java.awt.*;
public class Renderer implements Runnable {

    private final Simulation sim;
    private JFrame frame;
    private DrawPanel panel;
    public Renderer(Simulation sim) {
        this.sim = sim;
    }

    @Override
    public void run() {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("Particles Simulation");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            panel = new DrawPanel(sim);
            panel.setPreferredSize(new Dimension(Config.WIDTH, Config.HEIGHT));
            frame.setContentPane(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            int delay = 1000 / Config.FPS; //~16ms 60FPS

            Timer timer = new Timer(delay, e -> {
                panel.repaint();
                if (sim.isDone()) {
                    ((Timer) e.getSource()).stop();
                }
            });
            timer.start();
    });
    }
    private static class DrawPanel extends JPanel {
        private final Simulation sim;

        public DrawPanel(Simulation sim) {
            this.sim = sim;
            setBackground(Color.BLACK);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); //smooth circle drawing

                //scale factor from simulation space to current panel size
                double scaleX = getWidth() / (double) Config.WIDTH;
                double scaleY = getHeight() / (double) Config.HEIGHT;

                //waiting until simulation finishes its current step before reading positions to draw
                synchronized (sim.lock) {
                    if (sim.particles != null) {
                        for (Particles p : sim.particles) {
                            g2.setColor(p.charge > 0 ? Color.RED : Color.BLUE); //positive charge red, negative blue
                            int r = 4;
                            int x = (int) (p.x * scaleX);
                            int y = (int) (p.y * scaleY);
                            g2.fillOval(x - r, y - r, 2 * r, 2 * r);
                        }
                    }
                    g2.setColor(Color.WHITE);
                    g2.drawString("Cycles: " + sim.cyclesCompleted + (sim.isDone() ? " (done)" : ""), 10, 20); //current cycle displayed
                }
            } finally {
                g2.dispose();
            }
        }
        }
    }

