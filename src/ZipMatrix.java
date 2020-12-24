import java.util.Iterator;

public class ZipMatrix<E> extends Matrix<E> {

    private final MemoryController encodedMatrix;
    public final ZipEncoder<E> zipEncoder;
    public final MatrixEncoder<E> baseEncoder;
    private int height, width;

    public ZipMatrix(MemoryController encodedMatrix, MatrixEncoder<E> baseEncoder){
        this.encodedMatrix = encodedMatrix;
        this.baseEncoder = baseEncoder;
        this.zipEncoder = new ZipEncoder<>(baseEncoder);
        Matrix<E> temp = nestedMatrix();
        height = temp.height();
        width = temp.width();
        trim();
    }

    private Matrix<E> nestedMatrix(){
        return baseEncoder.getMatrix(
                zipEncoder.unZip(encodedMatrix,new MemoryController()),
                0
        );
    }

    public int estimateBitSize() {
        return encodedMatrix.size();
    }

    public void trim() {
        encodedMatrix.trim();
    }

    public E get(int r, int c) {
        return nestedMatrix().get(r,c);
    }

    public E set(int r, int c, E value) {
        return null; // Not supported
    }

    public int height(){
        return height;
    }

    public int width(){
        return width;
    }

    public int size(){
        return height()*width();
    }

    public E[][] toRawMatrix(){
        return zipEncoder.decodeMatrix(encodedMatrix);
    }

    public E[] getRow(int r, Class<E> type) {
        return null;
    }

    public E[][] bulkGet(int r, int c, int height, int width, Class<E> type) {
        return null;
    }

    public Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type){
        return nestedMatrix().iterator(r,c,h,w,type);
    }
}
