import java.awt.Color;

public class Atari2600PaletteSECAM {

    // SECAM possui apenas 8 cores: RGB simples de 3 bits
    private static final Color[] SECAM_BASE = new Color[] {
        new Color(0x00, 0x00, 0x00), // 0: Black
        new Color(0x00, 0x00, 0xFF), // 1: Blue
        new Color(0xFF, 0x00, 0x00), // 2: Red
        new Color(0xFF, 0x00, 0xFF), // 3: Magenta
        new Color(0x00, 0xFF, 0x00), // 4: Green
        new Color(0x00, 0xFF, 0xFF), // 5: Cyan
        new Color(0xFF, 0xFF, 0x00), // 6: Yellow
        new Color(0xFF, 0xFF, 0xFF)  // 7: White
    };

    public static final Color[] colorsSECAM = new Color[128];

    static {
        // Repete as 8 cores ao longo das 128 entradas
        for (int i = 0; i < 128; i++) {
            colorsSECAM[i] = SECAM_BASE[i % 8];
        }
    }
}