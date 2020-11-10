import java.util.LinkedList;

public class Quadrant {

    public final int height, width, xPos, yPos, quadrant;
    private final int nHeight, hDif, nWidth, wDif, newY, newX;
    public final Quadrant parent;

    public Quadrant(int yPos, int xPos, int height, int width){
        this(yPos,xPos,height,width,null,-1);
    }

    public Quadrant(int yPos, int xPos, int height, int width, Quadrant parent, int quadrant){
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
        if(o==null||o.getClass()!= Quadrant.class){
            return false;
        }
        Quadrant other = (Quadrant)o;
        return (yPos==other.yPos)&&(xPos==other.xPos)&&(height==other.height)&&(width==other.width);
    }

    private int getQuadrant(int r, int c){
        return (r>=yPos+nHeight?2:0) + (c>=xPos+nWidth?1:0);
    }

    private Quadrant getQuadrantFrame(int quadrant){
        Quadrant returned = switch(quadrant){
            case 0 -> new Quadrant(yPos, xPos, nHeight, nWidth, this, 0);
            case 1 -> new Quadrant(yPos, newX, nHeight, wDif, this, 1);
            case 2 -> new Quadrant(newY, xPos, hDif, nWidth, this, 2);
            case 3 -> new Quadrant(newY, newX, hDif, wDif, this, 3);
            default -> null;
        };
        if(returned==null||returned.size()==0){
            return null;
        }
        return returned;
    }

    private Quadrant nextParent(){
        if(parent==null){
            return null;
        }
        return parent.skipChildren();
    }

    public Quadrant firstChild(){
        return getQuadrantFrame(0);
    }

    public Quadrant lastChild(){
        int count = 3;
        Quadrant returned = getQuadrantFrame(count);
        while(returned==null){
            returned = getQuadrantFrame(--count);
        }
        return returned;
    }

    public Quadrant getNext(){
        if(size()<=1){
            return skipChildren();
        }
        return firstChild();
    }

    public Quadrant skipChildren(){
        Quadrant sibling = nextSibling();
        if(sibling==null){
            return nextParent();
        }
        return sibling;
    }

    public Quadrant getSibling(int quadrant){
        if(parent==null){
            return null;
        }
        return parent.getQuadrantFrame(quadrant);
    }

    public Quadrant nextSibling(){
        if(parent==null){
            return null;
        }
        Quadrant returned = null;
        for(int q = quadrant+1;q<4;q++){
            returned = getSibling(q);
            if(returned!=null){
                break;
            }
        }
        return returned;
    }

    public Quadrant prevSibling(){
        if(parent==null){
            return null;
        }
        Quadrant returned = null;
        for(int q = quadrant-1;q>=0;q--){
            returned = getSibling(q);
            if(returned!=null){
                break;
            }
        }
        return returned;
    }

    public boolean contains(Quadrant other){
        int thisYEnd = yPos + height, thisXEnd = xPos + width;
        int otherYEnd = other.yPos + other.height, otherXEnd = other.xPos + other.width;
        return other.xPos >= xPos && other.yPos >= yPos && otherYEnd <= thisYEnd && otherXEnd <= thisXEnd;
    }

    public Quadrant getChildContaining(int r, int c){
        return getQuadrantFrame(getQuadrant(r,c));
    }

    private LinkedList<Quadrant> getChildrenInRange(int start, int end){
        LinkedList<Quadrant> stack = new LinkedList<>();
        for(int q = start; q<=end; q++){
            Quadrant toAdd = getQuadrantFrame(q);
            if(toAdd!=null){
                stack.add(toAdd);
            }
        }
        return stack;
    }

    public LinkedList<Quadrant> getChildren(){
        return getChildrenInRange(0,3);
    }

    public LinkedList<Quadrant> getChildrenBefore(int r, int c){
        return getChildrenInRange(0,getQuadrant(r,c)-1);
    }

    public LinkedList<Quadrant> getChildrenAfter(int r, int c){
        return getChildrenInRange(getQuadrant(r,c)+1,3);
    }

    public String toString(){
        return "("+yPos+", "+xPos+", "+height+", "+width+")";
    }
}