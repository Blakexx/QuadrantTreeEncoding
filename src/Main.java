import javax.xml.crypto.Data;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.lang.reflect.Array;

class Main {
    public static void main(String[] args) throws Throwable{
        BiFunction<Byte, Integer, byte[]> enc = (num, bitsPerData)->new byte[]{(byte)(num<<(8-bitsPerData))};
        BiFunction<byte[], Integer, Byte> dec = (bytes, bitsPerData)->(byte)(bytes[0]>>>(8-bitsPerData));
        MatrixEncoder<Byte> encoder;
        encoder = new QuadrantTreeEncoder<>(
                null,
                8,
                enc,
                dec
        );
        Scanner in = new Scanner(System.in);
        System.out.println("1: Custom Tester");
        System.out.println("2: Disk Size Tester");
        System.out.println("3: Read/Write Tester");
        System.out.println("4: Multiplication Tester");
        int type = readInt("Test Type",in,(i)->i>=1&&i<=4);
        System.out.println();
        if(type==1){
            MemoryController controller = customTester(encoder);
            System.out.println();
            double cachePercent = readDouble("Cache %", in, (d)->d>=0&&d<=1);
            CachedTreeMatrix<Byte> matrix = new CachedTreeMatrix<>(
                    controller,
                    (b,bpd)->new byte[]{b},
                    (b,bpd)->b[0],
                    cachePercent
            );
            runGetTests(matrix,true);
            runSetTests(matrix,true);
            do{
                System.out.println("1: Get");
                System.out.println("2: Set");
                type = readInt("Operation",in,(i)->i==1||i==2);
                int row = readInt("Row",in,(i)->i>-1&&i<matrix.height());
                int column = readInt("Col",in,(i)->i>-1&&i<matrix.width());
                if(type==1){
                    System.out.println("Get: "+matrix.get(row,column)+"\n");
                }else{
                    int toSet = readInt("Data",in,(i)->true);
                    matrix.set(row,column,(byte)toSet);
                    System.out.println("Set: "+matrix.get(row,column)+"\n");
                }
            }while(true);
        }else if(type==2){
            System.out.println("1: Default Encoding");
            System.out.println("2: Hybrid Encoding");
            type = readInt("Encoding Type",in,(i)->i==1||i==2);
            System.out.println();
            encoder = type==2?new HybridMatrixEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            ):encoder;
            matrixTester(encoder);
        }else if(type==3){
            double fullness = readDouble("Fullness", in, (i)->i>=0&&i<=1);
            System.out.println();
            double cachePercent = readDouble("Cache %",in,(i)->i>=0&&i<=1);
            readWriteTester(fullness, cachePercent);
        }else{
            double full1 = readDouble("First Matrix Fullness",in,(i)->i>=0&&i<=1);
            System.out.println();
            double full2 = readDouble("Second Matrix Fullness",in,(i)->i>=0&&i<=1);
            multiplyTester(full1,full2);
        }
    }

    public static void multiplyTester(double full1, double full2) throws IOException{
        StringBuilder data = new StringBuilder("Rows Columns FirstFull SecondFull QTE_Multiply Default_Multiply");
        System.out.println(data);
        Random ran = new Random();
        final double scale = Math.pow(10,9);
        final int runsPerSize = 1;
        for(int d = 16; d<=1024; d*=2){
            double avgTreeTime = 0, avgRawTime = 0;
            for(int k = 0; k<runsPerSize;k++){
                Integer[][] firstRaw = generateMatrix(d,d,full1,(r,c)->ran.nextInt(),0,Integer.class);
                CachedTreeMatrix<Integer> first = new CachedTreeMatrix<>(
                        firstRaw,
                        32,
                        BitEncoders.intEncoder,
                        BitEncoders.intDecoder,
                        0
                );
                Integer[][] secondRaw = generateMatrix(d,d,full1,(r,c)->ran.nextInt(),0,Integer.class);
                CachedTreeMatrix<Integer> second = new CachedTreeMatrix<>(
                        firstRaw,
                        32,
                        BitEncoders.intEncoder,
                        BitEncoders.intDecoder,
                        0
                );
                long nanoTime = System.nanoTime();
                //first.bulkGet(0,0,d,d,firstRaw);
                //second.bulkGet(0,0,d,d,secondRaw);
                int[][] res = bulkMultiply(first,second);
                avgTreeTime+=System.nanoTime()-nanoTime;
                /*
                nanoTime = System.nanoTime();
                int[][] res2 = multiply(firstRaw,secondRaw);
                avgRawTime+=System.nanoTime()-nanoTime;
                */
            }
            avgTreeTime/=runsPerSize*scale;
            avgRawTime/=runsPerSize*scale;
            StringBuilder tempString = new StringBuilder();
            tempString.append(d).append(" ").append(d).append(" ").append(full1).append(" ").append(full2).append(" ").append(avgTreeTime).append(" ").append(avgRawTime);
            System.out.println(tempString);
            data.append(tempString);
        }
        PrintWriter pw = new PrintWriter(new File("multiplyData.txt"));
        pw.write(data.toString());
        pw.flush();
    }

    public static int[][] multiply(Integer[][] mat1, Integer[][] mat2){
        if(mat1[0].length!=mat2.length){
            throw new IllegalArgumentException("Invalid parameters");
        }
        int[][] res = new int[mat1.length][mat2[0].length];
        for(int r = 0; r<mat1.length;r++){
            for(int k = 0; k<mat1[r].length;k++){
                int el = mat1[r][k];
                for(int c = 0; c<mat2[k].length;c++){
                    res[r][c]+=el*mat2[k][c];
                }
            }
        }
        return res;
    }

    public static int[][] bulkMultiply(CachedTreeMatrix<Integer> mat1, CachedTreeMatrix<Integer> mat2){
        if(mat1.width()!=mat2.height()){
            throw new IllegalArgumentException("Illegal Matrices");
        }
        int[][] res = new int[mat1.height()][mat2.width()];
        Integer[] row;
        Integer[] row2;
        for(int r = 0; r<mat1.height();r++){
            row = mat1.getRow(r,Integer.class);
            for(int k = 0; k<row.length;k++){
                int el = row[k];
                row2 = mat2.getRow(k,Integer.class);
                for(int c = 0; c<row2.length;c++){
                    res[r][c]+=el*row2[c];
                }
            }
        }
        return res;
    }

    public static int[][] iteratorMultiply(CachedTreeMatrix<Integer> matrix1, CachedTreeMatrix<Integer> matrix2){
        if(matrix1.width()!=matrix2.height()){
            throw new IllegalArgumentException("Illegal Matrices");
        }
        int[][] res = new int[matrix1.height()][matrix2.width()];
        Iterator<DataPoint<Integer>> firstIt = matrix1.iterator(CachedTreeMatrix.IteratorType.BY_COL);
        Iterator<DataPoint<Integer>> secondIt = matrix2.iterator(CachedTreeMatrix.IteratorType.BY_ROW);
        for(int k = 0; k<matrix1.width();k++){
            Integer[] temp = new Integer[matrix2.width()];
            for(int r = 0; r<matrix1.height();r++){
                DataPoint<Integer> data = firstIt.next();
                for(int c = 0; c<matrix2.width();c++){
                    Integer second = temp[c];
                    if(second==null){
                        second = secondIt.next().data;
                        temp[c] = second;
                    }
                    res[r][c]+=data.data*second;
                }
            }
        }
        return res;
    }

    public static int[][] naiveRecursive(CachedTreeMatrix<Integer> matrix1, CachedTreeMatrix<Integer> matrix2){
        if(matrix1.width()!=matrix2.width()||matrix1.height()!=matrix2.height()||matrix1.height()!=matrix1.width()){
            throw new IllegalArgumentException("Illegal Matrices");
        }
        int[][] returned = new int[matrix1.height()][matrix2.width()];
        return returned;
    }

    public static void readWriteTester(double fullness, double cachePercent) throws IOException{
        StringBuilder data = new StringBuilder("Rows Columns Fullness Cache_Size Total_Bits Dense_Bits Iterative_Read Stride1_Read Random_Read Iterative_Write Stride1_Write Random_Write");
        System.out.println(data);
        data.append("\n");
        double scale = Math.pow(10,3);
        final int runsPerSize = 3;
        for(int d = 16; d<=1024;d*=2){
            long avgBits = 0;
            double[] timeData = new double[6];
            for(int i = 0; i<runsPerSize;i++){
                CachedTreeMatrix<Byte> matrix = new CachedTreeMatrix<>(
                        generateMatrix(d,d,fullness),
                        8,
                        (b,bpd)->new byte[]{b},
                        (b,bpd)->b[0],
                        cachePercent
                );
                double[] tempData;
                if(d<=1024){
                    tempData = runGetTests(matrix,false);
                    timeData[0]+=tempData[0];
                    timeData[1]+=tempData[1];
                    timeData[2]+=tempData[2];
                }
                if(d<=256){
                    tempData = runSetTests(matrix,false);
                    timeData[3]+=tempData[0];
                    timeData[4]+=tempData[1];
                    timeData[5]+=tempData[2];
                }
                avgBits+=matrix.estimateBitSize();
            }
            avgBits/=runsPerSize;
            StringBuilder tempString = new StringBuilder();
            tempString.append(d).append(" ").append(d).append(" ").append(String.format("%.2f", fullness)).append(" ").append(String.format("%.2f", cachePercent)).append(" ").append(avgBits).append(" ").append(d * d * 8);
            for(int i = 0; i<timeData.length;i++){
                timeData[i]/=runsPerSize;
                timeData[i]/=scale;
                tempString.append(" ").append(String.format("%.3f", timeData[i]));
            }
            System.out.println(tempString);
            tempString.append("\n");
            data.append(tempString);
        }
        PrintWriter pw = new PrintWriter(new File("runtimeData.txt"));
        pw.write(data.toString());
        pw.flush();
    }

    private static void printIf(String toPrint, boolean doPrint){
        if(doPrint){
            System.out.println(toPrint);
        }
    }

    private static double[] runSetTests(CachedTreeMatrix<Byte> matrix, boolean doPrint) throws IOException{
        double[] timeData = new double[3];
        printIf("Running set tests...\n", doPrint);
        Object[][] decodedRaw = matrix.toRawMatrix();
        Byte[][] decoded = new Byte[decodedRaw.length][decodedRaw[0].length];
        for(int r = 0; r<decoded.length;r++){
            for(int c = 0; c<decoded[r].length;c++){
                decoded[r][c] = (Byte)decodedRaw[r][c];
            }
        }
        Byte[][] empty = new Byte[decoded.length][decoded[0].length];
        for(int r = 0; r<empty.length;r++){
            for(int c = 0; c<empty[r].length;c++){
                empty[r][c] = 0;
            }
        }
        int failCount = 0;
        printProgressBar("Iterative writes",0,1,doPrint);
        long nanoTime = System.nanoTime();
        CachedTreeMatrix<Byte> toWrite = new CachedTreeMatrix<>(decoded,8,matrix.bitEncoder,matrix.bitDecoder,matrix.cachePercent);
        /*
        for(int r = 0; r<empty.length;r++){
          for(int c = 0; c<empty[r].length;c++){
            toWrite.set(r,c,(byte)0);
          }
        }
        */
        timeData[0] = (System.nanoTime()-nanoTime);
        boolean passed = equal(toWrite.toRawMatrix(),decoded);
        if(doPrint){
            System.out.printf("\rIterative writes %s in %.3fs\n",passed?"passed":"failed",timeData[0]/Math.pow(10,9));
        }
        timeData[0]/=toWrite.size();
        failCount+=passed?0:1;
        toWrite = new CachedTreeMatrix<>(empty,8,matrix.bitEncoder,matrix.bitDecoder,matrix.cachePercent);
        printProgressBar("Stride-1 writes",0,1,doPrint);
        int readCount = 0;
        for(int r = 0; r<matrix.height();r++){
            for(int c = 0; c<matrix.width();c++){
                nanoTime = System.nanoTime();
                Byte written = toWrite.set(r,c,decoded[r][c]);
                timeData[1] += System.nanoTime()-nanoTime;
                printProgressBar("Stride-1 writes",++readCount,matrix.size(),doPrint);
            }
        }
        passed = equal(decoded,toWrite.toRawMatrix());
        if(doPrint){
            System.out.printf("\rStride-1 writes %s in %.3fs\n",passed?"passed":"failed",timeData[1]/Math.pow(10,9));
        }
        timeData[1]/=readCount;
        failCount+=passed?0:1;
        toWrite = new CachedTreeMatrix<>(empty,8,matrix.bitEncoder,matrix.bitDecoder,matrix.cachePercent);
        printProgressBar("Random writes",0,1,doPrint);
        readCount = 0;
        ArrayList<Point> points = new ArrayList<>();
        for(int r = 0; r<matrix.height();r++){
            for(int c = 0; c<matrix.width();c++){
                points.add(new Point(r,c));
            }
        }
        Random rand = new Random();
        for(int count = 0; count<matrix.size();count++){
            int index = rand.nextInt(points.size());
            Point point = points.get(index);
            int r = point.row, c = point.column;
            nanoTime = System.nanoTime();
            empty[r][c] = toWrite.set(r,c,decoded[r][c]);
            timeData[2] += System.nanoTime()-nanoTime;
            points.remove(index);
            printProgressBar("Random writes",++readCount,matrix.size(),doPrint);
        }
        passed = equal(empty,toWrite.toRawMatrix());
        if(doPrint){
            System.out.printf("\rRandom writes %s in %.3fs\n",passed?"passed":"failed",timeData[2]/Math.pow(10,9));
        }
        timeData[2]/=readCount;
        failCount+=passed?0:1;
        printIf("\nTests "+(failCount==0?"passed":"failed")+"\n",failCount!=0||doPrint);
        return timeData;
    }

    private static double[] runGetTests(CachedTreeMatrix<Byte> matrix, boolean doPrint) throws IOException{
        double[] timeData = new double[3];
        printIf("\nRunning get tests...\n",doPrint);
        Object[][] decoded = matrix.toRawMatrix();
        int failCount = 0;
        boolean didFail = false;
        printProgressBar("Iterative reads",0,1,doPrint);
        int readCount = 0;
        long nanoTime;
        /*
        Iterator<DataPoint<Byte>> iterator = matrix.iterator();
        while(iterator.hasNext()){
            nanoTime = System.nanoTime();
            DataPoint<Byte> point = iterator.next();
            timeData[0] += (System.nanoTime()-nanoTime);
            int r = point.row, c = point.column;
            if(!point.data.equals(decoded[r][c])){
                failCount++;
            }
            printProgressBar("Iterative reads",++readCount,matrix.size(),doPrint);
        }
        */
        int height = matrix.height(), width = matrix.width();
        readCount = height*width;
        nanoTime = System.nanoTime();
        Byte[][] container = matrix.bulkGet(0,0,height,width,Byte.class);
        timeData[0] = System.nanoTime()-nanoTime;
        for(int r = 0; r<height;r++){
            for(int c = 0; c<width;c++){
                if(!decoded[r][c].equals(container[r][c])){
                    failCount++;
                }
            }
        }
        didFail = failCount>0;
        if(doPrint){
            System.out.printf("\rIterative reads %s in %.3fs\n",failCount==0?"passed":"failed",timeData[0]/Math.pow(10,9));
        }
        failCount = 0;
        timeData[0]/=readCount;
        printProgressBar("Stride-1 reads",0,1,doPrint);
        readCount = 0;

        Iterator<DataPoint<Byte>> iterator = matrix.iterator(CachedTreeMatrix.IteratorType.BY_ROW);
        while(iterator.hasNext()){
            nanoTime = System.nanoTime();
            DataPoint<Byte> point = iterator.next();
            timeData[1] += (System.nanoTime()-nanoTime);
            int r = point.row, c = point.column;
            if(!point.data.equals(decoded[r][c])){
                failCount++;
            }
            printProgressBar("Stride-1 reads",++readCount,matrix.size(),doPrint);
        }
        didFail |= failCount>0;
        if(doPrint){
            System.out.printf("\rStride-1 reads %s in %.3fs\n",failCount==0?"passed":"failed",timeData[1]/Math.pow(10,9));
        }
        failCount = 0;
        timeData[1]/=readCount;
        printProgressBar("Random reads",0,1,doPrint);
        readCount = 0;
        Random rand = new Random();
        for(int count = 0; count<matrix.size();count++){
            int r = rand.nextInt(matrix.height());
            int c = rand.nextInt(matrix.width());
            nanoTime = System.nanoTime();
            Byte data = matrix.get(r,c);
            timeData[2] += System.nanoTime()-nanoTime;
            if(!data.equals(decoded[r][c])){
                failCount++;
            }
            printProgressBar("Random reads",++readCount,matrix.size(),doPrint);
        }
        didFail |= failCount>0;
        if(doPrint){
            System.out.printf("\rRandom reads %s in %.3fs\n",failCount==0?"passed":"failed",timeData[2]/Math.pow(10,9));
        }
        timeData[2]/=readCount;
        printIf("\nTests "+(!didFail?"passed":"failed")+"\n",failCount!=0||doPrint);
        return timeData;
    }

    private static void printProgressBar(String name, int current, int goal, boolean doPrint){
        if(!doPrint){
            return;
        }
        String spaces = "----------";
        String lines = "==========";
            double prog = (double)current/goal;
            int progIndex = (int)(spaces.length()*prog);
            int printEvery = Math.max(1,(int)Math.round(goal/100.0));
            if(current%(printEvery)==0){
                String progress;
                if(progIndex>0&&progIndex<10){
                    progress = lines.substring(0,progIndex-1)+">";
                }else{
                    progress = lines.substring(0,progIndex);
                }
                progress+=spaces.substring(progIndex);
                System.out.printf("\r"+name+" %"+progress.length()+"s %.0f%%",progress,100*prog);
            }
    }

    private static int readInt(String name, Scanner in, Function<Integer,Boolean> tester){
        Integer returned = null;
        do{
            System.out.print(name+": ");
            try{
                returned = in.nextInt();
            }catch(Exception e){
                in.nextLine();
            }
        }while(returned==null||!tester.apply(returned));
        return returned;
    }

    private static double readDouble(String name, Scanner in, Function<Double,Boolean> tester){
        Double returned = null;
        do{
            System.out.print(name+": ");
            try{
                returned = in.nextDouble();
            }catch(Exception e){
                in.nextLine();
            }
        }while(returned==null||!tester.apply(returned));
        return returned;
    }

    public static MemoryController customTester(MatrixEncoder<Byte> encoder) throws IOException{
        Scanner in = new Scanner(System.in);
        int r = readInt("Rows",in,(i)->i>0);
        int c = readInt("Columns",in,(i)->i>0);
        double sparse = readDouble("Fullness", in, (d)->d>=0&&d<=1);
        System.out.println("\nEncoding matrix...\n");
        Byte[][] matrix = generateMatrix(r,c,sparse);
        encoder.setMatrix(matrix);
        MemoryController controller = encoder.encodeMatrix();
        Object[][] decoded = encoder.decodeMatrix(controller.inputStream());
        encoder.printAnalytics();
        System.out.println("CRS estimate ref size: "+estimateCRS(matrix));
        boolean correct = equal(matrix,decoded);
        System.out.println("\nCorrectly decoded: "+correct);
        if(!correct){
            System.out.println("\nOriginal: ");
            printMatrix(matrix);
            System.out.println("\nDecoded: ");
            printMatrix(decoded);
        }
        return controller;
    }

    public static void matrixTester(MatrixEncoder<Byte> encoder) throws IOException{
        String formatName = encoder.getClass()==HybridMatrixEncoder.class?"Hybrid":"QTE";
        StringBuilder data = new StringBuilder("Rows Columns Fullness "+formatName+"_Bits CRS_Bits Data_Bits Total_Bits Dense_Bits");
        System.out.println(data);
        data.append("\n");
        for(int d = 10; d<=1000;d*=10){
            Byte[][] matrix;
            for(double sparse = 0; sparse<=1;sparse+=sparse<=.09?.01:.1){
                long avgCRS = 0;
                long avgBits = 0;
                long averageTotal = 0;
                int runsPerSize = 3;
                for(int i = 0; i<runsPerSize;i++){
                    matrix = generateMatrix(d,d,sparse);
                    encoder.setMatrix(matrix);
                    MemoryController controller = encoder.encodeMatrix();
                    Object[][] decoded = encoder.decodeMatrix(controller.inputStream());
                    if(!equal(decoded,matrix)){
                        System.out.println("\nOriginal: ");
                        printMatrix(matrix);
                        System.out.println("\nDecoded: ");
                        printMatrix(decoded);
                        System.out.println("Encoding error...");
                        return;
                    }
                    avgBits+=encoder.refSize();
                    avgCRS+=estimateCRS(matrix);
                    averageTotal+=controller.size();
                }
                avgBits/=runsPerSize;
                avgCRS/=runsPerSize;
                averageTotal/=runsPerSize;
                String str = d+" "+d+" "+String.format("%.2f",sparse)+" "+avgBits+" "+avgCRS+" "+averageTotal+" "+(d*d*8);
                System.out.println(str);
                str+="\n";
                data.append(str);
            }
        }
        PrintWriter pw = new PrintWriter(new File("diskData.txt"));
        pw.write(data.toString());
        pw.flush();
    }

    public static Byte[][] generateMatrix(int h, int w, double sparse){
        Random ran = new Random();
        return generateMatrix(h,w,sparse,(r,c)->(byte)(ran.nextInt(127)+1),(byte)0, Byte.class);
    }

    public static <T> T[][] generateMatrix(int h, int w, double sparse, BiFunction<Integer,Integer,T> generator, T defaultVal, Class<T> type){
        int numItems = (int)Math.round(h*w*sparse);
        ArrayList<int[]> points = new ArrayList<>();
        for(int r = 0; r<h;r++){
            for(int c = 0; c<w;c++){
                points.add(new int[]{r,c});
            }
        }
        Random ran = new Random();
        T[][] matrix = (T[][])Array.newInstance(type,h,w);
        for(int i = 0; i<numItems;i++){
            int[] point = points.remove(ran.nextInt(points.size()));
            matrix[point[0]][point[1]] = generator.apply(point[0],point[1]);
        }
        for(int[] point : points){
            matrix[point[0]][point[1]] = defaultVal;
        }
        return matrix;
    }

    public static <T> String matrixToString(T[][] matrix){
        String data = Arrays.deepToString(matrix);
        return data.substring(1,data.length()-1).replaceAll("\\], ","\\]\n");
    }

    public static <T> void printMatrix(T[][] matrix){
        System.out.println(matrixToString(matrix));
    }

    public static int estimateCRS(Byte[][] matrix){
        return estimateCRS1(matrix);
    }

    private static int estimateCRS1(Byte[][] matrix){
        int data = 0, dataCount = 0;
        int bitsPerColRef = logBaseCeil(matrix[0].length,2);
        int bitsPerRowRef = logBaseCeil(matrix.length*matrix[0].length,2);
        for(int i = 0; i<matrix.length;i++){
            for(int j = 0; j<matrix[i].length;j++){
                dataCount+=!matrix[i][j].equals((byte)0)?1:0;
            }
        }
        data+=bitsPerColRef*dataCount;
        data+=bitsPerRowRef*matrix.length;
        return data;
    }

    private static int estimateCRS2(Byte[][] matrix){
        int dataCount = 0;
        int bitsPerColRef = logBaseCeil(matrix[0].length,2);
        int bitsPerRowRef = logBaseCeil(matrix.length,2);
        for(int i = 0; i<matrix.length;i++){
            for(int j = 0; j<matrix[i].length;j++){
                dataCount+=!matrix[i][j].equals((byte)0)?1:0;
            }
        }
        return dataCount*(bitsPerRowRef+bitsPerColRef);
    }

    private static int estimateCRS3(Byte[][] matrix){
        int data = 0;
        int bitsPerColRef = logBaseCeil(matrix[0].length,2)+1;
        int bitsPerRowRef = logBaseCeil(matrix.length,2)+1;
        for(int i = 0; i<matrix.length;i++){
            boolean hasData = false;
            for(int j = 0; j<matrix[i].length;j++){
                if(!matrix[i][j].equals((byte)0)){
                    if(!hasData){
                        hasData = true;
                        data+=bitsPerRowRef;
                    }
                    data+=bitsPerColRef;
                }
            }
        }
        return data;
    }

    public static int logBaseCeil(int num, int base){
        return (int)Math.ceil(Math.log(num)/Math.log(base));
    }

    public static boolean equal(Object[][] matrix1, Object[][] matrix2){
        for(int r = 0; r<matrix1.length;r++){
            for(int c = 0; c<matrix1[r].length;c++){
                if(!matrix1[r][c].equals(matrix2[r][c])){
                    return false;
                }
            }
        }
        return true;
    }
}