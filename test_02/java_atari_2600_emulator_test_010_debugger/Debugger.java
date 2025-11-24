import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// very simple experimental remote debugger
public class Debugger {
    
    public static boolean paused = false;
    public static boolean stepped = false;
    public static Map<Integer, String> source = new HashMap<>();
    public static List<Integer> breakpoints = new ArrayList<>();
    public static Map<Integer, Integer> lineNumberToAddress = new HashMap<>();

    public static void loadLstFile(String res) {
        //InputStream is = Debugger.class.getResourceAsStream(res);
        try (   InputStream is = new FileInputStream(res);
                BufferedReader br = new BufferedReader(new InputStreamReader(is)); ) {

            String line = null;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("-")) {
                    continue;
                }
                line = line.replaceAll("\t", "   ");
                String[] values = line.split("\\s+");
                String addressStr = values[1];
                addressStr = addressStr.replaceAll("U", "");
                int address = Integer.valueOf(addressStr, 16);
                source.put(address, line);
                int lineNumber = Integer.valueOf(values[0]);
                lineNumberToAddress.put(lineNumber, address);
                System.out.println(values[0] + " address: " + address);
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addBreakPoint(int address) {
        breakpoints.add(address);
    }

    private static int lastBreakpointRPC = -1;

    public static void pauseIfReachedBreakpoint(int currentRPC) {
        if (breakpoints.contains(currentRPC) && currentRPC != lastBreakpointRPC) {
            lastBreakpointRPC = currentRPC;
            if (breakpoints.size() == 1) {
                lastBreakpointRPC = -1;
            }
            paused = true;

            // TODO 
            if (client != null && client.connected) {
                String source = Debugger.source.get(Cpu.RPC);
                int lineNumber = Integer.parseInt(source.split("\\s+")[0]);
                client.sendBreakpointReached(lineNumber + " " + Cpu.getRegsInf());
            }
        }
    }

    public static boolean stackFrameRequested;

    public static void pause() {
        paused = true;    
        stackFrameRequested = true;
    }
    
    public static void resume() {
        paused = false;    
    }
    
    public static void step() {
        stepped = true;
        stackFrameRequested = true;
    }

    public static void updateBreakpoints(String[] tokens) {
        lastBreakpointRPC = -1;
        breakpoints.clear();
        for (int i = 1; i < tokens.length; i++) {
            Integer address = lineNumberToAddress.get(Integer.valueOf(tokens[i]));
            if (address != null) {
                addBreakPoint(address);
            }
        }
    }
    
    // --- remote server test ---

    private static ServerSocket serverSocket;
    public static Client client;

    public static void startRemoteServer(int port) {
        try {
            System.out.println("Waiting remote connection on port " + port + " ...");
            serverSocket = new ServerSocket(port);
            client = new Client(serverSocket.accept());
            Thread clientThread = new Thread(client);
            clientThread.start();
            System.out.println("Remote client connected on port " + port + " ...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static class Client implements Runnable {
        
        public boolean connected;

        public Socket socket;
        private PrintWriter out;
        
        public Client(Socket socket) {
            this.socket = socket;
            try {
                out = new PrintWriter(socket.getOutputStream());
                //out.println("Hello from Java Atari 2600 Debugger Server !");
                //out.flush();
                connected = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        public void sendBreakpointReached(String data) {
            out.println("reached_breakpoint " + data);
            out.flush();
        }

        public void sendStackFrame(String data) {
            out.println(data);
            out.flush();
            stackFrameRequested = false;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                while (connected) {
                    String command = br.readLine();
                    if (command == null) {
                        connected = false;
                        System.out.println("disconnected ...");
                    }
                    else if (command.isEmpty()) {
                        continue;
                    }
                    else {
                        command = command.trim();
                        String[] tokens = command.split("\\s+");
                        switch (tokens[0]) {
                            case "resume" -> resume();
                            case "pause" -> pause(); 
                            case "step" -> step(); 
                            case "breakpoints" -> updateBreakpoints(tokens);
                        }
                        System.out.println("command: " + command);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

    }

    // --- test ---

    public static void main(String[] args) {
        Debugger.pause();
    }

}
