import java.io.File;
import java.util.Iterator;
import java.util.function.BiFunction;

public class CCSMatrix<E> extends Matrix<E> {

    private final MemoryController encodedMatrix;
    public final BiFunction<E,Integer,byte[]> bitEncoder;
    public final BiFunction<byte[],Integer,E> bitDecoder;
    private final BiFunction<Integer,Integer,byte[]> intEncoder;
    private final BiFunction<byte[],Integer,Integer> intDecoder;
    private final StandardHeader<E> header;

    public CCSMatrix(MemoryController encodedMatrix, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        this.encodedMatrix = encodedMatrix;
        this.bitEncoder = bitEncoder;
        this.bitDecoder = bitDecoder;
        intEncoder = BitEncoders.intEncoder;
        intDecoder = BitEncoders.intDecoder;
        trim();
        header = new StandardHeader<>(encodedMatrix,bitDecoder);
    }

    public CCSMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        this(new CCSEncoder<>(
                matrix,
                bitsPerData,
                bitEncoder,
                bitDecoder
        ).encodeMatrix(new MemoryController()),bitEncoder,bitDecoder);
    }

    public CCSMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, File source){
        this(new CCSEncoder<>(
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

    private int getOffset(int col){
        int bitsPerSize = Main.logBaseCeil(height()*width()+1,2);
        return encodedMatrix.getBits(header.headerSize+bitsPerSize*(col),bitsPerSize,intDecoder);
    }

    public E get(int r, int c) {
        int bitsPerSize = Main.logBaseCeil(height()*width()+1,2);
        int start = getOffset(c);
        int toCheck = c==width()-1?height():getOffset(c+1)-start;
        int hasChecked = 0;
        int currentBit = header.headerSize+bitsPerSize*width() + start*(header.bitsPerHeight+header.bitsPerData);
        while(currentBit<encodedMatrix.size()&&hasChecked<toCheck){
            int row = encodedMatrix.getBits(currentBit,header.bitsPerHeight,intDecoder);
            currentBit+=header.bitsPerWidth;
            if(row==r){
                return encodedMatrix.getBits(currentBit,header.bitsPerData,bitDecoder);
            }
            currentBit+=header.bitsPerData;
            hasChecked++;
        }
        return header.defaultItem;
    }

    public E set(int r, int c, E value) {
        return null; // Not supported
    }

    public int height(){
        return header.height;
    }

    public int width(){
        return header.width;
    }

    public E[][] toRawMatrix(){
        return CCSEncoder.decodeMatrix(encodedMatrix,bitDecoder);
    }

    public Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type){
        Quadrant toIterate = new Quadrant(r,c,h,w);
        if(type==IteratorType.DEFAULT){
            return new ColumnIterator<>(this, toIterate);
        }
        return new GenericIterator<>(this, toIterate,type);
    }

    private static class ColumnIterator<V> implements Iterator<DataPoint<V>>{

        private final CCSMatrix<V> matrix;
        private int readCount, currentR, currentC, currentBit, cEndBit;
        private final int bitsPerSize;
        private final Quadrant readFrame;

        private ColumnIterator(CCSMatrix<V> matrix, Quadrant readFrame){
            this.matrix = matrix;
            this.readFrame = readFrame;
            bitsPerSize = Main.logBaseCeil(matrix.size()+1,2);
            currentC = readFrame.xPos;
            setUpCol(currentR);
        }

        private void setUpCol(int col){
            int rStart = matrix.getOffset(col);
            if(col==matrix.width()-1){
                cEndBit = matrix.encodedMatrix.size();
            }else{
                cEndBit = matrix.header.headerSize+bitsPerSize*matrix.width() + matrix.getOffset(col+1)*(matrix.header.bitsPerHeight+matrix.header.bitsPerData);
            }
            currentBit = matrix.header.headerSize+bitsPerSize*matrix.width() + rStart*(matrix.header.bitsPerHeight+matrix.header.bitsPerData);
        }

        public boolean hasNext(){
            return readCount<readFrame.size();
        }

        public DataPoint<V> next(){
            MemoryController encodedMatrix = matrix.encodedMatrix;
            if(currentR >= readFrame.yPos+readFrame.height){
                currentR = 0;
                currentC++;
                setUpCol(currentC);
            }
            V data = matrix.header.defaultItem;
            if(currentBit >= cEndBit){
                readCount++;
                return new DataPoint<>(data,currentR++,currentC);
            }
            int bitsPerHeight = matrix.header.bitsPerHeight;
            int bitsPerData = matrix.header.bitsPerData;
            int actualR = encodedMatrix.getBits(currentBit,bitsPerHeight,matrix.intDecoder);
            while(currentR < readFrame.yPos){
                if(actualR<readFrame.yPos && currentBit+(bitsPerData+bitsPerHeight) < cEndBit){
                    currentBit+=(bitsPerHeight+bitsPerData);
                    actualR = encodedMatrix.getBits(currentBit,bitsPerHeight,matrix.intDecoder);
                }
                currentR++;
            }
            readCount++;
            if(actualR == currentR){
                currentBit += bitsPerHeight;
                data = encodedMatrix.getBits(currentBit,bitsPerData,matrix.bitDecoder);
                currentBit += bitsPerData;
            }
            return new DataPoint<>(data,currentR++,currentC);
        }
    }
}
