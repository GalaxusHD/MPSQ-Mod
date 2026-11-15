package de.galaxushd.mpsqcamera;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Bounded 3D detours, cached between ticks; every edge checks the whole body. */
final class MpsqFlightNavigation {
    private record Cell(int x,int y,int z){}
    private record Node(Cell cell,double score){}
    private final double radius,height;
    private List<Vec3> route=List.of();
    private Vec3 goal=Vec3.ZERO;
    private long retry;
    MpsqFlightNavigation(double radius,double height){this.radius=radius;this.height=height;}
    private boolean clear(Minecraft c,Vec3 a,Vec3 b){
        int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)*4));
        for(int i=1;i<=steps;i++){var p=a.lerp(b,(double)i/steps);if(MpsqPetGrounding.blocked(c,p.x,p.y,p.z,radius,height))return false;}return true;
    }
    Vec3 next(Minecraft c,Vec3 from,Vec3 target,long now){
        if(clear(c,from,target)){route=List.of();return target;}
        if(now>=retry&&(route.isEmpty()||goal.distanceToSqr(target)>1)){
            retry=now+1000;goal=target;route=plan(c,from,target);
        }
        while(!route.isEmpty()&&from.distanceToSqr(route.get(0))<.09)route=route.subList(1,route.size());
        if(route.isEmpty())return null;
        if(!clear(c,from,route.get(0))){route=List.of();return null;}return route.get(0);
    }
    private List<Vec3> plan(Minecraft c,Vec3 origin,Vec3 target){
        Cell start=new Cell(0,0,0);Map<Cell,Double> costs=new HashMap<>();Map<Cell,Cell> parents=new HashMap<>();
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        costs.put(start,0.);open.add(new Node(start,0));Set<Cell> closed=new HashSet<>();
        int[][] offsets={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while(!open.isEmpty()&&closed.size()<768){
            Cell cell=open.remove().cell();if(!closed.add(cell))continue;Vec3 point=origin.add(cell.x,cell.y,cell.z);
            if(point.distanceToSqr(target)<2&&clear(c,point,target)){
                LinkedList<Vec3> result=new LinkedList<>();result.add(target);
                for(Cell at=cell;!at.equals(start);at=parents.get(at))result.addFirst(origin.add(at.x,at.y,at.z));return result;
            }
            for(int[] d:offsets){Cell next=new Cell(cell.x+d[0],cell.y+d[1],cell.z+d[2]);
                if(Math.abs(next.x)>20||Math.abs(next.z)>20||Math.abs(next.y)>6||closed.contains(next))continue;
                Vec3 p=origin.add(next.x,next.y,next.z);double cost=costs.get(cell)+1;
                if(cost>=costs.getOrDefault(next,Double.POSITIVE_INFINITY)||!clear(c,point,p))continue;
                costs.put(next,cost);parents.put(next,cell);open.add(new Node(next,cost+p.distanceTo(target)));
            }
        }return List.of();
    }
}
