import java.awt.event.*;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.*;
import javax.swing.*;
import javax.imageio.*;
import java.io.*;
import java.awt.geom.*;
import java.util.*;

class Point implements Comparable<Point> {
    public double x, y;
    double theta, dist;
    public Cluster cluster;

    private Point(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    public static Point fromPolar(double theta, double dist) {
        Point p = new Point(dist*Math.cos(theta), dist*Math.sin(theta));
        p.theta = theta;
        p.dist = dist;
        return p;
    }
    
    public static Point fromRect(double x, double y) {
        Point p = new Point(x, y);
        p.theta = Math.atan2(y, x);
        p.dist = Math.hypot(x, y);
        return p;
    }
    
    public int compareTo(Point p) {
        if (theta < p.theta) return -1;
        if (theta > p.theta) return +1;
        return 0;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
    
    public String toString() {
        return "(" + x + " " + y + ")";
    }
}

class Line {
    public double m, b;
    public double x0, y0, vx, vy;
    
    public Line(double m, double b) {
        this.m = m;
        this.b = b;
        x0 = 0;
        y0 = b;
        vx = 1;
        vy = m;
        double mag = Math.hypot(vx, vy);
        vx /= mag;
        vy /= mag;
    }
    
    public double getT(double x, double y) {
        return vx*(x-x0) + vy*(y-y0);
    }
    
    public Point getPoint(double t) {
        return Point.fromRect(x0 + vx*t, y0 + vy*t);
    }
}

class Cluster {
    public Color color;
    public List<Point> points = new LinkedList<>();
    public Line fitLine;
    public Point fitLineP1, fitLineP2;
    public double fitLineLength;
    
    public void addPoint(Point p) {
        points.add(p);
        p.cluster = this;
    }
    
    public void calcFitLine() {
        int n = points.size();
        
        double xMean = 0, yMean = 0;
        for (Point p : points) {
            xMean += p.x;
            yMean += p.y;
        }
        xMean /= n;
        yMean /= n;
        
        double sxx = 0, sxy = 0, syy = 0;
        for (Point p : points) {
            double dx = p.x - xMean;
            double dy = p.y - yMean;
            sxx += dx*dx;
            sxy += dx*dy;
            syy += dy*dy;
        }
        sxx /= n-1;
        sxy /= n-1;
        syy /= n-1;
        
        double dsxy = syy - sxx;
        double m = (dsxy + Math.sqrt(dsxy*dsxy + 4*sxy*sxy)) / (2*sxy);
        double b = yMean - m*xMean;
        fitLine = new Line(m, b);
        
        double minT = Double.MAX_VALUE, maxT = -Double.MAX_VALUE;
        for (Point p : points) {
            double t = fitLine.getT(p.x, p.y);
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
        }
        fitLineLength = maxT - minT;
        fitLineP1 = fitLine.getPoint(minT);
        fitLineP2 = fitLine.getPoint(maxT);
    }
}

public class Display extends JPanel {
    private final int width         = 1500;
    private final int height        = 1500;
    private       int widthMilli    = 10000;
    private       int heightMilli   = 10000;
    private final int radius        = 2;
    private final Font font         = new Font("Roboto", Font.BOLD, 48);
    
    private JLabel display;
    
    private Point[] points;
    private List<Cluster> clusters;
    
    public Display() {
        //setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        
        setSize(new Dimension(width, height)); 
        setPreferredSize(new Dimension(width, height));
        setLayout(null);
        
        // In case we need a display
        display = new JLabel();
        display.setBounds(0, 0, width, height / 8);
        display.setFont(font);
        display.setHorizontalTextPosition(JLabel.LEFT);
        add(display);
        
        setVisible(true); 
        
        // Creating Points
        points = generateArray();
        cluster();
    }
    
    public float curHue = 0;
    public Color nextColor() {
        curHue += 0.4;
        return Color.getHSBColor(curHue, 1, 0.5f);
    }
    
    public static final double EPS = 180;
    public static final int MIN_POINTS = 10;
    public void cluster() {
        System.out.println("Clustering...");
        long startTime = System.nanoTime();
        
        clusters = new ArrayList<>();
        LinkedList<Point> freePoints = new LinkedList<>(Arrays.asList(points));
        while (!freePoints.isEmpty()) {
            // find a cluster
            LinkedList<Point> openPoints = new LinkedList<>();
            openPoints.add(freePoints.pop());
            Cluster cluster = new Cluster();
            while (!openPoints.isEmpty()) {
                // find all the neighbors of this point
                Point cur = openPoints.pop();
                cluster.addPoint(cur);
                Iterator<Point> it = freePoints.iterator();
                while (it.hasNext()) {
                    Point p = it.next();
                    double dx = p.x-cur.x, dy = p.y-cur.y;
                    if (dx*dx + dy*dy <= EPS*EPS) {
                        // the point is close enough
                        openPoints.add(p);
                        it.remove();
                    }
                }
            }
            cluster.color = cluster.points.size() >= MIN_POINTS? nextColor() : Color.WHITE;
            cluster.calcFitLine();
            clusters.add(cluster);
        }
        
        long endTime = System.nanoTime();
        System.out.println("Done ("+((endTime-startTime)/1000000)+" ms)");
    }
    
    private double scale;
    private int centerX, centerY;
    public int[] getDrawLoc(Point p) {
        double actualX = p.getX() * scale + centerX;
        double actualY = p.getY() * scale + centerY;
        return new int[] {(int)actualX,
                          (int)actualY};
    }
    
    public void paint(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // Draw Display
        g.setFont(font);
        //g.drawString("insert text here", 20, 50);
        
        // Draw Points
        scale = Math.min((double)getWidth()/widthMilli, (double)getHeight()/heightMilli);
        centerX = getWidth()/2;
        centerY = getHeight()/2;
        int[] xPts = new int[points.length];
        int[] yPts = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            int[] pos = getDrawLoc(points[i]);
            xPts[i] = pos[0];
            yPts[i] = pos[1];
        }
        g.setColor(Color.GRAY);
        g.drawPolygon(xPts, yPts, points.length);
        for (int i = 0; i < points.length; i++) {
            Cluster c = points[i].cluster;
            g.setColor(c.color);
            g.fillRect(xPts[i] - radius, yPts[i] - radius, radius * 2, radius * 2);
        }
        
        for (Cluster c : clusters) {
            int[] p1 = getDrawLoc(c.fitLineP1);
            int[] p2 = getDrawLoc(c.fitLineP2);
            g.setColor(Color.WHITE);
            g.drawLine(p1[0], p1[1], p2[0], p2[1]);
        }
        
        g.setColor(Color.WHITE);
        g.drawOval(centerX-4, centerY-4, 8, 8);
        g.drawLine(20, 20, 20+(int)(EPS*scale), 20);
    }
    
    public Point[] generateArray() {
        System.out.println("Loading data...");
        LinkedList<Point> points = new LinkedList<Point>();
        
        double maxX = 0, maxY = 0;
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File("points_proc.txt")));
            
            String str;
            while ((str = br.readLine()) != null) {
                String[] polar = str.split(" ");
                double r = Double.parseDouble(polar[1]), theta = Math.toRadians(Double.parseDouble(polar[0]));
                if (r == 0 || r < 1900) continue;
                Point p = Point.fromPolar(theta, r);
                points.add(p);
                
                double x = Math.abs(p.x);
                if (x > maxX) maxX = x;
                double y = Math.abs(p.y);
                if (y > maxY) maxY = y;
                
                // if (points.size() > 4000) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        widthMilli = (int)(maxX*2.1);
        heightMilli = (int)(maxY*2.1);
        
        Collections.sort(points);
        System.out.println("Read "+points.size()+" points");
        return points.toArray(new Point[points.size()]);
    }
    
    public static void main(String[] args) {
        Display disp = new Display();
        
        JFrame frame = new JFrame();
        frame.setTitle("Test Visualizer");
        frame.add(disp);
        frame.pack();
        frame.setResizable(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
