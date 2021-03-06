import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.awt.Color;

class ReferenceModel {
    
    public static final double REFM_SIZE = 4000;
    public static final double REFM_SIZE2 = 3400;
    public static final ReferenceModel BOX = new ReferenceModel(new Segment[] {
        new Segment(new Line(0, +1, REFM_SIZE), -REFM_SIZE2, REFM_SIZE2),
        new Segment(new Line(0, -1, REFM_SIZE), -REFM_SIZE2, REFM_SIZE2),
        new Segment(new Line(+1, 0, REFM_SIZE2), -REFM_SIZE, REFM_SIZE),
        new Segment(new Line(-1, 0, REFM_SIZE2), -REFM_SIZE, REFM_SIZE)
    });
    
    public static final double IN_TO_MM = 25.4; // 1 inch = 25.4 millimeters
    public static final double TOWER_WIDTH = 17 * IN_TO_MM;
    public static final double TOWER_DEPTH = 21.5 * IN_TO_MM;
    private static final Point TOWER00 = Point.fromRect(0, -TOWER_WIDTH/2);
    private static final Point TOWER01 = Point.fromRect(0, +TOWER_WIDTH/2);
    private static final Point TOWER10 = Point.fromRect(TOWER_DEPTH, -TOWER_WIDTH/2);
    private static final Point TOWER11 = Point.fromRect(TOWER_DEPTH, +TOWER_WIDTH/2);
    
    public static final ReferenceModel TOWER = new ReferenceModel(
        // new Segment(TOWER00, TOWER10), // bottom (-Y) face
        new Segment(TOWER00, TOWER01) // front face
        // new Segment(TOWER01, TOWER11)  // top (+Y) face
    );
    
    
    public final Segment[] segments;
    
    public ReferenceModel(Segment... ss) {
        segments = ss;
    }
    
    public Point getClosestPoint(Point p) {
        double minDist = Double.POSITIVE_INFINITY;
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
    
    public static final Stroke stroke = new BasicStroke(8.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(stroke);
        g.setColor(Color.ORANGE);
        for (Segment s : segments) {
            Display.disp.drawLine(g, s.pMin, s.pMax);
        }
        g2.setStroke(oldStroke);
    }
    
    public String toString() {
        String str = "[";
        for (Segment s : segments) {
            str += "\n  "+s;
        }
        return str+"\n]";
    }
}