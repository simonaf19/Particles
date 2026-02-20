public class Config {
    public static int WIDTH = 800;
    public static int HEIGHT = 600;
    public static int NUM_PARTICLES = 500;
    public static int CYCLES = 10000;
    public static boolean GRAPHICS = true;
    public static int FPS = 60;
    public static String MODE = "sequential";
    public static int NUM_THREADS = Runtime.getRuntime().availableProcessors();

    //physics stabilization
    public static double DT = 0.01; //time step (seconds)
    public static double FORCE_SCALE = 400.0; //scales acceleration from computed force
    public static double SOFTENING = 10.0; //softening radius
    public static double MAX_SPEED = 150.0; //clamp speed (pixels/second)
    public static double WALL_K = 100.0; //wall repulsion strength
    public static long SEED = 42L; //deterministic default seed
}
