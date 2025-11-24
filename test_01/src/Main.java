import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

// https://github.com/SingleStepTests/65x02/tree/main/6502

public class Main {
    
    public static class Test {
        public String name;
        public State initialState;
        public State finalState;
        public List<CycleInfo> cycles = new ArrayList<>();
        public String id;
    }
    
    public static class State {
        public int pc;
        public int s;
        public int a;
        public int x;
        public int y;
        public int p;
        public Map<Integer, Integer> map = new HashMap<>();
    }
    
    public static State createState(JSONObject jsonObject) {
        State state = new State();
        state.pc = jsonObject.getInt("pc");
        state.s = jsonObject.getInt("s");
        state.a = jsonObject.getInt("a");
        state.x = jsonObject.getInt("x");
        state.y = jsonObject.getInt("y");
        state.p = jsonObject.getInt("p");

        JSONArray ram = jsonObject.getJSONArray("ram");
        for (int i = 0; i < ram.length(); i++) {
            JSONArray ramData = ram.getJSONArray(i);
            int address = ramData.getInt(0);
            int value = ramData.getInt(1);
            state.map.put(address, value);
        }

        return state;
    }
    
    public static class CycleInfo {
        int address;
        int value;
        String type;
    }

    public static void fillTestCycles(Test test, JSONArray cycles) {
        for (int i = 0; i < cycles.length(); i++) {
            JSONArray cyclesData = cycles.getJSONArray(i);
            
            //[address, value, type]
            // [42677, 161, "read"]

            int address = cyclesData.getInt(0);
            int value = cyclesData.getInt(1);
            String type = cyclesData.getString(2); // TODO <--- 
            CycleInfo cycleInfo = new CycleInfo();
            cycleInfo.address = address;
            cycleInfo.value = value;
            cycleInfo.type = type;

            test.cycles.add(cycleInfo);
        }
    }

    public static void main(String[] args) throws Exception {
    /*
        { 
            "name": "a1 b0 a1", 
            "initial": { 
                "pc": 42677, 
                "s": 178, 
                "a": 4, 
                "x": 142, 
                "y": 1, 
                "p": 38, 
                "ram": [ 
                    [42677, 161], 
                    [42678, 176], 
                    [42679, 161], 
                    [176, 138], 
                    [62, 24], 
                    [63, 91], 
                    [23320, 69]
                ]
            }, 
            "final": { 
                "pc": 42679, 
                "s": 178, 
                "a": 69, 
                "x": 142, 
                "y": 1, 
                "p": 36, 
                "ram": [ 
                    [62, 24], 
                    [63, 91], 
                    [176, 138], 
                    [23320, 69], 
                    [42677, 161], 
                    [42678, 176], 
                    [42679, 161]
                ]
            }, 
            "cycles": [ 
                [42677, 161, "read"], 
                [42678, 176, "read"], 
                [176, 138, "read"], 
                [62, 24, "read"], 
                [63, 91, "read"], 
                [23320, 69, "read"]
            ] 
        },
    */ 
        //String opcode = "61";
        //testOpcode(opcode, 6);

        testAllOpcodes(0x00);
    }

    private static void testAllOpcodes(int startTest) throws Exception {
        String opcodes = ""
            + "00,01,05,06,08,09,0A,0D,0E,10,11,15,16,18,19,1D,"
            + "1E,20,21,24,25,26,28,29,2A,2C,2D,2E,30,31,35,36,"
            + "38,39,3D,3E,40,41,45,46,48,49,4A,4C,4D,4E,50,51,"
            + "55,56,58,59,5D,5E,60,61,65,66,68,69,6A,6C,6D,6E," 
            + "70,71,75,76,78,79,7D,7E,81,84,85,86,88,8A,8C,8D," 
            + "8E,90,91,94,95,96,98,99,9A,9D,A0,A1,A2,A4,A5,A6,"
            + "A8,A9,AA,AC,AD,AE,B0,B1,B4,B5,B6,B8,B9,BA,BC,BD,"
            + "BE,C0,C1,C4,C5,C6,C8,C9,CA,CC,CD,CE,D0,D1,D5,D6,"
            + "D8,D9,DD,DE,E0,E1,E4,E5,E6,E8,E9,EA,EC,ED,EE,F0," 
            + "F1,F5,F6,F8,F9,FD,FE"; 
        
            //opcodes = "1D";

        String[] allOpcodes = opcodes.split(",");

        for (String opcode : allOpcodes) {
            
            opcode = opcode.trim();
            int opcodeNumber = Integer.valueOf(opcode, 16);

            if (opcodeNumber < startTest) continue;


            System.out.println("start testing ---> " + opcode);
            
            testOpcode(opcode, 0);

            System.out.println("end testing ---> " + opcode);
            System.out.println("");
        };

    }

    private static void testOpcode(String opcode, int lineNumber) throws Exception {
        String basePath = System.getProperty("user.dir") + "\\";
        String filePath = basePath + "nes\\" + opcode + ".json";

        //BufferedReader br = new BufferedReader(new FileReader(filePath));
        //String line;
        //while ((line = br.readLine()) != null) {
        //    System.out.println("line: " + line);
        //}
        // Lendo o conteúdo do arquivo como String
        String jsonString = "{ \"alltests\" : " 
            + new String(Files.readAllBytes(Paths.get(filePath)))       
            + "}";

        JSONObject jo = new JSONObject(jsonString);
        //System.out.println(jo);

        JSONArray alltests = jo.optJSONArray("alltests");
        for (int i = 0; i < alltests.length(); i++) {

            if (lineNumber > 0) {
                if ((i + 1) != lineNumber) continue;
            } 

            JSONObject testJSObject = alltests.getJSONObject(i);

            String name = testJSObject.getString("name"); // example: a1 b0 a1
            JSONObject initialState = testJSObject.getJSONObject("initial");
            JSONObject finalState = testJSObject.getJSONObject("final");
            JSONArray cycles = testJSObject.getJSONArray("cycles");
            System.out.println("test " + i + " initial_state: " + initialState + " final_state:" + finalState);
            System.out.println();

            //if (i == 5) {
            //    System.out.println("xxx");
            //}

            Test test = new Test();
            test.name = name;
            test.initialState = createState(initialState);
            test.finalState = createState(finalState);
            fillTestCycles(test, cycles);
            test.id = "<<<--- opcode " + opcode + " : line " + (i + 1) + " : " + testJSObject.toString() + " --->>>";

            startTest(i, test);
        }

        //JSONObject unprefixed = jo.getJSONObject("unprefixed");
        //JSONObject cbprefixed = jo.getJSONObject("cbprefixed");

    }

    public static void startTest(int testId, Test test) throws Exception {

        Bus.clearMem();
        Cpu.reset();
        
        // initial state

        Cpu.RA = test.initialState.a;
        Cpu.RX = test.initialState.x;
        Cpu.RY = test.initialState.y;
        Cpu.RS = test.initialState.s; // stack pointer
        Cpu.RP = test.initialState.p; // processor status
        Cpu.RPC = test.initialState.pc; // program counter      

        for (Integer address : test.initialState.map.keySet()) {
            Integer value = test.initialState.map.get(address);
            Bus.write(address, value);
        }

        // execute instruaction
        Cpu.executeNextInstruction();

        // check results
        if (Cpu.RA != test.finalState.a) {
            //     ra = 232 1110 1000
            //correto = 72  0100 1000
            throw new Exception("Test failed Cpu.RA != test.finalState.a \n test json: " + test.id);
        }
        if (Cpu.RX != test.finalState.x) {
            throw new Exception("Test failed Cpu.RX != test.finalState.x \n test json: " + test.id);
        }
        if (Cpu.RY != test.finalState.y) {
            throw new Exception("Test failed Cpu.RY != test.finalState.y \n test json: " + test.id);
        }
        if (Cpu.RS != test.finalState.s) {// stack pointer
            throw new Exception("Test failed Cpu.RS != test.finalState.s \n test json: " + test.id);
        }
        if (Cpu.RP != test.finalState.p) { // processor status
            //      rp=0010 0101
            // correto=1010 0101
            throw new Exception("Test failed Cpu.RP != test.finalState.p \n test json: " + test.id);
        }
        if (Cpu.RPC != test.finalState.pc) { // program counter      
            throw new Exception("Test failed Cpu.RPC != test.finalState.pc \n test json: " + test.id);
        }
        
        // teste memory content
        for (Integer address : test.finalState.map.keySet()) {
            Integer correctValue = test.finalState.map.get(address);
            Integer memValue = Bus.read(address);
            if (!memValue.equals(correctValue)) {
                throw new Exception("Test failed memory content addr=" + address + " mem_value=" + memValue + " correct_value=" + correctValue + " \n test json: " + test.id);
            }
        }        
        
        // test number of cycles
        int expectedCycles = test.cycles.size();
        if (Cpu.tc != expectedCycles) {
            throw new Exception("Test failed number of CPU cycles Cpu.tc=" + Cpu.tc + " expected=" + expectedCycles + "\n test json: " + test.id);
        }
    }

}