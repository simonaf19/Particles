import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//Parallel version of the simulation which extends Simulation and overrides the computeForces() method
public class ParallelSimulation extends Simulation {
    private final ExecutorService executor;
    private final int numThreads;

    public ParallelSimulation() {
        super(); //Simulation constructor
        this.numThreads = Config.NUM_THREADS; //based on available CPU cores
        this.executor = Executors.newFixedThreadPool(numThreads); //using a fixed thread pool
    }
    @Override
    protected void computeForces() {
        //each thread gets a chunk of particles (rows of the outer loop)
        //each thread uses its own local arrays to avoid race conditions
        //after all threads finish, local arrays are merged into the global fx/fy

        int n = particles.length;

        List<Future<double[][]>> futures = new ArrayList<>();

        //separating by chunks
        int chunkSize = (n + numThreads - 1) / numThreads;

        for (int t = 0; t < numThreads; t++) {
            final int startRow = t * chunkSize;
            final int endRow = Math.min(startRow + chunkSize, n);

            if (startRow >= n) break; //no more work

            //submit task to thread pool
            futures.add(executor.submit(new ForceTask(particles, n, startRow, endRow)));
        }

        //collecting results from all threads and merging into global force arrays
        for (Future<double[][]> future : futures) {
            try {
                double[][] localForces = future.get();
                for (int i = 0; i < n; i++) {
                    fx[i] += localForces[0][i];
                    fy[i] += localForces[1][i];
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    @Override
    public void run() {
        long start = System.nanoTime();

        for (int c = 0; c < Config.CYCLES; c++) {
            step();
            cyclesCompleted++;

            if (Config.GRAPHICS) {
                try {
                    Thread.sleep(1000 / Config.FPS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        double time = (System.nanoTime() - start) / 1e9;
        executor.shutdown(); //clean up thread pool

        System.out.println("Mode: parallel");
        System.out.println("Threads: " + numThreads);
        System.out.println("Particles: " + particles.length);
        System.out.println("Cycles: " + cyclesCompleted);
        System.out.printf("Runtime: %.3f seconds%n", time);
    }
 /*ForceTask computes particle-particle forces for rows [startRow, endRow)
   It keeps Newton's third law optimization (j = i+1) for efficiency.
   Returns localFx and localFy arrays to be merged by the main thread.
 */
        private static class ForceTask implements Callable<double[][]> {
            private final Particles[] particles;
            private final int n;
            private final int startRow;
            private final int endRow;

            ForceTask(Particles[] particles, int n, int startRow, int endRow) {
                this.particles = particles;
                this.n = n;
                this.startRow = startRow;
                this.endRow = endRow;
            }

            @Override
            public double[][] call() {
                //thread-local force arrays
                double[] localFx = new double[n];
                double[] localFy = new double[n];

                //compute forces only for this thread's assigned rows
                for (int i = startRow; i < endRow; i++) {
                    for (int j = i + 1; j < n; j++) {

                        //how far apart particles are
                        double dx = particles[j].x - particles[i].x;
                        double dy = particles[j].y - particles[i].y;

                        //actual distance between them
                        double d = Math.sqrt(dx * dx + dy * dy + Config.SOFTENING * Config.SOFTENING);

                        //attraction vs repulsion
                        double chargeProduct = particles[i].charge * particles[j].charge;
                        double mag = Math.abs(chargeProduct) / (d * d);
                        double direction = (chargeProduct > 0.0) ? 1.0 : -1.0;

                        double forceX = direction * mag * (dx / d);
                        double forceY = direction * mag * (dy / d);

                        //Newton's third law: equal and opposite forces
                        localFx[i] += forceX;
                        localFy[i] += forceY;
                        localFx[j] -= forceX;
                        localFy[j] -= forceY;
                    }
                }
                return new double[][]{localFx, localFy};
            }
        }
    }
