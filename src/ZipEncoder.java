import java.io.IOException;
import java.util.function.BiFunction;
import java.util.zip.*;

public class ZipEncoder<E> implements MatrixEncoder<E> {

    private MatrixEncoder<E> baseEncoder;
    private int totalSize;

    public ZipEncoder(MatrixEncoder<E> baseEncoder){
        this.baseEncoder = baseEncoder;
    }

    public String getName(){
        return "Zip";
    }

    public int refSize() {
        return 0;
    }

    public int dataSize() {
        return totalSize;
    }

    public int headerSize() {
        return 0;
    }

    public void setMatrix(E[][] m) {
        baseEncoder.setMatrix(m);
    }

    public void setEncoder(BiFunction<E, Integer, byte[]> e) {
        baseEncoder.setEncoder(e);
    }

    public void setDecoder(BiFunction<byte[], Integer, E> d) {
        baseEncoder.setDecoder(d);
    }

    public MemoryController encodeMatrix(MemoryController controller) {
        controller.clear();
        ZipOutputStream zos = new ZipOutputStream(controller.outputStream());
        try{
            zos.putNextEntry(new ZipEntry("data"));
            MemoryController nestedController = baseEncoder.encodeMatrix(new MemoryController());
            zos.write(nestedController.getBits(0,nestedController.size()));
            zos.close();
        }catch(IOException e){
            throw new RuntimeException("Error creating data entry");
        }
        controller.trim();
        totalSize = controller.size();
        return controller;
    }

    public Matrix<E> getMatrix(MemoryController controller, double cachePercent) {
        return new ZipMatrix<>(
                controller,
                baseEncoder
        );
    }

    public MemoryController unZip(MemoryController source, MemoryController dest){
        ZipInputStream zin = new ZipInputStream(source.inputStream());
        dest.clear();
        try{
            zin.getNextEntry();
            MemoryController.MemoryBitOutputStream outputStream = dest.outputStream();
            byte[] data = zin.readAllBytes();
            outputStream.writeBits(data.length*8,data);
            zin.close();
        }catch(IOException e){
            throw new RuntimeException("Decoding Error");
        }
        return dest;
    }

    public E[][] decodeMatrix(MemoryController controller) {
        return baseEncoder.decodeMatrix(unZip(controller,new MemoryController()));
    }

}
