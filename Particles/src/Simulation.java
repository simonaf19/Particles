import java.util.Random;

public class Simulation implements Runnable {

    public final Object lock = new Object(); //needed for thread synchronization

    Particles[] particles;
    public int cyclesCompleted = 0;
    private final long seed;

    //total force of particle per current cycle
    protected final double[] fx;
    protected final double[] fy;

    private boolean done = false;

    //constructor, creating particles
    public Simulation() {
        this.seed = Config.SEED; //same random seed
        particles = new Particles[Config.NUM_PARTICLES];
        fx = new double[Config.NUM_PARTICLES];
        fy = new double[Config.NUM_PARTICLES];

       Random r = new Random(seed);

        for (int i = 0; i < particles.length; i++) {
            double speed0 = 10 + 20 * r.nextDouble(); //random speed between 10-30
            double angle = 2 * Math.PI * r.nextDouble();
            double vx0 = speed0 * Math.cos(angle); //getting velocity based on angle and speed
            double vy0 = speed0 * Math.sin(angle);

            double charge = r.nextBoolean() ? 1.0 : -1.0; //random charge like coin flip

            particles[i] = new Particles(
                    r.nextDouble() * Config.WIDTH,
                    r.nextDouble() * Config.HEIGHT,
                    vx0,
                    vy0,
                    charge
            );
        }
        /*particles[0] = new Particles(350, 300, 0, 0, +1.0);
        particles[1] = new Particles(450, 300, 0, 0, -1.0);*/
    }

    public boolean isDone() {
        return done;
    }

    public void run() {
        long start = System.nanoTime();

        for (int c = 0; c < Config.CYCLES; c++) {
            step(); //do step until cycles completed
            cyclesCompleted++;

            //~1 step per frame when enabled graphics
            if (Config.GRAPHICS) {
                try {
                    Thread.sleep(1000 / Config.FPS);  //~16ms 60 FPS
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
      /* for (int c = 0; c < Config.CYCLES; c++) {
            step();
            cyclesCompleted++;

           System.out.printf(
                   "cycle=%d x0=%.8f vx0=%.8f   x1=%.8f vx1=%.8f   dist=%.8f%n",
                   cyclesCompleted,
                   particles[0].x, particles[0].vx,
                   particles[1].x, particles[1].vx,
                   Math.abs(particles[1].x - particles[0].x)
           );
        }*/

            double time = (System.nanoTime() - start) / 1e9; //time in seconds

            done = true; //finished, useful for renderer

            System.out.println("Mode: sequential");
            System.out.println("Particles: " + particles.length);
            System.out.println("Cycles: " + cyclesCompleted);
            System.out.printf("Runtime: %.3f seconds%n", time);
        }
                protected void computeForces() {
                    //particle-particle forces: F = |ci*cj| / d²
                    for (int i = 0; i < particles.length; i++) {
                        for (int j = i + 1; j < particles.length; j++) {

                            //how far apart particles are
                            double dx = particles[j].x - particles[i].x;
                            double dy = particles[j].y - particles[i].y;

                            //actual distance between them (Pythagorean theorem)
                            //SOFTENING added to prevent division by zero when particles overlap
                            double d = Math.sqrt(dx * dx + dy * dy + Config.SOFTENING * Config.SOFTENING);

                            //attraction vs repulsion
                            //same signs: (+1)*(+1)=+1 or (-1)*(-1)=+1 -> positive -> attract
                            //opposite: (+1)*(-1)=-1 -> negative -> repel
                            double chargeProduct = particles[i].charge * particles[j].charge;
                            double mag = Math.abs(chargeProduct) / (d * d);
                            double direction = (chargeProduct > 0.0) ? 1.0 : -1.0;

                            double forceX = direction * mag * (dx / d);
                            double forceY = direction * mag * (dy / d);

                            //Newton's third law: equal and opposite forces
                            fx[i] += forceX;
                            fy[i] += forceY;
                            fx[j] -= forceX;
                            fy[j] -= forceY;
                        }
                    }
                }

    public void step () {
        synchronized (lock) {
            //reseting forces each cycle
            for (int i = 0; i < particles.length; i++) {
                fx[i] = 0.0;
                fy[i] = 0.0;
            }

            computeForces();

            //boundary repeling particles
            for (int i = 0; i < particles.length; i++) {
                Particles p = particles[i];

                //how far particle is from each wall
                double dLeft = p.x;
                double dRight = Config.WIDTH - p.x;
                double dTop = p.y;
                double dBottom = Config.HEIGHT - p.y;

                //preventing division by 0
                double eps = 1e-6;

                //wall repulsion strength, scaled by particle's charge
                double wallForce = Config.WALL_K * Math.abs(p.charge);

                //each wall pushes the particle away using force = strength / distance^2
                fx[i] += wallForce / (dLeft * dLeft + eps); //pushes to the right
                fx[i] -= wallForce / (dRight * dRight + eps); //pushes to the left
                fy[i] += wallForce / (dTop * dTop + eps); //pushes to the bottom
                fy[i] -= wallForce / (dBottom * dBottom + eps); //pushes to the top
            }

            //applying forces and moving
            for (int i = 0; i < particles.length; i++) {
                //converting force into a velocity change: dv = force * scale * time step
                double dvx = fx[i] * Config.FORCE_SCALE * Config.DT;
                double dvy = fy[i] * Config.FORCE_SCALE * Config.DT;
                particles[i].applyForce(dvx, dvy);

                //clamping speed so particles don't fly too fast
                double speed = Math.sqrt(particles[i].vx * particles[i].vx + particles[i].vy * particles[i].vy);
                if (speed > Config.MAX_SPEED) {
                    double scale = Config.MAX_SPEED / speed;
                    particles[i].vx *= scale;
                    particles[i].vy *= scale;
                }
                particles[i].move(Config.DT);

                //boundary enforcement in case a particle escapes due to high velocity or large time step
                //putting it back to the wall and making it bounce
                if (particles[i].x < 0) {
                    particles[i].x = 0;
                    particles[i].vx = Math.abs(particles[i].vx);
                }
                if (particles[i].x > Config.WIDTH) {
                    particles[i].x = Config.WIDTH;
                    particles[i].vx = -Math.abs(particles[i].vx);
                }
                if (particles[i].y < 0) {
                    particles[i].y = 0;
                    particles[i].vy = Math.abs(particles[i].vy);
                }
                if (particles[i].y > Config.HEIGHT) {
                    particles[i].y = Config.HEIGHT;
                    particles[i].vy = -Math.abs(particles[i].vy);
                }
            }
        }
    }
}


