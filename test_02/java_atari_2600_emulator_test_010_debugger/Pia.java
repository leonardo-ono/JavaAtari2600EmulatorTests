public class Pia {

    // ram memory
    public static int[] ram = new int[0x10000];

    // /*
    // PIA 6532 - RAM, Switches, and Timer (Read/Write)
    //   80..FF  RAM     11111111  128 bytes RAM (in PIA chip) for variables and stack
    //   0280    SWCHA   11111111  Port A; input or output  (read or write)
    public static int SWCHA = 0xff;

    //   0281    SWACNT  11111111  Port A DDR, 0= input, 1=output
    //   0282    SWCHB   11111111  Port B; console switches (read only)
    public static int SWCHB = 0xff;
    //   0283    SWBCNT  11111111  Port B DDR (hardwired as input)

    //   0284    INTIM   11111111  Timer output (read only)
    public static int INTIM = 0;
    //   0285    INSTAT  11......  Timer Status (read only, undocumented)
    public static int INSTAT = 0;

    //   0294    TIM1T   11111111  set 1 clock interval (838 nsec/interval)
    public static void TIM1T(int value) {
        INTIM = value;
        timerMultiple = 1;
        timer = value * timerMultiple;
    }

    //   0295    TIM8T   11111111  set 8 clock interval (6.7 usec/interval)
    public static void TIM8T(int value) {
        INTIM = value;
        timerMultiple = 8;
        timer = value * timerMultiple;
    }

    //   0296    TIM64T  11111111  set 64 clock interval (53.6 usec/interval)
    public static int timer = 0;
    public static int timerMultiple = 1;
    public static void TIM64T(int value) {
        INTIM = value;
        timerMultiple = 64;
        timer = value * timerMultiple;
    }

    //   0297    T1024T  11111111  set 1024 clock interval (858.2 usec/interval)
    public static void T1024T(int value) {
        INTIM = value;
        timerMultiple = 1024;
        timer = value * timerMultiple;
    }

    // */    

}
