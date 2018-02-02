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
    
    public Line(double vx, double vy, double x0, double y0) {
        this.vx = vx;
        this.vy = vy;
        this.x0 = x0;
        this.y0 = y0;
        
        r = vy*x0 - vx*y0;
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
    
    public double getDistance(Point p) {
        return Math.abs(vy*p.x - vx*p.y - r);
    }
    
    public double getAvgDistance(Collection<Point> points) {
        // sum up the distances to the line
        double sumErr = 0;
        for (Point p : points) {
            sumErr += getDistance(p);
        }
        return sumErr / points.size();
    }
    
    public Segment getSegment(Collection<Point> points) {
        double minT = Double.MAX_VALUE, maxT = -Double.MAX_VALUE;
        for (Point p : points) {
            double t = getT(p.x, p.y);
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
        }
        return new Segment(this, minT, maxT);
    }
    
    public double getT(double x, double y) {
        return vx*(x-x0) + vy*(y-y0);
    }
    
    public double getT(Point p) {
        return getT(p.x, p.y);
    }
    
    public Point getPoint(double t) {
        return Point.fromRect(x0 + vx*t, y0 + vy*t);
    }
}

class Segment {
    public Line line;
    public double tMin, tMax;
    public Point pMin, pMax;
    
    public Segment(Line line, double tMin, double tMax) {
        this.line = line;
        this.tMin = tMin;
        this.tMax = tMax;
        this.pMin = line.getPoint(tMin);
        this.pMax = line.getPoint(tMax);
    }
    
    public double getDistance(Point p) {
        double t = line.getT(p);
        if (t <= tMin) return pMin.getDistance(p);
        if (t >= tMax) return pMax.getDistance(p);
        return line.getDistance(p);
    }
    
    public double getDistanceSq(Point p) {
        double t = line.getT(p);
        if (t <= tMin) return pMin.getDistanceSq(p);
        if (t >= tMax) return pMax.getDistanceSq(p);
        double d = line.getDistance(p);
        return d*d;
    }
    
    public Point getClosestPoint(Point p) {
        double t = line.getT(p);
        if (t <= tMin) return pMin;
        if (t >= tMax) return pMax;
        return line.getPoint(t);
    }
}

public class Display extends JPanel {
    private final int width         = 15000;
    private final int height        = 15000;
    private       int widthMilli;
    private       int heightMilli;
    private final int radius        = 2;
    private final Font font         = new Font("Roboto", Font.BOLD, 48);
    
    private JLabel display;
    
    private Point[] points;
    
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
        
        setFocusable(true);
        requestFocus();
        setVisible(true); 
        
        // Creating Points
        points = generateArray();
        calcBounds();
        doICP(reference, SINGLE_STEP? 0 : 5000);
        new javax.swing.Timer((int)(1000/FPS), evt -> {
            drawRev++;
            if (drawRev >= numRevs) drawRev = 0;
            repaint();
        }).start();
        
        MouseAdapter mouseListener = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                event(e.getX(), e.getY(), false);
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                event(e.getX(), e.getY(), SwingUtilities.isLeftMouseButton(e));
            }
            private int lastX=0, lastY=0;
            private void event(int x, int y, boolean drag) {
                int dx = x-lastX, dy = y-lastY;
                lastX = x;
                lastY = y;
                onMouse(x, y, dx, dy, drag);
            }
        };
        addMouseListener(mouseListener);
        addMouseMotionListener(mouseListener);
        
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                onKey(e.getKeyCode());
            }
        });
    }
    
    int selectedI = -1;
    public void onMouse(int mx, int my, int mdx, int mdy, boolean drag) {
        if (drag) {
            camX -= mdx / scale;
            camY -= mdy / scale;
            repaint();
        }
        
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
        
        if (selectedI != minI) {
            selectedI = minI;
            // debug("index: "+minI);
        }
    }
    
    public static final double ZOOM_RATE = 1.4;
    public void onKey(int code) {
        if (code == KeyEvent.VK_MINUS)
            scaleFactor /= ZOOM_RATE;
        if (code == KeyEvent.VK_EQUALS || code == KeyEvent.VK_PLUS)
            scaleFactor *= ZOOM_RATE;
        if (code == KeyEvent.VK_SPACE)
            doICP(reference, 1);
        if (code == KeyEvent.VK_L)
            connectPoints = !connectPoints;
        calcBounds();
        repaint();
    }
    
    public void debug(Object msg) {
        System.out.println(msg);
    }
    
    public static Color setAlpha(Color col, float alpha) {
        float[] rgb = col.getRGBColorComponents(null);
        return new Color(rgb[0],rgb[1],rgb[2], alpha);
    }
    
    //////////////////////////// ICP ////////////////////////////
    
    class PointPair {
        public Point a, b;
        public PointPair(Point a, Point b) {
            this.a = a;
            this.b = b;
        }
    }
    
    class Transform {
        // rotate by theta about the origin, then translate by <tx, ty>
        public final double theta, tx, ty;
        protected final double sin, cos; // cache these
        public Transform() {
            theta = 0;
            tx = ty = 0;
            sin = 0.0;
            cos = 1.0;
        }
        public Transform(double theta, double tx, double ty) {
            this(theta, tx, ty, Math.sin(theta), Math.cos(theta));
        }
        public Transform(double theta, double tx, double ty, double sin, double cos) {
            this.theta = theta;
            this.tx = tx;
            this.ty = ty;
            this.sin = sin;
            this.cos = cos;
        }
        public Point apply(Point p) {
            return Point.fromRect(p.x*cos - p.y*sin + tx,
                                  p.x*sin + p.y*cos + ty);
        }
        public Line apply(Line l) {
            return new Line(l.vx*cos - l.vy*sin,
                            l.vx*sin + l.vy*cos,
                            l.x0*cos - l.y0*sin + tx,
                            l.x0*sin + l.y0*cos + ty);
        }
        public Segment apply(Segment s) {
            return new Segment(apply(s.line), s.tMin, s.tMax);
        }
        public ReferenceModel apply(ReferenceModel rm) {
            Segment[] ss = new Segment[rm.segments.length];
            for (int i = 0; i < rm.segments.length; i++) {
                ss[i] = apply(rm.segments[i]);
            }
            return new ReferenceModel(ss);
        }
        public Transform inverse() {
            return new Transform(-theta,
                                 -tx*cos - ty*sin,
                                  tx*sin - ty*cos,
                                 -sin, cos);
        }
        public String toString() {
            return "["+Math.toDegrees(theta)+"° <"+tx+", "+ty+">]";
        }
    }
    
    class ReferenceModel {
        public Segment[] segments;
        public ReferenceModel(Segment[] ss) {
            segments = ss;
        }
        public Point getClosestPoint(Point p) {
            double minDist = Double.MAX_VALUE;
            Segment minSeg = null;
            for (Segment s : segments) {
                double dist = s.getDistanceSq(p);
                if (dist < minDist) {
                    minDist = dist;
                    minSeg = s;
                }
            }
            return minSeg.getClosestPoint(p);
        }
        public void draw(Graphics g) {
            g.setColor(Color.ORANGE);
            for (Segment s : segments) {
                drawLine(g, s.pMin, s.pMax);
            }
        }
    }
    
    public static final double REFM_SIZE = 4000;
    public static final double REFM_SIZE2 = 3400;
    ReferenceModel reference = new ReferenceModel(new Segment[] {
        new Segment(new Line(0, +1, REFM_SIZE), -REFM_SIZE2, REFM_SIZE2),
        new Segment(new Line(0, -1, REFM_SIZE), -REFM_SIZE2, REFM_SIZE2),
        new Segment(new Line(+1, 0, REFM_SIZE2), -REFM_SIZE, REFM_SIZE),
        new Segment(new Line(-1, 0, REFM_SIZE2), -REFM_SIZE, REFM_SIZE)
    });
    private ReferenceModel transReference;
    private Transform icpTrans = new Transform();
    
    public static final double OUTLIER_THRESH = 300;
    public static final boolean SINGLE_STEP = true;
    private ArrayList<PointPair> pairs = new ArrayList<>();
    public Transform doICP(ReferenceModel reference, int iterations) {
        debug("Doing ICP registration ("+iterations+" iters)...");
        long startTime = System.nanoTime();
        Transform trans = icpTrans;
        for (int n = 0; n < iterations+1; n++) {
            /// get pairs of corresponding points
            Transform transInv = trans.inverse();
            pairs.clear();
            
            double SumXa = 0, SumXb = 0, SumYa = 0, SumYb = 0;
            double Sxx = 0, Sxy = 0, Syx = 0, Syy = 0;
            for (Point p : points) {
                Point p2 = transInv.apply(p);
                Point rp = reference.getClosestPoint(p2);
                p.good = p2.getDistanceSq(rp) < OUTLIER_THRESH*OUTLIER_THRESH;
                if (!p.good) continue;
                pairs.add(new PointPair(p, rp));
                
                // Compute the terms:
                SumXa += p.x;
                SumYa += p.y;
                
                SumXb += rp.x;
                SumYb += rp.y;
                
                Sxx += p.x * rp.x;
                Sxy += p.x * rp.y;
                Syx += p.y * rp.x;
                Syy += p.y * rp.y;
            }
            
            // TODO: compute the mean & [variance|std. dev.] for the point distances,
            //       then reject outliers whose distance is more than mean+[some multiple of variance]
            // (also look up popular variants of ICP that deal with outliers)
            
            if (n==iterations) break;
            
            /// calculate the new transform
            // code based on http://mrpt.ual.es/reference/devel/se2__l2_8cpp_source.html#l00158
            final int N = pairs.size();
            if (N==0) return new Transform(); // TODO: handle this better, or avoid it
            final double N_inv = 1.0 / N;
            
            final double mean_x_a = SumXa * N_inv;
            final double mean_y_a = SumYa * N_inv;
            final double mean_x_b = SumXb * N_inv;
            final double mean_y_b = SumYb * N_inv;
            
            // Auxiliary variables Ax,Ay:
            final double Ax = N * (Sxx + Syy) - SumXa * SumXb - SumYa * SumYb;
            final double Ay = SumXa * SumYb + N * (Syx - Sxy) - SumXb * SumYa;
            
            final double theta = (Ax == 0 && Ay == 0)? 0.0 : Math.atan2(Ay, Ax);
            
            final double ccos = Math.cos(theta);
            final double csin = Math.sin(theta);
            
            final double tx = mean_x_a - mean_x_b * ccos + mean_y_b * csin;
            final double ty = mean_y_a - mean_x_b * csin - mean_y_b * ccos;
            
            if (theta==trans.theta && tx==trans.tx && ty==trans.ty) {
                debug("Converged on iteration n="+n);
                break;
            }
            trans = new Transform(theta, tx, ty, csin, ccos);
        }
        long endTime = System.nanoTime();
        debug("Done ("+((endTime-startTime)/1000000)+" ms)");
        debug(trans);
        icpTrans = trans;
        transReference = trans.apply(reference);
        return trans;
    }
    
    /////////////////////////////////////////////////////////////
    
    private double scale;
    private int centerX, centerY;
    public int[] getDrawLoc(Point p) {
        double actualX = (p.x-camX) * scale + centerX;
        double actualY = (p.y-camY) * scale + centerY;
        return new int[] {(int)actualX,
                          (int)actualY};
    }
    
    public Point getDataLoc(int x, int y) {
        return Point.fromRect((x-centerX) / scale + camX,
                              (y-centerY) / scale + camY);
    }
    
    public void drawLine(Graphics g, Point p1, Point p2) {
        int[] pos1 = getDrawLoc(p1);
        int[] pos2 = getDrawLoc(p2);
        g.drawLine(pos1[0], pos1[1], pos2[0], pos2[1]);
    }
    
    public static final float POINT_ALPHA = 0.9f;
    public static final boolean DRAW_FRAMES = false;
    public boolean connectPoints = true;
    public int drawRev = 1;
    public int numRevs;
    public void paint(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // Draw Display
        g.setFont(font);
        //g.drawString("insert text here", 20, 50);
        
        // Draw stuff
        scale = Math.min((double)getWidth()/widthMilli, (double)getHeight()/heightMilli);
        centerX = getWidth()/2;
        centerY = getHeight()/2;
        
        g.setColor(Color.RED);
        for (PointPair pair : pairs) {
            drawLine(g, pair.a, icpTrans.apply(pair.b));
        }
        
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
            colors[numPts] = p.good? new Color(0f, 1f, 0f, POINT_ALPHA) : new Color(1f, 1f, 1f, POINT_ALPHA);
            numPts++;
        }
        if (connectPoints) {
            g.setColor(new Color(1f, 1f, 1f, 0.2f));
            g.drawPolygon(xPts, yPts, numPts);
        }
        for (int i = 0; i < numPts; i++) {
            int x = xPts[i], y = yPts[i];
            g.setColor(colors[i]);
            g.fillRect(x - radius, y - radius, radius * 2, radius * 2);
        }
        
        transReference.draw(g);
        
        if (selectedI >= 0) {
            Point p = points[selectedI];
            Point rp = transReference.getClosestPoint(p);
            g.setColor(Color.RED);
            drawLine(g, p, rp);
        }
        
        int[] originPos = getDrawLoc(Point.fromRect(0, 0));
        g.setColor(Color.WHITE);
        g.drawOval(originPos[0]-4, originPos[1]-4, 8, 8);
        g.drawLine(20, 20, 20+(int)(OUTLIER_THRESH*scale), 20);
    }
    
    public static final int REVS_TO_READ = 1;
    public static final boolean CULL_CLOSE = false;
    public static final String dataFile = "points";//"data_cube";
    public Point[] generateArray() {
        debug("Loading data...");
        ArrayList<Point> points = new ArrayList<Point>();
        
        try {
            InputStream in = Display.class.getResourceAsStream(dataFile+"_proc.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            
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
            }
            numRevs = rev;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        debug("Read "+points.size()+" points");
        Collections.sort(points);
        return points.toArray(new Point[points.size()]);
    }
    
    public static double scaleFactor = 1.0, camX = 0, camY = 0;
    public void calcBounds() {
        double maxX = 0, maxY = 0;
        for (Point p : points) {
            double x = Math.abs(p.x);
            if (x > maxX) maxX = x;
            double y = Math.abs(p.y);
            if (y > maxY) maxY = y;
        }
        widthMilli = (int)(maxX*2.1 / scaleFactor);
        heightMilli = (int)(maxY*2.1 / scaleFactor);
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
        frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }
}
