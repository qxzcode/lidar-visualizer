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
    
    public ArrayList<Point> points;
    public Point averagePoint;
    
    public ICP icp;
    public static final boolean SINGLE_STEP = true;
    
    public static final double FPS = 10;
    public Display() {
        //setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        
        setSize(new Dimension(width, height)); 
        setPreferredSize(new Dimension(width, height));
        setLayout(null);
        
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
                onKey(e.getKeyCode(), (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0);
            }
        });
        
        // Creating Points
        reset(0, false);
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
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
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
    public void onKey(int code, boolean shift) {
        if (code == KeyEvent.VK_MINUS)
            scaleFactor /= ZOOM_RATE;
        if (code == KeyEvent.VK_EQUALS || code == KeyEvent.VK_PLUS)
            scaleFactor *= ZOOM_RATE;
        
        if (code == KeyEvent.VK_SPACE)
            icp.doICP(1);
        
        if (code == KeyEvent.VK_L)
            connectPoints = !connectPoints;
        if (code == KeyEvent.VK_I)
            drawICP = !drawICP;
        if (code == KeyEvent.VK_D)
            debugICP = !debugICP;
        if (code == KeyEvent.VK_F)
            drawFrames = !drawFrames;
        
        int n = code - KeyEvent.VK_0 - 1;
        if (n == -1) n = 9;
        if (n >= 0 && n < DATA_FILES.length) {
            reset(n, shift);
        } else {
            calcBounds(false);
        }
        
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
    
    public static final float POINT_ALPHA = 1.0f;
    public static boolean drawFrames = false;
    public boolean drawICP = false;
    public boolean debugICP = false;
    public boolean connectPoints = true;
    public int drawRev;
    public int numRevs;
    public void paint(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        // Draw text
        g.setFont(font);
        g.setColor(Color.WHITE);
        if (mouseLoc != null) {
            g.drawString("Mouse: ("+(int)(mouseLoc.x)+", "+(int)(mouseLoc.y)+")",
                         20, centerY*2 - font.getSize() - 20);
        }
        if (drawFrames) {
            int padLen = Integer.toString(numRevs).length();
            String str = Integer.toString(drawRev);
            while (str.length() < padLen) str = " "+str;
            g.drawString("Revolution: "+str+"/"+numRevs,
                         20, centerY*2 - font.getSize()*2 - 20);
        }
        int dataFileNum = curDataFile + 1;
        if (dataFileNum == 10) dataFileNum = 0;
        g.drawString("Dataset: "+DATA_FILES[curDataFile]+" ("+dataFileNum+")",
                         20, centerY*2 - font.getSize()*3 - 20);
        
        // Draw stuff
        scale = Math.min((double)getWidth()/widthMilli, (double)getHeight()/heightMilli);
        centerX = getWidth()/2;
        centerY = getHeight()/2;
        
        if (drawICP && debugICP) {
            g.setColor(Color.RED);
            for (ICP.PointPair pair : icp.pairs) {
                drawLine(g, pair.a, icp.icpTrans.apply(pair.b));
            }
        }
        
        int[] xPts = new int[points.size()];
        int[] yPts = new int[points.size()];
        Color[] colors = new Color[points.size()];
        int numPts = 0;
        for (Point p : points) {
            if (drawFrames && p.revNum != drawRev) continue;
            int[] pos = getDrawLoc(p);
            xPts[numPts] = pos[0];
            yPts[numPts] = pos[1];
            colors[numPts] = p.good && drawICP && debugICP? new Color(0f, 1f, 0f, POINT_ALPHA) : new Color(1f, 1f, 1f, POINT_ALPHA);
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
        
        if (drawICP)
            icp.transReference.draw(g);
        
        if (selectedI >= 0 && drawICP && debugICP) {
            Point p = points.get(selectedI);
            Point rp = icp.transReference.getClosestPoint(p);
            g.setColor(Color.RED);
            drawLine(g, p, rp);
        }
        
        int[] originPos = getDrawLoc(Point.fromRect(0, 0));
        g.setColor(Color.WHITE);
        g.drawOval(originPos[0]-4, originPos[1]-4, 8, 8);
        g.drawLine(20, 20, 20+(int)(icp.dbgLength*scale), 20);
    }
    
    public static final int REVS_TO_READ = Integer.MAX_VALUE;
    public static final boolean CULL_CLOSE = false;
    public static final boolean CULL_OTHERS = false;
    public ArrayList<Point> generateArray(String dataFile) {
        debug("Loading data...");
        ArrayList<Point> points = new ArrayList<Point>();
        double sumX = 0, sumY = 0;
        
        try {
            InputStream in = Display.class.getResourceAsStream("data/"+dataFile+".txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            
            String str;
            double lastTheta = -1;
            int rev = 0;
            while ((str = br.readLine()) != null) {
                String[] polar = str.split(" ");
                double r = Double.parseDouble(polar[1]), theta = Math.toRadians(Double.parseDouble(polar[0]));
                if (Double.isNaN(r) || Double.isNaN(theta)) throw new RuntimeException("NaN in data!");
                if (theta < lastTheta) rev++;
                lastTheta = theta;
                if (r == 0) continue;
                if (CULL_CLOSE && r < 1900) continue;
                Point p = Point.fromPolar(-theta, r); // flip in Y
                if (rev >= REVS_TO_READ) break;
                p.revNum = rev;
                if (CULL_OTHERS && !keepPoint(p)) continue;
                points.add(p);
                sumX += p.x;
                sumY += p.y;
            }
            numRevs = rev;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        debug("Read "+points.size()+" points ("+numRevs+" revolutions)");
        averagePoint = Point.fromRect(sumX/points.size(), sumY/points.size());
        sortPoints(points);
        return points;
    }
    
    public void sortPoints(List<Point> points) {
        Collections.sort(points);
    }
    
    public boolean keepPoint(Point p) {
        return p.x > 6000 && p.x < 8000 &&
               p.y > -7000 && p.y < 7000;
    }
    
    public static double scaleFactor, camX, camY;
    public void calcBounds(boolean setCam) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Point p : points) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
        }
        double dx = maxX-minX, dy = maxY-minY;
        widthMilli = (int)(dx*1.2 / scaleFactor);
        heightMilli = (int)(dy*1.2 / scaleFactor);
        widthMilli = 7000;
        heightMilli = 13000;
        if (setCam) {
            camX = 7000;//(maxX + minX) / 2;
            camY = (maxY + minY) / 2;
        }
    }
    
    public static final String[] DATA_FILES = new String[] {
        "left", "left-middle", "middle", "right-middle", "right", "right-far"
    };
    public int curDataFile = -1;
    public void reset(int dataI, boolean shift) {
        curDataFile = dataI;
        ArrayList<Point> newPoints = generateArray(DATA_FILES[dataI]);
        if (shift) {
            points.addAll(newPoints);
            sortPoints(points);
        } else {
            points = newPoints;
        }
        
        scaleFactor = 1.0;
        calcBounds(true);
        
        icp = new ICP(this, averagePoint);
        icp.doICP(SINGLE_STEP? 0 : 5000);
        
        drawRev = 0;
        selectedI = -1;
        mouseLoc = null;
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
