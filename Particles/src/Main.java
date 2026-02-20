import java.util.Scanner;

public class Main {

    //method for user input of integer
    private static int askInt(Scanner sc, String label, int def) {
        System.out.print(label + " [" + def + "]: ");
        String s = sc.nextLine().trim();
        if (s.isEmpty()) return def;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            System.out.println("Invalid integer, using default: " + def);
            return def;
        }
    }

    //method for user input of boolean
    private static boolean askBoolean(Scanner sc, String label, boolean def) {
        System.out.print(label + " [" + def + "]: ");
        String s = sc.nextLine().trim();
        if (s.isEmpty()) return def;
        String v = s.toLowerCase();
        if (v.equals("true") || v.equals("t") || v.equals("yes") || v.equals("y") || v.equals("1")) return true;
        if (v.equals("false") || v.equals("f") || v.equals("no") || v.equals("n") || v.equals("0")) return false;
        System.out.println("Invalid boolean, using default: " + def);
        return def;
    }

    public static void main(String[] args) {
        System.out.println("Particles Simulation (Sequential)");
        Scanner sc = new Scanner(System.in);

        int numParticles = askInt(sc, "Number of particles", Config.NUM_PARTICLES);
        int cycles = askInt(sc, "Cycles", Config.CYCLES);
        boolean graphics = askBoolean(sc, "Graphics (true/false)", Config.GRAPHICS);

        // Apply chosen settings
        Config.NUM_PARTICLES = numParticles;
        Config.CYCLES = cycles;
        Config.GRAPHICS = graphics;

        System.out.println();
        System.out.println("Starting simulation with:");
        System.out.println("  Particles = " + Config.NUM_PARTICLES);
        System.out.println("  Cycles    = " + Config.CYCLES);
        System.out.println("  Graphics  = " + Config.GRAPHICS + " @ " + Config.FPS + " FPS");
        System.out.println();

        Simulation sim = new Simulation();

        if (Config.GRAPHICS) {
            //starting graphics
            Renderer renderer = new Renderer(sim);
            renderer.run();

            //running the computation
            Thread computeThread = new Thread(sim);
            computeThread.start();

        } else {
            //sequential run without graphics
            sim.run();
        }
    }
}
