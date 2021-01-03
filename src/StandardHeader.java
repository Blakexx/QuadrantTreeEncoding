import java.util.function.BiFunction;

public class StandardHeader<E>{

    public final int bitsPerData, bitsPerHeight, height, bitsPerWidth, width, headerSize;

    public final E defaultItem;

    public StandardHeader(MemoryController controller, BiFunction<byte[],Integer,E> bitDecoder){
        BiFunction<byte[],Integer,Integer> intDecoder = BitEncoders.intDecoder;
        bitsPerData = controller.getBits(0,8,intDecoder);
        defaultItem = controller.getBits(8,bitsPerData,bitDecoder);
        bitsPerHeight = controller.getBits(8+bitsPerData,5,intDecoder)+1;
        height = controller.getBits(8+bitsPerData+5,bitsPerHeight,intDecoder);
        bitsPerWidth = controller.getBits(8+bitsPerData+5+bitsPerHeight,5,intDecoder)+1;
        width = controller.getBits(8+bitsPerData+5+bitsPerHeight+5,bitsPerWidth,intDecoder);
        headerSize = 8+bitsPerData+5+bitsPerHeight+5+bitsPerWidth;
    }
}
