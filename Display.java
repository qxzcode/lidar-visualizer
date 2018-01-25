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
    
    public double mCos, mSin;
    
    public boolean culled = false;
    public int revNum;
    
    public double err;

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
    public double vx, vy, r; // vy*x - vx*y = r
    public double x0, y0;
    
    public Line(double vx, double vy, double r) {
        this.vx = vx;
        this.vy = vy;
        this.r = r;
        
        x0 = vy*r;
        y0 = -vx*r;
    }
    
    public static Line getFitLine(Collection<Point> points) {
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
        double vy = dsxy + Math.sqrt(dsxy*dsxy + 4*sxy*sxy);
        double vx = 2*sxy;
        double mag = Math.hypot(vx, vy);
        vx /= mag;
        vy /= mag;
        double r = vy*xMean - vx*yMean;
        return new Line(vx, vy, r);
    }
    
    public double getError(Point p) {
        return Math.abs(vy*p.x - vx*p.y - r);
    }
    
    public double getAvgError(Collection<Point> points) {
        // sum up the distances to the line
        double sumErr = 0;
        for (Point p : points) {
            sumErr += getError(p);
        }
        return sumErr / points.size();
    }
    
    public void removeWorst(List<Point> list, double percentile) {
        removeWorst(list, (int)(list.size()*percentile));
    }
    
    public void removeWorst(List<Point> list, int numToRm) {
        for (Point p : list) p.err = getError(p);
        Collections.sort(list, (a, b) -> {
            if (b.err < a.err) return -1;
            if (b.err > a.err) return +1;
            return 0;
        });
        list.subList(0, numToRm).clear();
    }
    
    public Point drawP1, drawP2;
    public void calcDrawPoints(Collection<Point> points) {
        double minT = Double.MAX_VALUE, maxT = -Double.MAX_VALUE;
        for (Point p : points) {
            double t = getT(p.x, p.y);
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
        }
        drawP1 = getPoint(minT);
        drawP2 = getPoint(maxT);
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
    public boolean valid;
    
    public void addPoint(Point p) {
        points.add(p);
        p.cluster = this;
    }
    
    public void calcFitLine() {
        fitLine = Display.getCoolLine(points, points.size()/2);
        
        double minT = Double.MAX_VALUE, maxT = -Double.MAX_VALUE;
        for (Point p : points) {
            double t = fitLine.getT(p.x, p.y);
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
        }
        fitLineLength = maxT - minT; //minT=-999999; maxT=999999;
        fitLineP1 = fitLine.getPoint(minT);
        fitLineP2 = fitLine.getPoint(maxT);
    }
    
    double score = -1;
    public double getScore() {
        if (score < 0)
            score = fitLine.getAvgError(points) / fitLineLength;
        return score;
        // return points.size() / Math.pow(fitLineLength, 0.3);
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
    
    public static final double FPS = 10;
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
        calcBounds();
        cluster();
        scanLineFind();
        // calcLinearity();
        new javax.swing.Timer((int)(1000/FPS), evt -> {
            drawRev++;
            if (drawRev >= numRevs) drawRev = 0;
            repaint();
        }).start();
        
        MouseAdapter mouseListener = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                onMouse(e.getX(), e.getY());
            }
        };
        addMouseListener(mouseListener);
        addMouseMotionListener(mouseListener);
    }
    
    int lastI = -1;
    public void onMouse(int mx, int my) {
        Point m = getDataLoc(mx, my);
        
        double minDist = Double.MAX_VALUE;
        int minI = -1;
        for (int i = 0; i < points.length; i++) {
            Point p = points[i];
            double dx = p.x-m.x, dy = p.y-m.y;
            double dist = dx*dx + dy*dy;
            if (dist < minDist) {
                minDist = dist;
                minI = i;
            }
        }
        
        if (lastI != minI) {
            lastI = minI;
            debug("index: "+minI);
        }
    }
    
    public void debug(Object msg) {
        System.out.println(msg);
    }
    
    public static final float POINT_ALPHA = 0.6f;
    public float curHue = 0;
    public Color nextColor() {
        curHue += 0.4;
        return setAlpha(Color.getHSBColor(curHue, 1, 1.0f), POINT_ALPHA);
    }
    
    public static Color setAlpha(Color col, float alpha) {
        float[] rgb = col.getRGBColorComponents(null);
        return new Color(rgb[0],rgb[1],rgb[2], alpha);
    }
    
    public static final double EPS = 400;
    public static final int MIN_POINTS = 5;
    public static final boolean REV_CLUSTERS = false;
    public double maxScore = 0;
    public void cluster() {
        debug("Clustering...");
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
                    if (REV_CLUSTERS && p.revNum != cur.revNum) continue;
                    double dx = p.x-cur.x, dy = p.y-cur.y;
                    if (dx*dx + dy*dy <= EPS*EPS) {
                        // the point is close enough
                        openPoints.add(p);
                        it.remove();
                    }
                }
            }
            cluster.valid = cluster.points.size() >= MIN_POINTS;
            cluster.color = cluster.valid? nextColor() : new Color(1f,1f,1f,POINT_ALPHA);
            if (cluster.valid) cluster.calcFitLine();
            clusters.add(cluster);
            
            if (cluster.valid && cluster.getScore() > maxScore)
                maxScore = cluster.getScore();
        }
        
        // for (Cluster c : clusters) {
        //     if (!c.valid) continue;
        //     float alpha = 1 - (float)Math.pow(c.getScore()/maxScore, 0.5);
        //     c.color = new Color(alpha,alpha,alpha);
        // }
        
        long endTime = System.nanoTime();
        debug("Done ("+((endTime-startTime)/1000000)+" ms)");
    }
    
    public static final double EPS_2 = 500;
    public static final int MIN_POINTS_2 = 4;
    public void calcLinearity() {
        System.out.println("Calculating linearity stuff...");
        
        // calculate distance matrix
        int n = points.length;
        double[][] dists = new double[n][n];
        for (int i1 = 0; i1 < n; i1++) {
            Point p1 = points[i1];
            for (int i2 = i1+1; i2 < n; i2++) {
                Point p2 = points[i2];
                double dx = p1.x-p2.x, dy=p1.y-p2.y;
                dists[i1][i2] = dists[i2][i1] = dx*dx + dy*dy;
            }
        }
        
        for (int i = 0; i < n; i++) {
            final int fi = i;
            Point p = points[i];
            List<Integer> list = new ArrayList<>();
            List<Integer> list2 = new ArrayList<>();
            for (int i2 = 0; i2 < n; i2++) {
                if (i2 == i) continue;
                if (dists[i][i2] < EPS_2*EPS_2) list.add(i2);
                list2.add(i2);
            }
            if (list.size() < MIN_POINTS_2) {
                Collections.sort(list2, (Integer ia, Integer ib) -> {
                    return (int)Math.signum(dists[fi][ia] - dists[fi][ib]);
                });
                list = list2.subList(0, MIN_POINTS_2);
            }
            List<Point> pointList = new ArrayList<>();
            for (int i2 : list) {
                pointList.add(points[i2]);
            }
            
            Line line = Line.getFitLine(pointList);
            double angle = Math.atan2(line.vy, line.vx);
            p.mCos = Math.cos(angle);
            p.mSin = Math.sin(angle);
        }
        
        System.out.println("Done");
    }
    
    public static Line getCoolLine(List<Point> pointSet, int numToRm) {
        Line resLine = null;
        List<Point> curList = pointSet;
        for (int n = 0; n < numToRm; n++) {
            // find the point that, when removed, gives the best line
            double bestErr = Double.MAX_VALUE;
            Line bestLine = null;
            List<Point> bestList = null;
            for (int i = 0; i < curList.size(); i++) {
                ArrayList<Point> list = new ArrayList<>(curList);
                list.remove(i);
                Line line = Line.getFitLine(list);
                double err = line.getAvgError(list);
                if (err < bestErr) {
                    bestErr = err;
                    bestLine = line;
                    bestList = list;
                }
            }
            resLine = bestLine;
            curList = bestList;
        }
        return resLine;
        
        // Line line = Line.getFitLine(list);
        // line.removeWorst(list, 0.20);
        // line = Line.getFitLine(list);
    }
    
    public ArrayList<Line> lines = new ArrayList<>();
    public void scanLineFind() {
        try (PrintWriter writer = new PrintWriter("output.csv")) {
            final int GAP = 1;
            int n = points.length;
            for (int i = 0; i < n; i++) {
                /*ArrayList<Point> list = new ArrayList<>();
                for (int i2 = i; i2 <= i+GAP; i2++) {
                    list.add(points[i2 % points.length]);
                }
                Line line = getCoolLine(list, 1);
                line.calcDrawPoints(list);
                lines.add(line);//*/
                // double angle = Math.atan2(line.vy, line.vx);
                // if (angle < 0) angle += 2*Math.PI;
                // if (angle < 3 && i > 80) angle += 2*Math.PI;
                
                Point p1 = points[i];
                Point p2 = points[(i+GAP) % n];
                Point p3 = points[(i+GAP*2) % n];
                double dx1 = p1.x-p2.x, dy1 = p1.y-p2.y;
                double m1 = Math.hypot(dx1, dy1);
                dx1 /= m1;
                dy1 /= m1;
                double dx2 = p2.x-p3.x, dy2 = p2.y-p3.y;
                double m2 = Math.hypot(dx2, dy2);
                dx2 /= m2;
                dy2 /= m2;
                
                writer.println(i+", "+Math.min(m1, EPS*2));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private double scale;
    private int centerX, centerY;
    public int[] getDrawLoc(Point p) {
        double actualX = p.getX() * scale + centerX;
        double actualY = p.getY() * scale + centerY;
        return new int[] {(int)actualX,
                          (int)actualY};
    }
    
    public Point getDataLoc(int x, int y) {
        return Point.fromRect((x-centerX) / scale,
                              (y-centerY) / scale);
    }
    
    public static final boolean DRAW_FRAMES = false;
    public int drawRev = 1;
    public int numRevs;
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
        Color[] colors = new Color[points.length];
        int numPts = 0;
        for (int i = 0; i < points.length; i++) {
            Point p = points[i];
            if (DRAW_FRAMES && p.revNum != drawRev) continue;
            int[] pos = getDrawLoc(p);
            xPts[numPts] = pos[0];
            yPts[numPts] = pos[1];
            // float meme = (float)i/points.length;
            // colors[numPts] = setAlpha(Color.getHSBColor(meme, 1, 1), POINT_ALPHA);
            colors[numPts] = p.culled? Color.GRAY : p.cluster.color;
            numPts++;
        }
        g.setColor(new Color(255, 255, 255, 50));
        g.drawPolygon(xPts, yPts, numPts);
        for (int i = 0; i < numPts; i++) {
            int x = xPts[i], y = yPts[i];
            g.setColor(colors[i]);
            g.fillRect(x - radius, y - radius, radius * 2, radius * 2);
        }
        
        /// draw random lines
        g.setColor(setAlpha(Color.ORANGE, 0.3f));
        for (Line l : lines) {
            int[] p1 = getDrawLoc(l.drawP1);
            int[] p2 = getDrawLoc(l.drawP2);
            g.drawLine(p1[0], p1[1], p2[0], p2[1]);
        }
        
        /// draw cluster best-fit lines
        // for (Cluster c : clusters) {
        //     if (!c.valid || (REV_CLUSTERS && c.points.get(0).revNum!=drawRev)) continue;
        //     int[] p1 = getDrawLoc(c.fitLineP1);
        //     int[] p2 = getDrawLoc(c.fitLineP2);
        //     // double alpha = Math.pow(c.getScore()/maxScore, 0.5);
        //     // g.setColor(new Color(0f,1f,0f, (float)alpha));
        //     g.setColor(Color.GREEN);
        //     g.drawLine(p1[0], p1[1], p2[0], p2[1]);
        // }
        
        g.setColor(Color.WHITE);
        g.drawOval(centerX-4, centerY-4, 8, 8);
        g.drawLine(20, 20, 20+(int)(EPS*scale), 20);
        // g.drawLine(20, 40, 20+(int)(EPS_2*scale), 40);
        // g.drawLine(20, 60, 20+(int)(CULL_GAP*scale), 60);
    }
    
    public static final int REVS_TO_READ = 4;
    public static boolean CULL_CLOSE = false;
    public Point[] generateArray() {
        debug("Loading data...");
        ArrayList<Point> points = new ArrayList<Point>();
        
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File("data_cube_proc.txt")));
            
            String str;
            double lastTheta = -1;
            int rev = 0;
            while ((str = br.readLine()) != null) {
                String[] polar = str.split(" ");
                double r = Double.parseDouble(polar[1]), theta = Math.toRadians(Double.parseDouble(polar[0]));
                if (r == 0) continue;
                if (CULL_CLOSE && r < 1900) continue;
                Point p = Point.fromPolar(theta, r);
                if (p.theta < lastTheta) rev++;
                if (rev >= REVS_TO_READ) break;
                lastTheta = p.theta;
                p.revNum = rev;
                points.add(p);
                
                // if (points.size() >= 4000) break;
            }
            numRevs = rev;
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        debug("Read "+points.size()+" points");
        Collections.sort(points);
        // cullBadData(points);
        // debug("Culled down to "+points.size()+" points");
        return points.toArray(new Point[points.size()]);
    }
    
    public static final double CULL_GAP = 500;
    public void cullBadData(ArrayList<Point> list) {
        Iterator<Point> it = list.iterator();
        Point last = it.next();
        while (it.hasNext()) {
            Point p = it.next();
            if (p.theta - last.theta < 0.1 && Math.abs(p.dist - last.dist) > CULL_GAP) {
                // it.remove();
                p.culled = true;
            } else {
                last = p;
            }
        }
    }
    
    public void calcBounds() {
        double maxX = 0, maxY = 0;
        for (Point p : points) {
            double x = Math.abs(p.x);
            if (x > maxX) maxX = x;
            double y = Math.abs(p.y);
            if (y > maxY) maxY = y;
        }
        widthMilli = (int)(maxX*2.1 * 0.8);
        heightMilli = (int)(maxY*2.1 * 0.8);
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
