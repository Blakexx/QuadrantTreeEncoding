import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.lang.reflect.Array;
import java.util.function.*;
import java.util.stream.*;

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
        System.out.println("1: Disk Size Tester");
        System.out.println("2: Read Tester");
        int type = readInt("Test Type",in,(i)->i>=1&&i<=2);
        System.out.println();
        if(type==1){
            System.out.println("1: QTE");
            System.out.println("2: QTE/Dense Hybrid");
            System.out.println("3: QTE/Dense/CRS Hybrid");
            System.out.println("4: CRS");
            type = readInt("Encoding Type",in,(i)->i>=1&&i<=4);
            System.out.println();
            encoder = type==2?new DoubleHybridEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            ):type==3?new TripleHybridEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            ):type==4?new CRSEncoder<>(
                    null,
                    8,
                    enc,
                    dec
            ):encoder;
            matrixTester(encoder);
        }else if(type==2){
            System.out.println("1. QTE Matrix");
            System.out.println("2. CRS Matrix");
            type = readInt("Encoding Type",in,(i)->i>=1&&i<=2);
            System.out.println();
            double fullness = readDouble("Fullness", in, (i)->i>=0&&i<=1);
            double cachePercent = type==1?readDouble("Cache %",in,(i)->i>=0&&i<=1):0;
            readWriteTester(fullness, cachePercent, type);
        }
    }

    private static long runTest(Consumer<Matrix<Byte>> test, Matrix<Byte> matrix){
        long time = System.nanoTime();
        test.accept(matrix);
        return System.nanoTime()-time;
    }

    private static Matrix<Byte> getByteMatrix(double fullness, double cachePercent, int r, int c, int type){
        return switch(type){
            case 1 -> new CachedTreeMatrix<>(
                    generateMatrix(r,c,fullness),
                    8,
                    (b,bpd)->new byte[]{b},
                    (b,bpd)->b[0],
                    cachePercent/*,
                    new File("qte.matrix")*/
            );
            case 2 -> new CRSMatrix<>(
                    generateMatrix(r,c,fullness),
                    8,
                    (b,bpd)->new byte[]{b},
                    (b,bpd)->b[0]/*,
                    new File("crs.matrix")*/
            );
            default -> throw new IllegalArgumentException("Invalid Type");
        };
    }

    public static void readWriteTester(double fullness, double cachePercent, int type) throws IOException{
        List<Consumer<Matrix<Byte>>> toRun = Arrays.asList(
                Main::sequentialRowTest,
                Main::randomRowTest,
                Main::sequentialColTest,
                Main::randomColTest,
                Main::randomTest
        );
        StringBuilder data = new StringBuilder("Elements Fullness Cache_Size Total_Bits Dense_Bits SeqRow RanRow SeqCol RanCol Random");
        System.out.println(data);
        data.append("\n");
        double scale = Math.pow(10,6);
        final int runsPerSize = 1;
        for(int dim = 16; dim<=1024;dim*=2){
            long avgBits = 0;
            double[] timeData = new double[toRun.size()];
            for(int i = 0; i<runsPerSize;i++){
                Matrix<Byte> matrix = getByteMatrix(fullness,cachePercent,dim,dim,type);
                avgBits+=matrix.estimateBitSize();
                for(int r = 0; r<toRun.size();r++){
                    timeData[r]+=runTest(toRun.get(r),matrix);
                }
            }
            avgBits/=runsPerSize;
            StringBuilder tempString = new StringBuilder();
            tempString.append(dim*dim).append(" ").append(String.format("%.2f", fullness)).append(" ").append(String.format("%.2f", cachePercent)).append(" ").append(avgBits).append(" ").append(dim * dim * 8);
            for(int i = 0; i<timeData.length;i++){
                timeData[i]/=runsPerSize;
                timeData[i]/=scale;
                tempString.append(" ").append(String.format("%.0f", timeData[i]));
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

    private static void sequentialRowTest(Matrix<Byte> matrix){
        Iterator<DataPoint<Byte>> iterator = matrix.iterator(IteratorType.BY_ROW);
        while(iterator.hasNext()){
            iterator.next();
        }
    }

    private static void randomRowTest(Matrix<Byte> matrix){
        Random rand = new Random();
        List<Integer> rows = IntStream.rangeClosed(0,matrix.height()-1).boxed().collect(Collectors.toList());
        int width = matrix.width();
        while(rows.size()>0){
            int row = rows.remove(rand.nextInt(rows.size()));
            Iterator<DataPoint<Byte>> iterator = matrix.iterator(row,0,1,width,IteratorType.BY_ROW);
            while(iterator.hasNext()){
                iterator.next();
            }
        }
    }

    private static void sequentialColTest(Matrix<Byte> matrix){
        Iterator<DataPoint<Byte>> iterator = matrix.iterator(IteratorType.BY_COL);
        while(iterator.hasNext()){
            iterator.next();
        }
    }

    private static void randomColTest(Matrix<Byte> matrix){
        Random rand = new Random();
        List<Integer> cols = IntStream.range(0,matrix.width()).boxed().collect(Collectors.toList());
        int height = matrix.height();
        while(cols.size()>0){
            int col = cols.remove(rand.nextInt(cols.size()));
            Iterator<DataPoint<Byte>> iterator = matrix.iterator(0,col,height,1,IteratorType.BY_COL);
            while(iterator.hasNext()){
                iterator.next();
            }
        }
    }

    private static void randomTest(Matrix<Byte> matrix){
        Random rand = new Random();
        int height = matrix.height();
        int width = matrix.width();
        List<Integer> points = IntStream.range(0,height*width).boxed().collect(Collectors.toList());
        while(points.size()>0){
            int point = points.remove(rand.nextInt(points.size()));
            matrix.get(point/width,point%width);
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

    public static void matrixTester(MatrixEncoder<Byte> encoder) throws IOException{
        String formatName = encoder.getName();
        StringBuilder data = new StringBuilder("Rows Columns Fullness "+formatName+"_Bits CRS_Bits Data_Bits Total_Bits Dense_Bits");
        System.out.println(data);
        data.append("\n");
        for(int d = 10; d<=1000;d*=10){
            Byte[][] matrix = null;
            for(double sparse = 0; sparse<=1;sparse+=sparse<=.09?.01:.1){
                long avgCRS = 0;
                long avgBits = 0;
                long averageTotal = 0;
                int runsPerSize = 3;
                MemoryController controller = new MemoryController(new File("encoded/"+d+"x"+d+"-"+String.format("%.0f",sparse*100)));
                for(int i = 0; i<runsPerSize;i++){
                    matrix = generateMatrix(d,d,sparse);
                    encoder.setMatrix(matrix);
                    encoder.encodeMatrix(controller);
                    Object[][] decoded = encoder.decodeMatrix(controller);
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
                FileBitOutputStream stream = new FileBitOutputStream("dense/"+d+"x"+d+"-"+String.format("%.0f",sparse*100),false);
                for(int r = 0; r<matrix.length;r++){
                    for(int c = 0; c<matrix[r].length;c++){
                        stream.writeBits(8,new byte[]{matrix[r][c]});
                    }
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

    public static int roundUpDiv(int num, int den){
        return (num + den - 1) / den;
    }
}