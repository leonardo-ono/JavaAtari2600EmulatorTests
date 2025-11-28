public class Cartridge {

/*
Cartridge Memory (4 Kbytes area)
  F000-FFFF ROM   11111111  Cartridge ROM (4 Kbytes max)
  F000-F07F RAMW  11111111  Cartridge RAM Write (optional 128 bytes)
  F000-F0FF RAMW  11111111  Cartridge RAM Write (optional 256 bytes)
  F080-F0FF RAMR  11111111  Cartridge RAM Read (optional 128 bytes)
  F100-F1FF RAMR  11111111  Cartridge RAM Read (optional 256 bytes)
  003F      BANK  ......11  Cart Bank Switching (for some 8K ROMs, 4x2K)
  FFF4-FFFB BANK  <strobe>  Cart Bank Switching (for ROMs greater 4K)
  FFFC-FFFD ENTRY 11111111  Cart Entrypoint (16bit pointer)
  FFFE-FFFF BREAK 11111111  Cart Breakpoint (16bit pointer)
*/    

    public static int[] rom = new int[0x10000];
    
}
