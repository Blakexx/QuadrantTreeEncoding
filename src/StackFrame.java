import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

public class StackFrame{

    public final int height, width, xPos, yPos, quadrant;
    private final int nHeight, hDif, nWidth, wDif, newY, newX;
    public final StackFrame parent;

    public StackFrame(int yPos, int xPos, int height, int width){
        this(yPos,xPos,height,width,null,-1);
    }

    public StackFrame(int yPos, int xPos, int height, int width, StackFrame parent, int quadrant){
        this.height = height;
        this.width = width;
        this.xPos = xPos;
        this.yPos = yPos;
        this.parent = parent;
        this.quadrant = quadrant;
        nHeight = Math.max(1,height/2);
        hDif = height-nHeight;
        nWidth = Math.max(1,width/2);
        wDif = width-nWidth;
        newY = yPos+nHeight;
        newX = xPos+nWidth;
    }

    public int size(){
        return height*width;
    }

    public boolean contains(int r, int c){
        return r>=yPos&&r<yPos+height&&c>=xPos&&c<xPos+width;
    }

    public boolean equals(Object o){
        if(o==null||o.getClass()!=StackFrame.class){
            return false;
        }
        StackFrame other = (StackFrame)o;
        return (yPos==other.yPos)&&(xPos==other.xPos)&&(height==other.height)&&(width==other.width);
    }

    private int getQuadrant(int r, int c){
        return (r>=yPos+nHeight?2:0) + (c>=xPos+nWidth?1:0);
    }

    private StackFrame getQuadrantFrame(int quadrant){
        StackFrame returned = switch(quadrant){
            case 0 -> new StackFrame(yPos, xPos, nHeight, nWidth, this, 0);
            case 1 -> new StackFrame(yPos, newX, nHeight, wDif, this, 1);
            case 2 -> new StackFrame(newY, xPos, hDif, nWidth, this, 2);
            case 3 -> new StackFrame(newY, newX, hDif, wDif, this, 3);
            default -> null;
        };
        if(returned==null||returned.size()==0){
            return null;
        }
        return returned;
    }

    public StackFrame getChildContaining(int r, int c){
        return getQuadrantFrame(getQuadrant(r,c));
    }

    public static void pushFrame(LinkedList<StackFrame> stack){
        StackFrame parent = stack.removeLast();
        for(int q = 3; q>-1;q--){
            StackFrame toAdd = parent.getQuadrantFrame(q);
            if(toAdd!=null){
                stack.add(toAdd);
            }
        }
    }

    private LinkedList<StackFrame> getChildrenInRange(int start, int end){
        LinkedList<StackFrame> stack = new LinkedList<>();
        for(int q = start; q<=end; q++){
            StackFrame toAdd = getQuadrantFrame(q);
            if(toAdd!=null){
                stack.add(toAdd);
            }
        }
        return stack;
    }

    public LinkedList<StackFrame> getChildren(){
        return getChildrenInRange(0,3);
    }

    public LinkedList<StackFrame> getChildrenBefore(int r, int c){
        return getChildrenInRange(0,getQuadrant(r,c)-1);
    }

    public LinkedList<StackFrame> getChildrenAfter(int r, int c){
        return getChildrenInRange(getQuadrant(r,c)+1,3);
    }

    public String toString(){
        return "("+yPos+", "+xPos+", "+height+", "+width+")";
    }
}