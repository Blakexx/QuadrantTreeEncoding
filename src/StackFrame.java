import java.util.LinkedList;
import java.util.List;

public class StackFrame{

    public final int yLen, xLen, xOffset, yOffset, quadrant;
    public final StackFrame parent;

    public StackFrame(int yo, int xo, int yl, int xl){
        yLen = yl;
        xLen = xl;
        xOffset = xo;
        yOffset = yo;
        parent = null;
        quadrant = -1;
    }

    public StackFrame(int yo, int xo, int yl, int xl, StackFrame par, int quad){
        yLen = yl;
        xLen = xl;
        xOffset = xo;
        yOffset = yo;
        parent = par;
        quadrant = quad;
    }

    public int size(){
        return yLen*xLen;
    }

    public boolean contains(int r, int c){
        return r>=yOffset&&r<yOffset+yLen&&c>=xOffset&&c<xOffset+xLen;
    }

    public boolean equals(Object o){
        if(o==null){
            return false;
        }
        StackFrame other = (StackFrame)o;
        return (yOffset==other.yOffset)&&(xOffset==other.xOffset)&&(yLen==other.yLen)&&(xLen==other.xLen);
    }

    public String toString(){
        return "("+yLen+", "+xLen+", "+yOffset+", "+xOffset+")";
    }

    public static void pushFrame(LinkedList<StackFrame> stack){
        LinkedList<StackFrame> children = stack.removeLast().getChildren();
        while(children.size()>0){
            stack.add(children.removeLast());
        }
    }

    public LinkedList<StackFrame> getChildren(){
        LinkedList<StackFrame> stack = new LinkedList<>();
        int nyLen = Math.max(1,yLen/2), yDif = yLen-nyLen;
        int nxLen = Math.max(1,xLen/2), xDif = xLen-nxLen;
        int newY = yOffset+nyLen, newX = xOffset+nxLen;
        stack.add(new StackFrame(yOffset, xOffset, nyLen, nxLen,this,0));
        if(xLen>1){
            stack.add(new StackFrame(yOffset, newX, nyLen, xDif,this,1));
        }
        if(yLen>1){
            stack.add(new StackFrame(newY, xOffset, yDif, nxLen,this,2));
        }
        if(yLen>1&&xLen>1){
            stack.add(new StackFrame(newY, newX, yDif, xDif,this,3));
        }
        return stack;
    }

    public LinkedList<StackFrame> getChildrenBefore(int r, int c){
        LinkedList<StackFrame> stack = new LinkedList<>();
        int nyLen = Math.max(1,yLen/2), yDif = yLen-nyLen;
        int nxLen = Math.max(1,xLen/2), xDif = xLen-nxLen;
        int newY = yOffset+nyLen, newX = xOffset+nxLen;
        int quadrant = (r>=yOffset+nyLen?2:0) + (c>=xOffset+nxLen?1:0);
        if(quadrant>0){
            stack.add(new StackFrame(yOffset, xOffset, nyLen, nxLen,this,0));
        }
        if(quadrant>1&&xLen>1){
            stack.add(new StackFrame(yOffset, newX, nyLen, xDif,this,1));
        }
        if(quadrant>2&&yLen>1){
            stack.add(new StackFrame(newY, xOffset, yDif, nxLen,this,2));
        }
        return stack;
    }

    public StackFrame getChildContaining(int r, int c){
        int nyLen = Math.max(1,yLen/2), yDif = yLen-nyLen;
        int nxLen = Math.max(1,xLen/2), xDif = xLen-nxLen;
        int newY = yOffset+nyLen, newX = xOffset+nxLen;
        int quadrant = (r>=yOffset+nyLen?2:0) + (c>=xOffset+nxLen?1:0);
        return switch(quadrant){
            case 0 -> new StackFrame(yOffset, xOffset, nyLen, nxLen, this, 0);
            case 1 -> new StackFrame(yOffset, newX, nyLen, xDif, this, 1);
            case 2 -> new StackFrame(newY, xOffset, yDif, nxLen, this, 2);
            case 3 -> new StackFrame(newY, newX, yDif, xDif, this, 3);
            default -> null;
        };
    }

    public LinkedList<StackFrame> getChildrenAfter(int r, int c){
        LinkedList<StackFrame> stack = new LinkedList<>();
        int nyLen = Math.max(1,yLen/2), yDif = yLen-nyLen;
        int nxLen = Math.max(1,xLen/2), xDif = xLen-nxLen;
        int newY = yOffset+nyLen, newX = xOffset+nxLen;
        int quadrant = (r>=yOffset+nyLen?2:0) + (c>=xOffset+nxLen?1:0);
        if(quadrant<1&&xLen>1){
            stack.add(new StackFrame(yOffset, newX, nyLen, xDif,this,1));
        }
        if(quadrant<2&&yLen>1){
            stack.add(new StackFrame(newY, xOffset, yDif, nxLen,this,2));
        }
        if(quadrant<3&&yLen>1&&xLen>1){
            stack.add(new StackFrame(newY, newX, yDif, xDif,this,3));
        }
        return stack;
    }
}