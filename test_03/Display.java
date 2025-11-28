import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.io.FileInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

public class Display extends Canvas implements KeyListener, Runnable {
    
    private BufferStrategy bs;
    private Thread mainThread;

    private final Font debuggerFont = new Font("Courier New", Font.PLAIN, 12);

    public Display() {
    }

    private static AudioFormat audioFormat;
    private static final int BUFFER_SIZE = 1024;
    private static SourceDataLine sourceDataLine;
    private byte[] mixedsample = new byte[1];

    private static void initializeAudio() {
        try {
            audioFormat = new AudioFormat( TiaAudio.AUDIO_SAMPLE_RATE, 8, 1, false, false);
            sourceDataLine = (SourceDataLine) AudioSystem.getSourceDataLine(audioFormat);
            sourceDataLine.open(audioFormat, BUFFER_SIZE);
            sourceDataLine.start();
        
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("could not initialize audio !");
        }   
    }

    private void loadRom(String res) {
        try (   InputStream is = new FileInputStream(res);
                //InputStream is = getClass().getResourceAsStream(res);
            ) {

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

    public void start(String romfile) {
        if (mainThread == null) {
            loadRom(romfile);

            Cpu.reset();

            // set entry point
            Cpu.RPC = Cartridge.rom[0xfffc] + (Cartridge.rom[0xfffd] << 8);
            System.out.println();
            addKeyListener(this);

            initializeAudio();
            setIgnoreRepaint(true);
            createBufferStrategy(2);
            bs = getBufferStrategy();
            mainThread = new Thread(this);
            mainThread.start();
            addKeyListener(this);
        }
    }

    int totalCpuCycles = 0;
    int lastCrtY = 226;

    // return 'needs to draw frame?'
    private boolean processAtari2600(int pixelCycles) {

        totalCpuCycles = Cpu.tc;
        
        Tia.process(pixelCycles);

        if (Tia.renderTime) {
            Tia.renderTime = false;
            return true;
        }
        return false;
    }
    
    @Override
    public void run() {

        int samplesRendered = 0;

        while (Cpu.running) {
            boolean render = false;
            while (!render) {

                // debugger
                if (!Debugger.paused) {
                    Debugger.pauseIfReachedBreakpoint(Cpu.RPC);
                }
                if (Debugger.paused && !Debugger.stepped && !Tia.wsyncHaltCpu) {
                    break; 
                }
                Debugger.stepped = false;

                int prevRPC = Cpu.RPC;
                int prevTC = Cpu.tc;
                Cpu.executeNextInstruction();

                int cycles = Cpu.tc - totalCpuCycles;

                // TODO process timer
                if (Pia.timer >= cycles) {
                    Pia.timer -= cycles;
                    Pia.INTIM = Pia.timer / Pia.timerMultiple;
                }
                else {
                    if (Pia.timer != 0) {
                        System.out.println();
                    }
                    cycles = cycles - Pia.timer;
                    Pia.timer = 0;
                    Pia.INTIM = ((byte) (Pia.INTIM - cycles)) & 0xff;
                }

                // TODO hack/workaround process instruction again to get the correct INTIM
                if (Pia.intimRead) {
                    Cpu.RPC = prevRPC;
                    Cpu.tc = prevTC;
                    Cpu.executeNextInstruction();
                    Pia.intimRead = false;
                }

                int pixelClocks = cycles * 3;
                if (pixelClocks > 0){
                    render |= processAtari2600(pixelClocks);
                    Bus.commitPostWrite();
                    render |= Tia.renderTime; // rendertime pode ser true no Bus.commitPostWrite() TODO
                }
    
                int samplesToRender = Cpu.tc / 38;
                while (samplesToRender > samplesRendered) {
                    int out = TiaAudio.getMixedNextSample();
                    mixedsample[0] = (byte) out;
                    sourceDataLine.write(mixedsample, 0, 1);
                    samplesRendered++;
                }
            }

            Graphics2D g = (Graphics2D) bs.getDrawGraphics();
            lastCrtY = Tia.lastCrtY;
            // 20 + (226 - Tia2.lastCrtY) * 2
            g.drawImage(Tia.crt, 0, 0, (int) (228 * 2 * 2), 262 * 2,  null);
            if (Tia.renderTime) {
                Tia.crtg.clearRect(0, 0, 228, 262);
                Tia.renderTime = false;

            }
            
            g.clearRect(0, 262*2, 228 * 4, 200);

            if (Debugger.paused) {
                g.setColor(Color.WHITE);
                g.setXORMode(Color.BLACK);
                g.drawLine(Tia.crtX * 4, 0, Tia.crtX * 4, 262 * 2);
                g.drawLine(0, Tia.crtY * 2, 228 * 4, Tia.crtY * 2);
                g.setPaintMode();

                drawSource(g);
            }

            g.setColor(Color.BLACK);
            g.drawString("lastRemoveLineNumber: " + lastRemoveLineNumber, 50, 262 * 2 + 20);

            g.dispose();
            bs.show();        
        }
    }
    
    private int lastRemoveLineNumber = 0;

    private void drawSource(Graphics2D g) {
        g.setFont(debuggerFont);
        int y = 0;
        for (int i = -5; i < 10; i++) {
            String source = Debugger.source.get(Cpu.RPC + i);
            if (source != null) {
                g.setColor(Color.BLACK);
                if (i == 0) {
                    g.fillRect(0, 262 * 2 + (50 + y) - 11, 228 * 4, 13);
                    g.setColor(Color.WHITE);
                }
                g.drawString(source, 20, 262 * 2 + (50 + y));
                y += 12;
            }
        }

        // TODO 
        if (Debugger.client != null && Debugger.stackFrameRequested) {
            String source = Debugger.source.get(Cpu.RPC);
            int lineNumber = Integer.parseInt(source.split("\\s+")[0]);
            if (lastRemoveLineNumber != lineNumber) {
                Debugger.client.sendStackFrame(lineNumber + " " + Cpu.getRegsInf());
                lastRemoveLineNumber = lineNumber;
            }
        }
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
            case KeyEvent.VK_Z -> Tia.INPT4 = Tia.INPT4 & 0b01111111;

            case KeyEvent.VK_1 -> Pia.SWCHB = Pia.SWCHB & 0b00001000; // reset
            case KeyEvent.VK_2 -> Pia.SWCHB = Pia.SWCHB & 0b01000000; // difficulty

            case KeyEvent.VK_3 -> Pia.SWCHB = Pia.SWCHB & 0b10000000; // ?
            case KeyEvent.VK_4 -> Pia.SWCHB = Pia.SWCHB & 0b00100000; // ?
            case KeyEvent.VK_5 -> Pia.SWCHB = Pia.SWCHB & 0b00010000; // ?
            case KeyEvent.VK_6 -> Pia.SWCHB = Pia.SWCHB & 0b00000100; // ?
            case KeyEvent.VK_7 -> Pia.SWCHB = Pia.SWCHB & 0b00000010; // ?
            case KeyEvent.VK_8 -> Pia.SWCHB = Pia.SWCHB & 0b00000001; // ?

            // debugger
            case KeyEvent.VK_F5 -> Debugger.resume();
            case KeyEvent.VK_F6 -> Debugger.pause();
            case KeyEvent.VK_F10 -> Debugger.step();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> Pia.SWCHA = Pia.SWCHA | 0b00010000;
            case KeyEvent.VK_DOWN -> Pia.SWCHA = Pia.SWCHA | 0b00100000;
            case KeyEvent.VK_LEFT -> Pia.SWCHA = Pia.SWCHA | 0b01000000;
            case KeyEvent.VK_RIGHT -> Pia.SWCHA = Pia.SWCHA | 0b10000000;
            case KeyEvent.VK_Z -> Tia.INPT4 = Tia.INPT4 | 0b10000000;

            case KeyEvent.VK_1 -> Pia.SWCHB = Pia.SWCHB | 0b11110111; // reset
            case KeyEvent.VK_2 -> Pia.SWCHB = Pia.SWCHB | 0b10111111; // difficulty

            case KeyEvent.VK_3 -> Pia.SWCHB = Pia.SWCHB | 0b01111111; // ?
            case KeyEvent.VK_4 -> Pia.SWCHB = Pia.SWCHB | 0b11011111; // ?
            case KeyEvent.VK_5 -> Pia.SWCHB = Pia.SWCHB | 0b11101111; // ?
            case KeyEvent.VK_6 -> Pia.SWCHB = Pia.SWCHB | 0b11111011; // ?
            case KeyEvent.VK_7 -> Pia.SWCHB = Pia.SWCHB | 0b11111101; // ?
            case KeyEvent.VK_8 -> Pia.SWCHB = Pia.SWCHB | 0b11111110; // ?
        }
    }

    public static class DebuggerControlPainel extends JPanel {
        public DebuggerControlPainel() {
            setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
            JButton buttonPause;
            JButton buttonResume;
            JButton buttonStep;
            add(buttonResume = new JButton("resume (F5)"));
            add(buttonPause = new JButton("pause (F6)"));
            add(buttonStep = new JButton("step (F10)"));
            buttonPause.setFocusable(false);
            buttonResume.setFocusable(false);
            buttonStep.setFocusable(false);
            buttonPause.addActionListener(e -> Debugger.pause());
            buttonResume.addActionListener(e -> Debugger.resume());
            buttonStep.addActionListener(e -> Debugger.step());
        }
    }

}