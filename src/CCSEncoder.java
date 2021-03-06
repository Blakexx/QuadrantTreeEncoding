import java.util.HashMap;
import java.util.function.BiFunction;

public class CCSEncoder<E> implements MatrixEncoder<E> {

    private E[][] matrix;
    private int bitsPerData, refSize, dataSize, headerSize;
    private BiFunction<E,Integer,byte[]> encoder;
    private BiFunction<byte[],Integer,E> decoder;

    public CCSEncoder(E[][] matrix, int bitsPerData, BiFunction<E,Integer,byte[]> e, BiFunction<byte[],Integer,E> d){
        this.matrix = matrix;
        this.bitsPerData = bitsPerData;
        this.encoder = e;
        this.decoder = d;
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
        refSize = 0;
        dataSize = 0;
        headerSize = 0;
        int longestX = 0;
        HashMap<E,Integer> countMap = new HashMap<>();
        int maxCount = 0, itemCount = 0;
        E defaultItem = null;
        for(int r = 0; r<matrix.length;r++){
            longestX = Math.max(longestX,matrix[r].length);
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
        dataSize = (itemCount-maxCount)*bitsPerData;
        BiFunction<Integer,Integer,byte[]> intEncoder = BitEncoders.intEncoder;
        writer.writeBits(8,bitsPerData,intEncoder);
        writer.writeBits(bitsPerData,defaultItem,encoder);
        int height = matrix.length, width = longestX;
        int heightBits = Main.logBaseCeil(height+1,2);
        int widthBits = Main.logBaseCeil(width+1,2);
        writer.writeBits(5,heightBits-1,intEncoder);
        writer.writeBits(heightBits,height,intEncoder);
        writer.writeBits(5,widthBits-1,intEncoder);
        writer.writeBits(widthBits,width,intEncoder);
        headerSize = 8+bitsPerData+5+heightBits+5+widthBits;
        MemoryController dataController = new MemoryController();
        int sizeBits = Main.logBaseCeil(height*width+1,2);
        MemoryController.MemoryBitOutputStream dataWriter = dataController.outputStream();
        int totalWritten = 0;
        for(int c = 0; c<width;c++){
            writer.writeBits(sizeBits,totalWritten,intEncoder);
            for(int r = 0; r<height;r++){
                if(c >= matrix[r].length){
                    continue;
                }
                E item = matrix[r][c];
                if(item!=defaultItem){
                    dataWriter.writeBits(heightBits,r,intEncoder);
                    dataWriter.writeBits(bitsPerData,item,encoder);
                    totalWritten++;
                }
            }
        }
        writer.writeBits(dataController.size(),dataController.getBits(0,dataController.size()));
        refSize = controller.size()-dataSize-headerSize;
        controller.trim();
        return controller;
    }

    public Matrix<E> getMatrix(MemoryController controller, double cachePercent) {
        return new CCSMatrix<>(
                controller,
                encoder,
                decoder
        );
    }

    public E[][] decodeMatrix(MemoryController controller){
        return decodeMatrix(controller,decoder);
    }

    public static <V> V[][] decodeMatrix(MemoryController controller, BiFunction<byte[],Integer,V> decoder){
        MemoryController.MemoryBitInputStream input = controller.inputStream();
        BiFunction<byte[],Integer,Integer> intDecoder = BitEncoders.intDecoder;
        int bitsPerData = input.readBits(8,intDecoder);
        V defaultItem = input.readBits(bitsPerData,decoder);
        int heightBits = input.readBits(5,intDecoder)+1;
        int height = input.readBits(heightBits,intDecoder);
        int widthBits = input.readBits(5,intDecoder)+1;
        int width = input.readBits(widthBits,intDecoder);
        int sizeBits = Integer.toString(height*width,2).length();
        V[][] matrix = (V[][])new Object[height][width];
        int[] offsetRay = new int[height];
        for(int c = 0; c<width; c++){
            offsetRay[c] = input.readBits(sizeBits,intDecoder);
        }
        for(int c = 0; c<width; c++){
            int toRead = c==width-1?height:offsetRay[c+1]-offsetRay[c];
            int hasRead = 0;
            int lastRow = -1;
            while(input.hasNext()&&hasRead<toRead){
                int row = input.readBits(heightBits,intDecoder);
                V item = input.readBits(bitsPerData,decoder);
                for(int r = lastRow+1; r<row;r++){
                    matrix[r][c] = defaultItem;
                }
                matrix[row][c] = item;
                hasRead++;
                lastRow = row;
            }
            for(int r = lastRow+1; r<height; r++){
                matrix[r][c] = defaultItem;
            }
        }
        return matrix;
    }

    public String getName() {
        return "CCS";
    }

}
