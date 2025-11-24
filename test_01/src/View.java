import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.InputStream;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class View extends JPanel implements KeyListener {

    public View() {
        //loadRom("roms/river.bin");    
        //loadRom("roms/pitfall.bin");    
        //loadRom("roms/pacman.bin");    
        //loadRom("roms/space.bin");    
        loadRom("roms/enduro.bin");    
        Cpu.reset();
        // set entry point
        Cpu.RPC = Cartridge.rom[0xfffc] + (Cartridge.rom[0xfffd] << 8);
        System.out.println();
        addKeyListener(this);
    }

    private void loadRom(String res) {
        try (InputStream is = getClass().getResourceAsStream(res)) {
            int c;
            int romIndex = 0;
            while ((c = is.read()) >= 0 && romIndex < 4096) {
                Cartridge.rom[0xf000 + romIndex++] = c;
            }
        }
        catch (Exception e) {
            System.err.println("could not load rom!");
            System.exit(1);
        }
    }

    int totalCpuCycles = 0;
    int lastCrtY = 226;

    // return draw frame
    private boolean processAtari2600(int cycles) {

        totalCpuCycles = Cpu.tc;
        if (cycles > 10) {
            System.out.println();
        }

        Tia2.process(cycles);

        // timer
        if (Pia.timer >= cycles) {
            Pia.timer -= cycles;
            Pia.INTIM = Pia.timer / Pia.timerMultiple;
        }
        else {
            Pia.timer = 0;
            Pia.INTIM = ((byte) (Pia.INTIM - cycles)) & 0xff;
        }
        


        if (Tia2.renderTime) {
            Tia2.renderTime = false;
            return true;
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.drawLine(0, 0, getWidth(), getHeight());
        
        while (true) {
            Cpu.executeNextInstruction();
            int cycles = Cpu.tc - totalCpuCycles;
            boolean render = false;
            if (cycles > 0) {
                render |= processAtari2600(cycles);
                Bus.commitPostWrite();
            }
            //render |= processAtari2600(1);
            if (render) {
                break;
            }
        }
        
        //int dy = Tia2.lastCrtY - lastCrtY;
        //g.translate(50, 50 + 3 * dy);
        lastCrtY = Tia2.lastCrtY;
        g.drawImage(Tia2.crt, 0, 20 + (226 - Tia2.lastCrtY) * 2, (int) (228 * 2 * 2), 262 * 2,  null);

        try {
            Thread.sleep(1000/60);
        } catch (InterruptedException e) {
        }
        repaint();
        //System.out.println("next frame: " + System.nanoTime());
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            View view = new View();
            view.setPreferredSize(new Dimension(1000, 600));
            JFrame frame = new JFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().add(view);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            view.requestFocus();
        });
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> Pia.SWCHA = Pia.SWCHA & 0b11101111;
            case KeyEvent.VK_DOWN -> Pia.SWCHA = Pia.SWCHA & 0b11011111;
            case KeyEvent.VK_LEFT -> Pia.SWCHA = Pia.SWCHA & 0b10111111;
            case KeyEvent.VK_RIGHT -> Pia.SWCHA = Pia.SWCHA & 0b01111111;
            case KeyEvent.VK_Z -> Tia2.INPT4 = Tia2.INPT4 & 0b01111111;
            case KeyEvent.VK_R -> Pia.SWCHB = Pia.SWCHB & 0b00000000;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> Pia.SWCHA = Pia.SWCHA | 0b00010000;
            case KeyEvent.VK_DOWN -> Pia.SWCHA = Pia.SWCHA | 0b00100000;
            case KeyEvent.VK_LEFT -> Pia.SWCHA = Pia.SWCHA | 0b01000000;
            case KeyEvent.VK_RIGHT -> Pia.SWCHA = Pia.SWCHA | 0b10000000;
            case KeyEvent.VK_Z -> Tia2.INPT4 = Tia2.INPT4 | 0b10000000;
            case KeyEvent.VK_R -> Pia.SWCHB = Pia.SWCHB | 0b11111111;
        }
    }

}