import java.awt.Graphics;
import java.awt.Color;

class ReferenceModel {
    
    public static final double REFM_SIZE = 4000;
    public static final double REFM_SIZE2 = 3400;
    public static final Point BOX00 = Point.fromRect(-REFM_SIZE2, -REFM_SIZE);
    public static final Point BOX01 = Point.fromRect(-REFM_SIZE2, +REFM_SIZE);
    public static final Point BOX10 = Point.fromRect(+REFM_SIZE2, -REFM_SIZE);
    public static final Point BOX11 = Point.fromRect(+REFM_SIZE2, +REFM_SIZE);
    public static final ReferenceModel BOX = new ReferenceModel(new Segment[] {
        // new Segment(new Line(0, +1, REFM_SIZE), -REFM_SIZE2, REFM_SIZE2),
        // new Segment(new Line(0, -1, REFM_SIZE), -REFM_SIZE2, REFM_SIZE2),
        // new Segment(new Line(+1, 0, REFM_SIZE2), -REFM_SIZE, REFM_SIZE),
        // new Segment(new Line(-1, 0, REFM_SIZE2), -REFM_SIZE, REFM_SIZE)
        new Segment(BOX00, BOX01),
        // new Segment(BOX01, BOX11),
        // new Segment(BOX11, BOX10),
        // new Segment(BOX10, BOX00),
    });
    
    public static final double IN_TO_MM = 25.4; // 1 inch = 25.4 millimeters
    public static final double TOWER_WIDTH = 17 * IN_TO_MM;
    public static final double TOWER_DEPTH = 21.5 * IN_TO_MM;
    private static final Point TOWER00 = Point.fromRect(0, -TOWER_WIDTH/2);
    private static final Point TOWER01 = Point.fromRect(0, +TOWER_WIDTH/2);
    private static final Point TOWER10 = Point.fromRect(TOWER_DEPTH, -TOWER_WIDTH/2);
    private static final Point TOWER11 = Point.fromRect(TOWER_DEPTH, +TOWER_WIDTH/2);
    
    public static final ReferenceModel TOWER = new ReferenceModel(
        new Segment(TOWER00, TOWER10), // bottom (-Y) face
        new Segment(TOWER00, TOWER01), // front face
        new Segment(TOWER01, TOWER11)  // top (+Y) face
    );
    
    
    public final Segment[] segments;
    public ReferenceModel(Segment... ss) {
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
            Display.disp.drawLine(g, s.pMin, s.pMax);
        }
    }
}