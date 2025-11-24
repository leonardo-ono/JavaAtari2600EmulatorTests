import java.util.Arrays;

public class Bus {
    
    public static int[] mem = new int[0x10000];

/*
  0000-002C  TIA Write
  0000-000D  TIA Read (sometimes mirrored at 0030-003D)
  0080-00FF  PIA RAM (128 bytes)
  0280-0297  PIA Ports and Timer
  F000-FFFF  Cartridge Memory (4 Kbytes area)
*/    
    public static void clearMem() {
        Arrays.fill(mem, 0);
    }

    public static int read(int address) {
        if (address <= 0x80) { //	TIA Read (sometimes mirrored at 0030-003D)
            switch (address) {
                // 3C      INPT4   1.......  read input
                case 0x0C: return Tia2.INPT4;
                // 3D      INPT5   1.......  read input
                case 0x0D: return Tia2.INPT5;
            }
        }
        else if (address >= 0x0080 && address <= 0x00FF) { // ram
            return mem[address];
        }
        else if (address >= 0x0180 && address <= 0x01FF) { // ram mirror (stack)
            return mem[address - 0x100];
        }
        else if (address >= 0x003C && address <= 0x003D) { //	TIA Read (sometimes mirrored at 0030-003D)
            switch (address) {
                // 3C      INPT4   1.......  read input
                case 0x3C: return Tia2.INPT4;
                // 3D      INPT5   1.......  read input
                case 0x3D: return Tia2.INPT5;
            }
        }
        /*
        if (address >= 0x0000 && address <= 0x002C) { // TIA Write
        }	
        else if (address >= 0x0080 && address <= 0x00FF) { //	PIA RAM (128 bytes)
            return mem[address];
        }
        */
        else if (address >= 0x0280 && address <= 0x0297) { //	PIA Ports and Timer
            switch (address) {
                //   0280    SWCHA   11111111  Port A; input or output  (read or write)
                case 0x280: return Pia.SWCHA;
                //   0282    SWCHB   11111111  Port B; console switches (read only)
                case 0x282: return Pia.SWCHB;
                //   0284    INTIM   11111111  Timer output (read only)
                case 0x284: return Pia.INTIM;
                //   0285    INSTAT  11......  Timer Status (read only, undocumented)
                case 0x285: return Pia.INSTAT;
            }
        }
        else if (address >= 0xF000 && address <= 0xFFFF) { //	Cartridge Memory (4 Kbytes area)
            return Cartridge.rom[address];
        }
        return 0; //mem[address & 0xffff] & 0xff;
    }

    public static boolean postWrite = false;
    private static int writeAddress;
    private static int writeValue;
    private static int writeAddress2;
    private static int writeValue2;
    private static int writeCount;

    public static void write(int address, int value) {
        if (postWrite) {
            writeAddress2 = address;
            writeValue2 = value;
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
            writePost(writeAddress2, writeValue2);
            postWrite = false;
            writeCount = 0;
        }
    }

    public static void writePost(int address, int value) {
        if (address >= 0x0080 && address <= 0x00FF) { // ram
            mem[address] = value;
        }
        else if (address >= 0x0180 && address <= 0x01FF) { // ram mirror
            mem[address - 0x100] = value;
        }
        else if (address >= 0x0000 && address <= 0x002C) { // TIA Write
            switch (address) {
                //   00      VSYNC   ......1.  vertical sync set-clear
                case 0x00 -> Tia2.VSYNC(value);
                //   01      VBLANK  11....1.  vertical blank set-clear
                case 0x01 -> Tia2.VBLANK(value);
                //   02      WSYNC   <strobe>  wait for leading edge of horizontal blank
                case 0x02 -> Tia2.WSYNC(value);
                
                //   04      NUSIZ0  ..111111  number-size player-missile 0
                case 0x04 -> Tia2.NUSIZ0 = value;
                //   05      NUSIZ1  ..111111  number-size player-missile 1
                case 0x05 -> Tia2.NUSIZ1 = value;

                //   06      COLUP0  1111111.  color-lum player 0 and missile 0
                case 0x06 -> Tia2.COLUP0 = value;
                //   07      COLUP1  1111111.  color-lum player 1 and missile 1
                case 0x07 -> Tia2.COLUP1 = value;

                //   08      COLUPF  1111111.  color-lum playfield and ball
                case 0x08 -> Tia2.COLUPF = value;
                //   09      COLUBK  1111111.  color-lum background
                case 0x09 -> Tia2.COLUBK = value;
                //   0A      CTRLPF  ..11.111  control playfield ball size & collisions
                case 0x0A -> Tia2.CTRLPF = value;

                //   0B      REFP0   ....1...  reflect player 0
                case 0x0B -> Tia2.REFP0 = value;
                //   0C      REFP1   ....1...  reflect player 1
                case 0x0C -> Tia2.REFP1 = value;

                //   0D      PF0     1111....  playfield register byte 0
                case 0x0D -> Tia2.PF0 = value;
                //   0E      PF1     11111111  playfield register byte 1
                case 0x0E -> Tia2.PF1 = value;
                //   0F      PF2     11111111  playfield register byte 2
                case 0x0F -> Tia2.PF2 = value;
                
                //   10      RESP0   <strobe>  reset player 0
                case 0x10 -> Tia2.RESP0(value);
                //   11      RESP1   <strobe>  reset player 1
                case 0x11 -> Tia2.RESP1(value);

                //   1B      GRP0    11111111  graphics player 0
                case 0x1B -> Tia2.GRP0 = value;
                //   1C      GRP1    11111111  graphics player 1
                case 0x1C -> Tia2.GRP1 = value;

                //   20      HMP0    1111....  horizontal motion player 0
                case 0x20 -> Tia2.HMP0 = value;
                //   21      HMP1    1111....  horizontal motion player 1
                case 0x21 -> Tia2.HMP1 = value;
                //   2A      HMOVE   <strobe>  apply horizontal motion
                case 0x2A -> Tia2.HMOVE(value);
                //   2B      HMCLR   <strobe>  clear horizontal motion registers
                case 0x2B -> Tia2.HMCLR(value);
                
                default -> {}
            }
        }	
        else if (address >= 0x0280 && address <= 0x0297) { //	PIA Ports and Timer
            switch (address) {
                //   0280    SWCHA   11111111  Port A; input or output  (read or write)
                case 0x280 -> Pia.SWCHA = value;

                //   0294    TIM1T   11111111  set 1 clock interval (838 nsec/interval)
                case 0x294 -> Pia.TIM1T(value);
                //   0295    TIM8T   11111111  set 8 clock interval (6.7 usec/interval)
                case 0x295 -> Pia.TIM8T(value);
                //   0296    TIM64T  11111111  set 64 clock interval (53.6 usec/interval)
                case 0x296 -> Pia.TIM64T(value);
                //   0297    T1024T  11111111  set 1024 clock interval (858.2 usec/interval)
                case 0x297 -> Pia.T1024T(value);

            }
        }
        else {
            System.out.println("address: " + address + " value: " + value);
        }
        /*
        else if (address >= 0x0000 && address <= 0x000D) { //	TIA Read (sometimes mirrored at 0030-003D)
        }
        else if (address >= 0x0080 && address <= 0x00FF) { //	PIA RAM (128 bytes)
            mem[address] = address;
        }
        else if (address >= 0xF000 && address <= 0xFFFF) { //	Cartridge Memory (4 Kbytes area)
            // Cartridge.rom[address] = value; // <- read only
        }
        */
        //mem[address & 0xffff] = value & 0xff;
    }

}