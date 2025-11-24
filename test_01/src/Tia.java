import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;


// This was a complete disaster. 
public class Tia {
  
  public static BufferedImage crt = new BufferedImage(228, 262, BufferedImage.TYPE_INT_RGB);
  public static Graphics2D crtg = (Graphics2D) crt.getGraphics();
  private static int crtX;
  private static int crtY;

  // TIA - WRITE ADDRESS SUMMARY (Write only)
  public static boolean renderTime = false;
  //   00      VSYNC   ......1.  vertical sync set-clear
  private static boolean vsyncSet = false;
  public static void VSYNC(int arg) {
    vsyncSet = false;
    if ((arg & 2) == 2) {
      vsyncSet = true;
      //System.out.println("new frame, last scanline: " + crtY);
      crtY = 0;
      renderTime = true;
    }
  }

  //   01      VBLANK  11....1.  vertical blank set-clear
  private static boolean vblankSet = false;
  public static void VBLANK(int arg) {
    vblankSet = (arg & 2) == 2;
  }

  //   02      WSYNC   <strobe>  wait for leading edge of horizontal blank
  public static boolean wsyncHaltCpu = false;
  public static void WSYNC(int arg) {
    wsyncHaltCpu = true;
  }

  //   03      RSYNC   <strobe>  reset horizontal sync counter
  //   04      NUSIZ0  ..111111  number-size player-missile 0
  //   05      NUSIZ1  ..111111  number-size player-missile 1
  
  //   06      COLUP0  1111111.  color-lum player 0 and missile 0
  public  static int COLUP0 = 0;
  //   07      COLUP1  1111111.  color-lum player 1 and missile 1
  public  static int COLUP1 = 0;

  //   08      COLUPF  1111111.  color-lum playfield and ball
  public static int COLUPF = 0;

  //   09      COLUBK  1111111.  color-lum background
  public static int COLUBK = 0;

  //   0A      CTRLPF  ..11.111  control playfield ball size & collisions
  public static int CTRLPF = 0;

  //   0B      REFP0   ....1...  reflect player 0
  //   0C      REFP1   ....1...  reflect player 1

  //   0D      PF0     1111....  playfield register byte 0
    public  static int PF0 = 0;
  //   0E      PF1     11111111  playfield register byte 1
    public  static int PF1 = 0;
  //   0F      PF2     11111111  playfield register byte 2
    public  static int PF2 = 0;

  //   10      RESP0   <strobe>  reset player 0
  //   11      RESP1   <strobe>  reset player 1
  //   12      RESM0   <strobe>  reset missile 0
  //   13      RESM1   <strobe>  reset missile 1
  //   14      RESBL   <strobe>  reset ball
  //   15      AUDC0   ....1111  audio control 0
  //   16      AUDC1   ....1111  audio control 1
  //   17      AUDF0   ...11111  audio frequency 0
  //   18      AUDF1   ...11111  audio frequency 1
  //   19      AUDV0   ....1111  audio volume 0
  //   1A      AUDV1   ....1111  audio volume 1
  
  //   1B      GRP0    11111111  graphics player 0
  public  static int GRP0 = 0;
  //   1C      GRP1    11111111  graphics player 1
  public  static int GRP1 = 0;

  //   1D      ENAM0   ......1.  graphics (enable) missile 0
  //   1E      ENAM1   ......1.  graphics (enable) missile 1
  //   1F      ENABL   ......1.  graphics (enable) ball
  //   20      HMP0    1111....  horizontal motion player 0
  //   21      HMP1    1111....  horizontal motion player 1
  //   22      HMM0    1111....  horizontal motion missile 0
  //   23      HMM1    1111....  horizontal motion missile 1
  //   24      HMBL    1111....  horizontal motion ball
  //   25      VDELP0  .......1  vertical delay player 0
  //   26      VDELP1  .......1  vertical delay player 1
  //   27      VDELBL  .......1  vertical delay ball
  //   28      RESMP0  ......1.  reset missile 0 to player 0
  //   29      RESMP1  ......1.  reset missile 1 to player 1
  //   2A      HMOVE   <strobe>  apply horizontal motion
  //   2B      HMCLR   <strobe>  clear horizontal motion registers
  //   2C      CXCLR   <strobe>  clear collision latches
  //TIA - READ ADDRESS SUMMARY (Read only)
  // 30      CXM0P   11......  read collision M0-P1, M0-P0 (Bit 7,6)
  // 31      CXM1P   11......  read collision M1-P0, M1-P1
  // 32      CXP0FB  11......  read collision P0-PF, P0-BL
  // 33      CXP1FB  11......  read collision P1-PF, P1-BL
  // 34      CXM0FB  11......  read collision M0-PF, M0-BL
  // 35      CXM1FB  11......  read collision M1-PF, M1-BL
  // 36      CXBLPF  1.......  read collision BL-PF, unused
  // 37      CXPPMM  11......  read collision P0-P1, M0-M1
  // 38      INPT0   1.......  read pot port
  // 39      INPT1   1.......  read pot port
  // 3A      INPT2   1.......  read pot port
  // 3B      INPT3   1.......  read pot port

  // 3C      INPT4   1.......  read input
  public static int INPT4 = 0xff; // botao P0 (invertido)
  // 3D      INPT5   1.......  read input
  public static int INPT5 = 0xff; // botao P1 (invertido)

  // pixel clock constant for NTSC
  private static final int COLOR_CLOCK = 3580000;
  private static final int SCANLINE_WIDTH = 228;
  private static final int SCANLINE_HEIGHT = 262;

  private static int currentPixelClock = 0;
  private static int targetPixelClock = 0;

  /*
The TIA uses the same polynomial counter circuit for all of its
horizontal counters - there is a HSync counter, two Player
Position and two Missile Position counters, and the Ball Position
counter. The sound generator has a more complex design involving
another polynomial counter or two - I haven't delved into the
workings of this one yet.   */
  private static int hsyncCounter;
  private static int hP0Counter;
  private static int hP1Counter;
  private static int hM0Counter;
  private static int hM1Counter;
  private static int hBallCounter;

  private static int PFindex;
  private static boolean hblank = true; // enable/disable display output
  
  public static void process(int cpuCycle) {
    targetPixelClock += cpuCycle * 3;
    while (targetPixelClock - currentPixelClock >= 4) {
      currentPixelClock += 4;
      hsyncCounter = (currentPixelClock / 4) % 57;
      crtX = hsyncCounter * 4;

      if (hsyncCounter == 17) { // note: actually, hblank is reset at HCount=16
        hblank = false;         //       but the pixel itself start drawing at HCount=17
      }

      PFindex = -1;
      if (hsyncCounter >= 17) {
        PFindex = hsyncCounter - 17;
        drawPF(PFindex);
      }

      if (hsyncCounter == 56) { // note: actually, hblank is enabled at HCount=224
        wsyncHaltCpu = false;
      }

      if (hsyncCounter == 56) { // note: actually, hblank is enabled at HCount=224
        hblank = true;         //       but it disables on next HCount
        crtY++; // next line
        
      }
  
      //System.out.println("HCount: " + hsyncCounter + " PF:" + PFindex + " hblank: " + hblank + " currentCpuCycle: " + (currentPixelClock / 3.0) + " crtY=" + crtY + " vsync: " + vsyncSet);
    }

  }

  private static void drawPF(int pFindex2) {
    int pfIndex = pFindex2;
    boolean center = false;
    if (pfIndex >= 20) {
      pfIndex -= 20;
      center = true;
    }

    if (vblankSet) {
      crtg.setColor(Color.BLACK);
    }
    else {
      crtg.setColor(Atari2600PaletteNTSC.colorsNTSC[COLUBK>>1]);
      
      if (center && (CTRLPF & 1)==1) { // mirror playfield
        pfIndex = 19 - pfIndex;
      }

      if (pfIndex < 4) {
        if (((PF0 >> 4) & (1 << pfIndex)) > 0) {
          crtg.setColor(Atari2600PaletteNTSC.colorsNTSC[COLUPF>>1]);
        }
      }
      else if (pfIndex < 12) {
        if (((Integer.reverse(PF1) >> 24) & (1 << (pfIndex - 4))) > 0) {
          crtg.setColor(Atari2600PaletteNTSC.colorsNTSC[COLUPF>>1]);
        }
      }
      else if (pfIndex < 20) {
        if ((PF2 & (1 << (pfIndex - 12))) > 0) {
          crtg.setColor(Atari2600PaletteNTSC.colorsNTSC[COLUPF>>1]);
        }
      }
    
    }

    crtg.drawLine(crtX, crtY, crtX + 4, crtY);

    // TODO sprite test
    if (pFindex2 == 1) {
      for (int i = 0; i < 4; i++) {
        if ((GRP0 & (1 << i)) > 0) {
          crt.setRGB(crtX + i, crtY, 0xff0000ff);
        }
      }
    }
    else if (pFindex2 == 2) {
      for (int i = 0; i < 4; i++) {
        if (((GRP0 >> 4) & (1 << i)) > 0) {
          crt.setRGB(crtX + i, crtY, 0xff0000ff);
        }
      }
    }

    if (pFindex2 == 3) {
      for (int i = 0; i < 4; i++) {
        if ((GRP1 & (1 << i)) > 0) {
          crt.setRGB(crtX + i, crtY, 0x0000ffff);
        }
      }
    }
    else if (pFindex2 == 4) {
      for (int i = 0; i < 4; i++) {
        if (((GRP1 >> 4) & (1 << i)) > 0) {
          crt.setRGB(crtX + i, crtY, 0x0000ffff);
        }
      }
    }

  }

  public static void main(String[] args) throws InterruptedException {
    for (int i = 0; i < 76; i++) {
      Tia.process(1);
      Thread.sleep(10);
    }
  }

}
