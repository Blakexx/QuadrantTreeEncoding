import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.BiFunction;

public class ImprovedMatrixEncoder<E> implements MatrixEncoder<E> {

  private E[][] matrix;
  private BiFunction<E,Integer,byte[]> encoder;
  private BiFunction<byte[],Integer,E> decoder;
  private E defaultItem;
  private MemoryController controller;
  private MemoryController.MemoryBitOutputStream writer;
  private int refSize, dataSize, headerSize;
  private int bitsPerData, longestX, lastData;

  public ImprovedMatrixEncoder(E[][] m, int bitsPerData, BiFunction<E,Integer,byte[]> e, BiFunction<byte[],Integer,E> d){
    matrix = m;
    encoder = e;
    decoder = d;
    this.bitsPerData = bitsPerData;
  }

  public void setMatrix(E[][] m){
    matrix = m;
  }

  public void setEncoder(BiFunction<E,Integer,byte[]> e){
    encoder = e;
  }

  public void setDecoder(BiFunction<byte[],Integer,E> d){
    decoder = d;
  }

  public void printAnalytics(){
    System.out.println("Header size: "+headerSize+" bits");
    System.out.println("Data size: "+dataSize+" bits");
    System.out.println("Ref size: "+refSize+" bits");
  }

  public int refSize(){
    return refSize;
  }

  public void encodeMatrix(String path) throws IOException{
    encodeMatrix();
    byte[] bytes = controller.getBits(0,controller.size());
    FileBitOutputStream fileWriter = new FileBitOutputStream(path,false);
    fileWriter.writeBits(controller.size(),bytes);
  }

  public MemoryController encodeMatrix(){
    controller = new MemoryController();
    writer = controller.outputStream();
    refSize = 0;
    dataSize = 0;
    headerSize = 0;
    longestX = 0;
    HashMap<E,Integer> countMap = new HashMap<>();
    for(int r = 0; r<matrix.length;r++){
      longestX = Math.max(longestX,matrix[r].length);
      for(int c = 0; c<matrix[r].length;c++){
        Integer val = countMap.get(matrix[r][c]);
        if(val==null){
          val = 0;
        }
        countMap.put(matrix[r][c], val+1);
      }
    }
    int maxCount = 0;
    defaultItem = null;
    for(E key : countMap.keySet()){
      int count = countMap.get(key);
      if(count>maxCount){
        defaultItem = key;
        maxCount = count;
      }
    }
    for(int r = 0; r<matrix.length;r++){
      for(int c = 0; c<matrix[r].length;c++){
        dataSize+=!defaultItem.equals(matrix[r][c])?bitsPerData:0;
      }
    }
    BiFunction<Integer,Integer,byte[]> intEncoder = BitEncoders.intEncoder;
    writer.writeBits(8,bitsPerData,intEncoder);
    writer.writeBits(bitsPerData,defaultItem,encoder);
    int height = matrix.length, width = longestX;
    int heightBits = Integer.toString(height,2).length();
    int widthBits = Integer.toString(width,2).length();
    writer.writeBits(5,heightBits-1,intEncoder);
    writer.writeBits(heightBits,height,intEncoder);
    writer.writeBits(5,widthBits-1,intEncoder);
    writer.writeBits(widthBits,width,intEncoder);
    headerSize=8+bitsPerData+5+heightBits+5+widthBits;
    if((longestX>1||matrix.length>1)&&dataSize>0){
      writer.writeBit(true);
      lastData = controller.size();
      encodeHelper(0, 0, matrix.length, longestX);
      //controller.delete(lastData,controller.size());
    }else{
      writer.writeBit(false);
    }
    refSize = controller.size()-dataSize-headerSize;
    controller.trim();
    return controller;
  }

  private boolean encodeHelper(int yOffset, int xOffset, int yLen, int xLen){
    if(yOffset>=matrix.length){
      return false;
    }
    if(yLen<=1&&xLen<=1){
      if(xOffset>=matrix[yOffset].length){
        if(xOffset<longestX){
          writer.writeBit(false);
        }
        return false;
      }
      E item = matrix[yOffset][xOffset];
      if(item.equals(defaultItem)){
        writer.writeBit(false);
        return false;
      }
      writer.writeBit(true);
      writer.writeBits(bitsPerData,item,encoder);
      lastData = controller.size();
      return true;
    }else{
      int nyLen = yLen/2, yDif = yLen-nyLen;
      int nxLen = xLen/2, xDif = xLen-nxLen;
      int newY = yOffset+nyLen, newX = xOffset+nxLen;
      boolean foundData = doPathSetup(yOffset, xOffset, nyLen, nxLen,0);
      if(xLen>1){
        foundData|=doPathSetup(yOffset, newX, nyLen, xDif,1);
      }
      if(yLen>1){
        foundData|=doPathSetup(newY, xOffset, yDif, nxLen,2);
      }
      if(yLen>1&&xLen>1){
        foundData|=doPathSetup(newY, newX, yDif, xDif,3);
      }
      return foundData;
    }
  }

  private boolean doPathSetup(int yOffset, int xOffset, int yLen, int xLen, int quad){
    int prevLength = controller.size();
    if(yLen>1||xLen>1){
      writer.writeBit(true);
    }
    boolean gotData = encodeHelper(yOffset, xOffset, yLen, xLen);
    if((yLen>1||xLen>1)&&!gotData){
      controller.delete(prevLength,controller.size());
      writer.writeBit(false);
    }
    return gotData;
  }

  public E[][] decodeMatrix(BitReader input) throws IOException{
    return decodeMatrix(input,decoder);
  }

  public static <V> V[][] decodeMatrix(BitReader input, BiFunction<byte[],Integer,V> decoder) throws IOException{
    BiFunction<byte[],Integer,Integer> intDecoder = BitEncoders.intDecoder;
    int bitsPerData = input.readBits(8,intDecoder);
    V defaultItem = input.readBits(bitsPerData,decoder);
    ArrayList<StackFrame> stack = new ArrayList<>();
    int heightBits = input.readBits(5,intDecoder)+1;
    int height = input.readBits(heightBits,intDecoder);
    int widthBits = input.readBits(5,intDecoder)+1;
    int width = input.readBits(widthBits,intDecoder);
    stack.add(new StackFrame(0,0,height,width));
    V[][] matrix = (V[][])new Object[height][width];
    while(stack.size()>0&&input.hasNext()){
      boolean nextInst = input.readBit();
      int lastIndex = stack.size()-1;
      StackFrame current = stack.get(lastIndex);
      boolean readMode = current.xLen<=1&&current.yLen<=1;;
      if(nextInst){
        if(readMode){
          V data = input.readBits(bitsPerData,decoder);
          matrix[current.yOffset][current.xOffset] = data;
          stack.remove(lastIndex);
        }else{
          StackFrame.pushFrame(stack);
        }
      }else{
        stack.remove(lastIndex);
      }
    }
    for(int r = 0; r<height;r++){
      for(int c = 0; c<width;c++){
        if(matrix[r][c]==null){
          matrix[r][c] = defaultItem;
        }
      }
    }
    return matrix;
  }
}