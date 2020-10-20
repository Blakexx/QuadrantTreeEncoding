import java.io.IOException;
import java.util.Iterator;
import java.util.function.BiFunction;

public class CRSMatrix<E> implements Matrix<E> {

    private final MemoryController encodedMatrix;
    public final BiFunction<E,Integer,byte[]> bitEncoder;
    public final BiFunction<byte[],Integer,E> bitDecoder;
    private final BiFunction<Integer,Integer,byte[]> intEncoder;
    private final BiFunction<byte[],Integer,Integer> intDecoder;

    public CRSMatrix(MemoryController encodedMatrix, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        this.encodedMatrix = encodedMatrix;
        this.bitEncoder = bitEncoder;
        this.bitDecoder = bitDecoder;
        intEncoder = BitEncoders.intEncoder;
        intDecoder = BitEncoders.intDecoder;
        trim();
    }

    public CRSMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        this(new CRSEncoder<>(
                matrix,
                bitsPerData,
                bitEncoder,
                bitDecoder
        ).encodeMatrix(),bitEncoder,bitDecoder);
    }

    public int estimateBitSize() {
        return encodedMatrix.size();
    }

    public void trim() {
        encodedMatrix.trim();
    }

    private int getOffset(int row){
        int bitsPerSize = Main.logBaseCeil(height()*width()+1,2);
        return encodedMatrix.getBits(headerSize()+bitsPerSize*(row),bitsPerSize,intDecoder);
    }

    public E get(int r, int c) {
        int bitsPerSize = Main.logBaseCeil(height()*width()+1,2);
        int start = getOffset(r);
        int toCheck = r==height()-1?width():getOffset(r+1)-start;
        int hasChecked = 0;
        int currentBit = headerSize()+bitsPerSize*height() + start*(bitsPerWidth()+bitsPerData());
        while(currentBit<encodedMatrix.size()&&hasChecked<toCheck){
            int col = encodedMatrix.getBits(currentBit,bitsPerWidth(),intDecoder);
            currentBit+=bitsPerWidth();
            if(col==c){
                return encodedMatrix.getBits(currentBit,bitsPerData(),bitDecoder);
            }
            currentBit+=bitsPerData();
            hasChecked++;
        }
        return defaultItem();
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

    public E[][] toRawMatrix() throws IOException {
        return CRSEncoder.decodeMatrix(encodedMatrix.inputStream(),bitDecoder);
    }

    public E[] getRow(int r, Class<E> type) {
        return null;
    }

    public E[][] bulkGet(int r, int c, int height, int width, Class<E> type) {
        return null;
    }

    public Iterator<DataPoint<E>> iterator(){
        return iterator(IteratorType.DEFAULT);
    }

    public Iterator<DataPoint<E>> iterator(IteratorType type){
        return iterator(0,0,height(),width(),type);
    }

    public Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type){
        StackFrame toIterate = new StackFrame(r,c,h,w);
        if(type==IteratorType.DEFAULT){
            return new RowIterator<>(this, toIterate);
        }
        return new GenericIterator<>(this, toIterate,type);
    }

    private static class RowIterator<V> implements Iterator<DataPoint<V>>{

        private final CRSMatrix<V> matrix;
        private int readCount, currentR, currentC, currentBit, rStart, rEndBit;
        private final int bitsPerSize;
        private final StackFrame readFrame;

        private RowIterator(CRSMatrix<V> matrix, StackFrame readFrame){
            this.matrix = matrix;
            this.readFrame = readFrame;
            bitsPerSize = Main.logBaseCeil(matrix.size()+1,2);
            currentR = readFrame.yPos;
            setUpRow(currentR);
        }

        private void setUpRow(int row){
            rStart = matrix.getOffset(row);
            if(row==matrix.height()-1){
                rEndBit = matrix.encodedMatrix.size();
            }else{
                rEndBit = matrix.headerSize()+bitsPerSize*matrix.height() + matrix.getOffset(row+1)*(matrix.bitsPerWidth()+matrix.bitsPerData());
            }
            currentBit = matrix.headerSize()+bitsPerSize*matrix.height() + rStart*(matrix.bitsPerWidth()+matrix.bitsPerData());
        }

        public boolean hasNext(){
            return readCount<readFrame.size();
        }

        public DataPoint<V> next(){
            MemoryController encodedMatrix = matrix.encodedMatrix;
            if(currentC>=readFrame.xPos+readFrame.width){
                currentC = 0;
                currentR++;
                setUpRow(currentR);
            }
            V data = matrix.defaultItem();
            if(currentBit>=rEndBit){
                readCount++;
                return new DataPoint<>(data,currentR,currentC++);
            }
            int actualC = encodedMatrix.getBits(currentBit,matrix.bitsPerWidth(),matrix.intDecoder);
            while(currentC<readFrame.xPos){
                if(actualC<readFrame.xPos&&currentBit<rEndBit){
                    currentBit+=(matrix.bitsPerWidth()+matrix.bitsPerData());
                    actualC = encodedMatrix.getBits(currentBit,matrix.bitsPerWidth(),matrix.intDecoder);
                }
                currentC++;
            }
            readCount++;
            if(actualC==currentC){
                currentBit+=matrix.bitsPerWidth();
                data = encodedMatrix.getBits(currentBit, matrix.bitsPerData(),matrix.bitDecoder);
                currentBit+= matrix.bitsPerData();
            }
            return new DataPoint<>(data,currentR,currentC++);
        }
    }
}
