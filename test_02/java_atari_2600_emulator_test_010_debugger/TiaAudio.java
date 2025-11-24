import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public class TiaAudio {

    private static int lfsr4Bit = 0b1111;
    
    public static int nextSample4Bit() {
        int out = lfsr4Bit & 1;
        lfsr4Bit = lfsr4Bit >> 1;
        int n = (lfsr4Bit & 1) ^ out;
        lfsr4Bit = lfsr4Bit | (n << 3);
        return out;
    }
    
    private static int lfsr5Bit = 0b11111;

    public static int nextSample5Bit() {
        int out = lfsr5Bit & 1;
        lfsr5Bit = lfsr5Bit >> 1;
        int n = ((lfsr5Bit & 0b10) >> 1) ^ out;
        lfsr5Bit = lfsr5Bit | (n << 4);
        return out;
    }
    
    private static int lfsr9Bit = 0b111111111;

    public static int nextSample9Bit() {
        int out = lfsr9Bit & 1;
        lfsr9Bit = lfsr9Bit >> 1;
        int n = ((lfsr9Bit & 0b1000) >> 3) ^ out;
        lfsr9Bit = lfsr9Bit | (n << 8);
        return out;
    }

    private static class AUDC {
        int samples[];
        double sampleIndex = 0;
        int getNextSample(double sampleStep) {
            sampleIndex += sampleStep;
            return samples[((int) sampleIndex) % samples.length];
        }
    }

    //   0  1 ----------------------------------------------------------------------
    //   0  set to 1 
    private static class AUDC0 extends AUDC {
        private AUDC0() {
            samples = new int[1];
            samples[0] = 0xff;
        }
    }

    //   1  15 ----___-__--_-_----___-__--_-_----___-__--_-_----___-__--_-_----___-__
    //         ----___-__--_-_----___-__--_-_----___-__--_-_----___-__--_-_----___-__
    //   1  4 bit poly                  
    private static class AUDC1 extends AUDC {
        private AUDC1() {
            samples = new int[15];
            lfsr4Bit = 0b1111;
            for (int i = 0; i < 15; i++) {
                int sample = nextSample4Bit() == 1 ? 0xff : 0x00;
                samples[i] = sample;
            }            
        }
    }
    
    //   2  465 --------------------------------------------------------------________
    //   2  div 15 -> 4 bit poly       
    // Type 2 fetches next 4bit poly (type 1) on each div31 (type 6) transition, the
    // duration of each Poly4 bit is thus multiplied by 18 or by 13.
    private static class AUDC2 extends AUDC {
        private AUDC2() {
            samples = new int[465];
            lfsr4Bit = 0b1111;
            int sample = 0;
            for (int i = 0; i < 465; i++) {
                int lfsrIndex = (i % 32);
                boolean advanceLfsr = lfsrIndex == 0 || lfsrIndex == 18;
                if (advanceLfsr) {
                    sample = nextSample4Bit() == 1 ? 0xff : 0x00;
                }
                samples[i] = sample;
            }
        }
    }

    //          ----___-__--_-_----___-__--_-_----___-__--_-_----___-__--_-_----___-__ <- poly4
    //          -----___--_---_-_-____-__-_--__-----___--_---_-_-____-__-_--__-----___ <-- poly5
    //          ----_______-___-------___--_------______--__----_____---__------_____
    //   3  465 ------______-___---__-----___-------___----___--__--_____---------___-
    //   3  5 bit poly -> 4 bit poly    
    // Type 3 uses the most complicated mechanism (for reverse engineering). The Poly5
    // operation takes place each cycle. If Poly5-Carry is 1, then the 4bit poly
    // operation is executed and the Poly4-Carry is output to the sound channel,
    // otherwise Poly4 (and sound output) are kept unchanged.    

    // TODO: comparing to other emulators, apparently the sound looks ok, maybe the doc is incorrect?
    private static class AUDC3 extends AUDC {
        private AUDC3() {
            samples = new int[465];
            lfsr4Bit = 0b1111;
            lfsr5Bit = 0b11111;
            int sample = 0xff;
            for (int i = 0; i < 465; i++) {
                if (nextSample5Bit() == 1) {
                    sample = nextSample4Bit() == 1 ? 0xff : 0x00;
                }
                samples[i] = sample;
            }            
        }
    }
    
    //   4  2 -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
    //   4  div 2 : pure tone           
    private static class AUDC4 extends AUDC {
        private AUDC4() {
            samples = new int[2];
            samples[0] = 0xff;
            samples[1] = 0x00;
        }
    }
    
    //   5  2 -_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_
    //   5  div 2 : pure tone           
    private static class AUDC5 extends AUDC {
        private AUDC5() {
            samples = new int[2];
            samples[0] = 0xff;
            samples[1] = 0x00;
        }
    }
    
    //   6  31 ------------------_____________------------------_____________--------
    //   6  div 31 : pure tone          
    private static class AUDC6 extends AUDC {
        private AUDC6() {
            samples = new int[31];
            for (int i = 0; i < 31; i++) {
                int sample = i < 18 ? 0xff : 0x00;
                samples[i] = sample;
            }
        }
    }
    
    //   7  31 -----___--_---_-_-____-__-_--__-----___--_---_-_-____-__-_--__-----___
    //   7  5 bit poly -> div 2         
    private static class AUDC7 extends AUDC {
        private AUDC7() {
            samples = new int[31];
            lfsr5Bit = 0b11111;
            for (int i = 0; i < 31; i++) {
                int out = nextSample5Bit() == 1 ? 0xff : 0x00;
                samples[i] = out;
            }            
        }
    }
    
    //   8  511 ---------_____----_-----___-_---__--__-_____-__-_-__---_--_-___----__-
    //   8  9 bit poly (white noise)
    private static class AUDC8 extends AUDC {
        private AUDC8() {
            samples = new int[511];
            lfsr9Bit = 0b111111111;
            for (int i = 0; i < 511; i++) {
                int sample = nextSample9Bit() == 1 ? 0xff : 0x00;
                samples[i] = sample;
            }            
        }
    }
    
    //   9  31 -----___--_---_-_-____-__-_--__-----___--_---_-_-____-__-_--__-----___
    //   9  5 bit poly
    private static class AUDC9 extends AUDC {
        private AUDC9() {
            samples = new int[31];
            lfsr5Bit = 0b11111;
            for (int i = 0; i < 31; i++) {
                int sample = nextSample5Bit() == 1 ? 0xff : 0x00;
                samples[i] = sample;
            }            
        }
    }
    
    //   A  31 ------------------_____________------------------_____________--------
    //   A  div 31 : pure tone
    private static class AUDCA extends AUDC {
        private AUDCA() {
            samples = new int[31];
            for (int i = 0; i < 31; i++) {
                int sample = i < 18 ? 0xff : 0x00;
                samples[i] = sample;
            }
        }
    }
    
    //   B  1 ----------------------------------------------------------------------
    //   B  set last 4 bits to 1
    private static class AUDCB extends AUDC {
        private AUDCB() {
            samples = new int[1];
            samples[0] = 0xff;
        }
    }
    
    //   C   6 ---___---___---___---___---___---___---___---___---___---___---___---_
    //   C  div 6 : pure tone
    private static class AUDCC extends AUDC {
        private AUDCC() {
            samples = new int[6];
            for (int i = 0; i < 6; i++) {
                int sample = i < 3 ? 0xff : 0x00;
                samples[i] = sample;
            }
        }
    }
    
    //   D  6 ---___---___---___---___---___---___---___---___---___---___---___---_
    //   D  div 6 : pure tone
    private static class AUDCD extends AUDC {
        private AUDCD() {
            samples = new int[6];
            for (int i = 0; i < 6; i++) {
                int sample = i < 3 ? 0xff : 0x00;
                samples[i] = sample;
            }
        }
    }
    
    //   E  93 -------------------------------------------------_____________________
    //   E  div 93 : pure tone
    private static class AUDCE extends AUDC {
        private AUDCE() {
            samples = new int[93];
            for (int i = 0; i < 93; i++) {
                int sample = i < 54 ? 0xff : 0x00;
                samples[i] = sample;
            }
        }
    }
    
    //   F  93 ----------_____---_______----__________------___------____---------___
    //   F  5 bit poly div 6

    // TODO not working?
    
    private static class AUDCF extends AUDC {
        private AUDCF() {
            samples = new int[93];
            lfsr5Bit = 0b11111;
            int sample = 0x00;
            for (int i = 0; i < 93; i++) {
                if (i % 3 == 0) {
                    sample = nextSample5Bit() == 1 ? 0xff : 0x00;
                }
                samples[i] = sample;
            }                   
        }
    }
    
    public static class Channel {
        
        private AUDC[] AUDCs = new AUDC[16];

        public int AUDC = 0;
        public int AUDV = 0;
        public int AUDF = 0;

        public Channel() {
            AUDCs[0] = new AUDC0();
            AUDCs[1] = new AUDC1();
            AUDCs[2] = new AUDC2();
            AUDCs[3] = new AUDC3();
            AUDCs[4] = new AUDC4();
            AUDCs[5] = new AUDC5();
            AUDCs[6] = new AUDC6();
            AUDCs[7] = new AUDC7();
            AUDCs[8] = new AUDC8();
            AUDCs[9] = new AUDC9();
            AUDCs[10] = new AUDCA();
            AUDCs[11] = new AUDCB();
            AUDCs[12] = new AUDCC();
            AUDCs[13] = new AUDCD();
            AUDCs[14] = new AUDCE();
            AUDCs[15] = new AUDCF();
        }
        
        public int getNextSample() {
            //AUDV = 2;
            //AUDC = 15;
            //AUDF = 3;
            double SampleStep = 1.0 / (AUDF + 1);
            return (int) (AUDCs[AUDC].getNextSample(SampleStep) * (AUDV / 15.0));
        }

    }

    public static Channel AUD0 = new Channel();
    public static Channel AUD1 = new Channel();
    
    public static int getMixedNextSample() {
        int out1 = AUD0.getNextSample();        
        int out2 = AUD1.getNextSample();        
        int mixed = (byte) ((out1 + out2) >> 1);
        return mixed;
    }

    public static final int AUDIO_SAMPLE_RATE = 31400;

    // --- test

    public static void main(String[] args) {
        //AUDCF audc1 = new AUDCF();
        testPlay();
    }

    private static void testPlay() {
        //printLfsr4Bit();
        byte[] musicData = new byte[1];
        try {
            AudioFormat audioFormat = new AudioFormat( AUDIO_SAMPLE_RATE
                , 8, 1, false, false);

            SourceDataLine sourceDataLine 
                = (SourceDataLine) AudioSystem.getSourceDataLine(audioFormat);

            sourceDataLine.open(audioFormat);
            sourceDataLine.start();

            Channel channel = new Channel();
            channel.AUDV = 7;
            channel.AUDC = 3;
            channel.AUDF = 7;

            for (int i = 0; i < AUDIO_SAMPLE_RATE * 5; i++) {
                int out = channel.getNextSample();
                musicData[0] = (byte) out;
                sourceDataLine.write(musicData, 0, 1);
            }

            sourceDataLine.drain();
            sourceDataLine.close();
        } catch (Exception e) {
            e.printStackTrace();
        }    

    }

    /*
    private static void printLfsr4Bit() {
        for (int i = 0; i < 32; i++) {
            String b = "0000" + Integer.toBinaryString(lfsr4Bit);
            b = b.substring(b.length() - 4, b.length());
            int out = nextSample4Bit();
            System.out.println("i=" + i + ": " + out);
        }
    }

    private static void printLfsr5Bit() {
        for (int i = 0; i < 35; i++) {
            String b = "00000" + Integer.toBinaryString(lfsr5Bit);
            b = b.substring(b.length() - 5, b.length());
            System.out.println("i=" + i + ": " + b);
            nextSample5Bit();
        }
    }

    private static void printLfsr9Bit() {
        for (int i = 0; i < 515; i++) {
            String b = "000000000" + Integer.toBinaryString(lfsr9Bit);
            b = b.substring(b.length() - 9, b.length());
            System.out.println("i=" + i + ": " + b);
            nextSample9Bit();
        }
    }
    */

}