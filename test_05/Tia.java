import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class Tia {

    public static BufferedImage crt = new BufferedImage(228, 262, BufferedImage.TYPE_INT_RGB);
    public static Graphics2D crtg = (Graphics2D) crt.getGraphics();
    public static int crtX;
    public static int crtY;

    private static final int CRT_WIDTH = 228; // ntsc

    private static int currentPixelClock = 0;
    private static int targetPixelClock = 0;

    private static boolean hblank;

    public static void process(int pixelCycles) {
        //targetPixelClock += cpuCycles * 3;
        targetPixelClock += pixelCycles;
        
        //System.out.print(((targetPixelClock % CRT_WIDTH)-68) + "/" + hBLCounter + " "); // <------------------ apagar

        while (currentPixelClock < targetPixelClock) {
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
            if (hblank && hmoveEnabled && (scanlineX % 4 == 0) && (HMM0DX > 0)) {
                hMP0Counter = (hMP0Counter + 1) % 160;
                HMM0DX--;
            }
            if (hblank && hmoveEnabled && (scanlineX % 4 == 0) && (HMM1DX > 0)) {
                hMP1Counter = (hMP1Counter + 1) % 160;
                HMM1DX--;
            }
            if (hblank && hmoveEnabled && (scanlineX % 4 == 0) && (HMBLDX > 0)) {
                hBLCounter = (hBLCounter + 1) % 160;
                HMBLDX--;
            }

            int color = 0xff000000;

            if (!hblank) {
                collisions = 0; // reset collision mask

                hmoveEnabled = false;
                
                int pfIndex = scanlineX - 68;

                int pixelP0 = updateP0();
                int pixelP1 = updateP1();
                int pixelMP0 = updateMP0();
                int pixelMP1 = updateMP1();
                int pixelBL = updateBL();

                if (!vblankSet) {
                    color = Atari2600PaletteNTSC.colorsNTSC[(COLUBK & 0xff) >> 1].getRGB();
                    boolean pfPixel = getPFColor() == 1;
                    int pfColor = color = pfPixel ? Atari2600PaletteNTSC.colorsNTSC[(COLUPF & 0xff) >> 1].getRGB() : color;

                    // 1    Playfield Color          1=Score Mode
                    if (pfPixel && (CTRLPF & 0b10) > 0) {
                        if (pfIndex >= 80) {
                            pfColor = color = Atari2600PaletteNTSC.colorsNTSC[(COLUP1 & 0xff) >> 1].getRGB();
                        }
                        else {
                            pfColor = color = Atari2600PaletteNTSC.colorsNTSC[(COLUP0 & 0xff) >> 1].getRGB();
                        }
                    }

                    color = pixelBL == 1 ? Atari2600PaletteNTSC.colorsNTSC[(COLUPF & 0xff) >> 1].getRGB() : color;
                    color = pixelMP1 == 1 ? Atari2600PaletteNTSC.colorsNTSC[(COLUP1 & 0xff) >> 1].getRGB() : color;
                    color = pixelMP0 == 1 ? Atari2600PaletteNTSC.colorsNTSC[(COLUP0 & 0xff) >> 1].getRGB() : color;
                    color = pixelP1 == 1 ? Atari2600PaletteNTSC.colorsNTSC[(COLUP1 & 0xff) >> 1].getRGB() : color;
                    color = pixelP0 == 1 ? Atari2600PaletteNTSC.colorsNTSC[(COLUP0 & 0xff) >> 1].getRGB() : color;

                    
                    if (pfPixel && (CTRLPF & 0b100) > 0) {
                        color = pfColor;
                    }
                    
                    updateCollisionRegisters();
                }
            }

            crtX = scanlineX;
            try {
                crt.setRGB(crtX, crtY, color);
            }
            catch (Exception e) {}

            //System.out.println("scanlineX: " + scanlineX + " hblank: " + hblank + " crtY: " + crtY);
            
            if (scanlineX == 227) { 
                wsyncHaltCpu = false;
                crtY++; // next line
                
                //System.out.println("crtY: " + crtY);
                //System.out.println(""); // <------------------ apagar
            }
            
            // TODO fix hmove test
            if (hmoveActivated > 0) {
                hmoveActivated--;
                if (hmoveActivated == 0) {
                    activateHMOVE();
                }
            }
            
            currentPixelClock++;

            // bitmap.a26 -> a cor do playfield precisa ser obtida logo depois do incremento do currentPixelClock
            // workaround, playfield bit pixel is updated only 
            scanlineX = currentPixelClock % CRT_WIDTH;
            int pfIndex = scanlineX - 68;
            updatePF(pfIndex);

            prevGRP0new = GRP0new;
            prevGRP0old = GRP0old;
            prevVDELP0 = VDELP0;
            prevREFP0 = REFP0;
            prevNUSIZ0 = NUSIZ0;            

            prevGRP1new = GRP1new;
            prevGRP1old = GRP1old;
            prevVDELP1 = VDELP1;
            prevREFP1 = REFP1;
            prevNUSIZ1 = NUSIZ1;         

            prevGRBLnew = GRBLnew;
            prevGRBLold = GRBLold;
        }
    }

    public static int lastCrtY = 226;
    public static boolean renderTime = false;
    private static boolean vsyncSet = false;
    private static int vsyncCurrentScanline;
    
    public static void VSYNC(int arg) {
        int vsyncScanlineCount = crtY - vsyncCurrentScanline;
        if (vsyncSet && (arg & 2) == 0 && vsyncScanlineCount >= 1 && vsyncScanlineCount <= 4) {
            vsyncSet = false;
            lastCrtY = crtY;
            // System.out.println("new frame, last scanline: " + lastCrtY);
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
        int scanlineX = currentPixelClock % CRT_WIDTH;

        // TODO wsync invoked exactly when scanline x=0
        //System.out.println("wsync scanline x=" + scanlineX);
        if (scanlineX == 0) { 
            wsyncHaltCpu = false;
        }
    }
    
    public static void RSYNC(int arg) {
        currentPixelClock = 0;
        targetPixelClock = 0;
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

        // os bits sao atualizados somente no inicio de cada 4 pixels ?
        if ((pfIndex % 4) == 0) {
            PF = (PF0 << 16) + (PF1 << 8) + PF2;
        }
    }

    //            PF0      PF1      PF2
    //  bits 2222xxxx 11111100 00000000         
    //       3210xxxx 54321098 76543210
    //
    //                      11 11111111
    // order 3210     45678901 98765432
    private static final int[] PFPixelOrderMap 
        = { 20, 21, 22, 23, 15, 14, 13, 12, 11, 10, 9, 8, 0, 1, 2, 3, 4, 5, 6, 7 };
    
    private static int PF = 0;

    private static int getPFColor() {
        boolean pfMirrored = (CTRLPF & 1) == 1;
        int pfIndex = hPFScanIndex;
        if (hPFCenter && pfMirrored) {
            pfIndex = 19 - hPFScanIndex;
        }
        int pixel = (PF & (1 << PFPixelOrderMap[pfIndex])) > 0 ? 1 : 0;
        if (pixel == 1) {
            setCollisionBit(COLLISION_BIT_PF);
        }
        return pixel;
    }

    // --- P0 ---
    
    public static int prevVDELP0 = 0;
    public static int VDELP0 = 0;

    public static void RESP0(int value) {
        // hblank global esta com valor errado aqui qdo (currentPixelClock % CRT_WIDTH) = 0
        boolean hblank2 = (currentPixelClock % CRT_WIDTH) < 68;
        if (hblank2) {
            hP0Counter = 158;
        }
        else {
            hP0Counter = 156;
        }        
    }

    public static void GRP0(int value) {
        GRP0new = value;
        GRP1old = GRP1new;
    }

    public static int GRP0 = 0;
    public static int prevREFP0 = 0;
    public static int REFP0 = 0;
    public static int prevNUSIZ0 = 0;
    public static int NUSIZ0 = 0;
    public static int prevGRP0new = 0;
    public static int prevGRP0old = 0;
    public static int GRP0new = 0;
    public static int GRP0old = 0;
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
        int nusiz0Copy = prevNUSIZ0 & 0b111;
        if ((hP0Counter == 1 && p0Start) ||
                (hP0Counter == 17 && (nusiz0Copy == 1 || nusiz0Copy == 3)) ||
                (hP0Counter == 33 && (nusiz0Copy == 3 || nusiz0Copy == 2 || nusiz0Copy == 6)) ||
                (hP0Counter == 65 && (nusiz0Copy == 4 || nusiz0Copy == 6))) {
            p0Start = false;
            hP0Start = true;
            hP0ScanCounter = 0;
        }
        if (hP0Start) {
            int p0Ref = (hP0ScanCounter >> 2);
            if ((prevREFP0 & 0b1000) == 0) {
                p0Ref = 7 - p0Ref;
            }
            GRP0 = (prevVDELP0 & 1) > 0 ? prevGRP0old : prevGRP0new;
            pixelP0 = (GRP0 & (1 << p0Ref)) > 0 ? 1 : 0;
            int p0Size = spriteWidth[prevNUSIZ0 & 0b111];
            hP0ScanCounter = (hP0ScanCounter + p0Size); // % 32;
            if (hP0ScanCounter > 0b11111) {
                hP0Start = false;
            }
        }
        if (hP0Counter == 156) {
            p0Start = true;
        }
        hP0Counter = (hP0Counter + 1) % 160; // = (hP0Counter + 1) % 160;

        if (pixelP0 == 1) {
            setCollisionBit(COLLISION_BIT_P0);
        }
        return pixelP0;
    }


    // --- P1 ---
    
    public static int prevVDELP1 = 0;
    public static int VDELP1 = 0;

    public static void RESP1(int value) {
        boolean hblank2 = (currentPixelClock % CRT_WIDTH) < 68;
        if (hblank2) {
            hP1Counter = 158;
        }
        else {
            hP1Counter = 156;
        }            
    }
    
    public static void GRP1(int value) {
        GRP1new = value;
        GRP0old = GRP0new;
        GRBLold = GRBLnew;
    }

    public static int GRP1 = 0;
    public static int prevREFP1 = 0;
    public static int REFP1 = 0;
    public static int prevNUSIZ1 = 0;
    public static int NUSIZ1 = 0;
    public static int prevGRP1new = 0;
    public static int prevGRP1old = 0;
    public static int GRP1new = 0;
    public static int GRP1old = 0;
    public static int COLUP1 = 0;
    
    public static int HMP1 = 0;
    private static int HMP1DX = 0;

    private static int hP1Counter = 0;
    private static boolean hP1Start = false;
    private static int hP1ScanCounter = 0; // 5.3 fixed point
    private static boolean p1Start = false;

    private static int updateP1() {
        int pixelP1 = 0;
        int nusiz1Copy = prevNUSIZ1 & 0b111;
        if ((hP1Counter == 1 && p1Start) ||
                (hP1Counter == 17 && (nusiz1Copy == 1 || nusiz1Copy == 3)) ||
                (hP1Counter == 33 && (nusiz1Copy == 3 || nusiz1Copy == 2 || nusiz1Copy == 6)) ||
                (hP1Counter == 65 && (nusiz1Copy == 4 || nusiz1Copy == 6))) {

            p1Start = false;
            hP1Start = true;
            hP1ScanCounter = 0;
        }
        if (hP1Start) {
            int p1Ref = (hP1ScanCounter >> 2);
            if ((prevREFP1 & 0b1000) == 0) {
                p1Ref = 7 - p1Ref;
            }
            GRP1 = (prevVDELP1 & 1) > 0 ? prevGRP1old : prevGRP1new;
            pixelP1 = (GRP1 & (1 << p1Ref)) > 0 ? 1 : 0;
            int p1Size = spriteWidth[prevNUSIZ1 & 0b111];
            hP1ScanCounter = (hP1ScanCounter + p1Size);
            if (hP1ScanCounter > 0b11111) {
                hP1Start = false;
                hP1ScanCounter = 0;
            }
        }
        if (hP1Counter == 156) {
            p1Start = true;
        }
        hP1Counter = (hP1Counter + 1) % 160; // = (hP1Counter + 1) % 160;

        if (pixelP1 == 1) {
            setCollisionBit(COLLISION_BIT_P1);
        }
        return pixelP1;
    }

    // --- M0 ---

    //   12      RESM0   <strobe>  reset missile 0
    //   1D      ENAM0   ......1.  graphics (enable) missile 0
    //   22      HMM0    1111....  horizontal motion missile 0
    //   28      RESMP0  ......1.  reset missile 0 to player 0

    public static void ENAM0(int value) {
        GRMP0 = (value & 0b00000010) > 0 ? 0x01 : 0x00;
    }   

    private static int RESMP0 = 0;

    public static void RESMP0(int value) {
        RESMP0 = value;
        if ((RESMP0 & 0b00000010) == 0) {
            hMP0Counter = hP0Counter;
        }
    }

    public static void RESM0(int value) {
        boolean hblank2 = (currentPixelClock % CRT_WIDTH) < 68;
        if (hblank2) {
            hMP0Counter = 158;
        }
        else {
            hMP0Counter = 156;
        }            
    }

    private static int GRMP0 = 0;
    public static int HMM0 = 0;
    private static int HMM0DX = 0;

    private static int hMP0Counter = 0;
    private static boolean hMP0Start = false;
    private static int hMP0ScanCounter = 0; // 6.2 fixed point
    private static boolean mp0Start = false;

    private static int[] spriteMissileWidth = { 0b1000, 0b0100, 0b0010, 0b0001 };

    private static int updateMP0() {
        int pixelMP0 = 0;
        int nusiz0Copy = NUSIZ0 & 0b111;
        if ((hMP0Counter == 0 && mp0Start) ||
                (hMP0Counter == 16 && (nusiz0Copy == 1 || nusiz0Copy == 3)) ||
                (hMP0Counter == 32 && (nusiz0Copy == 3 || nusiz0Copy == 2 || nusiz0Copy == 6)) ||
                (hMP0Counter == 64 && (nusiz0Copy == 4 || nusiz0Copy == 6))) {
            mp0Start = false;
            hMP0Start = true;
            hMP0ScanCounter = 0;
        }
        if (hMP0Start) {
            int mp0Ref = (hMP0ScanCounter >> 3);
            pixelMP0 = (GRMP0 & (1 << mp0Ref)) > 0 ? 1 : 0;
            if ((RESMP0 & 0b00000010) == 1) pixelMP0 = 0;
            int mp0Size = spriteMissileWidth[(NUSIZ0 & 0b110000) >> 4];
            hMP0ScanCounter = (hMP0ScanCounter + mp0Size); // % 64;
            if (hMP0ScanCounter > 0b1111111) {
                hMP0Start = false;
            }
        }
        if (hMP0Counter == 156) {
            mp0Start = true;
        }
        hMP0Counter = (hMP0Counter + 1) % 160; // = (hP0Counter + 1) % 160;

        if (pixelMP0 == 1) {
            setCollisionBit(COLLISION_BIT_M0);
        }
        return pixelMP0;
    }

    // --- M1 ---
    
    //   13      RESM1   <strobe>  reset missile 1
    //   29      RESMP1  ......1.  reset missile 1 to player 1    
    //   1E      ENAM1   ......1.  graphics (enable) missile 1
    //   23      HMM1    1111....  horizontal motion missile 1

    public static void ENAM1(int value) {
        GRMP1 = (value & 0b00000010) > 0 ? 0x01 : 0x00;
    }   

    private static int RESMP1 = 0;
    
    public static void RESMP1(int value) {
        RESMP1 = value;
        if ((RESMP1 & 0b00000010) == 0) {
            hMP1Counter = hP1Counter;
        }
    }

    public static void RESM1(int value) {
        boolean hblank2 = (currentPixelClock % CRT_WIDTH) < 68;
        if (hblank2) {
            hMP1Counter = 158;
        }
        else {
            hMP1Counter = 156;
        }            
    }

    private static int GRMP1 = 0;
    public static int HMM1 = 0;
    private static int HMM1DX = 0;

    private static int hMP1Counter = 0;
    private static boolean hMP1Start = false;
    private static int hMP1ScanCounter = 0; // 6.2 fixed point
    private static boolean mp1Start = false;

    private static int updateMP1() {
        int pixelMP1 = 0;
        int nusiz1Copy = NUSIZ1 & 0b111;
        if ((hMP1Counter == 0 && mp1Start) ||
                (hMP1Counter == 16 && (nusiz1Copy == 1 || nusiz1Copy == 3)) ||
                (hMP1Counter == 32 && (nusiz1Copy == 3 || nusiz1Copy == 2 || nusiz1Copy == 6)) ||
                (hMP1Counter == 64 && (nusiz1Copy == 4 || nusiz1Copy == 6))) {
            mp1Start = false;
            hMP1Start = true;
            hMP1ScanCounter = 0;
        }
        if (hMP1Start) {
            int mp1Ref = (hMP1ScanCounter >> 3);
            pixelMP1 = (GRMP1 & (1 << mp1Ref)) > 0 ? 1 : 0;
            if ((RESMP1 & 0b00000010) == 1) pixelMP1 = 0;
            int mp1Size = spriteMissileWidth[(NUSIZ1 & 0b110000) >> 4];
            hMP1ScanCounter = (hMP1ScanCounter + mp1Size);// % 64;
            if (hMP1ScanCounter > 0b1111111) {
                hMP1Start = false;
            }
        }
        if (hMP1Counter == 156) {
            mp1Start = true;
        }
        hMP1Counter = (hMP1Counter + 1) % 160; // = (hP1Counter + 1) % 160;

        if (pixelMP1 == 1) {
            setCollisionBit(COLLISION_BIT_M1);
        }
        return pixelMP1;
    }
  
    // --- BALL ---
    //   14      RESBL   <strobe>  reset ball
    //   1F      ENABL   ......1.  graphics (enable) ball
    //   24      HMBL    1111....  horizontal motion ball
    //   27      VDELBL  .......1  vertical delay ball
    
    public static int prevVDELBL = 0;
    public static int VDELBL = 0;

    public static void ENABL(int value) {
        GRBLnew = (value & 0b00000010) > 0 ? 0x01 : 0x00;
    }   

    public static void RESBL(int value) {
        // hblank global esta com valor errado aqui qdo (currentPixelClock % CRT_WIDTH) = 0
        boolean hblank2 = (currentPixelClock % CRT_WIDTH) < 68;
        if (hblank2) {
            hBLCounter = 158;
        }
        else {
            hBLCounter = 156;
        }
    }

    private static int prevGRBLnew = 0;
    private static int prevGRBLold = 0;
    private static int GRBLnew = 0;
    private static int GRBLold = 0;
    public static int HMBL = 0;
    private static int HMBLDX = 0;
    
    private static int hBLCounter = 0;
    private static boolean hBLStart = false;
    private static int hBLScanCounter = 0; // 6.2 fixed point

    private static int updateBL() {
        int pixelBL = 0;
        if (hBLCounter == 0 ) {
            hBLStart = true;
            hBLScanCounter = 0;
        }
        if (hBLStart) {
            int blRef = (hBLScanCounter >> 3);
            int GRBL = (prevVDELBL & 1) > 0 ? prevGRBLold : prevGRBLnew;
            pixelBL = (GRBL & (1 << blRef)) > 0 ? 1 : 0;
            int blSize = spriteMissileWidth[(CTRLPF & 0b110000) >> 4];
            hBLScanCounter = (hBLScanCounter + blSize); // % 64;
            if (hBLScanCounter > 0b1111111) {
                hBLStart = false;
            }
        }
        hBLCounter = (hBLCounter + 1) % 160; // = (hP1Counter + 1) % 160;
        if (pixelBL == 1) {
            setCollisionBit(COLLISION_BIT_BL);
        }
        return pixelBL;
    }


    // --- hmove ---

    private static boolean hmoveEnabled = false;
    private static int hmoveActivated = 0;

    public static void HMOVE(int value) {
        hmoveActivated = 6;
    }

    private static void activateHMOVE() {
        hmoveActivated = 0;
        hmoveEnabled = true;
        HMP0DX = ((HMP0 >> 4) ^ 0b1000);
        HMP1DX = ((HMP1 >> 4) ^ 0b1000);
        HMM0DX = ((HMM0 >> 4) ^ 0b1000);
        HMM1DX = ((HMM1 >> 4) ^ 0b1000);
        HMBLDX = ((HMBL >> 4) ^ 0b1000);
    }
    
    //   2B      HMCLR   <strobe>  clear horizontal motion registers
    public static void HMCLR(int value) {
        HMP0 = 0;
        HMP1 = 0;
        HMM0 = 0;
        HMM1 = 0;
        HMBL = 0;
    }

    // --- collisions ---
    
    private static final int COLLISION_BIT_P0 = 0;
    private static final int COLLISION_BIT_P1 = 1;
    private static final int COLLISION_BIT_M0 = 2;
    private static final int COLLISION_BIT_M1 = 3;
    private static final int COLLISION_BIT_BL = 4;
    private static final int COLLISION_BIT_PF = 5;
    private static int collisions = 0;

    private static void setCollisionBit(int collisionBit) {
        collisions = collisions | (1 << collisionBit);
    }
    
    private static boolean checkCollision(int collisionPair) {
        return (collisions & collisionPair) == collisionPair;
    }

    private static final int COLLISION_M0_P1 = 0b00000110;
    private static final int COLLISION_M0_P0 = 0b00000101;
    private static final int COLLISION_M1_P1 = 0b00001010;
    private static final int COLLISION_M1_P0 = 0b00001001;
    private static final int COLLISION_P0_PF = 0b00100001;
    private static final int COLLISION_P0_BL = 0b00010001;
    private static final int COLLISION_P1_PF = 0b00100010;
    private static final int COLLISION_P1_BL = 0b00010010;
    private static final int COLLISION_M0_PF = 0b00100100;
    private static final int COLLISION_M0_BL = 0b00010100;
    private static final int COLLISION_M1_PF = 0b00101000;
    private static final int COLLISION_M1_BL = 0b00011000;
    private static final int COLLISION_BL_PF = 0b00110000;
    private static final int COLLISION_P0_P1 = 0b00000011;
    private static final int COLLISION_M0_M1 = 0b00001100;

    private static void updateCollisionRegisters() {
        if (checkCollision(COLLISION_M0_P1)) {
            CXM0P = CXM0P | 0b10000000;
            //System.out.println("#111");
        }
        if (checkCollision(COLLISION_M0_P0)) {
            CXM0P = CXM0P | 0b01000000;
            //System.out.println("#222");
        }
        if (checkCollision(COLLISION_M1_P1)) {
            CXM1P = CXM1P | 0b01000000;
            //System.out.println("#333");
        }
        if (checkCollision(COLLISION_M1_P0)) {
            CXM1P = CXM1P | 0b10000000;
            //System.out.println("#444");
        }
        if (checkCollision(COLLISION_P0_PF)) {
            CXP0FB = CXP0FB | 0b10000000;
            //System.out.println("#555");
        }
        if (checkCollision(COLLISION_P0_BL)) {
            CXP0FB = CXP0FB | 0b01000000;
            //System.out.println("#666");
        }

        if (checkCollision(COLLISION_P1_PF)) {
            CXP1FB = CXP1FB | 0b10000000;
            //System.out.println("#555bbb");
        }
        if (checkCollision(COLLISION_P1_BL)) {
            CXP1FB = CXP1FB | 0b01000000;
            //System.out.println("#666bbb");
        }

        if (checkCollision(COLLISION_M0_PF)) {
            CXM0FB = CXM0FB | 0b10000000;
            //System.out.println("#777");
        }
        if (checkCollision(COLLISION_M0_BL)) {
            CXM0FB = CXM0FB | 0b01000000;
            //System.out.println("#888");
        }
        if (checkCollision(COLLISION_M1_PF)) {
            CXM1FB = CXM1FB | 0b10000000;
            //System.out.println("#999");
        }
        if (checkCollision(COLLISION_M1_BL)) {
            CXM1FB = CXM1FB | 0b01000000;
            //System.out.println("#aaa");
        }
        if (checkCollision(COLLISION_BL_PF)) {
            CXBLPF = CXBLPF | 0b10000000;
            //System.out.println("#bbb");
        }
        if (checkCollision(COLLISION_P0_P1)) {
            CXPPMM = CXPPMM | 0b10000000;
            //System.out.println("#ccc");
        }
        if (checkCollision(COLLISION_M0_M1)) { 
            CXPPMM = CXPPMM | 0b01000000;
            //System.out.println("#ddd");
        }
    }

    //   2C      CXCLR   <strobe>  clear collision latches    
    public static void CXCLR(int value) {
        CXM0P = 0;
        CXM1P = 0;
        CXP0FB = 0;
        CXP1FB = 0;
        CXM0FB = 0;
        CXM1FB = 0;
        CXBLPF = 0;
        CXPPMM = 0;
    }

    // 30      CXM0P   11......  read collision M0-P1, M0-P0 (Bit 7,6)
    // 31      CXM1P   11......  read collision M1-P0, M1-P1
    // 32      CXP0FB  11......  read collision P0-PF, P0-BL
    // 33      CXP1FB  11......  read collision P1-PF, P1-BL
    // 34      CXM0FB  11......  read collision M0-PF, M0-BL
    // 35      CXM1FB  11......  read collision M1-PF, M1-BL
    // 36      CXBLPF  1.......  read collision BL-PF, unused
    // 37      CXPPMM  11......  read collision P0-P1, M0-M1
    public static int CXM0P = 0;
    public static int CXM1P = 0;
    public static int CXP0FB = 0;
    public static int CXP1FB = 0;
    public static int CXM0FB = 0;
    public static int CXM1FB = 0;
    public static int CXBLPF = 0;
    public static int CXPPMM = 0;

    // --- INPT --

    // 3C      INPT4   1.......  read input
    public static int INPT4 = 0xff; // botao P0 (invertido)
    // 3D      INPT5   1.......  read input
    public static int INPT5 = 0xff; // botao P1 (invertido)

}
