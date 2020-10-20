import java.util.function.BiFunction;
import java.io.*;

public class DiskController implements BitController{

    RandomAccessFile bytes;

    DiskController(File file) throws FileNotFoundException {
        bytes = new RandomAccessFile(file,"rdw");
    }

    DiskController(String path) throws FileNotFoundException {
        this(new File(path));
    }

    public void clear(){

    }

    public void delete(int start, int end) {

    }

    public int size() {
        return 0;
    }

    public Boolean getBit(int index) {
        return null;
    }

    public byte[] getBits(int index, int length) {
        return new byte[0];
    }

    public <E> E getBits(int index, int length, BiFunction<byte[], Integer, E> decoder) {
        return null;
    }

    public void setBit(int index, boolean bit) {

    }

    public void setBits(int index, int length, byte[] data) {

    }

    public <E> void setBits(int index, int length, E data, BiFunction<E, Integer, byte[]> encoder) {

    }

    public void trim() {

    }

    public FileBitInputStream inputStream() {
        return null;
    }

    public FileBitOutputStream outputStream() {
        return null;
    }

    public String bitToString(int start) {
        return null;
    }

    public String toStringWithCapacity() {
        return null;
    }
}
