import java.io.File;
import java.util.Iterator;
import java.util.function.BiFunction;

public class DirectMatrix<E> extends Matrix<E> {

    private final MemoryController encodedMatrix;
    public final BiFunction<E,Integer,byte[]> bitEncoder;
    public final BiFunction<byte[],Integer,E> bitDecoder;
    private final BiFunction<Integer,Integer,byte[]> intEncoder;
    private final BiFunction<byte[],Integer,Integer> intDecoder;

    public DirectMatrix(MemoryController encodedMatrix, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        this.encodedMatrix = encodedMatrix;
        this.bitEncoder = bitEncoder;
        this.bitDecoder = bitDecoder;
        intEncoder = BitEncoders.intEncoder;
        intDecoder = BitEncoders.intDecoder;
        trim();
    }

    public DirectMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        this(new CRSEncoder<>(
                matrix,
                bitsPerData,
                bitEncoder,
                bitDecoder
        ).encodeMatrix(new MemoryController()),bitEncoder,bitDecoder);
    }

    public DirectMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, File source){
        this(new CRSEncoder<>(
                matrix,
                bitsPerData,
                bitEncoder,
                bitDecoder
        ).encodeMatrix(new MemoryController(source)),bitEncoder,bitDecoder);
    }

    public int estimateBitSize() {
        return encodedMatrix.size();
    }

    public void trim() {
        encodedMatrix.trim();
    }

    public E get(int r, int c) {
        int rawIndex = width() * r + c;
        int bpd = bitsPerData();
        rawIndex *= bpd;
        return encodedMatrix.getBits(
                headerSize()+rawIndex,
                bpd,
                bitDecoder
        );
    }

    public E set(int r, int c, E value) {
        return null; // Not supported
    }

    public int bitsPerData(){
        return encodedMatrix.getBits(0,8,intDecoder);
    }

    public E defaultItem(){
        return encodedMatrix.getBits(8,bitsPerData(),bitDecoder);
    }

    private int bitsPerHeight(){
        return encodedMatrix.getBits(8+bitsPerData(),5,intDecoder)+1;
    }

    public int height(){
        return encodedMatrix.getBits(8+bitsPerData()+5,bitsPerHeight(),intDecoder);
    }

    private int bitsPerWidth(){
        return encodedMatrix.getBits(8+bitsPerData()+5+bitsPerHeight(),5,intDecoder)+1;
    }

    public int width(){
        return encodedMatrix.getBits(8+bitsPerData()+5+bitsPerHeight()+5,bitsPerWidth(),intDecoder);
    }

    private int headerSize(){
        return 8+bitsPerData()+5+bitsPerHeight()+5+bitsPerWidth();
    }

    public int size(){
        return height()*width();
    }

    public E[][] toRawMatrix(){
        return DirectEncoder.decodeMatrix(encodedMatrix,bitDecoder);
    }

    public E[] getRow(int r, Class<E> type) {
        return null;
    }

    public E[][] bulkGet(int r, int c, int height, int width, Class<E> type) {
        return null;
    }

    public Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type){
        Quadrant toIterate = new Quadrant(r,c,h,w);
        return new DirectMatrix.DirectIterator<>(this, toIterate,type);
    }

    private static class DirectIterator<V> implements Iterator<DataPoint<V>>{

        private final DirectMatrix<V> matrix;
        private final Quadrant readFrame;
        private final IteratorType type;
        private int row, column;

        private DirectIterator(DirectMatrix<V> matrix, Quadrant readFrame, IteratorType type){
            this.matrix = matrix;
            this.readFrame = readFrame;
            this.type = type == IteratorType.DEFAULT? IteratorType.BY_ROW : type;
            row = readFrame.yPos;
            column = readFrame.xPos;
        }

        public boolean hasNext(){
            return readFrame.contains(row,column);
        }

        public DataPoint<V> next(){
            DataPoint<V> returned = new DataPoint<>(matrix.get(row,column),row,column);
            if(type == IteratorType.BY_ROW){
                if(++column == readFrame.xPos + readFrame.width){
                    row++;
                    column = readFrame.xPos;
                }
            }else if(type == IteratorType.BY_COL){
                if(++row == readFrame.yPos + readFrame.height){
                    column++;
                    row = readFrame.yPos;
                }
            }
            return returned;
        }
    }
}
