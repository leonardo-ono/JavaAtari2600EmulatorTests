import java.util.Arrays;

public class Bus {
    
/*
  0000-002C  TIA Write
  0000-000D  TIA Read (sometimes mirrored at 0030-003D)
  0080-00FF  PIA RAM (128 bytes)
  0280-0297  PIA Ports and Timer
  F000-FFFF  Cartridge Memory (4 Kbytes area)
*/    
    public static void clearMem() {
        Arrays.fill(Pia.ram, 0);
    }

    public static int read(int address) {
        address = address & 0x1fff;
        boolean a12 = (address & 0b1000000000000) > 0;
        boolean a9 = (address & 0b1000000000) > 0;
        boolean a7 = (address & 0b10000000) > 0;

        if (!a12 && !a7) { //address < 0x80) { //	TIA Read (sometimes mirrored at 0030-003D)
            address = address & 0x3f;
            switch (address) {
                // 30      CXM0P   11......  read collision M0-P1, M0-P0 (Bit 7,6)
                case 0x0: return Tia.CXM0P;
                case 0x30: return Tia.CXM0P;
                // 31      CXM1P   11......  read collision M1-P0, M1-P1
                case 0x01: return Tia.CXM1P;
                case 0x31: return Tia.CXM1P;
                // 32      CXP0FB  11......  read collision P0-PF, P0-BL
                case 0x02: return Tia.CXP0FB;
                case 0x32: return Tia.CXP0FB;
                // 33      CXP1FB  11......  read collision P1-PF, P1-BL
                case 0x03: return Tia.CXP1FB;
                case 0x33: return Tia.CXP1FB;
                // 34      CXM0FB  11......  read collision M0-PF, M0-BL
                case 0x04: return Tia.CXM0FB;
                case 0x34: return Tia.CXM0FB;
                // 35      CXM1FB  11......  read collision M1-PF, M1-BL
                case 0x05: return Tia.CXM1FB;
                case 0x35: return Tia.CXM1FB;
                // 36      CXBLPF  1.......  read collision BL-PF, unused
                case 0x06: return Tia.CXBLPF;
                case 0x36: return Tia.CXBLPF;
                // 37      CXPPMM  11......  read collision P0-P1, M0-M1
                case 0x07: return Tia.CXPPMM;
                case 0x37: return Tia.CXPPMM;

                // 3C      INPT4   1.......  read input
                case 0x0C: return Tia.INPT4;
                case 0x3C: return Tia.INPT4;
                // 3D      INPT5   1.......  read input
                case 0x0D: return Tia.INPT5;
                case 0x3D: return Tia.INPT5;

                default:
                    //System.out.println("NOT HANDLED READ address: " + address);
            }
        }
        else if (!a12 && !a9 && a7) { //address >= 0x0080 && address <= 0x00FF) { // ram
            address = address & 0x7F | 0x80;
            return Pia.ram[address];
        }
        else if (!a12 && a9 && a7) { //address >= 0x0280 && address <= 0x0297) { //	PIA Ports and Timer
            switch (address) {
                //   0280    SWCHA   11111111  Port A; input or output  (read or write)
                case 0x280: return Pia.SWCHA;
                //   0282    SWCHB   11111111  Port B; console switches (read only)
                case 0x282: return Pia.SWCHB;
                //   0284    INTIM   11111111  Timer output (read only)
                case 0x284: return Pia.INTIM();
                //   0285    INSTAT  11......  Timer Status (read only, undocumented)
                case 0x285: return Pia.INSTAT;

                default:
                    //System.out.println("NOT HANDLED READ address: " + address);
            }
        }
        else if (a12) { // address >= 0x1000 && address <= 0x1FFF) { //	Cartridge Memory (4 Kbytes area)
            return Cartridge.rom[address + 0xe000 + Cartridge.romOffset];
        }
        else {
            //System.out.println("NOT HANDLED READ address: " + address);
            
            // for E0 - 8k cartridge:
            // LDA $1FE0	select bank 0
            // LDA $1FE1	select bank 1
            // LDA $1FE2	select bank 2
            // LDA $1FE3	select bank 3            
            if (Display.romFileSize == 8192) {
                switch (address) {
                    case 0x1FE0 -> Cartridge.romOffset = 0;
                    case 0x1FE1 -> Cartridge.romOffset = 1024;
                    case 0x1FE2 -> Cartridge.romOffset = 2048;
                    case 0x1FE3 -> Cartridge.romOffset = 3072;
                }
            }

        }

        return 0; //mem[address & 0xffff] & 0xff;
    }

    public static boolean postWrite = false;
    private static int writeAddress;
    private static int writeValue;
    private static int writeAddress2;
    private static int writeValue2;
    private static int writeCount;
    private static boolean write2 = false;
    
    public static void write(int address, int value) {

        if (postWrite) {
            writeAddress2 = address;
            writeValue2 = value;
            write2 = true;
            return;
        }
        writeAddress = address;
        writeValue = value;
        postWrite = true;
        writeCount++;
        if (writeCount > 2) {
            System.out.println();
        }
    }
    
    public static void commitPostWrite() {
        if (postWrite) {
            writePost(writeAddress, writeValue);
            if (write2) {
                writePost(writeAddress2, writeValue2);
                write2 = false;
            }
            postWrite = false;
            writeCount = 0;
        }
    }

    // test
    private static int saveWriteAddress;
    private static int saveWriteValue;
    private static int saveWriteAddress2;
    private static int saveWriteValue2;
    private static int saveWriteCount;
    public static boolean saveWrite = false;
    public static void savePostWrite() {
        if (postWrite) {
            saveWriteAddress = writeAddress;
            saveWriteValue = writeValue;
            saveWriteAddress2 = writeAddress2;
            saveWriteValue2 = writeValue2;
            saveWriteCount = writeCount;
            saveWrite = true;
        }
    }
    
    public static void commitSaveWrite() {
        saveWrite = false;
        writePost(saveWriteAddress, saveWriteValue);
        if (saveWriteCount > 1) {
            writePost(saveWriteAddress2, saveWriteValue2);
        }
    }

    public static void writePost(int address, int value) {
        address = address & 0x1fff;
        boolean a12 = (address & 0b1000000000000) > 0;
        boolean a9 = (address & 0b1000000000) > 0;
        boolean a7 = (address & 0b10000000) > 0;

        if (!a12 && !a9 && a7) { // ram
            address = address & 0x7F | 0x80;
            Pia.ram[address] = value;
        }
        else if (!a12 && !a7) { // address >= 0x0000 && address <= 0x002C) { // TIA Write
            address = address & 0x7f;
            switch (address) {
                //   00      VSYNC   ......1.  vertical sync set-clear
                case 0x00 -> Tia.VSYNC(value);
                //   01      VBLANK  11....1.  vertical blank set-clear
                case 0x01 -> Tia.VBLANK(value);
                //   02      WSYNC   <strobe>  wait for leading edge of horizontal blank
                case 0x02 -> Tia.WSYNC(value);
                
                //   03      RSYNC   <strobe>  reset horizontal sync counter
                case 0x03 -> Tia.RSYNC(value);

                //   04      NUSIZ0  ..111111  number-size player-missile 0
                case 0x04 -> {
                    Tia.NUSIZ0 = value;
                    //if (value == 0) {
                    //    System.out.println("nusiz0=0 crty=" + Tia.crtY);
                    //    Debugger.pause();
                    //}
                }
                //   05      NUSIZ1  ..111111  number-size player-missile 1
                case 0x05 -> Tia.NUSIZ1 = value;

                //   06      COLUP0  1111111.  color-lum player 0 and missile 0
                case 0x06 -> Tia.COLUP0 = value;
                //   07      COLUP1  1111111.  color-lum player 1 and missile 1
                case 0x07 -> Tia.COLUP1 = value;

                //   08      COLUPF  1111111.  color-lum playfield and ball
                case 0x08 -> Tia.COLUPF = value;
                //   09      COLUBK  1111111.  color-lum background
                case 0x09 -> Tia.COLUBK = value;
                //   0A      CTRLPF  ..11.111  control playfield ball size & collisions
                case 0x0A -> Tia.CTRLPF = value;

                //   0B      REFP0   ....1...  reflect player 0
                case 0x0B -> Tia.REFP0 = value;
                //   0C      REFP1   ....1...  reflect player 1
                case 0x0C -> Tia.REFP1 = value;

                //   0D      PF0     1111....  playfield register byte 0
                case 0x0D -> Tia.PF0 = value;
                //   0E      PF1     11111111  playfield register byte 1
                case 0x0E -> Tia.PF1 = value;
                //   0F      PF2     11111111  playfield register byte 2
                case 0x0F -> Tia.PF2 = value;
                
                //   10      RESP0   <strobe>  reset player 0
                case 0x10 -> Tia.RESP0(value);
                //   11      RESP1   <strobe>  reset player 1
                case 0x11 -> Tia.RESP1(value);
                //   12      RESM0   <strobe>  reset missile 0
                case 0x12 -> Tia.RESM0(value);
                //   13      RESM1   <strobe>  reset missile 1
                case 0x13 -> Tia.RESM1(value);
                //   14      RESBL   <strobe>  reset ball
                case 0x14 -> Tia.RESBL(value);

                //   15      AUDC0   ....1111  audio control 0
                case 0x15 -> TiaAudio.AUD0.AUDC = value;
                //   16      AUDC1   ....1111  audio control 1
                case 0x16 -> TiaAudio.AUD1.AUDC = value;
                //   17      AUDF0   ...11111  audio frequency 0
                case 0x17 -> TiaAudio.AUD0.AUDF = value;
                //   18      AUDF1   ...11111  audio frequency 1
                case 0x18 -> TiaAudio.AUD1.AUDF = value;
                //   19      AUDV0   ....1111  audio volume 0
                case 0x19 -> TiaAudio.AUD0.AUDV = value;
                //   1A      AUDV1   ....1111  audio volume 1
                case 0x1A -> TiaAudio.AUD1.AUDV = value;

                //   1B      GRP0    11111111  graphics player 0
                case 0x1B -> Tia.GRP0(value);
                //   1C      GRP1    11111111  graphics player 1
                case 0x1C -> Tia.GRP1(value);

                //   1D      ENAM0   ......1.  graphics (enable) missile 0
                case 0x1D -> Tia.ENAM0(value);
                //   1E      ENAM1   ......1.  graphics (enable) missile 1
                case 0x1E -> Tia.ENAM1(value);
                //   1F      ENABL   ......1.  graphics (enable) ball
                case 0x1F -> Tia.ENABL(value);

                //   20      HMP0    1111....  horizontal motion player 0
                case 0x20 -> Tia.HMP0 = value;
                //   21      HMP1    1111....  horizontal motion player 1
                case 0x21 -> Tia.HMP1 = value;
                //   22      HMM0    1111....  horizontal motion missile 0
                case 0x22 -> Tia.HMM0 = value;
                //   23      HMM1    1111....  horizontal motion missile 1
                case 0x23 -> Tia.HMM1 = value;
                //   24      HMBL    1111....  horizontal motion ball
                case 0x24 -> Tia.HMBL = value;

                //   25      VDELP0  .......1  vertical delay player 0
                case 0x25 -> Tia.VDELP0 = value;
                //   26      VDELP1  .......1  vertical delay player 1
                case 0x26 -> Tia.VDELP1 = value;

                //   27      VDELBL  .......1  vertical delay ball
                case 0x27 -> Tia.VDELBL = value;
                //   28      RESMP0  ......1.  reset missile 0 to player 0
                case 0x28 -> Tia.RESMP0(value);
                //   29      RESMP1  ......1.  reset missile 1 to player 1
                case 0x29 -> Tia.RESMP1(value);

                //   2A      HMOVE   <strobe>  apply horizontal motion
                case 0x2A -> Tia.HMOVE(value);
                //   2B      HMCLR   <strobe>  clear horizontal motion registers
                case 0x2B -> Tia.HMCLR(value);
                
                //   2C      CXCLR   <strobe>  clear collision latches    
                case 0x2C -> Tia.CXCLR(value);
                
                // 2D~1F do nothing ok
                //default ->
                //    System.out.println("NOT HANDLED WRITE address: " + address + " value: " + value);
            }
        }	
        else if (!a12 && a9 && a7) { // address >= 0x0280 && address <= 0x0297) { //	PIA Ports and Timer
            switch (address) {
                //   0280    SWCHA   11111111  Port A; input or output  (read or write)
                case 0x280 -> Pia.SWCHA = value;

                //   0294    TIM1T   11111111  set 1 clock interval (838 nsec/interval)
                case 0x284 -> Pia.TIM1T(value);
                case 0x294 -> Pia.TIM1T(value);
                //   0295    TIM8T   11111111  set 8 clock interval (6.7 usec/interval)
                case 0x285 -> Pia.TIM8T(value);
                case 0x295 -> Pia.TIM8T(value);
                //   0296    TIM64T  11111111  set 64 clock interval (53.6 usec/interval)
                case 0x286 -> Pia.TIM64T(value);
                case 0x296 -> Pia.TIM64T(value);
                //   0297    T1024T  11111111  set 1024 clock interval (858.2 usec/interval)
                case 0x287 -> Pia.T1024T(value);
                case 0x297 -> Pia.T1024T(value);
                
                //default ->
                //    System.out.println("NOT HANDLED WRITE address: " + address + " value: " + value);
            }
        }
        else {
  
            // for F8 - 8k cartridge:
            // $1FF8 → select bank 0
            // $1FF9 → select bank 1
            if (Display.romFileSize == 8192) {
                switch (address) {
                    case 0x1ff8 -> Cartridge.romOffset = 0;
                    case 0x1ff9 -> Cartridge.romOffset = 4096;
                }
            }

            //System.out.println("NOT HANDLED WRITE address: " + address + " value: " + value);
        }
    }

}