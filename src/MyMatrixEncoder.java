/*
import java.io.IOException;
import java.util.*;
import java.util.function.*;
import java.lang.reflect.*;

public class MyMatrixEncoder<E> implements MatrixEncoder<E>{

    private E[][] matrix;
    private FileBitOutputStream writer;
    private Function<E,String> encoder;
    private Function<String,E> decoder;
    private E defaultItem;
    private StringBuilder writtenPath;
    private int refSize, dataSize, headerSize;
    private int bitsPerData, longestX;

    public MyMatrixEncoder(E[][] m, Function<E,String> e, Function<String,E> d){
        matrix = m;
        encoder = e;
        decoder = d;
    }

    public void setMatrix(E[][] m){
        matrix = m;
    }

    public void setEncoder(Function<E,String> e){
        encoder = e;
    }

    public void setDecoder(Function<String,E> d){
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

    //Proof of concept, its kinda cringe
    public void encodeMatrix(String path) throws IOException{
        refSize = 0;
        dataSize = 0;
        headerSize = 0;
        longestX = 0;
        FileBitOutputStream writer = new FileBitOutputStream(path,false);
        HashMap<E,Integer> countMap = new HashMap<E,Integer>();
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
        bitsPerData = encoder.apply(defaultItem).length();
        for(int r = 0; r<matrix.length;r++){
            for(int c = 0; c<matrix[r].length;c++){
                bitsPerData = Math.max(bitsPerData,encoder.apply(matrix[r][c]).length());
                dataSize+=!defaultItem.equals(matrix[r][c])?1:0;
            }
        }
        dataSize*=bitsPerData;
        writer.writeBits(String.format("%8s", Integer.toBinaryString(bitsPerData)).replace(" ", "0"));
        writer.writeBits(String.format("%"+bitsPerData+"s",encoder.apply(defaultItem)).replaceAll(" ","0"));
        int height = matrix.length, width = longestX;
        int heightBits = Integer.toString(height,2).length();
        int widthBits = Integer.toString(width,2).length();
        writer.writeBits(String.format("%5s",Integer.toString(heightBits-1,2)).replace(" ", "0"));
        writer.writeBits(Integer.toString(height,2));
        writer.writeBits(String.format("%5s",Integer.toString(widthBits-1,2)).replace(" ", "0"));
        writer.writeBits(Integer.toString(width,2));
        headerSize=8+bitsPerData+5+heightBits+5+widthBits;
        writtenPath = new StringBuilder();
        if(longestX>1||matrix.length>1){
            writtenPath.append("u");
        }
        encodeHelper(0, 0, matrix.length, longestX);
        for(int i = writtenPath.length()-1;i>-2;i--){
            if(i==-1){
                writtenPath.delete(0,writtenPath.length());
            }else if(writtenPath.charAt(i)=='1'||writtenPath.charAt(i)=='0'){
                writtenPath.delete(i+1,writtenPath.length());
                break;
            }
        }
        refSize = writtenPath.length()-dataSize;
        String data = writtenPath.toString();
        data = data.replaceAll("[on]","0").replaceAll("[ud]","1").replaceAll(" ","");
        writer.writeBits(data);
        writer.flush();
        writer.close();
    }

    //Code so spaghetti it needs meatballs
    private Object[][] encodeHelper(int yPos, int xPos, int height, int width) throws IOException{
        Boolean[] foundData = new Boolean[5];
        for(int i = 0; i<foundData.length;i++){
            foundData[i] = false;
        }
        Integer[] indexData = new Integer[5];
        for(int i = 0; i<indexData.length;i++){
            indexData[i] = 0;
        }
        if(yPos>=matrix.length){
            Object[][] ret = new Object[2][];
            ret[0] = foundData;
            ret[1] = indexData;
            return ret;
        }
        if(height<=1&&width<=1){
            if(xPos>=matrix[yPos].length){
                Object[][] ret = new Object[2][];
                ret[0] = foundData;
                ret[1] = indexData;
                if(xPos<longestX){
                    int len = writtenPath.length();
                    //writtenPath.delete(len-1,len);
                    writtenPath.append("nd");
                }
                return ret;
            }
            E item = matrix[yPos][xPos];
            if(item.equals(defaultItem)){
                int len = writtenPath.length();
                //writtenPath.delete(len-1,len);
                writtenPath.append("nd");
                Object[][] ret = new Object[2][];
                ret[0] = foundData;
                ret[1] = indexData;
                return ret;
            }
            writtenPath.append("u");
            writtenPath.append(String.format("%"+bitsPerData+"s",encoder.apply(item)).replaceAll(" ","0"));
            foundData[4] = true;
            Object[][] ret = new Object[2][];
            ret[0] = foundData;
            ret[1] = indexData;
            return ret;
        }else{
            int nHeight = height/2, hDif = height-nHeight;
            int nWidth = width/2, wDif = width-nWidth;
            int newY = yPos+nHeight, newX = xPos+nWidth;
            indexData[0] = writtenPath.length();
            foundData[0] = doPathSetup(yPos, xPos, nHeight, nWidth,0);
            indexData[1] = writtenPath.length();
            if(width>1){
                foundData[1] = doPathSetup(yPos, newX, nHeight, wDif,1);
            }
            indexData[2] = writtenPath.length();
            if(height>1){
                foundData[2] = doPathSetup(newY, xPos, hDif, nWidth,2);
            }
            indexData[3] = writtenPath.length();
            if(height>1&&width>1){
                foundData[3] = doPathSetup(newY, newX, hDif, wDif,3);
            }
            indexData[4] = writtenPath.length();
            foundData[4] = foundData[0]||foundData[1]||foundData[2]||foundData[3];
            Object[][] ret = new Object[2][];
            ret[0] = foundData;
            ret[1] = indexData;
            return ret;
        }
    }

    private boolean doPathSetup(int yPos, int xPos, int height, int width, int quad) throws IOException{
        int prevLength = writtenPath.length();
        boolean readMode = height/2.0<=1&&width/2.0<=1;
        if(height>1||width>1){
            writtenPath.append("u");
        }
        Object[][] data = encodeHelper(yPos, xPos, height, width);
        Boolean[] foundData = Arrays.copyOf(data[0],data[0].length,Boolean[].class);
        Integer[] indexData = Arrays.copyOf(data[1],data[1].length,Integer[].class);
        boolean gotData = foundData[4];
        if((height>1||width>1)){
            int lastItem;
            for(lastItem = 3; lastItem>-1;lastItem--){
                if(!indexData[lastItem].equals(indexData[lastItem+1])){
                    break;
                }
            }
            if(gotData&&!foundData[lastItem]){
                for(int i = lastItem-1; i>-1;i--){
                    if(foundData[i]){
                        writtenPath.delete(indexData[i+1],indexData[4]);
                        writtenPath.append("o");
                        readMode|= height==3&&width==2&&i+1==1;
                        readMode|= height==2&&width==3&&i+1==2;
                        if(readMode){
                            writtenPath.append("o");
                        }
                        break;
                    }
                }
            }else if(!gotData){
                writtenPath.delete(prevLength,writtenPath.length());
                writtenPath.append("uo");
                if(height/2<=1&&width/2<=1){
                    writtenPath.append("o");
                }
            }
        }
        return gotData;
    }

    public E[][] decodeMatrix(String path) throws IOException{
        StringBuilder decodedData = new StringBuilder();
        FileBitInputStream input = new FileBitInputStream(path);
        int bitsPerData = Integer.parseInt(input.readBits(8),2);
        E defaultItem = decoder.apply(input.readBits(bitsPerData));
        ArrayList<StackFrame> stack = new ArrayList<>();
        int heightBits = Integer.parseInt(input.readBits(5),2)+1;
        int height = Integer.parseInt(input.readBits(heightBits),2);
        int widthBits = Integer.parseInt(input.readBits(5),2)+1;
        int width = Integer.parseInt(input.readBits(widthBits),2);
        stack.add(new StackFrame(0,0,height,width,0,0,null));
        E[][] matrix = (E[][])new Object[height][width];
        while(stack.size()>0&&input.hasNext()){
            boolean nextInst = input.readBit();
            int lastIndex = stack.size()-1;
            StackFrame current = stack.get(lastIndex);
            boolean currentReadMode = current.width<=1&&current.height<=1;
            boolean readMode = currentReadMode;
            for(int i = lastIndex;readMode&&i>=current.frameEnd;i--){
                readMode = readMode && stack.get(i).height<=1&&stack.get(i).width<=1;
            }
            if(nextInst){
                decodedData.append("u");
                if(readMode||currentReadMode){
                    String dataStr = input.readBits(bitsPerData);
                    E data = decoder.apply(dataStr);
                    decodedData.append(dataStr);
                    matrix[current.yPos][current.xPos] = data;
                    stack.remove(lastIndex);
                }else{
                    pushFrame(stack);
                }
            }else{
                StackFrame parent = current.parent;
                boolean override = false;
                if(parent!=null){
                    override = current.quadrant==0 && parent.width/2<=1&&parent.height/2<=1;
                    override = override || (parent.width==3&&parent.height==2&&current.quadrant==2);
                    override = override || (parent.width==2&&parent.height==3&&current.quadrant==1);
                }
                if(currentReadMode||override){
                    if(!input.hasNext()){
                        break;
                    }
                    boolean nextPart = input.readBit();
                    if(!nextPart){
                        decodedData.append("oo");
                        popOut(stack);
                    }else{
                        decodedData.append("nd");
                        stack.remove(lastIndex);
                    }
                }else{
                    decodedData.append("o");
                    popOut(stack);
                }
            }
        }
        for(int r = 0; r<height;r++){
            for(int c = 0; c<width;c++){
                if(matrix[r][c]==null){
                    matrix[r][c] = defaultItem;
                }
            }
        }
        input.close();
        return matrix;
    }

    private static void pushFrame(ArrayList<StackFrame> stack){
        int lastIndex = stack.size()-1;
        StackFrame current = stack.get(lastIndex);
        int height = current.height, width = current.width;
        int yPos = current.yPos, xPos = current.xPos;
        int nHeight = height/2, hDif = height-nHeight;
        int nWidth = width/2, wDif = width-nWidth;
        int newY = yPos+nHeight, newX = xPos+nWidth;
        if(height>1&&width>1){
            stack.add(new StackFrame(newY, newX, hDif, wDif,3,lastIndex,current));
        }
        if(height>1){
            stack.add(new StackFrame(newY, xPos, hDif, nWidth,2,lastIndex,current));
        }
        if(width>1){
            stack.add(new StackFrame(yPos, newX, nHeight, wDif,1,lastIndex,current));
        }
        stack.add(new StackFrame(yPos, xPos, nHeight, nWidth,0,lastIndex,current));
        stack.remove(lastIndex);
    }

    private static void popOut(ArrayList<StackFrame> stack){
        int parentIndex = stack.get(stack.size()-1).frameEnd;
        while(stack.size()>parentIndex){
            stack.remove(stack.size()-1);
        }
    }

    private static class StackFrame{
        int height, width, xPos, yPos, quadrant, frameEnd;
        StackFrame parent;
        private StackFrame(int yo, int xo, int yl, int xl, int q, int fr, StackFrame p){
            height = yl;
            width = xl;
            xPos = xo;
            yPos = yo;
            quadrant = q;
            frameEnd = fr;
            parent = p;
        }

        public String toString(){
            return "("+quadrant+", "+height+", "+width+", "+yPos+", "+xPos+", "+frameEnd+")";
        }
    }
}
*/