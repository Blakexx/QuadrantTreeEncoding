import java.util.function.BiFunction;

public class BitEncoders {

    public static final BiFunction<Integer,Integer,byte[]> intEncoder = (i, bpd)->{
        int bitsPerCon = BitList.bitsPerCon;
        byte[] returned = new byte[(int)Math.ceil(bpd/(double)bitsPerCon)];
        i<<=(returned.length*bitsPerCon-bpd);
        for(int index = returned.length-1; index>-1;index--){
            returned[index] = i.byteValue();
            i>>>=bitsPerCon;
        }
        return returned;
    };

    public static final BiFunction<byte[],Integer,Integer> intDecoder = (l, bpd)->{
        int bitsPerCon = BitList.bitsPerCon;
        int returned = 0;
        for(int i = 1; i<=l.length;i++){
            returned|=((int)l[l.length-i]&255)<<((i-1)*bitsPerCon);
        }
        returned>>>=(l.length*bitsPerCon-bpd);
        return returned;
    };
}
