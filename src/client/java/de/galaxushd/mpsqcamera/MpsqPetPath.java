package de.galaxushd.mpsqcamera;
import java.util.*;
/** Bounded A* on walkable surfaces; a route may initially lead away from its destination. */
final class MpsqPetPath {
    record Point(double x,double y,double z){}
    interface Ground { double step(Point from,double x,double z); }
    private record Key(int x,int y,int z){}
    private record Node(Point point,Key key,double cost,double estimate,Node parent){}
    static List<Point> find(Point start,double tx,double tz,Ground ground){
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(n->n.cost+n.estimate));
        Map<Key,Double> cost=new HashMap<>();Key root=new Key(0,(int)Math.round(start.y*16),0);
        open.add(new Node(start,root,0,Math.hypot(tx-start.x,tz-start.z),null));cost.put(root,0d);
        int visited=0;
        while(!open.isEmpty()&&visited++<1024){
            Node n=open.remove();if(n.cost>cost.getOrDefault(n.key,Double.MAX_VALUE))continue;
            if(Math.hypot(n.point.x-tx,n.point.z-tz)<.8){
                LinkedList<Point> path=new LinkedList<>();for(Node p=n;p.parent!=null;p=p.parent)path.addFirst(p.point);return path;
            }
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}}){
                int gx=n.key.x+d[0],gz=n.key.z+d[1];if(Math.abs(gx)>24||Math.abs(gz)>24)continue;
                double nx=start.x+gx,nz=start.z+gz;
                if(d[0]!=0&&d[1]!=0&&(!Double.isFinite(ground.step(n.point,nx,n.point.z))||!Double.isFinite(ground.step(n.point,n.point.x,nz))))continue;
                double y=ground.step(n.point,nx,nz);if(!Double.isFinite(y))continue;
                Point p=new Point(nx,y,nz);Key key=new Key(gx,(int)Math.round(y*16),gz);
                double g=n.cost+Math.hypot(d[0],d[1])+Math.abs(y-n.point.y)*.3;
                if(g>=cost.getOrDefault(key,Double.MAX_VALUE))continue;
                cost.put(key,g);open.add(new Node(p,key,g,Math.hypot(nx-tx,nz-tz),n));
            }
        }
        return List.of();
    }
}
