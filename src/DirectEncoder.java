import java.util.HashMap;
import java.util.function.BiFunction;

public class DirectEncoder<E> implements MatrixEncoder<E> {

    private E[][] matrix;
    private int bitsPerData, refSize, dataSize, headerSize;
    private BiFunction<E,Integer,byte[]> encoder;
    private BiFunction<byte[],Integer,E> decoder;

    public DirectEncoder(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> e, BiFunction<byte[],Integer,E> d){
        this.matrix = matrix;
        this.bitsPerData = bitsPerData;
        this.encoder = e;
        this.decoder = d;
    }

    public String getName() {
        return "Direct";
    }

    public int refSize() {
        return refSize;
    }

    public int dataSize() {
        return dataSize;
    }

    public int headerSize() {
        return headerSize;
    }

    public void setMatrix(E[][] m) {
        matrix = m;
    }

    public void setEncoder(BiFunction<E, Integer, byte[]> e) {
        encoder = e;
    }

    public void setDecoder(BiFunction<byte[], Integer, E> d) {
        decoder = d;
    }

    public MemoryController encodeMatrix(MemoryController controller) {
        controller.clear();
        MemoryController.MemoryBitOutputStream writer = controller.outputStream();
        int height = matrix.length, width = 0;
        HashMap<E,Integer> countMap = new HashMap<>();
        int maxCount = 0, itemCount = 0;
        E defaultItem = null;
        for(int r = 0; r<matrix.length;r++){
            width = Math.max(width,matrix[r].length);
            itemCount+=matrix[r].length;
            for(int c = 0; c<matrix[r].length;c++){
                E item = matrix[r][c];
                Integer val = countMap.get(matrix[r][c]);
                if(val==null){
                    val = 0;
                }
                if(++val > maxCount){
                    maxCount = val;
                    defaultItem = item;
                }
                countMap.put(matrix[r][c], val);
            }
        }
        dataSize = bitsPerData * height * width;
        BiFunction<Integer,Integer,byte[]> intEncoder = BitEncoders.intEncoder;
        writer.writeBits(8,bitsPerData,intEncoder);
        writer.writeBits(bitsPerData,defaultItem,encoder);
        int heightBits = Main.logBaseCeil(height+1,2);
        int widthBits = Main.logBaseCeil(width+1,2);
        writer.writeBits(5,heightBits-1,intEncoder);
        writer.writeBits(heightBits,height,intEncoder);
        writer.writeBits(5,widthBits-1,intEncoder);
        writer.writeBits(widthBits,width,intEncoder);
        headerSize = 8+bitsPerData+5+heightBits+5+widthBits;
        for(int r = 0; r<matrix.length;r++){
            for(int c = 0; c<width;c++){
                E item = c >= matrix[r].length ? defaultItem : matrix[r][c];
                writer.writeBits(bitsPerData,item,encoder);
            }
        }
        controller.trim();
        return controller;
    }

    public Matrix<E> getMatrix(MemoryController controller, double cachePercent) {
        return new DirectMatrix<>(
                controller,
                encoder,
                decoder
        );
    }

    public Matrix<E> getMatrix(MemoryController controller) {
        return new DirectMatrix<>(
                controller,
                encoder,
                decoder
        );
    }

    public E[][] decodeMatrix(MemoryController controller){
        return decodeMatrix(controller,decoder);
    }

    public static <V> V[][] decodeMatrix(MemoryController controller, BiFunction<byte[],Integer,V> decoder) {
        MemoryController.MemoryBitInputStream input = controller.inputStream();
        BiFunction<byte[],Integer,Integer> intDecoder = BitEncoders.intDecoder;
        int bitsPerData = input.readBits(8,intDecoder);
        input.readBits(bitsPerData);
        int heightBits = input.readBits(5,intDecoder)+1;
        int height = input.readBits(heightBits,intDecoder);
        int widthBits = input.readBits(5,intDecoder)+1;
        int width = input.readBits(widthBits,intDecoder);
        V[][] matrix = (V[][])new Object[height][width];
        for(int r = 0; r<height; r++){
            for(int c = 0; c<width; c++){
                matrix[r][c] = input.readBits(bitsPerData,decoder);
            }
        }
        return matrix;
    }

}
