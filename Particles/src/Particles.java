public class Particles {
        double x, y; //position on screen
        double vx, vy; //velocity
        double charge; //+1 or -1

        public Particles(double x, double y, double vx, double vy, double charge) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.charge = charge;
        }

        //update velocity
        public void applyForce(double dvx, double dvy) {
            vx += dvx;
            vy += dvy;
        }

        //update position, based on velocity scaled by time step
        public void move(double dt) {
            x += vx * dt;
            y += vy * dt;
        }
}
