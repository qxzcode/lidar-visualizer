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

class Point {
    public double x, y;
    public Cluster cluster;

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }
    
    public double getX() { return x; }
    public double getY() { return y; }
    
    public String toString() {
        return "(" + x + " " + y + ")";
    }
}

class Cluster {
    public Color color;
    public List<Point> points = new LinkedList<>();
    
    public void addPoint(Point p) {
        points.add(p);
        p.cluster = this;
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
        return Color.getHSBColor(curHue, 1, 1);
    }
    
    public static final double EPS = 180;
    public static final int MIN_POINTS = 5;
    public void cluster() {
        System.out.println("Clustering...");
        long startTime = System.nanoTime();
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
        }
        long endTime = System.nanoTime();
        System.out.println("Done ("+((endTime-startTime)/1000000)+" ms)");
    }
    
    public void paint(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // Draw Display
        g.setFont(font);
        //g.drawString("insert text here", 20, 50);
        
        // Draw Points
        double scale = Math.min((double)getWidth()/widthMilli, (double)getHeight()/heightMilli);
        int centerX = getWidth()/2, centerY = getHeight()/2;
        int[] xPts = new int[points.length];
        int[] yPts = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            double actualX = points[i].getX() * scale + centerX;
            double actualY = points[i].getY() * scale + centerY;
            xPts[i] = (int) actualX;
            yPts[i] = (int) actualY;
        }
        g.setColor(Color.GRAY);
        // g.drawPolygon(xPts, yPts, points.length);
        for (int i = 0; i < points.length; i++) {
            g.setColor(points[i].cluster.color);
            g.fillRect(xPts[i] - radius, yPts[i] - radius, radius * 2, radius * 2);
        }
        
        g.setColor(Color.WHITE);
        g.drawOval(centerX-4, centerY-4, 8, 8);
        g.drawLine(20, 20, 20+(int)(EPS*scale), 20);
    }
    
    public Point[] generateArray() {
        LinkedList<Point> points = new LinkedList<Point>();
        
        double maxX = 0, maxY = 0;
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File("points.txt")));
            
            String str;
            while ((str = br.readLine()) != null) {
                str = str.replace("   theta: ", "");
                str = str.replace("Dist: ", "");
                String[] polar = str.split(" ");
                double r = Double.parseDouble(polar[1]), theta = Math.toRadians(Double.parseDouble(polar[0]));
                if (r == 0) continue;
                double x = r*Math.cos(theta), y = r*Math.sin(theta);
                points.add(new Point(x, y));
                
                x = Math.abs(x);
                if (x > maxX) maxX = x;
                y = Math.abs(y);
                if (y > maxY) maxY = y;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        widthMilli = (int)(maxX*2.1);
        heightMilli = (int)(maxY*2.1);
        
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
