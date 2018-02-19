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

public class Display extends JPanel {
    private final int width         = 15000;
    private final int height        = 15000;
    private       int widthMilli;
    private       int heightMilli;
    private final int radius        = 2;
    private final Font font         = new Font("Consolas", Font.PLAIN, 30);
    
    private JLabel display;
    
    public Point[] points;
    
    public final ICP icp;
    public static final boolean SINGLE_STEP = true;
    
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
        
        // Creating Points
        points = generateArray();
        calcBounds();
        icp = new ICP(this);
        icp.doICP(SINGLE_STEP? 0 : 5000);
        new javax.swing.Timer((int)(1000/FPS), evt -> {
            drawRev++;
            if (drawRev >= numRevs) drawRev = 0;
            repaint();
        }).start();
    }
    
    int selectedI = -1;
    Point mouseLoc = null;
    public void onMouse(int mx, int my, int mdx, int mdy, boolean drag) {
        if (drag) {
            camX -= mdx / scale;
            camY -= mdy / scale;
            repaint();
        }
        
        Point m = mouseLoc = getDataLoc(mx, my);
        
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
            icp.doICP(1);
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
    public static final boolean DRAW_ICP = true;
    public boolean connectPoints = true;
    public int drawRev = 1;
    public int numRevs;
    public void paint(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // Draw Display
        g.setFont(font);
        g.setColor(Color.WHITE);
        if (mouseLoc != null) {
            g.drawString("Mouse: ("+(int)(mouseLoc.x)+", "+(int)(mouseLoc.y)+")",
                         20, centerY*2 - font.getSize() - 20);
        }
        
        // Draw stuff
        scale = Math.min((double)getWidth()/widthMilli, (double)getHeight()/heightMilli);
        centerX = getWidth()/2;
        centerY = getHeight()/2;
        
        if (DRAW_ICP) {
            g.setColor(Color.RED);
            for (ICP.PointPair pair : icp.pairs) {
                drawLine(g, pair.a, icp.icpTrans.apply(pair.b));
            }
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
            colors[numPts] = p.good && DRAW_ICP? new Color(0f, 1f, 0f, POINT_ALPHA) : new Color(1f, 1f, 1f, POINT_ALPHA);
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
        
        if (DRAW_ICP)
            icp.transReference.draw(g);
        
        if (selectedI >= 0 && DRAW_ICP) {
            Point p = points[selectedI];
            Point rp = icp.transReference.getClosestPoint(p);
            g.setColor(Color.RED);
            drawLine(g, p, rp);
        }
        
        int[] originPos = getDrawLoc(Point.fromRect(0, 0));
        g.setColor(Color.WHITE);
        g.drawOval(originPos[0]-4, originPos[1]-4, 8, 8);
        g.drawLine(20, 20, 20+(int)(icp.dbgLength*scale), 20);
    }
    
    public static final int REVS_TO_READ = 5000;
    public static final boolean CULL_CLOSE = false;
    public static final boolean CULL_OTHERS = true;
    public static final String dataFile = "new_points";//"data_cube";
    public Point[] generateArray() {
        debug("Loading data...");
        ArrayList<Point> points = new ArrayList<Point>();
        
        try {
            InputStream in = Display.class.getResourceAsStream("data/"+dataFile+".txt");
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
                if (CULL_OTHERS && !keepPoint(p)) continue;
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
    
    public boolean keepPoint(Point p) {
        return p.x > 6000 && p.x < 8000 &&
               p.y > -4000 && p.y < 0;
    }
    
    public static double scaleFactor = 1.0, camX = 0, camY = 0;
    public void calcBounds() {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Point p : points) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
        }
        double dx = maxX-minX, dy = maxY-minY;
        widthMilli = (int)(dx*1.1 / scaleFactor);
        heightMilli = (int)(dy*1.1 / scaleFactor);
        camX = (maxX + minX) / 2;
        camY = (maxY + minY) / 2;
    }
    
    public static Display disp;
    
    public static void main(String[] args) {
        disp = new Display();
        
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
