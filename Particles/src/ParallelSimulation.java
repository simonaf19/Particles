import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//Parallel version of the simulation which extends Simulation and overrides the computeForces() method
public class ParallelSimulation extends Simulation {
    private final ExecutorService executor;
    private final int numThreads;

    private final double[][] threadLocalFx;
    private final double[][] threadLocalFy;
    private final List<ForceTask> tasks;

    public ParallelSimulation() {
        super(); //Simulation constructor
        this.numThreads = Config.NUM_THREADS; //based on available CPU cores
        this.executor = Executors.newFixedThreadPool(numThreads); //using a fixed thread pool
        int n = particles.length;
        threadLocalFx = new double[numThreads][n];
        threadLocalFy = new double[numThreads][n];
        //create the task objects once, reuse them every cycle
        tasks = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            tasks.add(new ForceTask(particles, n, t, numThreads, threadLocalFx[t], threadLocalFy[t]));
        }
    }
    @Override
    protected void computeForces() {
        //each thread gets a chunk of particles (rows of the outer loop)
        //each thread uses its own local arrays to avoid race conditions
        //after all threads finish, local arrays are merged into the global fx/fy

        int n = particles.length;

        for (int t = 0; t < numThreads; t++) {
            Arrays.fill(threadLocalFx[t], 0.0);
            Arrays.fill(threadLocalFy[t], 0.0);
        }

        //submit all tasks to the thread pool
        try {
            //invokeAll starts all tasks and waits for all to finish
            List<Future<Void>> futures = executor.invokeAll(tasks);

            //merge results from each thread into the global force arrays
            for (int t = 0; t < numThreads; t++) {
                for (int i = 0; i < n; i++) {
                    fx[i] += threadLocalFx[t][i];
                    fy[i] += threadLocalFy[t][i];
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        /*double tfx = 0, tfy = 0;
        for (int i = 0; i < particles.length; i++) {
            tfx += fx[i];
            tfy += fy[i];
        }
        System.out.printf("After computeForces: fx=%.10f fy=%.10f%n", tfx, tfy);*/
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
        private static class ForceTask implements Callable<Void> {
            private final Particles[] particles;
            private final int n;
            private final int threadId;
            private final int numThreads;
            private final double[] localFx;
            private final double[] localFy;


            ForceTask(Particles[] particles, int n, int threadId, int numThreads, double[] localFx, double[] localFy) {
                this.particles = particles;
                this.n = n;
                this.threadId = threadId;
                this.numThreads = numThreads;
                this.localFx = localFx;
                this.localFy = localFy;
            }

            @Override
            public Void call() {

                //compute forces only for this thread's assigned rows
                for (int i = threadId; i < n; i+= numThreads) {
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
                return null;
            }
        }
    }
