import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Display display = new Display();
            display.setPreferredSize(new Dimension(1000, 700));

            JFrame frame = new JFrame("Java Atari Emulator - Test #04");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(display, BorderLayout.NORTH);
            frame.getContentPane().add(new Display.DebuggerControlPainel(), BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            display.requestFocus();
            
            //String romFile = args[0];
            String romBasePath = "F:\\leo_hd_d_backup\\atari_2600_dev\\writing_an_emulator\\java_atari_2600_emulator_test_012_memory_mirrors\\roms\\";
            
            String romFile = romBasePath + "space.bin";
            
            String lstFile = romFile.substring(0, romFile.length() - 4) + ".lst";

            
            System.out.println("loading LST " + romFile + " file...");
            Debugger.loadLstFile(lstFile);
            
            //Debugger.addBreakPoint(0xf1eb);
            //Debugger.addBreakPoint(0xf011);

            System.out.println("starting ROM " + romFile + " file...");
            display.start(romFile);    

            //display.start("roms/pacman.bin");    
        });

        Debugger.startRemoteServer(8080);
    }

}
