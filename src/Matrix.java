import java.io.IOException;
import java.util.Iterator;

public interface Matrix<E>{

    int estimateBitSize();
    void trim();

    E get(int r, int c);
    E set(int r, int c, E value);

    int height();
    int width();
    int size();

    E[][] toRawMatrix() throws IOException;

    E[] getRow(int r, Class<E> type);
    E[][] bulkGet(int r, int c, int height, int width, Class<E> type);

    Iterator<DataPoint<E>> iterator();

    Iterator<DataPoint<E>> iterator(IteratorType type);

    Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type);


}
