import java.util.Random;
import mpi.*;

/*Distributed version of the simulation using MPJ Express
Rank 0 creates all the particles, then the work is split across processes.
Each process only owns its own particles' velocities, positions are shared
between everyone each cycle.
*/

public class Distributed {

    public static void main(String[] args) throws Exception {
        args = MPI.Init(args); //starting MPI

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        //read particle count and cycle count from the command line, else defaults
        int n      = (args.length >= 1) ? Integer.parseInt(args[0]) : Config.NUM_PARTICLES;
        int cycles = (args.length >= 2) ? Integer.parseInt(args[1]) : Config.CYCLES;

        //split the particles across processes and if it doesn't divide evenly, the first few processes get one extra
        int[] counts = new int[size];   // particles per rank
        int[] displs = new int[size];   //where each process's chunk starts
        int base = n / size, rem = n % size, off = 0;
        for (int i = 0; i < size; i++) {
            counts[i] = base + (i < rem ? 1 : 0);
            displs[i] = off;
            off += counts[i];
        }
        int localCount = counts[rank];
        int localStart = displs[rank];

        // doubled layout for the interleaved (x,y) position exchange
        int[] c2 = new int[size];
        int[] d2 = new int[size];
        for (int i = 0; i < size; i++) { c2[i] = counts[i] * 2; d2[i] = displs[i] * 2; }

        //arrays holding only this process's own particles
        double[] lx  = new double[localCount];
        double[] ly  = new double[localCount];
        double[] lvx = new double[localCount];
        double[] lvy = new double[localCount];
        double[] lc  = new double[localCount];
        double[] lfx = new double[localCount];
        double[] lfy = new double[localCount];
        double[] lp  = new double[localCount * 2];   //own positions packed as (x,y) to send

        //arrays holding info about ALL particles
        double[] gc = new double[n];         // charges (gathered once)
        double[] gp = new double[n * 2];     // interleaved (x,y), refreshed each cycle

        //only rank 0 builds the starting particles (same seed as seq/parallel)
        double[] allX = null, allY = null, allVx = null, allVy = null, allC = null;
        if (rank == 0) {
            allX  = new double[n]; allY  = new double[n];
            allVx = new double[n]; allVy = new double[n];
            allC  = new double[n];
            Random r = new Random(Config.SEED);
            for (int i = 0; i < n; i++) {
                double speed = 10 + 20 * r.nextDouble();
                double angle = 2 * Math.PI * r.nextDouble();
                allVx[i] = speed * Math.cos(angle);
                allVy[i] = speed * Math.sin(angle);
                allC[i]  = r.nextBoolean() ? 1.0 : -1.0;
                allX[i]  = r.nextDouble() * Config.WIDTH;
                allY[i]  = r.nextDouble() * Config.HEIGHT;
            }
        }

        //rank 0 hands each process its own chunk of each field
        MPI.COMM_WORLD.Scatterv(allX,  0, counts, displs, MPI.DOUBLE, lx,  0, localCount, MPI.DOUBLE, 0);
        MPI.COMM_WORLD.Scatterv(allY,  0, counts, displs, MPI.DOUBLE, ly,  0, localCount, MPI.DOUBLE, 0);
        MPI.COMM_WORLD.Scatterv(allVx, 0, counts, displs, MPI.DOUBLE, lvx, 0, localCount, MPI.DOUBLE, 0);
        MPI.COMM_WORLD.Scatterv(allVy, 0, counts, displs, MPI.DOUBLE, lvy, 0, localCount, MPI.DOUBLE, 0);
        MPI.COMM_WORLD.Scatterv(allC,  0, counts, displs, MPI.DOUBLE, lc,  0, localCount, MPI.DOUBLE, 0);

        //collect every charge onto every process, just once (charges never change)
        MPI.COMM_WORLD.Allgatherv(lc, 0, localCount, MPI.DOUBLE, gc, 0, counts, displs, MPI.DOUBLE);

        double soft2 = Config.SOFTENING * Config.SOFTENING;

        //build the first global view of positions before the loop starts
        sharePositions(localCount, lx, ly, lp, gp, c2, d2);

        //all processes start the timer together
        MPI.COMM_WORLD.Barrier();
        long t0 = System.nanoTime();
        for (int c = 0; c < cycles; c++) {
            cycle(localCount, n, localStart, soft2,
                    lx, ly, lvx, lvy, lc, lfx, lfy, gc, lp, gp, c2, d2);
        }
        MPI.COMM_WORLD.Barrier(); //stop timing only when everyone is done
        double t = (System.nanoTime() - t0) / 1e9;

        if (rank == 0) {
            System.out.println("Mode: distributed");
            System.out.println("Processes: " + size);
            System.out.println("Particles: " + n);
            System.out.println("Cycles: " + cycles);
            System.out.printf("Runtime: %.3f seconds%n", t);
        }

        MPI.Finalize();
    }

    /*One simulation step:
      work out the forces on this process's particles, add the wall push,
      move them, then share the new positions for the next cycle.*/
    private static void cycle(int localCount, int n, int localStart, double soft2,
                              double[] lx, double[] ly, double[] lvx, double[] lvy,
                              double[] lc, double[] lfx, double[] lfy, double[] gc,
                              double[] lp, double[] gp, int[] c2, int[] d2) throws Exception {

        //force on each of this process's particles from every other particle
        for (int i = 0; i < localCount; i++) {
            int gi = localStart + i; //this particle's index in the global list
            double xi = lx[i], yi = ly[i], ci = lc[i];
            double fx = 0.0, fy = 0.0;
            for (int j = 0; j < n; j++) {
                if (j == gi) continue;
                //how far apart this particle and particle j are
                double dx = gp[2 * j]     - xi;
                double dy = gp[2 * j + 1] - yi;
                //distance, with softening so we never divide by zero
                double r2 = dx * dx + dy * dy + soft2;
                double invD = 1.0 / Math.sqrt(r2);
                //attraction vs repulsion, scaled by the charges and distance
                double s = (ci * gc[j]) * invD * invD * invD;   // = cp / d^3
                fx += s * dx;
                fy += s * dy;
            }
            lfx[i] = fx;   //store total force
            lfy[i] = fy;
        }

        //wall repulsion
        for (int i = 0; i < localCount; i++) {
            double dL = lx[i], dR = Config.WIDTH  - lx[i];
            double dT = ly[i], dB = Config.HEIGHT - ly[i];
            double eps = 1e-6;
            double wf  = Config.WALL_K * Math.abs(lc[i]);
            lfx[i] += wf / (dL * dL + eps);
            lfx[i] -= wf / (dR * dR + eps);
            lfy[i] += wf / (dT * dT + eps);
            lfy[i] -= wf / (dB * dB + eps);
        }

        //update velocity and position for this process's particles
        for (int i = 0; i < localCount; i++) {
            lvx[i] += lfx[i] * Config.FORCE_SCALE * Config.DT;
            lvy[i] += lfy[i] * Config.FORCE_SCALE * Config.DT;

            double sp = Math.sqrt(lvx[i] * lvx[i] + lvy[i] * lvy[i]);
            if (sp > Config.MAX_SPEED) {
                double s = Config.MAX_SPEED / sp;
                lvx[i] *= s; lvy[i] *= s;
            }

            lx[i] += lvx[i] * Config.DT;
            ly[i] += lvy[i] * Config.DT;

            //if a particle hits a wall, keep it inside and bounce it back
            if (lx[i] < 0)             { lx[i] = 0;             lvx[i] =  Math.abs(lvx[i]); }
            if (lx[i] > Config.WIDTH)  { lx[i] = Config.WIDTH;  lvx[i] = -Math.abs(lvx[i]); }
            if (ly[i] < 0)             { ly[i] = 0;             lvy[i] =  Math.abs(lvy[i]); }
            if (ly[i] > Config.HEIGHT) { ly[i] = Config.HEIGHT; lvy[i] = -Math.abs(lvy[i]); }
        }

        //share the new positions so the next cycle can see them
        sharePositions(localCount, lx, ly, lp, gp, c2, d2);
    }

    /* Pack this rank's (x,y) into lp and Allgatherv into the global gp buffer. */
    private static void sharePositions(int localCount, double[] lx, double[] ly,
                                       double[] lp, double[] gp, int[] c2, int[] d2) throws Exception {
        for (int i = 0; i < localCount; i++) {
            lp[2 * i]     = lx[i];
            lp[2 * i + 1] = ly[i];
        }
        MPI.COMM_WORLD.Allgatherv(lp, 0, localCount * 2, MPI.DOUBLE, gp, 0, c2, d2, MPI.DOUBLE);
    }
}