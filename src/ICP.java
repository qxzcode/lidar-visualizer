import java.util.ArrayList;

public class ICP {
    
    private final Display disp;
    
    public ICP(Display d, Point guessPos) {
        disp = d;
        icpTrans = new Transform(0, guessPos.x, guessPos.y);
    }
    
    
    public static class PointPair {
        public Point a, b;
        double dist;
        public PointPair(Point a, Point b, double dist) {
            this.a = a;
            this.b = b;
            this.dist = dist;
        }
    }
    
    public ReferenceModel reference = ReferenceModel.TOWER;
    public ReferenceModel transReference;
    public Transform icpTrans;
    
    public static final double OUTLIER_THRESH = 1.0; // multiplier of mean
    public ArrayList<PointPair> pairs = new ArrayList<>();
    public double dbgLength;
    public double lastMean = Double.POSITIVE_INFINITY;
    public Transform doICP(int iterations) {
        disp.debug("Doing ICP registration ("+iterations+" iters)...");
        long startTime = System.nanoTime();
        Transform trans = icpTrans;
        for (int n = 0; n < iterations+1; n++) {
            /// get pairs of corresponding points
            Transform transInv = trans.inverse();
            pairs.clear();
            
            double sumDists = 0;
            final double threshold = lastMean*OUTLIER_THRESH;
            dbgLength = threshold;
            
            double SumXa = 0, SumXb = 0, SumYa = 0, SumYb = 0;
            double Sxx = 0, Sxy = 0, Syx = 0, Syy = 0;
            for (Point p : disp.points) {
                Point p2 = transInv.apply(p);
                Point rp = reference.getClosestPoint(p2);
                double dist = p2.getDistance(rp);
                sumDists += dist;
                
                p.good = dist < threshold;
                if (!p.good) continue;
                pairs.add(new PointPair(p, rp, dist));
                
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
            
            if (n==iterations) break;
            lastMean = sumDists / disp.points.size();
            
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
                disp.debug("Converged on iteration n="+n);
                break;
            }
            trans = new Transform(theta, tx, ty, csin, ccos);
        }
        long endTime = System.nanoTime();
        disp.debug("Done ("+Math.round((endTime-startTime)/1000000f)+" ms)");
        disp.debug(trans);
        icpTrans = trans;
        transReference = trans.apply(reference);
        return trans;
    }
    
}