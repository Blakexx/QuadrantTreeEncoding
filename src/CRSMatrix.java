import java.io.File;
import java.util.Iterator;
import java.util.function.BiFunction;

public class CRSMatrix<E> extends Matrix<E> {

    private final MemoryController encodedMatrix;
    public final BiFunction<E,Integer,byte[]> bitEncoder;
    public final BiFunction<byte[],Integer,E> bitDecoder;
    private final BiFunction<Integer,Integer,byte[]> intEncoder;
    private final BiFunction<byte[],Integer,Integer> intDecoder;
    private final StandardHeader<E> header;

    public CRSMatrix(MemoryController encodedMatrix, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        this.encodedMatrix = encodedMatrix;
        this.bitEncoder = bitEncoder;
        this.bitDecoder = bitDecoder;
        intEncoder = BitEncoders.intEncoder;
        intDecoder = BitEncoders.intDecoder;
        trim();
        header = new StandardHeader<>(encodedMatrix,bitDecoder);
    }

    public CRSMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder){
        this(new CRSEncoder<>(
                matrix,
                bitsPerData,
                bitEncoder,
                bitDecoder
        ).encodeMatrix(new MemoryController()),bitEncoder,bitDecoder);
    }

    public CRSMatrix(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> bitEncoder, BiFunction<byte[],Integer,E> bitDecoder, File source){
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

    private int getOffset(int row){
        int bitsPerSize = Main.logBaseCeil(height()*width()+1,2);
        return encodedMatrix.getBits(header.headerSize+bitsPerSize*(row),bitsPerSize,intDecoder);
    }

    public E get(int r, int c) {
        int bitsPerSize = Main.logBaseCeil(height()*width()+1,2);
        int start = getOffset(r);
        int toCheck = r==height()-1?width():getOffset(r+1)-start;
        int hasChecked = 0;
        int currentBit = header.headerSize+bitsPerSize*height() + start*(header.bitsPerWidth+header.bitsPerData);
        while(currentBit<encodedMatrix.size()&&hasChecked<toCheck){
            int col = encodedMatrix.getBits(currentBit,header.bitsPerWidth,intDecoder);
            currentBit+=header.bitsPerWidth;
            if(col==c){
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
        return CRSEncoder.decodeMatrix(encodedMatrix,bitDecoder);
    }

    public Iterator<DataPoint<E>> iterator(int r, int c, int h, int w, IteratorType type){
        Quadrant toIterate = new Quadrant(r,c,h,w);
        if(type==IteratorType.DEFAULT){
            return new RowIterator<>(this, toIterate);
        }
        return new GenericIterator<>(this, toIterate,type);
    }

    private static class RowIterator<V> implements Iterator<DataPoint<V>>{

        private final CRSMatrix<V> matrix;
        private int readCount, currentR, currentC, currentBit, rEndBit;
        private final int bitsPerSize;
        private final Quadrant readFrame;

        private RowIterator(CRSMatrix<V> matrix, Quadrant readFrame){
            this.matrix = matrix;
            this.readFrame = readFrame;
            bitsPerSize = Main.logBaseCeil(matrix.size()+1,2);
            currentR = readFrame.yPos;
            setUpRow(currentR);
        }

        private void setUpRow(int row){
            int rStart = matrix.getOffset(row);
            if(row==matrix.height()-1){
                rEndBit = matrix.encodedMatrix.size();
            }else{
                rEndBit = matrix.header.headerSize+bitsPerSize*matrix.height() + matrix.getOffset(row+1)*(matrix.header.bitsPerWidth+matrix.header.bitsPerData);
            }
            currentBit = matrix.header.headerSize+bitsPerSize*matrix.height() + rStart*(matrix.header.bitsPerWidth+matrix.header.bitsPerData);
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
            V data = matrix.header.defaultItem;
            if(currentBit>=rEndBit){
                readCount++;
                return new DataPoint<>(data,currentR,currentC++);
            }
            int bitsPerWidth = matrix.header.bitsPerWidth;
            int bitsPerData = matrix.header.bitsPerData;
            int actualC = encodedMatrix.getBits(currentBit,bitsPerWidth,matrix.intDecoder);
            while(currentC<readFrame.xPos){
                if(actualC<readFrame.xPos&&currentBit+(bitsPerData+bitsPerWidth)<rEndBit){
                    currentBit+=(bitsPerWidth+bitsPerData);
                    actualC = encodedMatrix.getBits(currentBit,bitsPerWidth,matrix.intDecoder);
                }
                currentC++;
            }
            readCount++;
            if(actualC==currentC){
                currentBit+=bitsPerWidth;
                data = encodedMatrix.getBits(currentBit,bitsPerData,matrix.bitDecoder);
                currentBit+=bitsPerData;
            }
            return new DataPoint<>(data,currentR,currentC++);
        }
    }
}
