import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class Tia2 {

    public static BufferedImage crt = new BufferedImage(228, 262, BufferedImage.TYPE_INT_RGB);
    public static Graphics2D crtg = (Graphics2D) crt.getGraphics();
    private static int crtX;
    private static int crtY;

    private static final int CRT_WIDTH = 228; // ntsc

    private static int currentPixelClock = 0;
    private static int targetPixelClock = 0;

    private static int hM0Counter;
    private static int hM1Counter;
    private static int hBallCounter;

    private static boolean hblank;

    public static void process(int cpuCycles) {
        targetPixelClock += cpuCycles * 3;
        while (targetPixelClock > currentPixelClock) {
            int scanlineX = currentPixelClock % CRT_WIDTH;
            
            hblank = scanlineX < (68 + (hmoveEnabled ? 8 : 0));

            if (hblank && hmoveEnabled && (scanlineX % 4 == 0) && (HMP0DX > 0)) {
                hP0Counter = (hP0Counter + 1) % 160;
                HMP0DX--;
            }
            if (hblank && hmoveEnabled && (scanlineX % 4 == 0) && (HMP1DX > 0)) {
                hP1Counter = (hP1Counter + 1) % 160;
                HMP1DX--;
            }
            if (hblank && resp0DuringHBlank) {
                //updateP0();
                //updateP0();
                hP0Counter = (hP0Counter + 1) % 160;
                hP0Counter = (hP0Counter + 1) % 160;
                resp0DuringHBlank = false;
            }
            if (hblank && resp1DuringHBlank) {
                //updateP1();
                //updateP1();
                hP1Counter = (hP1Counter + 1) % 160;
                hP1Counter = (hP1Counter + 1) % 160;
                resp1DuringHBlank = false;
            }
            if (!hblank) {
                hmoveEnabled = false;
                updatePF(scanlineX - 68);
                int pixelP0 = updateP0();
                int pixelP1 = updateP1();

                int color = 0xff000000;
                if (!vblankSet) {
                    color = Atari2600PaletteNTSC.colorsNTSC[(COLUBK & 0xff) >> 1].getRGB();
                    color = getPFColor() == 1 ? Atari2600PaletteNTSC.colorsNTSC[(COLUPF & 0xff) >> 1].getRGB() : color;
                    color = pixelP0 == 1 ? Atari2600PaletteNTSC.colorsNTSC[(COLUP0 & 0xff) >> 1].getRGB() : color;
                    color = pixelP1 == 1 ? Atari2600PaletteNTSC.colorsNTSC[(COLUP1 & 0xff) >> 1].getRGB() : color;
                }
                crtX = scanlineX;
                try {
                    crt.setRGB(crtX, crtY, color);
                }
                catch (Exception e) {}
            }

            //System.out.println("scanlineX: " + scanlineX + " hblank: " + hblank + " crtY: " + crtY);

            if (scanlineX == 227) { 
                wsyncHaltCpu = false;
                crtY++; // next line
                //System.out.println("crtY: " + crtY);
            }

            currentPixelClock++;
        }
    }
    public static int lastCrtY = 226;
    public static boolean renderTime = false;
    private static boolean vsyncSet = false;
    private static int vsyncCurrentScanline;
    public static void VSYNC(int arg) {
        int vsyncScanlineCount = crtY - vsyncCurrentScanline;
        if (vsyncSet && (arg & 2) == 0 && vsyncScanlineCount >= 1 && vsyncScanlineCount <= 3) {
            vsyncSet = false;
            lastCrtY = crtY;
            System.out.println("new frame, last scanline: " + lastCrtY);
            crtY = 0;
            renderTime = true;
        }
        else if ((arg & 2) == 2) {
            vsyncSet = true;
            vsyncCurrentScanline = crtY;
        }
    }

    private static boolean vblankSet = false;
    public static void VBLANK(int arg) {
        vblankSet = (arg & 2) == 2;
    }

    public static boolean wsyncHaltCpu = false;
    public static void WSYNC(int arg) {
        wsyncHaltCpu = true;
    }
    
    public static int COLUBK = 0;

    public static int PF0 = 0;
    public static int PF1 = 0;
    public static int PF2 = 0;
    public static int COLUPF = 0;
    public static int CTRLPF = 0;

    private static boolean hPFCenter = false;
    private static int hPFScanIndex = 0;

    private static void updatePF(int pfIndex) {
        hPFCenter = false;
        if (pfIndex >= 80) { // center
            pfIndex -= 80;
            hPFCenter = true;
        }
        hPFScanIndex = pfIndex / 4;
    }

    //            PF0      PF1      PF2
    //  bits 2222xxxx 11111100 00000000         
    //       3210xxxx 54321098 76543210
    //
    //                      11 11111111
    // order 3210     45678901 98765432
    private static final int[] PFPixelOrderMap 
        = { 20, 21, 22, 23, 15, 14, 13, 12, 11, 10, 9, 8, 0, 1, 2, 3, 4, 5, 6, 7 };

    private static int getPFColor() {
        int PF = (PF0 << 16) + (PF1 << 8) + PF2;
        boolean pfMirrored = (CTRLPF & 1) == 1;
        int pfIndex = hPFScanIndex;
        if (hPFCenter && pfMirrored) {
            pfIndex = 19 - hPFScanIndex;
        }
        return (PF & (1 << PFPixelOrderMap[pfIndex])) > 0 ? 1 : 0;
    }

    // --- P0 ---

    private static boolean resp0DuringHBlank = false;
    private static int resp0Counter = 0;
    public static void RESP0(int value) {
        resp0Counter = 4;
        resp0DuringHBlank = hblank;
    }

    public static int REFP0 = 0;
    public static int NUSIZ0 = 0;
    public static int GRP0 = 0;
    public static int COLUP0 = 0;
    
    public static int HMP0 = 0;
    private static int HMP0DX = 0;

    private static int hP0Counter = 0;
    private static boolean hP0Start = false;
    private static int hP0ScanCounter = 0; // 6.2 fixed point
    private static int[] spriteWidth = { 0b100, 0b100, 0b100, 0b100, 0b100, 0b010, 0b100, 0b001 };
    private static boolean p0Start = false;

    private static int updateP0() {
        int pixelP0 = 0;
        int nusiz0Copy = NUSIZ0 & 0b111;
        if ((hP0Counter == 1 && p0Start) ||
                (hP0Counter == 17 && (nusiz0Copy == 1 || nusiz0Copy == 3)) ||
                (hP0Counter == 33 && (nusiz0Copy == 3 || nusiz0Copy == 2 || nusiz0Copy == 6)) ||
                (hP0Counter == 65 && (nusiz0Copy == 4 || nusiz0Copy == 6))) {
            p0Start = false;
            hP0Start = true;
        }
        if (hP0Start) {
            int p0Ref = (hP0ScanCounter >> 2);
            if ((REFP0 & 0b1000) == 0) {
                p0Ref = 7 - p0Ref;
            }
            pixelP0 = (GRP0 & (1 << p0Ref)) > 0 ? 1 : 0;
            int p0Size = spriteWidth[NUSIZ0 & 0b111];
            hP0ScanCounter = (hP0ScanCounter + p0Size) % 32;
            if (hP0ScanCounter == 0) {
                hP0Start = false;
            }
        }
        if (hP0Counter == 156) {
            //RESP0(0);
            p0Start = true;
        }
        hP0Counter = (hP0Counter + 1) % 160; // = (hP0Counter + 1) % 160;
        if (resp0Counter > 0) {
            resp0Counter--;
            if (resp0Counter == 0) {
                hP0Counter = 0;
            }
        }
        return pixelP0;
    }


    // --- P1 ---
    
    private static boolean resp1DuringHBlank = false;
    private static int resp1Counter = 0;
    public static void RESP1(int value) {
        resp1Counter = 4;
        resp1DuringHBlank = hblank;
    }

    public static int REFP1 = 0;
    public static int NUSIZ1 = 0;
    public static int GRP1 = 0;
    public static int COLUP1 = 0;
    
    public static int HMP1 = 0;
    private static int HMP1DX = 0;

    private static int hP1Counter = 0;
    private static boolean hP1Start = false;
    private static int hP1ScanCounter = 0; // 5.3 fixed point
    private static boolean p1Start = false;

    private static int updateP1() {
        int pixelP1 = 0;
        int nusiz1Copy = NUSIZ1 & 0b111;
        if ((hP1Counter == 1 && p1Start) ||
                (hP1Counter == 17 && (nusiz1Copy == 1 || nusiz1Copy == 3)) ||
                (hP1Counter == 33 && (nusiz1Copy == 3 || nusiz1Copy == 2 || nusiz1Copy == 6)) ||
                (hP1Counter == 65 && (nusiz1Copy == 4 || nusiz1Copy == 6))) {

            p1Start = false;
            hP1Start = true;
        }
        if (hP1Start) {
            int p1Ref = (hP1ScanCounter >> 2);
            if ((REFP1 & 0b1000) == 0) {
                p1Ref = 7 - p1Ref;
            }
            pixelP1 = (GRP1 & (1 << p1Ref)) > 0 ? 1 : 0;
            int p1Size = spriteWidth[NUSIZ1 & 0b111];
            hP1ScanCounter = (hP1ScanCounter + p1Size) % 32;
            if (hP1ScanCounter == 0) {
                hP1Start = false;
            }
        }
        if (hP1Counter == 156) {
            p1Start = true;
            //RESP1(0);
        }
        hP1Counter = (hP1Counter + 1) % 160; // = (hP1Counter + 1) % 160;
        if (resp1Counter > 0) {
            resp1Counter--;
            if (resp1Counter == 0) {
                hP1Counter = 0;
            }
        }
        return pixelP1;
    }
    
    // --- hmove ---

    private static boolean hmoveEnabled = false;
    public static void HMOVE(int value) {
        hmoveEnabled = true;
        HMP0DX = (HMP0 >> 4) ^ 0b1000;
        HMP1DX = (HMP1 >> 4) ^ 0b1000;
    }
    
    //   2B      HMCLR   <strobe>  clear horizontal motion registers
    public static void HMCLR(int value) {
        HMP0 = 0;
        HMP1 = 0;
    }

    // --- INPT --

    // 3C      INPT4   1.......  read input
    public static int INPT4 = 0xff; // botao P0 (invertido)
    // 3D      INPT5   1.......  read input
    public static int INPT5 = 0xff; // botao P1 (invertido)

}
