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

    //method for user input of mode
    private static String askMode(Scanner sc) {
        System.out.print("Mode (sequential/parallel) [sequential]: ");
        String s = sc.nextLine().trim().toLowerCase();
        if (s.isEmpty()) return "sequential";
        if (s.equals("s") || s.equals("seq") || s.equals("sequential")) return "sequential";
        if (s.equals("p") || s.equals("par") || s.equals("parallel")) return "parallel";
        System.out.println("Invalid mode, using default: sequential");
        return "sequential";
    }

    public static void main(String[] args) {
        System.out.println("Particles Simulation");
        System.out.println("Available CPUs: " + Runtime.getRuntime().availableProcessors());
        System.out.println();

        Scanner sc = new Scanner(System.in);

        String mode = askMode(sc);
        int numParticles = askInt(sc, "Number of particles", Config.NUM_PARTICLES);
        int cycles = askInt(sc, "Cycles", Config.CYCLES);
        boolean graphics = askBoolean(sc, "Graphics (true/false)", Config.GRAPHICS);

        //apply chosen settings
        Config.MODE = mode;
        Config.NUM_PARTICLES = numParticles;
        Config.CYCLES = cycles;
        Config.GRAPHICS = graphics;

        System.out.println();
        System.out.println("Starting simulation with:");
        System.out.println("  Mode      = " + Config.MODE);
        System.out.println("  Particles = " + Config.NUM_PARTICLES);
        System.out.println("  Cycles    = " + Config.CYCLES);
        if (mode.equals("parallel")) {
            System.out.println("  Threads   = " + Config.NUM_THREADS);
        }
        System.out.println("  Graphics  = " + Config.GRAPHICS + " @ " + Config.FPS + " FPS");
        System.out.println();

        Simulation sim;
        switch (mode) {
            case "parallel":
                sim = new ParallelSimulation();
                break;
            case "sequential":
            default:
                sim = new Simulation();
                break;
        }

        if (Config.GRAPHICS) {
            //starting graphics
            Renderer renderer = new Renderer(sim);
            renderer.run();

            //running the computation
            Thread computeThread = new Thread(sim);
            computeThread.start();

        } else {
            //run without graphics
            sim.run();
        }
    }
}
