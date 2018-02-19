public class Point implements Comparable<Point> {
    public final double x, y;
    public final double theta, dist;
    public int revNum;
    
    public boolean good = true;
    
    private Point(double x, double y, double theta, double dist) {
        this.x = x;
        this.y = y;
        this.theta = theta;
        this.dist = dist;
    }
    
    public static Point fromPolar(double theta, double dist) {
        return new Point(dist*Math.cos(theta), dist*Math.sin(theta), theta, dist);
    }
    
    public static Point fromRect(double x, double y) {
        return new Point(x, y, 0, 0);//, Math.atan2(y, x), Math.hypot(x, y));
    }
    
    public double getDistanceSq(Point p) {
        double dx = x-p.x, dy = y-p.y;
        return dx*dx + dy*dy;
    }
    
    public double getDistance(Point p) {
        return Math.sqrt(getDistanceSq(p));
    }
    
    public int compareTo(Point p) {
        if (theta < p.theta) return -1;
        if (theta > p.theta) return +1;
        return 0;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
    
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}