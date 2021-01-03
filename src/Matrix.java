import java.util.Iterator;

public abstract class Matrix<E>{

    public abstract int estimateBitSize();
    public abstract void trim();

    public abstract E get(int r, int c);
    public abstract E set(int r, int c, E value);

    public abstract int height();
    public abstract int width();
    public int size(){
        return height() * width();
    }

    public abstract E[][] toRawMatrix();

    public Iterator<DataPoint<E>> iterator(){
        return iterator(IteratorType.DEFAULT);
    }

    public Iterator<DataPoint<E>> iterator(IteratorType type){
        return iterator(0,0,height(),width(),type);
    }

    public abstract Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type);

    public String toString(){
        Object[][] mat;
        try{
            mat = toRawMatrix();
        }catch(Exception e){
            return e.toString();
        }
        return Main.matrixToString(mat);
    }


}
